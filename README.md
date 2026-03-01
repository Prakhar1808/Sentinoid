# 🛡️ Sentinoid

**The Hardware-Bound, Air-Gapped Mobile Security Fortress with AI-Powered Threat Detection**

---

## 📜 The Sentinoid Manifesto

In an age of AI-driven swarm attacks and silent telemetry, we believe that if code can touch the internet, it can be compromised. Sentinoid is an uncompromising security framework designed for absolute isolation. By operating on a Strict Zero-Internet Policy, Sentinoid anchors your data directly to your device's physical silicon. We bind encryption to hardware-locked biometrics, ensuring your data exists only when you are physically present.

> **No Cloud. No Backdoors. No Compromise.**

---

## 🚀 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SENTINOID SUITE                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────┐              ┌──────────────────────────────┐ │
│  │   Android Device │              │         PC Engine            │ │
│  │                  │              │                              │ │
│  │  ┌────────────┐  │   USB AOA   │  ┌────────────────────────┐ │ │
│  │  │LogCapture  │──┼──────────────┼─▶│   AOA Bridge (libusb)  │ │ │
│  │  └────────────┘  │              │  └───────────┬────────────┘ │ │
│  │                  │              │              │              │ │
│  │  ┌────────────┐  │              │  ┌──────────▼───────────┐  │ │
│  │  │ AOA Service│──┘              │  │ Security Engine      │  │ │
│  │  └────────────┘  │              │  │  - Log Parser         │  │ │
│  │                  │              │  │  - Threat Detector    │  │ │
│  │  ┌────────────┐  │              │  │  - Alert Manager      │  │ │
│  │  │KeyManager   │  │              │  └──────────┬───────────┘  │ │
│  │  └────────────┘  │              │             │               │ │
│  │                  │              │  ┌──────────▼───────────┐   │ │
│  │  ┌────────────┐  │              │  │ LLM Client (Ollama)  │   │ │
│  │  │SecurityMod │  │              │  │ - Auto GPU Detect    │   │ │
│  │  └────────────┘  │              │  │ - Model Management   │   │ │
│  └──────────────────┘              │  └──────────────────────┘   │ │
│                                     └──────────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Core Features

### 🔐 Biometric-Bound Cryptographic Shroud
- **Encrypted Wrapping:** AES-256-GCM hardware-backed encryption
- **TEE/StrongBox Binding:** Keys generated in Trusted Execution Environment
- **Zero-Password Policy:** Access tied to your unique biometric signature

### 🔌 Connectivity Shield (Absolute Air-Gap)
- **Hardened Manifest:** Zero network permissions
- **USB AOA Protocol:** Direct Android-to-PC communication without network
- **No ADB Required:** Works without USB debugging enabled

### 🤖 AI-Powered Threat Detection
- **Local LLM Inference:** Runs entirely offline
- **Multi-Tier Hardware Support:** RTX GPUs, AMD GPUs, iGPU, CPU
- **Real-time Analysis:** Sub-10ms threat scoring on NPU/GPUs

### 🛑 Feature Permission Manager (FPM)
- Service-level interception of hardware calls
- Mock-stream injection for unauthorized apps

---

## 💻 Hardware Support (Auto-Detected)

| Tier | Hardware | Model | Latency |
|------|----------|-------|---------|
| T1 | RTX 5060+ | Qwen2.5-7B | ~50ms |
| T2 | AMD GPU | Qwen2.5-7B | ~80ms |
| T3 | Older NVIDIA | Qwen2.5-4B | ~150ms |
| T4 | iGPU | Llama3.2-3B | ~500ms |
| T5 | CPU | Qwen2.5-1.8B | ~2s |

---

## 📁 Project Structure

```
Sentinoid/
├── android/                    # Android app
│   ├── app/src/main/
│   │   ├── java/com/sentinoid/shield/
│   │   │   ├── MainActivity.kt
│   │   │   ├── AoaService.kt       # USB accessory communication
│   │   │   ├── LogCapture.kt      # Security log capture
│   │   │   ├── KeyManager.kt
│   │   │   ├── SecurityModule.kt
│   │   │   └── ...
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── pc/                         # PC security engine
│   ├── src/
│   │   ├── main.cpp            # Entry point
│   │   ├── llm_client.cpp     # Ollama wrapper + auto-detect
│   │   ├── aoa_bridge.cpp    # USB AOA communication
│   │   └── security_engine.cpp # Threat analysis
│   ├── include/               # Headers
│   ├── models/               # Model configs (JSON)
│   └── Makefile
│
├── docs/
│   ├── HARDWARE_SETUP.md      # Per-tier setup guides
│   ├── AOA_PROTOCOL.md       # USB protocol details
│   └── SECURITY_PROMPTS.md   # LLM system prompts
│
└── README.md
```

---

## 🛠️ Quick Start

### 1. Install Dependencies

#### Arch Linux
```bash
# Android - AUR
yay -S android-sdk

# PC - Install build deps
sudo pacman -S base-devel cmake libusb curl jsoncpp ollama
```

#### Ubuntu/Debian
```bash
sudo apt install build-essential cmake libusb-1.0-dev libcurl4-openssl-dev libjsoncpp-dev
curl -fsSL https://ollama.com/install.sh | sh
```

### 2. Pull Models

```bash
# Recommended for RTX 5060+
ollama pull qwen2.5:7b

# For older GPUs
ollama pull qwen2.5:4b

# CPU fallback
ollama pull qwen2.5:1.8b
```

### 3. Build PC Engine

```bash
cd pc
make
```

### 4. Run

```bash
# Start Ollama (in another terminal)
ollama serve

# Run Sentinoid (auto-detects hardware)
./bin/sentinoid-pc

# Test mode
./bin/sentinoid-pc --test
```

### 5. Android App

1. Build with Android Studio / Gradle
2. Install APK on device
3. Connect via USB
4. App automatically starts AOA when accessory detected

---

## 🎯 Hackathon Features

- **AMD Hardware Demo:** Target Ryzen AI for that "NPU-native" angle
- **Air-Gap Security:** No network = no worries
- **USB AOA:** Works without ADB - impressive demo
- **Auto-Detection:** Shows smart hardware utilization
- **Real-time Analysis:** Live threat scoring impresses judges

---

## 📅 Development Roadmap

| Week | Milestone |
|------|-----------|
| 1 | Core encryption + LLM client with auto-detect |
| 2 | AOA Bridge + Android service |
| 3 | End-to-end log streaming |
| 4 | Multi-tier model support + polish |

---

## ⚖️ License

**GNU General Public License v3.0 (GPL-3.0)**

### Verification Process
- **AndroidManifest.xml:** Confirm absence of `android.permission.INTERNET`
- **AoaBridge.cpp:** Audit USB-only communication
- **LlmClient.cpp:** Verify offline-only inference

---

## 🔗 Documentation

- [Hardware Setup Guide](docs/HARDWARE_SETUP.md)
- [AOA Protocol Details](docs/AOA_PROTOCOL.md)
- [Security Prompts](docs/SECURITY_PROMPTS.md)

---

> **Sentinoid: Secure by Design. Isolated by Choice.**
