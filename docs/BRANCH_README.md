# ⚙️ Feature: Build System

## Overview

This branch contains the complete Gradle build configuration for Sentinoid, including dependencies for security libraries, NDK support, and Jetpack Compose.

## What's Included

### Core Configuration
- **build.gradle.kts** (project level) - Root project configuration
- **build.gradle.kts** (app level) - Application build configuration
- **settings.gradle.kts** - Project structure definition
- **gradle/libs.versions.toml** - Version catalog
- **gradle/** - Wrapper files
- **gradle.properties** - Build properties

## Dependencies

### Security
```kotlin
// Android Security
implementation("androidx.security:security-crypto:1.1.0-alpha06")
implementation("androidx.biometric:biometric:1.1.0")

// Database Encryption
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
implementation("androidx.room:room-runtime:2.6.1")

// Recovery
implementation("cash.z.ecc.android:kotlin-bip39:1.0.4")
```

### AI/ML
```kotlin
// TensorFlow Lite
implementation("org.tensorflow:tensorflow-lite:2.14.0")
```

### UI
```kotlin
// Jetpack Compose
implementation(platform("androidx.compose:compose-bom:2024.02.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
```

### NDK
```kotlin
android {
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

## Build Features

| Feature | Configuration |
|---------|--------------|
| Compile SDK | 36 |
| Min SDK | 33 (Android 13+) |
| Target SDK | 36 |
| Java Version | 11 |
| NDK | CMake 3.22.1 |
| Compose | BOM 2024.02.00 |

## Zero-Internet Policy

Critical configuration for air-gapped security:
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
```

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Build NDK
./gradlew externalNativeBuildDebug
```

## Integration with Main

This branch provides the foundation that all other branches depend on:
- Security branches need the crypto dependencies
- NDK branch needs CMake configuration
- UI branch needs Compose dependencies
- Malware branch needs TFLite dependencies

## Merge Checklist

- [ ] All dependencies resolve correctly
- [ ] NDK builds successfully
- [ ] Compose compiler version matches BOM
- [ ] No internet permissions in manifest
- [ ] Build completes without errors
- [ ] Tests run successfully
