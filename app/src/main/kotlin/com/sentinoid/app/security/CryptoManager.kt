package com.sentinoid.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import androidx.biometric.BiometricPrompt
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "sentinoid_master_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val KEY_SIZE = 256

        const val PREFS_VAULT_INITIALIZED = "vault_initialized"
        const val PREFS_SHARD_1_ENCRYPTED = "shard_1_encrypted"
        const val PREFS_SHARD_2_ENCRYPTED = "shard_2_encrypted"
        const val PREFS_SHARD_3_ENCRYPTED = "shard_3_encrypted"
        const val PREFS_HONEYPOT_DATA = "honeypot_data"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val securePreferences = SecurePreferences(context)

    fun isVaultInitialized(): Boolean {
        return securePreferences.getBoolean(PREFS_VAULT_INITIALIZED, false)
    }

    fun initializeVault() {
        try {
            createMasterKey()
            securePreferences.putBoolean(PREFS_VAULT_INITIALIZED, true)
        } catch (e: Exception) {
            throw SecurityException("Failed to initialize vault", e)
        }
    }

    private fun createMasterKey(): SecretKey {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .setRandomizedEncryptionRequired(true)

        // Try to use StrongBox if available
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
        } catch (e: StrongBoxUnavailableException) {
            // Fallback to TEE
            builder.setIsStrongBoxBacked(false)
        }

        if (android.os.Build.VERSION.SDK_INT < 30) {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(30)
        }

        keyGen.init(builder.build())
        return keyGen.generateKey()
    }

    private fun getMasterKey(): SecretKey {
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw SecurityException("Master key not found or invalidated")
    }

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        // Combine IV + ciphertext
        val combined = ByteBuffer.allocate(iv.size + ciphertext.size)
            .put(iv)
            .put(ciphertext)
            .array()

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(ciphertext: String): String {
        val combined = Base64.decode(ciphertext, Base64.NO_WRAP)

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    fun encryptWithPasskey(plaintext: String, cipher: Cipher): String {
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    fun decryptWithPasskey(ciphertext: String, cipher: Cipher): String {
        val encrypted = Base64.decode(ciphertext, Base64.NO_WRAP)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    fun getEncryptCipher(): Cipher {
        return Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getMasterKey())
        }
    }

    fun getDecryptCipher(iv: ByteArray): Cipher {
        return Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
    }

    fun isKeyValid(): Boolean {
        return try {
            keyStore.getKey(KEY_ALIAS, null) != null
        } catch (e: Exception) {
            false
        }
    }

    fun purgeKeys() {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
            securePreferences.clear()
        } catch (e: Exception) {
            throw SecurityException("Failed to purge keys", e)
        }
    }

    fun generateSecureRandom(bytes: Int): ByteArray {
        return ByteArray(bytes).apply {
            SecureRandom().nextBytes(this)
        }
    }

    fun hashData(data: ByteArray): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
}
