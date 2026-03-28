package com.sentinoid.shield

import android.app.Service
import android.content.Intent
import android.os.BatteryManager
import android.os.IBinder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.os.Build
import java.io.File
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Power-optimized VaultService with lazy key initialization and chunked file I/O.
 */
class VaultService : Service() {
    private val TAG = "VaultService"
    private val keyAlias = "SentinoidVaultKey"
    private val lazySecretKey = AtomicReference<SecretKey?>(null)
    private var isLowPowerMode = false
    private val CHUNK_SIZE = 8192 // 8KB chunks for memory efficiency

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        updatePowerMode()
    }

    /**
     * Lazy key initialization - only creates key when first needed.
     */
    private fun getOrCreateKey(): SecretKey {
        lazySecretKey.get()?.let { return it }
        
        synchronized(this) {
            lazySecretKey.get()?.let { return it }
            
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = if (keyStore.containsAlias(keyAlias)) {
                keyStore.getKey(keyAlias, null) as SecretKey
            } else {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }
            lazySecretKey.set(key)
            return key
        }
    }

    fun encryptString(plainText: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val cipherText = Base64.encodeToString(
            cipher.doFinal(plainText.toByteArray()),
            Base64.NO_WRAP
        )
        return Pair(cipherText, iv)
    }

    fun decryptString(cipherText: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        val plainBytes = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP))
        return String(plainBytes)
    }

    /**
     * Memory-efficient chunked file encryption for large files.
     * Uses streaming to avoid loading entire file into memory.
     */
    suspend fun encryptFileChunked(inputFile: File, outputFile: File): String {
        return withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            
            inputFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(CHUNK_SIZE)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val encrypted = if (bytesRead == CHUNK_SIZE) {
                            cipher.update(buffer)
                        } else {
                            cipher.doFinal(buffer.copyOf(bytesRead))
                        }
                        output.write(encrypted)
                        
                        // Yield on low power mode
                        if (isLowPowerMode && bytesRead > 0) {
                            kotlinx.coroutines.yield()
                        }
                    }
                }
            }
            iv
        }
    }

    /**
     * Memory-efficient chunked file decryption.
     */
    suspend fun decryptFileChunked(inputFile: File, outputFile: File, iv: String) {
        withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            
            inputFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(CHUNK_SIZE + 16) // Account for GCM tag
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val decrypted = if (bytesRead == buffer.size) {
                            cipher.update(buffer)
                        } else {
                            cipher.doFinal(buffer.copyOf(bytesRead))
                        }
                        output.write(decrypted)
                        
                        if (isLowPowerMode && bytesRead > 0) {
                            kotlinx.coroutines.yield()
                        }
                    }
                }
            }
        }
    }

    /**
     * Quick encrypt for small files (legacy compatibility).
     */
    fun encryptFile(inputFile: File, outputFile: File): String {
        if (inputFile.length() > 10 * 1024 * 1024) { // 10MB threshold
            Log.w(TAG, "Large file detected, consider using encryptFileChunked")
        }
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val plainBytes = inputFile.readBytes()
        val cipherBytes = cipher.doFinal(plainBytes)
        outputFile.writeBytes(cipherBytes)
        return iv
    }

    fun decryptFile(inputFile: File, outputFile: File, iv: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        val cipherBytes = inputFile.readBytes()
        val plainBytes = cipher.doFinal(cipherBytes)
        outputFile.writeBytes(plainBytes)
    }

    private fun updatePowerMode() {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        isLowPowerMode = level < 20
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            isLowPowerMode = isLowPowerMode || powerManager.isPowerSaveMode
        }
    }

    fun clearKey() {
        lazySecretKey.set(null)
    }
}
