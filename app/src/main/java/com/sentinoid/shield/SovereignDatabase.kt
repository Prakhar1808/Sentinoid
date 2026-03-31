package com.sentinoid.shield

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Secure audit log database with SQLCipher encryption.
 * Using direct SQLite to avoid Room/kapt metadata version issues.
 */
data class AuditLogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val category: String,
    val message: String,
    val severity: Int,
    val sessionId: String? = null
)

class SovereignDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "sentinoid_ledger.db"
        const val DATABASE_VERSION = 1
        const val TABLE_AUDIT_LOGS = "audit_logs"

        @Volatile
        private var INSTANCE: SovereignDatabase? = null

        fun getInstance(context: Context): SovereignDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SovereignDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_AUDIT_LOGS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                category TEXT NOT NULL,
                message TEXT NOT NULL,
                severity INTEGER NOT NULL,
                sessionId TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_timestamp ON $TABLE_AUDIT_LOGS(timestamp)")
        db.execSQL("CREATE INDEX idx_category ON $TABLE_AUDIT_LOGS(category)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_AUDIT_LOGS")
        onCreate(db)
    }

    fun insertLog(entry: AuditLogEntry): Long {
        val values = ContentValues().apply {
            put("timestamp", entry.timestamp)
            put("category", entry.category)
            put("message", entry.message)
            put("severity", entry.severity)
            put("sessionId", entry.sessionId)
        }
        return writableDatabase.insert(TABLE_AUDIT_LOGS, null, values)
    }

    fun getRecentLogs(limit: Int): Flow<List<AuditLogEntry>> = flow {
        val logs = mutableListOf<AuditLogEntry>()
        readableDatabase.query(
            TABLE_AUDIT_LOGS,
            null, null, null, null, null,
            "timestamp DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                logs.add(cursor.toAuditLogEntry())
            }
        }
        emit(logs)
    }

    fun getLogsByCategory(category: String, limit: Int): Flow<List<AuditLogEntry>> = flow {
        val logs = mutableListOf<AuditLogEntry>()
        readableDatabase.query(
            TABLE_AUDIT_LOGS,
            null,
            "category = ?",
            arrayOf(category),
            null, null,
            "timestamp DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                logs.add(cursor.toAuditLogEntry())
            }
        }
        emit(logs)
    }

    fun purgeOldLogs(olderThan: Long): Int {
        return writableDatabase.delete(
            TABLE_AUDIT_LOGS,
            "timestamp < ?",
            arrayOf(olderThan.toString())
        )
    }

    fun getLogCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_AUDIT_LOGS", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun android.database.Cursor.toAuditLogEntry(): AuditLogEntry {
        return AuditLogEntry(
            id = getLong(getColumnIndexOrThrow("id")),
            timestamp = getLong(getColumnIndexOrThrow("timestamp")),
            category = getString(getColumnIndexOrThrow("category")),
            message = getString(getColumnIndexOrThrow("message")),
            severity = getInt(getColumnIndexOrThrow("severity")),
            sessionId = getString(getColumnIndexOrThrow("sessionId"))
        )
    }
}
