# Security Scan Worker

## Overview
WorkManager-based background task for reliable periodic security scanning.

## Features
- **Periodic Scanning**: Runs every 6 hours with 15-minute flex window
- **Automatic Retry**: Exponential backoff on scan failures
- **Notification Integration**: Sends high-risk app alerts via system notifications
- **WorkManager Scheduling**: Reliable background execution even when device is dozing
- **Coroutine Support**: Async scanning with proper cancellation handling

## Key Components
- `SecurityScanWorker`: CoroutineWorker implementation for background scans
- `schedulePeriodicScans()`: Schedules recurring 6-hour scan intervals
- `cancelScheduledScans()`: Cancels pending scan work

## Usage
```kotlin
// Schedule periodic scans
SecurityScanWorker.schedulePeriodicScans(context)

// Cancel when no longer needed
SecurityScanWorker.cancelScheduledScans(context)
```

## Configuration
- **Interval**: 6 hours
- **Flex Window**: 15 minutes
- **Retry Policy**: Exponential backoff on failure
- **Notification Channel**: `security_alerts` (IMPORTANCE_HIGH)

## Dependencies
- WorkManager (AndroidX)
- Kotlin Coroutines
