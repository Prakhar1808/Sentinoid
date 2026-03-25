# PR: Add WorkManager Security Scan Worker for Periodic Background Scanning

## Summary
This PR implements a WorkManager-based background task for reliable periodic security scanning. The worker runs every 6 hours to detect threats even when the app is not actively running.

## Changes
- **SecurityScanWorker.kt**: New CoroutineWorker implementation with:
  - Periodic scanning every 6 hours with 15-minute flex window
  - Automatic retry with exponential backoff
  - High-risk app notification on detection
  - Notification channel creation for security alerts

## Features
- **WorkManager Scheduling**: Reliable background execution using Android WorkManager
- **Periodic Execution**: 6-hour intervals with 15-minute flexibility
- **Auto-Retry**: Exponential backoff on scan failures
- **Notification Integration**: Sends high-priority notifications for threats
- **Coroutine Support**: Async scanning with proper cancellation handling

## API
```kotlin
// Schedule periodic scans
SecurityScanWorker.schedulePeriodicScans(context)

// Cancel scheduled scans
SecurityScanWorker.cancelScheduledScans(context)
```

## Technical Details
- Extends `CoroutineWorker` for Kotlin coroutines support
- Uses `PeriodicWorkRequestBuilder` for recurring work
- Notification channel ID: `security_alerts`
- Notification importance: HIGH
- Work name: `security_periodic_scan` (unique)

## Testing
- Verify work is scheduled using `adb shell dumpsys jobscheduler`
- Check notifications appear when threats detected
- Test retry behavior by simulating scan failures
- Verify work persists across device reboots

## Dependencies
- WorkManager (AndroidX)
- Kotlin Coroutines
- NotificationCompat

## Related
- Closes TODO: WorkManager integration for background scheduling
- Closes TODO: Notification system for security alerts

---
**Branch**: `feature/security-worker`
**Base**: `main`
