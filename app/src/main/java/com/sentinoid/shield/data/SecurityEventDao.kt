package com.sentinoid.shield.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sentinoid.shield.SecurityEvent

@Dao
interface SecurityEventDao {
    @Insert
    suspend fun insertEvent(event: SecurityEvent): Long

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentEvents(): List<SecurityEvent>

    @Query("SELECT * FROM security_events WHERE type = :type ORDER BY timestamp DESC")
    suspend fun getEventsByType(type: String): List<SecurityEvent>

    @Query("DELETE FROM security_events WHERE timestamp < :timestamp")
    suspend fun deleteOldEvents(timestamp: Long)
}
