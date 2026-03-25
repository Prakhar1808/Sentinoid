# Watchdog Service

## Overview
Background security monitoring service that performs real-time threat detection and system integrity checks.

## Features
- **Device Root Detection**: 5-layer root detection (su binaries, root apps, writable paths, system properties)
- **USB Debugging Monitoring**: Detects and warns when USB debugging is enabled
- **Silent Alarm Integration**: Triggers lockdown via SilentAlarmManager on root detection
- **Side-Channel Jamming**: Acoustic/EM jamming simulation for security
- **Callback System**: ThreatListener interface for UI updates

## Key Components
- `WatchdogService`: Background service for continuous monitoring
- `WatchdogManager`: Orchestrates security checks and manages risk thresholds
- `ThreatListener`: Callback interface for threat detection events

## Architecture
```
WatchdogService
    ├── WatchdogManager (security orchestration)
    ├── SilentAlarmManager (lockdown on critical threats)
    └── SecurityScanNotificationHelper (user alerts)
```

## Usage
```kotlin
// Start the service
val intent = Intent(context, WatchdogService::class.java)
context.startService(intent)

// Listen for threats
watchdogManager.addThreatListener(object : WatchdogManager.ThreatListener {
    override fun onThreatDetected(packageName: String, riskScore: Float) {
        // Handle threat
    }
    override fun onScanComplete(results: Map<String, Float>) {
        // Update UI
    }
})
```

## Root Detection Methods
1. Su binary/magisk path detection (15+ paths checked)
2. Root-only path writability test (/data, /system)
3. Root management app detection (SuperSU, Magisk, etc.)
4. Dangerous system property checks (ro.debuggable, ro.secure)
5. Command execution test (which su)
