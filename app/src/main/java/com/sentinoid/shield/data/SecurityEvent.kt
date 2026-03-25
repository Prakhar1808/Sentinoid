package com.sentinoid.shield

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_events")
data class SecurityEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val packageName: String? = null,
    val riskScore: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)
