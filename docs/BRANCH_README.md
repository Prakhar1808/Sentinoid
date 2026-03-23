# 🎨 Feature: UI Components

## Overview

This branch implements the user interface layer for Sentinoid, providing dashboards, security status displays, secret management, and onboarding flows using Jetpack Compose.

## What's Included

### Core Components
- **SentinelDashboard.kt** - Main security dashboard with risk indicators
- **TacticalDashboard.kt** - Real-time security status display
- **SecretManagerScreen.kt** - Encrypted secret management UI
- **OnboardingWizard.kt** - First-time setup flow
- **ui/theme/** - Material3 theme (Color.kt, Theme.kt, Type.kt)

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MainActivity                              │
│              (Orchestration & Navigation)                    │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │   Sentinel   │  │   Tactical   │  │    Secret    │       │
│  │  Dashboard   │  │  Dashboard   │  │   Manager    │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│  ┌──────────────┐                                            │
│  │  Onboarding  │                                            │
│  │    Wizard    │                                            │
│  └──────────────┘                                            │
└─────────────────────────────────────────────────────────────┘
```

## Components

### SentinelDashboard
Main security overview showing:
- High-risk apps from MalwareScanner
- USB bridge connection status
- Security alerts summary
- Quick actions (Open Vault)

```kotlin
@Composable
fun SentinoidDashboard(
    highRiskApps: List<RiskyApp>,
    bridgeConnected: Boolean,
    onOpenVault: () -> Unit
)
```

### TacticalDashboard
Real-time security monitoring:
- Watchdog status indicators
- FPM active sessions
- Honeypot trigger status
- Lockdown state

### SecretManagerScreen
Encrypted vault management:
- List of encrypted secrets
- Add new secret (with biometric)
- Decrypt/view secret (with biometric)
- Delete secrets

### OnboardingWizard
First-time user setup:
- Welcome screens
- Permission requests
- Biometric enrollment guidance
- Recovery phrase generation

## Theme System

### Material3 Theme
- **Color.kt** - Color palette (light/dark)
- **Theme.kt** - Theme configuration
- **Type.kt** - Typography scale

## Integration with Main

All UI components integrate with:
- **ViewModels** - SecretViewModel for data
- **Services** - Watchdog, FPM, Honeypot status
- **Security** - BiometricPrompt for authentication

## Usage

```kotlin
// In MainActivity
setContent {
    SentinoidTheme {
        var currentScreen by remember { mutableStateOf("DASHBOARD") }
        
        when (currentScreen) {
            "DASHBOARD" -> SentinoidDashboard(...)
            "VAULT" -> SecretManagerScreen(...)
        }
    }
}
```

## Biometric Integration

```kotlin
private fun showBiometricPrompt(onSuccess: () -> Unit) {
    biometricPrompt = BiometricPrompt(this, executor,
        object : AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: Result) {
                onSuccess()
            }
        })
    biometricPrompt.authenticate(promptInfo)
}
```

## Testing

```bash
# Launch app
adb shell am start -n com.sentinoid.shield/.MainActivity

# Test navigation
# Tap "Open Vault" → Biometric prompt → Secret Manager

# Test dashboard
# Verify risk apps display correctly
```

## Merge Checklist

- [ ] SentinelDashboard displays risk apps
- [ ] TacticalDashboard shows real-time status
- [ ] SecretManagerScreen works with biometric
- [ ] OnboardingWizard guides new users
- [ ] Theme applies correctly (light/dark)
- [ ] Navigation between screens works
- [ ] All UI is responsive
