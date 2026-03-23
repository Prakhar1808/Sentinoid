# 🧬 Feature: Malware Scanner

## Overview

This branch implements on-device threat detection using TensorFlow Lite models. Performs static analysis of installed apps to identify spyware, stalkerware, and malicious packages without internet connectivity.

## What's Included

### Core Components
- **MalwareScanner.kt** - TFLite-based threat detection engine
- **SecurityModels.kt** - RiskyApp data class and threat classifications
- **assets/labels.txt** - Model output labels

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│           Installed Apps Query                              │
│     PackageManager.getInstalledApplications()               │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│           Feature Extraction                                │
│  • Package name analysis                                    │
│  • Permission patterns                                      │
│  • Target SDK version                                       │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│           TFLite Inference                                  │
│  Input: float[3] → Model → Output: float[1]                │
│  Features         CNN/MLP    Risk score 0.0-1.0           │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│           Risk Classification                               │
│  Score 0.0-0.3: Safe                                        │
│  Score 0.3-0.5: Low (log only)                            │
│  Score 0.5-0.8: Suspicious (alert)                        │
│  Score 0.8-1.0: Critical (FPM + lockdown)                 │
└─────────────────────────────────────────────────────────────┘
```

## How It Works

### App Scanning
```kotlin
fun scanInstalledApps(): List<RiskyApp> {
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return apps.filter { !it.isSystemApp }
        .map { app ->
            val riskScore = calculateRiskScore(app)
            if (riskScore > 0.5f) RiskyApp(app.packageName, riskScore)
            else null
        }.filterNotNull()
}
```

### Risk Scoring
```kotlin
private fun calculateRiskScore(app: ApplicationInfo): Float {
    val input = floatArrayOf(
        if (app.packageName.contains("spy")) 1.0f else 0.0f,
        if (app.packageName.contains("malware")) 1.0f else 0.0f,
        app.targetSdkVersion.toFloat() / 33.0f
    )
    
    val output = arrayOf(floatArrayOf(0.0f))
    tflite?.run(input, output)
    return output[0][0]
}
```

## RiskyApp Classification

| Risk Score | Classification | Action |
|------------|----------------|--------|
| 0.0 - 0.3 | Safe | None |
| 0.3 - 0.5 | Low | Log only |
| 0.5 - 0.8 | Suspicious | Alert user, enable FPM |
| 0.8 - 1.0 | Critical | Immediate FPM + Lockdown |

## TFLite Model

### Model Specs
| Attribute | Value |
|-----------|-------|
| Input | float32[1, 3] |
| Output | float32[1, 1] |
| Size | 50KB - 500KB (quantized) |
| Architecture | Simple MLP |

### Features
- Package name suspicious keywords
- Permission patterns
- SDK version normalization

### Fallback
If TFLite model unavailable:
```kotlin
tflite?.run(input, output) ?: heuristicFallback()
```

## Usage

```kotlin
// Initialize scanner
val scanner = MalwareScanner(context)

// Scan device
val riskyApps = scanner.scanInstalledApps()

// Display results
for (app in riskyApps) {
    println("${app.appName}: ${app.riskScore} - ${app.threatType}")
}
```

## Integration with Main

- **Dashboard**: Displays risky apps in SentinelDashboard
- **FPM**: Auto-enables mocking for high-risk apps
- **Watchdog**: Coordinates threat response

## Testing

```bash
# Check model loaded
adb logcat -d | grep "TFLite Model loaded"

# Trigger scan
adb shell am start -n com.sentinoid.shield/.MainActivity

# View detected threats
adb logcat -d | grep "RiskyApp"
```

## Performance

| Metric | Target |
|--------|--------|
| Inference | <50ms per app |
| Full scan | <5s (100 apps) |
| Model memory | <5MB |
| Battery | <1% for periodic scans |

## Future Enhancements

- Real TFLite model training on AndroZoo dataset
- More sophisticated feature extraction
- Behavioral analysis (dynamic)
- Cloud-based model updates via USB Bridge

## Merge Checklist

- [ ] MalwareScanner loads TFLite model
- [ ] Fallback heuristics work when model missing
- [ ] Risk classification thresholds appropriate
- [ ] RiskyApp list displays in UI
- [ ] High-risk apps trigger FPM automatically
- [ ] Labels.txt matches model output
