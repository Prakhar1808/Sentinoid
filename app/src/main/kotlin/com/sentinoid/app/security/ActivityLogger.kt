package com.sentinoid.app.security

import android.content.Context
import android.content.SharedPreferences
import com.sentinoid.app.SentinoidApp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Centralized Activity Logger for all Sentinoid features.
 * Provides structured logging with timestamps, categories, and severity levels.
 * Stores logs in SharedPreferences with automatic rotation.
 */
class ActivityLogger private constructor(context: Context) {
    companion object {
        private const val PREFS_NAME = "sentinoid_activity_logs"
        private const val MAX_LOGS_PER_CATEGORY = 100
        private const val MAX_TOTAL_LOGS = 500

        // Log categories
        const val CATEGORY_WATCHDOG = "WATCHDOG"
        const val CATEGORY_HONEYPOT = "HONEYPOT"
        const val CATEGORY_FPM = "FPM"
        const val CATEGORY_BRIDGE = "BRIDGE"
        const val CATEGORY_ATMOSPHERE = "ATMOSPHERE"
        const val CATEGORY_RECOVERY = "RECOVERY"
        const val CATEGORY_CRYPTO = "CRYPTO"
        const val CATEGORY_TAMPER = "TAMPER"
        const val CATEGORY_SYSTEM = "SYSTEM"

        // Severity levels
        const val SEVERITY_INFO = "INFO"
        const val SEVERITY_WARNING = "WARNING"
        const val SEVERITY_ERROR = "ERROR"
        const val SEVERITY_CRITICAL = "CRITICAL"
        const val SEVERITY_DEBUG = "DEBUG"

        @Volatile
        private var instance: ActivityLogger? = null

        fun getInstance(context: Context): ActivityLogger {
            return instance ?: synchronized(this) {
                instance ?: ActivityLogger(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): ActivityLogger {
            return instance ?: throw IllegalStateException("ActivityLogger not initialized")
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val memoryLogs = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    data class LogEntry(
        val timestamp: Long,
        val category: String,
        val severity: String,
        val message: String,
        val details: Map<String, String> = emptyMap(),
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("timestamp", timestamp)
                put("category", category)
                put("severity", severity)
                put("message", message)
                put("timeFormatted", formatTime(timestamp))
                val detailsObj = JSONObject()
                details.forEach { (k, v) -> detailsObj.put(k, v) }
                put("details", detailsObj)
            }
        }

        private fun formatTime(timestamp: Long): String {
            return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
        }
    }

    init {
        // Load existing logs from storage
        loadLogsFromStorage()
    }

    /**
     * Log an activity event
     */
    fun log(
        category: String,
        message: String,
        severity: String = SEVERITY_INFO,
        details: Map<String, String> = emptyMap(),
    ) {
        val entry =
            LogEntry(
                timestamp = System.currentTimeMillis(),
                category = category,
                severity = severity,
                message = message,
                details = details,
            )

        memoryLogs.offer(entry)

        // Trim if needed
        if (memoryLogs.size > MAX_TOTAL_LOGS) {
            memoryLogs.poll()
        }

        // Persist to storage asynchronously
        persistLog(entry)

        // Also send broadcast for real-time updates
        broadcastLogEvent(entry)
    }

    /**
     * Convenience methods for each category
     */
    fun logWatchdog(
        message: String,
        severity: String = SEVERITY_INFO,
        details: Map<String, String> = emptyMap(),
    ) {
        log(CATEGORY_WATCHDOG, message, severity, details)
    }

    fun logHoneypot(
        message: String,
        severity: String = SEVERITY_INFO,
        details: Map<String, String> = emptyMap(),
    ) {
        log(CATEGORY_HONEYPOT, message, severity, details)
    }

    fun logFPM(
        message: String,
        severity: String = SEVERITY_INFO,
        details: Map<String, String> = emptyMap(),
    ) {
        log(CATEGORY_FPM, message, severity, details)
    }

    fun logBridge(
        message: String,
        severity: String = SEVERITY_INFO,
        details: Map<String, String> = emptyMap(),
    ) {
        log(CATEGORY_BRIDGE, message, severity, details)
    }

    fun logAtmosphere(
        message: String,
        severity: String = SEVERITY_INFO,
        details: Map<String, String> = emptyMap(),
    ) {
        log(CATEGORY_ATMOSPHERE, message, severity, details)
    }

    fun logRecovery(
        message: String,
        severity: String = SEVERITY_INFO,
        details: Map<String, String> = emptyMap(),
    ) {
        log(CATEGORY_RECOVERY, message, severity, details)
    }

    fun logCrypto(
        message: String,
        severity: String = SEVERITY_INFO,
        details: Map<String, String> = emptyMap(),
    ) {
        log(CATEGORY_CRYPTO, message, severity, details)
    }

    fun logTamper(
        message: String,
        severity: String = SEVERITY_CRITICAL,
        details: Map<String, String> = emptyMap(),
    ) {
        log(CATEGORY_TAMPER, message, severity, details)
    }

    fun logSystem(
        message: String,
        severity: String = SEVERITY_INFO,
        details: Map<String, String> = emptyMap(),
    ) {
        log(CATEGORY_SYSTEM, message, severity, details)
    }

    /**
     * Get logs by category
     */
    fun getLogsByCategory(
        category: String,
        limit: Int = 50,
    ): List<LogEntry> {
        return memoryLogs
            .filter { it.category == category }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    /**
     * Get all logs
     */
    fun getAllLogs(limit: Int = 100): List<LogEntry> {
        return memoryLogs
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    /**
     * Get recent logs across all categories
     */
    fun getRecentLogs(minutes: Int = 5): List<LogEntry> {
        val cutoff = System.currentTimeMillis() - (minutes * 60 * 1000)
        return memoryLogs
            .filter { it.timestamp > cutoff }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Get log statistics
     */
    fun getLogStats(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        memoryLogs.groupBy { it.category }.forEach { (cat, logs) ->
            stats[cat] = logs.size
        }
        return stats
    }

    /**
     * Get formatted logs for display
     */
    fun getFormattedLogs(
        category: String? = null,
        limit: Int = 50,
    ): String {
        val logs =
            if (category != null) {
                getLogsByCategory(category, limit)
            } else {
                getAllLogs(limit)
            }

        return logs.joinToString("\n") { entry ->
            val icon =
                when (entry.severity) {
                    SEVERITY_CRITICAL -> "🔴"
                    SEVERITY_ERROR -> "🟠"
                    SEVERITY_WARNING -> "🟡"
                    SEVERITY_INFO -> "🟢"
                    else -> "⚪"
                }
            "$icon [${entry.toJson().getString("timeFormatted")}] ${entry.category}: ${entry.message}"
        }
    }

    /**
     * Get logs as JSON array for UI display
     */
    fun getLogsAsJson(
        category: String? = null,
        limit: Int = 50,
    ): JSONArray {
        val logs =
            if (category != null) {
                getLogsByCategory(category, limit)
            } else {
                getAllLogs(limit)
            }

        return JSONArray().apply {
            logs.forEach { put(it.toJson()) }
        }
    }

    /**
     * Clear all logs
     */
    fun clearLogs() {
        memoryLogs.clear()
        prefs.edit().clear().apply()
        logSystem("All logs cleared", SEVERITY_WARNING)
    }

    /**
     * Clear logs by category
     */
    fun clearLogsByCategory(category: String) {
        val iterator = memoryLogs.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().category == category) {
                iterator.remove()
            }
        }
        persistAllLogs()
        logSystem("Logs cleared for category: $category", SEVERITY_WARNING)
    }

    private fun persistLog(entry: LogEntry) {
        try {
            val key = "log_${entry.timestamp}_${entry.category}"
            prefs.edit().putString(key, entry.toJson().toString()).apply()
        } catch (e: Exception) {
            // Silent fail
        }
    }

    private fun persistAllLogs() {
        try {
            val editor = prefs.edit()
            editor.clear()
            memoryLogs.forEach { entry ->
                val key = "log_${entry.timestamp}_${entry.category}"
                editor.putString(key, entry.toJson().toString())
            }
            editor.apply()
        } catch (e: Exception) {
            // Silent fail
        }
    }

    private fun loadLogsFromStorage() {
        try {
            val allEntries = prefs.all
            allEntries.forEach { (key, value) ->
                if (key.startsWith("log_") && value is String) {
                    try {
                        val json = JSONObject(value)
                        val details = mutableMapOf<String, String>()
                        val detailsObj = json.optJSONObject("details")
                        detailsObj?.let {
                            it.keys().forEach { k ->
                                details[k] = it.getString(k)
                            }
                        }

                        val entry =
                            LogEntry(
                                timestamp = json.getLong("timestamp"),
                                category = json.getString("category"),
                                severity = json.getString("severity"),
                                message = json.getString("message"),
                                details = details,
                            )
                        memoryLogs.offer(entry)
                    } catch (e: Exception) {
                        // Skip corrupted entries
                    }
                }
            }
        } catch (e: Exception) {
            // Silent fail
        }
    }

    private fun broadcastLogEvent(entry: LogEntry) {
        try {
            val appContext = SentinoidApp.instance?.applicationContext
            val intent =
                android.content.Intent("com.sentinoid.app.LOG_EVENT").apply {
                    setPackage(appContext?.packageName) // Keep broadcast within app
                    putExtra("timestamp", entry.timestamp)
                    putExtra("category", entry.category)
                    putExtra("severity", entry.severity)
                    putExtra("message", entry.message)
                }
            appContext?.sendBroadcast(intent)
        } catch (e: Exception) {
            // Silent fail
        }
    }

    /**
     * Get compressed logs for efficient storage/transfer
     */
    fun getCompressedLogs(): ByteArray {
        val logs = getAllLogs(500)
        val json =
            JSONArray().apply {
                logs.forEach { put(it.toJson()) }
            }
        val input = json.toString().toByteArray()
        return try {
            val output = java.io.ByteArrayOutputStream()
            java.util.zip.GZIPOutputStream(output).use { gzip ->
                gzip.write(input)
            }
            output.toByteArray()
        } catch (e: Exception) {
            input
        }
    }

    /**
     * Get logs export as formatted JSON string
     */
    fun exportLogsAsJson(): String {
        val json = JSONObject()
        val categories = JSONArray()

        memoryLogs.groupBy { it.category }.forEach { (cat, logs) ->
            val catObj = JSONObject()
            catObj.put("category", cat)
            catObj.put("count", logs.size)
            catObj.put(
                "logs",
                JSONArray().apply {
                    logs.sortedByDescending { it.timestamp }.take(20).forEach { put(it.toJson()) }
                },
            )
            categories.put(catObj)
        }

        json.put("exportTime", dateFormat.format(Date()))
        json.put("totalLogs", memoryLogs.size)
        json.put("categories", categories)

        return json.toString(2)
    }

    /**
     * Export logs as CSV for analysis
     */
    fun exportLogsAsCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("timestamp,category,severity,message,details")

        memoryLogs.sortedByDescending { it.timestamp }.forEach { entry ->
            val details = entry.details.entries.joinToString(";") { "${it.key}=${it.value}" }
            sb.appendLine("${entry.timestamp},${entry.category},${entry.severity},\"${entry.message.replace("\"", "\"\"")}\",\"$details\"")
        }

        return sb.toString()
    }

    /**
     * Get severity distribution statistics
     */
    fun getSeverityStats(): Map<String, Int> {
        return memoryLogs.groupBy { it.severity }
            .mapValues { it.value.size }
            .toSortedMap()
    }

    /**
     * Get category distribution statistics
     */
    fun getCategoryStats(): Map<String, Int> {
        return memoryLogs.groupBy { it.category }
            .mapValues { it.value.size }
            .toSortedMap()
    }

    /**
     * Search logs by keyword
     */
    fun searchLogs(keyword: String): List<LogEntry> {
        val lowerKeyword = keyword.lowercase()
        return memoryLogs.filter { entry ->
            entry.message.lowercase().contains(lowerKeyword) ||
                entry.category.lowercase().contains(lowerKeyword) ||
                entry.details.any { it.key.lowercase().contains(lowerKeyword) || it.value.lowercase().contains(lowerKeyword) }
        }.sortedByDescending { it.timestamp }
    }

    /**
     * Get critical logs only
     */
    fun getCriticalLogs(limit: Int = 50): List<LogEntry> {
        return memoryLogs
            .filter { it.severity == SEVERITY_CRITICAL }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    /**
     * Check if there are recent errors or critical events
     */
    fun hasRecentIssues(minutes: Int = 5): Boolean {
        val cutoff = System.currentTimeMillis() - (minutes * 60 * 1000)
        return memoryLogs.any {
            it.timestamp > cutoff && (it.severity == SEVERITY_ERROR || it.severity == SEVERITY_CRITICAL)
        }
    }
}
