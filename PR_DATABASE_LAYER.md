# PR: Add Room Database Layer with SecurityEvent Entity and DAO

## Summary
This PR implements the data persistence layer using Room database for storing security events and threat history. Provides a clean API for recording and querying security incidents.

## Changes
- **SovereignDatabase.kt**: Room database with singleton pattern
- **data/SecurityEvent.kt**: Entity for security events
- **data/SecurityEventDao.kt**: Data access object with CRUD operations

## Features
- **SecurityEvent Entity**: Stores threat detections with:
  - Event type (THREAT_DETECTED, SCAN_COMPLETED, etc.)
  - Package name (optional)
  - Risk score (optional)
  - Timestamp (auto-generated)

- **SecurityEventDao**: Provides operations:
  - `insertEvent()`: Record new security event
  - `getRecentEvents()`: Get last 100 events
  - `getEventsByType()`: Filter by event type
  - `deleteOldEvents()`: Cleanup old records

- **SovereignDatabase**: Singleton pattern for thread-safe access

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

## API
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
val events = db.securityEventDao().getRecentEvents()
```

## Technical Details
- Database name: `sovereign_database`
- Version: 1
- Thread-safe singleton pattern
- Suspend functions for coroutines

## Testing
- Verify database creation on first run
- Test insert and query operations
- Check auto-increment primary key
- Verify singleton behavior

## Dependencies
- Room (AndroidX)
- Kotlin Coroutines

## Related
- Closes TODO: Database layer for security event storage
- Enables threat history tracking and analytics

---
**Branch**: `feature/database-layer`
**Base**: `main`
