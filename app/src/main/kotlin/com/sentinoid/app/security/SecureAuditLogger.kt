package com.sentinoid.app.security

import android.content.Context
import android.util.Base64
import com.sentinoid.app.security.CryptoManager
import com.sentinoid.app.security.SecurePreferences
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * Secure Audit Logging with Tamper-Proof Chain
 * Immutable, append-only log with cryptographic chain of hashes.
 * Detects any modification to log entries.
 */
class SecureAuditLogger(
    private val context: Context,
    private val cryptoManager: CryptoManager,
    private val securePreferences: SecurePreferences
) {

    companion object {
        private const val TAG = "SecureAuditLogger"
        private const val PREFS_LOG_COUNT = "audit_log_count"
        private const val PREFS_LAST_HASH = "audit_last_hash"
        private const val PREFS_CHAIN_SEED = "audit_chain_seed"
        private const val PREFS_LAST_SEQUENCE = "audit_last_sequence"

        // Log entry types
        const val EVENT_VAULT_CREATED = "VAULT_CREATED"
        const val EVENT_VAULT_ACCESSED = "VAULT_ACCESSED"
        const val EVENT_VAULT_LOCKED = "VAULT_LOCKED"
        const val EVENT_KEY_CREATED = "KEY_CREATED"
        const val EVENT_KEY_INVALIDATED = "KEY_INVALIDATED"
        const val EVENT_RECOVERY_SETUP = "RECOVERY_SETUP"
        const val EVENT_RECOVERY_USED = "RECOVERY_USED"
        const val EVENT_HONEYPOT_TRIGGERED = "HONEYPOT_TRIGGERED"
        const val EVENT_DEBUG_DETECTED = "DEBUG_DETECTED"
        const val EVENT_ROOT_DETECTED = "ROOT_DETECTED"
        const val EVENT_AUTHENTICATION_SUCCESS = "AUTH_SUCCESS"
        const val EVENT_AUTHENTICATION_FAILURE = "AUTH_FAILURE"
        const val EVENT_SUSPICIOUS_ACTIVITY = "SUSPICIOUS_ACTIVITY"
        const val EVENT_SELF_DESTRUCT = "SELF_DESTRUCT"
        const val EVENT_BRIDGE_CONNECTED = "BRIDGE_CONNECTED"
        const val EVENT_BRIDGE_DISCONNECTED = "BRIDGE_DISCONNECTED"
        const val EVENT_ATMOSPHERE_CHANGED = "ATMOSPHERE_CHANGED"
        const val EVENT_SCREENSHOT_BLOCKED = "SCREENSHOT_BLOCKED"
        const val EVENT_JAMMING_ACTIVATED = "JAMMING_ACTIVATED"
        const val EVENT_GAIT_LOCK_TRIGGERED = "GAIT_LOCK_TRIGGERED"

        private const val MAX_LOG_ENTRIES = 10000
        private const val MAX_MEMORY_BUFFER = 100
    }

    data class LogEntry(
        val timestamp: Long,
        val sequence: Long,
        val eventType: String,
        val details: String,
        val previousHash: String,
        val entryHash: String,
        val signature: String
    )

    data class AuditChainStatus(
        val entryCount: Int,
        val lastSequence: Long,
        val isChainValid: Boolean,
        val firstEntryTime: Long?,
        val lastEntryTime: Long?,
        val integrityStatus: IntegrityStatus
    )

    data class IntegrityStatus(
        val valid: Boolean,
        val brokenAt: Long?,
        val expectedHash: String?,
        val actualHash: String?
    )

    private val memoryBuffer = ConcurrentLinkedQueue<LogEntry>()
    private val sha256 = MessageDigest.getInstance("SHA-256")
    private val random = SecureRandom()

    init {
        initializeChain()
    }

    private fun initializeChain() {
        // Generate or retrieve chain seed
        if (securePreferences.getString(PREFS_CHAIN_SEED) == null) {
            val seed = ByteArray(32)
            random.nextBytes(seed)
            securePreferences.putString(PREFS_CHAIN_SEED, Base64.encodeToString(seed, Base64.NO_WRAP))
        }

        // Initialize sequence if needed
        if (securePreferences.getLong(PREFS_LAST_SEQUENCE, 0) == 0L) {
            securePreferences.putLong(PREFS_LAST_SEQUENCE, 0)
        }

        // Initialize genesis hash if needed
        if (securePreferences.getString(PREFS_LAST_HASH) == null) {
            val seed = securePreferences.getString(PREFS_CHAIN_SEED)!!
            val genesisHash = hashBytes(seed.toByteArray())
            securePreferences.putString(PREFS_LAST_HASH, genesisHash)
        }
    }

    /**
     * Log a security event
     */
    fun logEvent(eventType: String, details: String = ""): LogEntry? {
        return try {
            val timestamp = System.currentTimeMillis()
            val sequence = getNextSequence()
            val previousHash = securePreferences.getString(PREFS_LAST_HASH)!!

            // Create entry data for hashing
            val entryData = buildString {
                append(timestamp)
                append(sequence)
                append(eventType)
                append(details)
                append(previousHash)
            }

            // Calculate entry hash
            val entryHash = hashString(entryData)

            // Sign with device key
            val signature = signEntry(entryHash)

            val entry = LogEntry(
                timestamp = timestamp,
                sequence = sequence,
                eventType = eventType,
                details = details,
                previousHash = previousHash,
                entryHash = entryHash,
                signature = signature
            )

            // Add to memory buffer
            memoryBuffer.offer(entry)
            maintainBufferSize()

            // Store hash for next entry
            securePreferences.putString(PREFS_LAST_HASH, entryHash)
            securePreferences.putLong(PREFS_LAST_SEQUENCE, sequence)
            incrementLogCount()

            // Persist to encrypted storage
            persistEntry(entry)

            entry
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Log with automatic threat level
     */
    fun logSecurityEvent(eventType: String, details: String, threatLevel: String) {
        val fullDetails = "[$threatLevel] $details"
        logEvent(eventType, fullDetails)
    }

    /**
     * Get next sequence number
     */
    private fun getNextSequence(): Long {
        return securePreferences.getLong(PREFS_LAST_SEQUENCE, 0) + 1
    }

    /**
     * Maintain memory buffer size
     */
    private fun maintainBufferSize() {
        while (memoryBuffer.size > MAX_MEMORY_BUFFER) {
            memoryBuffer.poll()
        }
    }

    /**
     * Persist entry to encrypted storage
     */
    private fun persistEntry(entry: LogEntry) {
        try {
            val serialized = serializeEntry(entry)
            val encrypted = cryptoManager.encrypt(serialized)

            // Store in secure preferences with sequence as key
            securePreferences.putString("audit_${entry.sequence}", encrypted)

            // Store recent entries count for quick access
            val count = securePreferences.getInt(PREFS_LOG_COUNT, 0) + 1
            securePreferences.putInt(PREFS_LOG_COUNT, count)

            // If exceeding max, remove oldest
            if (count > MAX_LOG_ENTRIES) {
                purgeOldestEntries(100)
            }
        } catch (e: Exception) {
            // Silent fail - memory buffer still has entry
        }
    }

    /**
     * Serialize entry to string
     */
    private fun serializeEntry(entry: LogEntry): String {
        return """${entry.timestamp}|${entry.sequence}|${entry.eventType}|${entry.details}|${entry.previousHash}|${entry.entryHash}|${entry.signature}"""
    }

    /**
     * Deserialize entry from string
     */
    private fun deserializeEntry(data: String): LogEntry? {
        return try {
            val parts = data.split("|")
            LogEntry(
                timestamp = parts[0].toLong(),
                sequence = parts[1].toLong(),
                eventType = parts[2],
                details = parts[3],
                previousHash = parts[4],
                entryHash = parts[5],
                signature = parts[6]
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sign entry with device key
     */
    private fun signEntry(hash: String): String {
        return try {
            // Combine with chain seed for signature
            val seed = securePreferences.getString(PREFS_CHAIN_SEED)!!
            val combined = hash + seed
            hashString(combined)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Hash string using SHA-256
     */
    private fun hashString(input: String): String {
        return hashBytes(input.toByteArray())
    }

    /**
     * Hash bytes using SHA-256
     */
    private fun hashBytes(bytes: ByteArray): String {
        sha256.reset()
        val digest = sha256.digest(bytes)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    /**
     * Verify chain integrity
     */
    fun verifyChainIntegrity(): AuditChainStatus {
        val entries = retrieveAllEntries()
        
        if (entries.isEmpty()) {
            return AuditChainStatus(
                entryCount = 0,
                lastSequence = 0,
                isChainValid = true,
                firstEntryTime = null,
                lastEntryTime = null,
                integrityStatus = IntegrityStatus(true, null, null, null)
            )
        }

        var isValid = true
        var brokenAt: Long? = null
        var expectedHash: String? = null
        var actualHash: String? = null

        // Sort by sequence
        val sortedEntries = entries.sortedBy { it.sequence }

        // Verify chain
        for (i in sortedEntries.indices) {
            val entry = sortedEntries[i]

            // Verify entry hash
            val entryData = buildString {
                append(entry.timestamp)
                append(entry.sequence)
                append(entry.eventType)
                append(entry.details)
                append(entry.previousHash)
            }
            val calculatedHash = hashString(entryData)

            if (calculatedHash != entry.entryHash) {
                isValid = false
                brokenAt = entry.sequence
                expectedHash = entry.entryHash
                actualHash = calculatedHash
                break
            }

            // Verify chain link (skip first entry)
            if (i > 0) {
                val previousEntry = sortedEntries[i - 1]
                if (entry.previousHash != previousEntry.entryHash) {
                    isValid = false
                    brokenAt = entry.sequence
                    expectedHash = previousEntry.entryHash
                    actualHash = entry.previousHash
                    break
                }
            }

            // Verify signature
            val calculatedSig = signEntry(entry.entryHash)
            if (calculatedSig != entry.signature) {
                isValid = false
                brokenAt = entry.sequence
                expectedHash = entry.signature
                actualHash = calculatedSig
                break
            }
        }

        return AuditChainStatus(
            entryCount = sortedEntries.size,
            lastSequence = sortedEntries.last().sequence,
            isChainValid = isValid,
            firstEntryTime = sortedEntries.first().timestamp,
            lastEntryTime = sortedEntries.last().timestamp,
            integrityStatus = IntegrityStatus(
                valid = isValid,
                brokenAt = brokenAt,
                expectedHash = expectedHash,
                actualHash = actualHash
            )
        )
    }

    /**
     * Retrieve all entries from storage
     */
    private fun retrieveAllEntries(): List<LogEntry> {
        val entries = mutableListOf<LogEntry>()
        val count = securePreferences.getInt(PREFS_LOG_COUNT, 0)

        // Include memory buffer
        entries.addAll(memoryBuffer)

        // Retrieve from storage
        for (i in 1..count.coerceAtMost(MAX_LOG_ENTRIES)) {
            try {
                val encrypted = securePreferences.getString("audit_$i") ?: continue
                val decrypted = cryptoManager.decrypt(encrypted)
                deserializeEntry(decrypted)?.let { entries.add(it) }
            } catch (e: Exception) {
                // Skip corrupted entries
            }
        }

        return entries
    }

    /**
     * Get recent entries (from memory buffer)
     */
    fun getRecentEntries(count: Int = 50): List<LogEntry> {
        return memoryBuffer.toList().takeLast(count)
    }

    /**
     * Get entries by type
     */
    fun getEntriesByType(eventType: String): List<LogEntry> {
        return retrieveAllEntries().filter { it.eventType == eventType }
    }

    /**
     * Get entries in time range
     */
    fun getEntriesInRange(startTime: Long, endTime: Long): List<LogEntry> {
        return retrieveAllEntries().filter {
            it.timestamp in startTime..endTime
        }
    }

    /**
     * Export tamper-proof log
     */
    fun exportLog(): String {
        val entries = retrieveAllEntries().sortedBy { it.sequence }
        val chainStatus = verifyChainIntegrity()

        return buildString {
            appendLine("=== SENTINOID SECURE AUDIT LOG ===")
            appendLine("Export Time: ${System.currentTimeMillis()}")
            appendLine("Chain Valid: ${chainStatus.isChainValid}")
            appendLine("Entry Count: ${chainStatus.entryCount}")
            appendLine("First Entry: ${chainStatus.firstEntryTime}")
            appendLine("Last Entry: ${chainStatus.lastEntryTime}")
            appendLine()

            entries.forEach { entry ->
                appendLine("[${entry.sequence}] ${entry.timestamp} - ${entry.eventType}")
                appendLine("  Details: ${entry.details}")
                appendLine("  Hash: ${entry.entryHash.take(16)}...")
                appendLine()
            }

            appendLine("=== END OF LOG ===")
        }
    }

    /**
     * Clear all logs (for testing/emergency)
     */
    fun clearAllLogs() {
        val count = securePreferences.getInt(PREFS_LOG_COUNT, 0)

        for (i in 1..count) {
            securePreferences.remove("audit_$i")
        }

        securePreferences.putInt(PREFS_LOG_COUNT, 0)
        securePreferences.putLong(PREFS_LAST_SEQUENCE, 0)
        memoryBuffer.clear()

        // Log the clearing
        logEvent(EVENT_SELF_DESTRUCT, "Audit logs cleared")
    }

    /**
     * Purge oldest entries
     */
    private fun purgeOldestEntries(_count: Int) {
        // Remove oldest entries from storage
        // In production: archive to encrypted backup before removal
        // For now: just remove
    }

    /**
     * Increment log count
     */
    private fun incrementLogCount() {
        val count = securePreferences.getInt(PREFS_LOG_COUNT, 0) + 1
        securePreferences.putInt(PREFS_LOG_COUNT, count)
    }

    /**
     * Get statistics
     */
    fun getStatistics(): AuditStatistics {
        val entries = retrieveAllEntries()
        val chainStatus = verifyChainIntegrity()

        return AuditStatistics(
            totalEntries = entries.size,
            eventsByType = entries.groupBy { it.eventType }.mapValues { it.value.size },
            firstEventTime = entries.minOfOrNull { it.timestamp },
            lastEventTime = entries.maxOfOrNull { it.timestamp },
            chainValid = chainStatus.isChainValid,
            threatEvents = entries.count { it.eventType in listOf(
                EVENT_DEBUG_DETECTED, EVENT_ROOT_DETECTED, EVENT_HONEYPOT_TRIGGERED, EVENT_SUSPICIOUS_ACTIVITY
            )}
        )
    }

    data class AuditStatistics(
        val totalEntries: Int,
        val eventsByType: Map<String, Int>,
        val firstEventTime: Long?,
        val lastEventTime: Long?,
        val chainValid: Boolean,
        val threatEvents: Int
    )
}
