package com.sentinoid.shield

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultService : Service() {

    private val keyAlias = "SentinoidVaultKey"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        initVaultKey()
    }

    fun initVaultKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(keyAlias)) {
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
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    fun encryptString(plainText: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.DEFAULT)
        val cipherText = Base64.encodeToString(cipher.doFinal(plainText.toByteArray()), Base64.DEFAULT)
        return Pair(cipherText, iv)
    }

    fun decryptString(cipherText: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        val plainBytes = cipher.doFinal(Base64.decode(cipherText, Base64.DEFAULT))
        return String(plainBytes)
    }

    fun encryptFile(inputFile: File, outputFile: File): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.DEFAULT)
        val plainBytes = inputFile.readBytes()
        val cipherBytes = cipher.doFinal(plainBytes)
        outputFile.writeBytes(cipherBytes)
        return iv
    }

    fun decryptFile(inputFile: File, outputFile: File, iv: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        val cipherBytes = inputFile.readBytes()
        val plainBytes = cipher.doFinal(cipherBytes)
        outputFile.writeBytes(plainBytes)
    }
}
