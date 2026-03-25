# PR: Add Watchdog Service with Root Detection and Callback System

## Summary
This PR implements the background security monitoring service that performs continuous threat detection, device integrity checks, and provides real-time callbacks to the UI layer.

## Changes
- **WatchdogService.kt**: Complete implementation with:
  - 5-layer root detection system
  - USB debugging monitoring
  - Silent alarm integration (lockdown on root)
  - Side-channel jamming simulation
  - Coroutine-based async scanning with retry logic
  - Lifecycle management (proper cleanup on destroy)

- **WatchdogManager.kt**: Security orchestration layer with:
  - ThreatListener callback interface
  - Custom per-app risk thresholds
  - Unified interface for security operations

## Features
- **Root Detection**: 5 different detection methods
  1. Su binary/magisk path detection (15+ paths)
  2. Root-only path writability test
  3. Root management app detection
  4. Dangerous system property checks
  5. Command execution test

- **USB Debugging Monitoring**: Detects ADB enabled state and warns user
- **Silent Alarm Integration**: Triggers lockdown on critical threats
- **Callback System**: ThreatListener interface for UI updates
- **Retry Logic**: 3-attempt retry with 5-second delays
- **Side-Channel Jamming**: Acoustic/EM jamming simulation

## Technical Details
- Uses coroutines for async operations
- Proper resource cleanup in `onDestroy()`
- Integration with SilentAlarmManager for lockdown
- Integration with SecurityScanNotificationHelper for alerts

## Testing
- Test root detection on rooted and non-rooted devices
- Verify USB debugging detection
- Check callback system with test ThreatListener
- Verify proper service lifecycle management

## Dependencies
- Kotlin Coroutines
- SilentAlarmManager (existing)
- SecurityScanNotificationHelper

## Related
- Closes TODO: Comprehensive root detection
- Closes TODO: USB debugging notification
- Closes TODO: UI callback system

---
**Branch**: `feature/watchdog-service`
**Base**: `main`
