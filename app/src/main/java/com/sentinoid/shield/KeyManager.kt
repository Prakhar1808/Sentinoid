package com.sentinoid.shield

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.crypto.KeyGenerator

import com.sentinoid.app.security.BIP39Provider

object KeyManager {
    private const val DB_KEY_ALIAS = "SentinoidDbKey"
    private const val HANDSHAKE_KEY_ALIAS = "SentinoidHandshakeKey"
    private const val PREFS_NAME = "SentinoidSecurePrefs"
    private const val ENCRYPTED_PASS_KEY = "db_passphrase"
    private const val MNEMONIC_KEY = "mnemonic_phrase"

    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        val masterKey =
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun hasPassphrase(context: Context): Boolean {
        val prefs = getEncryptedPrefs(context)
        return prefs.contains(MNEMONIC_KEY)
    }

    fun getMnemonic(context: Context): String? {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(MNEMONIC_KEY, null)
    }

    fun generateMnemonic(): String {
        val provider = BIP39Provider()
        val mnemonicSeed = provider.generateMnemonic(24)
        return mnemonicSeed.words.joinToString(" ")
    }

    fun saveMnemonicSecure(
        context: Context,
        mnemonic: String,
    ) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().putString(MNEMONIC_KEY, mnemonic).apply()
    }

    /**
     * Derives a 32-byte (256-bit) passphrase from the BIP39 mnemonic.
     * This is used to unlock the SQLCipher database.
     */
    fun derivePassphraseFromMnemonic(mnemonic: String): ByteArray {
        val provider = BIP39Provider()
        val words = mnemonic.split(" ")
        val seed = provider.mnemonicToSeed(words)
        // We take the first 32 bytes of the 64-byte 512-bit seed for AES-256 compatibility
        val passphrase = seed.copyOfRange(0, 32)
        seed.fill(0) // Scrubber
        return passphrase
    }

    fun getPassphrase(context: Context): ByteArray {
        val mnemonic = getMnemonic(context) ?: throw IllegalStateException("Mnemonic not set")
        return derivePassphraseFromMnemonic(mnemonic)
    }

    /**
     * TEE-backed RSA KeyPair for the AOA Handshake.
     */
    fun getOrCreateHandshakeKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (!keyStore.containsAlias(HANDSHAKE_KEY_ALIAS)) {
            val kpg =
                KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA,
                    "AndroidKeyStore",
                )
            val parameterSpec =
                KeyGenParameterSpec.Builder(
                    HANDSHAKE_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                ).run {
                    setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    setKeySize(2048)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        setUserAuthenticationRequired(true)
                        setInvalidatedByBiometricEnrollment(true)
                    }
                    build()
                }
            kpg.initialize(parameterSpec)
            kpg.generateKeyPair()
        }

        val entry = keyStore.getEntry(HANDSHAKE_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return KeyPair(entry.certificate.publicKey, entry.privateKey)
    }

    fun createSelfDestructingKey() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                "SelfDestructKey",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        keyGenerator.generateKey()
    }

    fun getOrCreatePassphrase(context: Context): ByteArray {
        return if (hasPassphrase(context)) {
            getPassphrase(context)
        } else {
            val mnemonic = generateMnemonic()
            saveMnemonicSecure(context, mnemonic)
            derivePassphraseFromMnemonic(mnemonic)
        }
    }
}
