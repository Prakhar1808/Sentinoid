# Database Layer

## Overview
Room database for persisting security events and threat history with a singleton pattern.

## Features
- **SecurityEvent Entity**: Stores threat detections with package name, risk score, timestamp
- **SecurityEventDao**: Data access object for CRUD operations
- **SovereignDatabase**: Room database with singleton pattern for thread safety
- **Auto-cleanup**: Delete old events to prevent database bloat

## Key Components
- `SovereignDatabase`: Room database singleton
- `SecurityEvent`: Room entity for security events
- `SecurityEventDao`: DAO for database operations

## Schema
```kotlin
@Entity(tableName = "security_events")
data class SecurityEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val packageName: String? = null,
    val riskScore: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)
```

## Usage
```kotlin
// Get database instance
val db = SovereignDatabase.getDatabase(context)

// Insert event
db.securityEventDao().insertEvent(
    SecurityEvent(
        type = "THREAT_DETECTED",
        packageName = "com.example.malware",
        riskScore = 0.85f
    )
)

// Query recent events
val recent = db.securityEventDao().getRecentEvents()

// Cleanup old events (older than 30 days)
db.securityEventDao().deleteOldEvents(
    System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000
)
```

## Dependencies
- Room (AndroidX)
- Kotlin Coroutines
