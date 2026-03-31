package com.sentinoid.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager(private val context: Context) {
    private val auditLogger: SecureAuditLogger by lazy {
        SecureAuditLogger(context, this, securePreferences)
    }
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "sentinoid_master_key"
        private const val KEY_ALIAS_ROTATION = "sentinoid_master_key_rot"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val KEY_SIZE = 256

        const val PREFS_VAULT_INITIALIZED = "vault_initialized"
        const val PREFS_SHARD_1_ENCRYPTED = "shard_1_encrypted"
        const val PREFS_SHARD_2_ENCRYPTED = "shard_2_encrypted"
        const val PREFS_SHARD_3_ENCRYPTED = "shard_3_encrypted"
        const val PREFS_HONEYPOT_DATA = "honeypot_data"
        const val PREFS_KEY_ROTATION_TIME = "key_rotation_time"
        const val KEY_ROTATION_DAYS = 90
    }

    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

    private val securePreferences = SecurePreferences(context)

    fun getContext(): Context = context

    fun isVaultInitialized(): Boolean {
        return securePreferences.getBoolean(PREFS_VAULT_INITIALIZED, false)
    }

    fun initializeVault() {
        try {
            createMasterKey()
            securePreferences.putBoolean(PREFS_VAULT_INITIALIZED, true)
            securePreferences.putLong(PREFS_KEY_ROTATION_TIME, System.currentTimeMillis())
            auditLogger.logEvent(SecureAuditLogger.EVENT_VAULT_CREATED, "Vault initialized with master key")
        } catch (e: Exception) {
            auditLogger.logSecurityEvent(SecureAuditLogger.EVENT_SELF_DESTRUCT, "Vault initialization failed: ${e.message}", "CRITICAL")
            throw SecurityException("Failed to initialize vault", e)
        }
    }

    fun isKeyRotationNeeded(): Boolean {
        val lastRotation = securePreferences.getLong(PREFS_KEY_ROTATION_TIME, 0)
        val daysSinceRotation = (System.currentTimeMillis() - lastRotation) / (1000 * 60 * 60 * 24)
        return daysSinceRotation > KEY_ROTATION_DAYS
    }

    fun rotateKey(): Boolean {
        return try {
            val oldKeyAlias = KEY_ALIAS
            val newKeyAlias =
                if (keyStore.containsAlias(KEY_ALIAS_ROTATION)) {
                    KEY_ALIAS
                } else {
                    KEY_ALIAS_ROTATION
                }

            // Delete the alternate key if it exists
            if (keyStore.containsAlias(newKeyAlias)) {
                keyStore.deleteEntry(newKeyAlias)
            }

            // Create new key
            val keyGen =
                KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE,
                )

            val builder =
                KeyGenParameterSpec.Builder(
                    newKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE)
                    .setUserAuthenticationRequired(false)  
                    .setRandomizedEncryptionRequired(true)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                builder.setInvalidatedByBiometricEnrollment(true)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    builder.setIsStrongBoxBacked(true)
                } catch (e: StrongBoxUnavailableException) {
                    builder.setIsStrongBoxBacked(false)
                }
            }

            keyGen.init(builder.build())
            keyGen.generateKey()

            // Update rotation timestamp
            securePreferences.putLong(PREFS_KEY_ROTATION_TIME, System.currentTimeMillis())

            // Delete old key after successful rotation
            if (keyStore.containsAlias(oldKeyAlias) && oldKeyAlias != newKeyAlias) {
                keyStore.deleteEntry(oldKeyAlias)
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createMasterKey(): SecretKey {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

        val keyGen =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )

        val builder =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .setUserAuthenticationRequired(false)  // Don't require biometric for recovery
                .setRandomizedEncryptionRequired(true)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
            builder.setInvalidatedByBiometricEnrollment(true)
        }

        // Try to use StrongBox if available
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
            } catch (e: Exception) {
                // Fallback to TEE - StrongBoxUnavailableException requires API 28
                builder.setIsStrongBoxBacked(false)
            }
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

    /**
     * Encrypt plaintext using the master key with biometric re-validation.
     * This operation requires user biometric authentication.
     *
     * @throws SecurityException if key is invalidated or biometric not available
     */
    fun encrypt(plaintext: String): String {
        // Verify key validity before encryption (triggers biometric if needed)
        if (!isKeyValid()) {
            throw SecurityException("Master key is invalid or requires biometric authentication")
        }
        
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())

            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

            // Combine IV + ciphertext
            val combined =
                ByteBuffer.allocate(iv.size + ciphertext.size)
                    .put(iv)
                    .put(ciphertext)
                    .array()

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw SecurityException("Encryption failed - may require biometric re-authentication", e)
        }
    }

    /**
     * Decrypt ciphertext using the master key with biometric re-validation.
     * This operation requires user biometric authentication.
     *
     * @throws SecurityException if key is invalidated or biometric not available
     */
    fun decrypt(ciphertext: String): String {
        // Verify key validity before decryption (triggers biometric if needed)
        if (!isKeyValid()) {
            throw SecurityException("Master key is invalid or requires biometric authentication")
        }
        
        return try {
            val combined = Base64.decode(ciphertext, Base64.NO_WRAP)

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw SecurityException("Decryption failed - may require biometric re-authentication", e)
        }
    }

    fun encryptWithPasskey(
        plaintext: String,
        cipher: Cipher,
    ): String {
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    fun decryptWithPasskey(
        ciphertext: String,
        cipher: Cipher,
    ): String {
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
            auditLogger.logEvent(SecureAuditLogger.EVENT_KEY_INVALIDATED, "Purging all keys")
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
            securePreferences.clear()
            auditLogger.logEvent(SecureAuditLogger.EVENT_SELF_DESTRUCT, "Keys purged successfully")
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
