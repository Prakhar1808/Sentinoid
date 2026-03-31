package com.sentinoid.app.security

import java.math.BigInteger
import java.security.MessageDigest

class RecoveryManager(
    private val cryptoManager: CryptoManager,
    private val securePreferences: SecurePreferences,
) {
    companion object {
        private const val SHARD_COUNT = 3
        private const val SHARD_THRESHOLD = 2
        private const val PREFS_SHARD_PREFIX = "shard_"
        private const val PREFS_RECOVERY_SETUP = "recovery_setup_complete"
        private const val PREFS_MASTER_KEY_ENCRYPTED = "master_key_encrypted"
        private const val PREFS_SEED_HASH = "seed_hash"
    }

    private val bip39Provider = BIP39Provider()
    private val shamir = ShamirSecretSharing()
    private val activityLogger by lazy { ActivityLogger.getInstance(cryptoManager.getContext()) }

    data class RecoverySetup(
        val mnemonicWords: List<String>,
        val shardStrings: List<String>,
    )

    fun setupRecovery(): RecoverySetup {
        // Generate 24-word BIP39 mnemonic
        val mnemonic = bip39Provider.generateMnemonic(24)

        // Hash the 64-byte seed to 32 bytes for Shamir splitting (fits in 257-bit prime)
        val seedHash = hashSeed(mnemonic.seed)

        // Split the hashed seed into 3 shards (2-of-3 threshold)
        val shares = shamir.splitSecret(seedHash, SHARD_THRESHOLD, SHARD_COUNT)

        // Convert shares to string representations
        val shardStrings = shares.map { it.toStringRepresentation() }

        // Store encrypted shards
        shares.forEachIndexed { index, share ->
            val encrypted = cryptoManager.encrypt(share.toStringRepresentation())
            securePreferences.putString("${PREFS_SHARD_PREFIX}${index + 1}", encrypted)
        }

        // Store encrypted master key reference
        securePreferences.putString(
            PREFS_MASTER_KEY_ENCRYPTED,
            cryptoManager.encrypt(mnemonic.masterKey.joinToString("") { "%02x".format(it) }),
        )

        // Store seed hash for verification
        securePreferences.putString(
            PREFS_SEED_HASH,
            cryptoManager.encrypt(seedHash.joinToString("") { "%02x".format(it) }),
        )

        securePreferences.putBoolean(PREFS_RECOVERY_SETUP, true)

        return RecoverySetup(mnemonic.words, shardStrings)
    }

    fun isRecoverySetup(): Boolean {
        return securePreferences.getBoolean(PREFS_RECOVERY_SETUP, false)
    }

    fun recoverFromShards(shardStrings: List<String>): RecoveryResult {
        if (shardStrings.size < SHARD_THRESHOLD) {
            return RecoveryResult.Error("Need at least $SHARD_THRESHOLD shards")
        }

        return try {
            // Parse shares from strings
            val shares =
                shardStrings.take(SHARD_THRESHOLD).map {
                    ShamirSecretSharing.Share.fromString(it)
                }

            // Validate shares belong to same set
            if (!shamir.validateShares(shares)) {
                return RecoveryResult.Error("Invalid or mismatched shards")
            }

            // Reconstruct the secret
            val reconstructedSeed = shamir.reconstructSecret(shares)

            // Verify against stored hash if available
            val success = verifyReconstructedSeed(reconstructedSeed)

            if (success) {
                RecoveryResult.Success(reconstructedSeed)
            } else {
                RecoveryResult.Error("Reconstructed seed verification failed")
            }
        } catch (e: Exception) {
            RecoveryResult.Error("Recovery failed: ${e.message}")
        }
    }

    fun recoverFromMnemonic(words: List<String>): RecoveryResult {
        return try {
            if (!bip39Provider.validateMnemonic(words)) {
                return RecoveryResult.Error("Invalid mnemonic words")
            }

            val seed = bip39Provider.mnemonicToSeed(words)
            val seedHash = hashSeed(seed)
            val success = verifyReconstructedSeed(seedHash)

            if (success) {
                RecoveryResult.Success(seedHash)
            } else {
                RecoveryResult.Error("Seed verification failed")
            }
        } catch (e: Exception) {
            RecoveryResult.Error("Recovery failed: ${e.message}")
        }
    }

    private fun hashSeed(seed: ByteArray): ByteArray {
        // Hash the 64-byte BIP39 seed to 32 bytes to fit within 257-bit prime
        return MessageDigest.getInstance("SHA-256").digest(seed)
    }

    private fun verifyReconstructedSeed(seedHash: ByteArray): Boolean {
        return try {
            val storedHashEncrypted = securePreferences.getString(PREFS_SEED_HASH)
            if (storedHashEncrypted == null) {
                // No stored hash available - accept valid reconstruction
                // This handles offline recovery after app reinstall or data loss
                return seedHash.size == 32
            }
            val storedHashHex = cryptoManager.decrypt(storedHashEncrypted)
            val storedHash = storedHashHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            seedHash.contentEquals(storedHash)
        } catch (e: Exception) {
            // If no stored hash (legacy), accept any 32-byte hash
            seedHash.size == 32
        }
    }

    fun getStoredShard(index: Int): String? {
        if (index < 1 || index > SHARD_COUNT) return null
        val encrypted = securePreferences.getString("${PREFS_SHARD_PREFIX}$index") ?: return null
        return try {
            cryptoManager.decrypt(encrypted)
        } catch (e: Exception) {
            null
        }
    }

    fun purgeAllRecoveryData() {
        (1..SHARD_COUNT).forEach { index ->
            securePreferences.remove("${PREFS_SHARD_PREFIX}$index")
        }
        securePreferences.remove(PREFS_RECOVERY_SETUP)
        securePreferences.remove(PREFS_MASTER_KEY_ENCRYPTED)
        securePreferences.remove(PREFS_SEED_HASH)
    }

    fun validateShard(shardString: String): Boolean {
        return try {
            val share = ShamirSecretSharing.Share.fromString(shardString)
            share.x > BigInteger.ZERO && share.y > BigInteger.ZERO
        } catch (e: Exception) {
            false
        }
    }

    sealed class RecoveryResult {
        data class Success(val seed: ByteArray) : RecoveryResult()

        data class Error(val message: String) : RecoveryResult()
    }
}
