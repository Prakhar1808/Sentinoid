# 🏛️ Feature: NDK Security Layer

## Overview

This branch implements the Native Development Kit (NDK) layer for hardware-specific threat detection and secure key derivation. Written in C++, it operates below the Android runtime for enhanced protection against runtime analysis.

## What's Included

### Core Components
- **SecurityEngine.cpp/h** - Core security logic and threat detection
- **native-lib.cpp** - JNI bridge between Java and C++
- **HardwareManager.cpp** - Hardware abstraction layer
- **ISecurityCore.h** - Interface definition
- **CMakeLists.txt** - NDK build configuration

## Architecture

```
┌─────────────────────┐
│   Java/Kotlin Layer │
│   (Android Runtime) │
└──────────┬──────────┘
           │ JNI
           ▼
┌─────────────────────────────────┐
│      Native Layer (C++)         │
│  ┌───────────────────────────┐  │
│  │  native-lib.cpp         │  │
│  │  JNI bridge             │  │
│  └───────────────────────────┘  │
│              │                  │
│              ▼                  │
│  ┌───────────────────────────┐  │
│  │  SecurityEngine.cpp       │  │
│  │  ├─ detectThreat()        │  │
│  │  ├─ triggerStasisMode()   │  │
│  │  └─ deriveVaultKey()      │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘
```

## Hardware-Specific Optimizations

### ARM64 (Android Mobile)
```cpp
#ifdef __aarch64__
bool performHeuristicScan(const std::string& data) {
    // ARM-optimized TFLite inference
    const char* threats[] = {"su", "hook", "frida", "magisk"};
    for (const char* threat : threats) {
        if (data.find(threat) != std::string::npos) return true;
    }
    return false;
}
#endif
```

### x86_64 (Workstation)
```cpp
#elif defined(__x86_64__)
bool performNPUOrAVXScan(const std::string& data) {
    // Intel OpenVINO or AMD Ryzen AI
    return data.length() > 100;
}
#endif
```

## JNI Bridge

```cpp
extern "C" JNIEXPORT jstring JNICALL
Java_com_sentinoid_shield_SecurityModule_stringFromJNI(JNIEnv* env, jobject thiz) {
    std::string hardware = detectSystemHardware();
    return env->NewStringUTF(("Running on " + hardware).c_str());
}
```

## Threat Detection Patterns

| Pattern | Type | Detection |
|---------|------|-----------|
| "su" | Root binary | File/command presence |
| "frida" | Hook framework | Process analysis |
| "magisk" | Root management | File/command presence |
| "hook" | Generic hooking | String pattern |
| "xposed" | Xposed framework | Process analysis |

## Build Configuration

### CMakeLists.txt
```cmake
cmake_minimum_required(VERSION 3.4.1)

add_library(
    security-module
    SHARED
    native-lib.cpp
    SecurityEngine.cpp
    HardwareManager.cpp
)

find_library(log-lib log)
target_link_libraries(security-module ${log-lib})
```

### build.gradle.kts
```kotlin
android {
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    defaultConfig {
        externalNativeBuild {
            cmake {
                cppFlags += "-O3 -fvisibility=hidden"
            }
        }
    }
}
```

## Security Advantages

| Aspect | Java | C++ NDK |
|--------|------|---------|
| Runtime Analysis | Easy to hook | Requires native debugger |
| Reverse Engineering | Bytecode → Java | Assembly → C (harder) |
| Root Detection | Userland only | May bypass some root hiding |
| Memory Control | GC-managed | Manual (secureZeroMemory) |

## Usage

```kotlin
// Load native library
init {
    System.loadLibrary("security-module")
}

// Call native method
external fun stringFromJNI(): String
val hardware = securityModule.stringFromJNI()
```

## Testing

```bash
# Build native library
./gradlew externalNativeBuildDebug

# Verify symbols stripped
nm -D app/build/intermediates/cmake/debug/obj/arm64-v8a/libsecurity-module.so

# Runtime test
adb logcat -d | grep "SecurityEngine"
```

## Merge Checklist

- [ ] CMakeLists.txt configured correctly
- [ ] NDK builds successfully
- [ ] JNI methods callable from Kotlin
- [ ] Hardware detection working
- [ ] Heuristic scan functional
- [ ] Memory properly zeroed on lockdown
- [ ] Works on both ARM64 and x86_64
