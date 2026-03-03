# 🛡️ Sentinoid — Sentinel Edge

**The Hardware-Bound, Air-Gapped Mobile Security Fortress**

---

## 📜 Manifesto

**Zero Internet Permissions. Zero Backdoors. Absolute Sovereignty.**

Sentinoid binds secrets to physical silicon and biometric presence. If the device or biometric is not present, the keys do not exist.

> **No Cloud. No Backdoors. Absolute Sovereignty.**

---

## 🎯 Principles

| Principle | Description |
|-----------|-------------|
| **Isolation by Default** | The app is architecturally incapable of network communication |
| **Hardware Binding** | Keys are generated and stored in TEE or StrongBox and are non-exportable |
| **User Sovereignty** | Recovery is offline and user-controlled via BIP39 and Shamir shards |
| **Transparency** | Core components are open source and auditable |

---

## 🔐 Core Security Pillars

### Biometric-Bound Cryptographic Shroud

- **Encrypted Wrapping:** AES-256-GCM for vaults and sensitive app data
- **TEE Binding:** Keys generated inside the TEE and unsealed only on biometric match
- **InvalidatedByBiometricEnrollment:** Adding a new biometric triggers immediate key purge

### Connectivity Shield

- **Zero Internet Manifest:** `android.permission.INTERNET` is intentionally absent
- **Air-Gap Enforcement:** Runtime monitoring terminates unauthorized Wi-Fi, Bluetooth, NFC attempts
- **Unidirectional Update Diode:** Updates delivered via signed BIP39 QR bundles; device never requests network access

### Feature Permission Manager (FPM)

- **Service-Level Interception:** Uses Accessibility and Device Admin to intercept Mic, Camera, GPS calls
- **Mock-Stream Injection:** Unauthorized callers receive null or static streams to avoid alerting malware while preserving OS stability

---

## 🛡️ Advanced Tactical Defense

### Honeypot Trap

- **Ghost Data:** AI-generated decoy logs and files that look realistic to scrapers
- **Silent Alarm:** Unauthorized access to ghost data triggers local Total Lockdown and records hardware ID

### Hardware Watchdog & Anti-Tamper

- **Integrity Monitoring:** Detects rooting, bootloader unlock, USB debugging, and voltage anomalies
- **Self-Destruct Protocol:** Hardware keys are purged on tamper conditions or unauthorized biometric enrollment

### Local Heuristic Malware Engine

- **Offline Behavioral Analysis:** Compressed INT8 TFLite model for overlay and screen-scraping detection
- **Compressed Hash Registry:** Local signatures for known threats
- **Battery Optimized:** Asynchronous scanning designed for minimal drain (demo target <1%)

---

## 🏗️ Architecture Summary

Three Atmospheres share one core engine (LACE) and a hardware abstraction layer.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SENTINOID SUITE                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌──────────────────────┐  │
│  │   LITE              │  │   ULTRA             │  │   MOBILE-A           │  │
│  │   Universal Android│  │   AMD PC            │  │   Samsung S26 + AMD │  │
│  │                     │  │                     │  │                      │  │
│  │  • BIP39+Shamir     │  │  • NPU/Accelerator  │  │  • RDNA Shroud      │  │
│  │  • Deception       │  │  • SEV-SNP          │  │  • Gait-Lock        │  │
│  │    Honeypot        │  │    Isolation        │  │  • NPU Heuristics   │  │
│  │  • LACE Core       │  │  • Side-Channel     │  │                      │  │
│  │                     │  │    Jamming          │  │                      │  │
│  └─────────┬───────────┘  └──────────┬──────────┘  └──────────┬───────────┘  │
│            │                         │                         │              │
│            └─────────────────────────┼─────────────────────────┘              │
│                                      │                                        │
│                           ┌──────────▼──────────┐                            │
│                           │   LACE Core Engine   │                            │
│                           │   INT8 TFLite + HAL  │                            │
│                           └──────────────────────┘                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Three Atmospheres

| Atmosphere | Target Hardware | Focus |
|------------|-----------------|-------|
| **LITE** | Universal Android/ARM | Deception honeypot, BIP39 + Shamir recovery, lightweight LACE core |
| **ULTRA** | AMD PC (High-performance) | NPU/accelerator offload, side-channel jamming, SEV-SNP isolation |
| **MOBILE-A** | Samsung S26 with AMD accelerator | RDNA frame-buffer obfuscation, NPU-driven gait/proximity locks |

### Hardware Auto Detect

HAL chooses execution path: NPU → OpenVINO/AVX → Standard C++ heuristics.

### Adoption Bridge

Bridge Mode uses USB encrypted telemetry so high-performance AMD PCs can act as local security clouds for budget devices without Internet.

---

## ⚙️ Core Components

| Component | Description |
|-----------|-------------|
| **LACE** | Local AI Engine — Quantized INT8 TFLite (distilled CNN + Random Forest). CPU/XNNPACK on LITE; offload to AMD accelerators on ULTRA and MOBILE-A |
| **Shroud** | Biometric + TEE binding; keys invalidated on unauthorized enrollment |
| **Shield** | Hardware air-lock; unidirectional update diode via signed BIP39 QR bundles |
| **Tactical Layer** | Local heuristic engine; AI-generated honeypot and entropy file systems |
| **Watchdog** | Hardware watchdog integrated with secure processor; monitors voltage, timing, and side-channel anomalies |
| **Recovery** | BIP39 + Shamir's Secret Sharing (2-of-3 shards: paper, hardware, biometric) |

---

## 🔒 Security Enhancements

- **Post-Quantum Cryptography:** Kyber-768 and Dilithium hybrid for vault sealing and signatures
- **Acoustic Masking:** NPU-driven ultrasonic jitter to prevent sonic PIN exfiltration
- **Side-Channel Jamming:** Randomized accelerator power and emission patterns to mask EM and acoustic signatures on ULTRA and MOBILE-A

---

## 📅 Roadmap & Prototype Plan

**Goal:** Deliver an auditable Sentinoid prototype demonstrating hardware-bound keys, zero-internet operation, offline recovery, honeypot detection, local heuristic detection, and a Bridge Mode demo with accelerator offload simulation.

**Duration:** 4 weeks (28 days)

### Week 1 — Core Crypto & Recovery

- Day 1–2: Implement BIP39 provider and unit tests
- Day 3–4: Implement AES-256-GCM vault wrapper and Keystore integration
- Day 5–7: Implement TEE binding and InvalidatedByBiometricEnrollment lifecycle
- **Milestone:** Offline Emergency Recovery using 2 of 3 shards

### Week 2 — Interception & Honeypot

- Day 8–10: Build FPMInterceptor using Accessibility and Device Admin hooks
- Day 11–13: Implement HoneypotEngine and silent alarm logging
- Day 14: Integrate WatchdogService root/bootloader checks
- **Milestone:** Ghost file access triggers local lockdown

### Week 3 — Local AI & Optimization

- Day 15–17: Integrate malware_model.tflite and wire LACE inference pipeline
- Day 18–20: Optimize inference with XNNPACK and quantized INT8 path
- Day 21: Battery profiling and tuning
- **Milestone:** Heuristic detection with minimal battery impact

### Week 4 — Bridge Mode & Demo Polish

- Day 22–24: Implement USB Bridge Mode and Action Token protocol
- Day 25–26: Add accelerator offload simulation and performance gauge UI
- Day 27: Full end-to-end hero demo run
- Day 28: Final audit pass and prepare demo assets
- **Milestone:** Successful hero demo and audit checklist

---

## 📁 Project Structure

```
Sentinoid/
├── app/
│   ├── src/main/cpp/
│   │   └── SecurityModule.cpp
│   ├── src/main/kotlin/
│   │   ├── WatchdogService.kt
│   │   ├── FPMInterceptor.kt
│   │   ├── HoneypotEngine.kt
│   │   └── BIP39Provider.kt
│   └── AndroidManifest.xml
├── assets/
│   └── malware_model.tflite
├── docs/
│   ├── architecture_diagram_text.md
│   └── pitch_90s_script.md
├── vendor/
│   └── mobile_a_samsung_s26_module/
└── Makefile
```

---

## 🛠️ Quick Start

### 1. Clone and Build

```bash
git clone https://github.com/Prakhar1808/Sentinoid.git
cd Sentinoid

# Build the C++ LACE core
make
```

### 2. Install APK

```bash
# Install LITE APK on an air-gapped test device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Run Emergency Recovery

1. Launch the app
2. Navigate to Recovery
3. Enter 2 of 3 BIP39 shards
4. Validate offline seed reconstitution

### 4. Test Honeypot

1. Access ghost/decoy files
2. Observe Silent Alarm triggering
3. Verify local lockdown activation

### 5. Optional: MOBILE-A Module

On Samsung S26 with AMD accelerator:

```bash
adb install vendor/mobile_a_samsung_s26_module/*.apk
```

---

## ✅ Testing & Validation Checklist

- [ ] Confirm `android.permission.INTERNET` absent in built manifest
- [ ] Validate TEE key lifecycle and InvalidatedByBiometricEnrollment behavior
- [ ] Reconstruct BIP39 seed from two shards offline
- [ ] Honeypot triggers lockdown on ghost file access
- [ ] FPM returns mock streams without crashing OS
- [ ] LACE detects simulated overlay and scraper events
- [ ] Battery profiling meets demo drain target (<1%)
- [ ] Offload simulation shows CPU vs accelerator usage graph
- [ ] MOBILE-A verification ensures vendor module activates only on Samsung S26 with AMD accelerator

---

## 📋 Technical Specifications

| Component | Technology | Detail |
|-----------|------------|--------|
| **Encryption** | AES-256-GCM, Kyber-768, Dilithium | Hardware-backed, PQC hybrid |
| **Connectivity** | Air-Gapped | Strict Zero-Internet Policy |
| **Recovery** | BIP39 Mnemonic + Shamir | 24-word seed split into 2-of-3 shards |
| **Key Storage** | Android Keystore, StrongBox | Non-exportable, TEE-bound keys |
| **Interception** | Accessibility Framework | Service-level hardware call masking |

---

## 💻 Hardware Support

| Tier | Hardware | Model | Latency |
|------|----------|-------|---------|
| **ULTRA** | AMD PC (High-performance) | Qwen2.5-7B | ~50ms |
| **MOBILE-A** | Samsung S26 + AMD | Native NPU | ~30ms |
| **LITE** | Android 8+ (2GB RAM) | INT8 TFLite | ~500ms |

---

## ⚖️ License

**GNU General Public License v3.0 (GPL-3.0)** for core components.

### Audit Targets

- **AndroidManifest.xml:** Confirm absence of `android.permission.INTERNET`
- **SecurityModule.cpp:** Review hardware-bound key derivation
- **BIP39Provider.kt:** Verify offline seed generation and shard logic
- **Makefile & Build Scripts:** Ensure no hidden network steps in CI

### Suggested Audit Steps

1. Build from source in an air-gapped environment
2. Inspect binary for network syscall usage
3. Validate TEE key lifecycle and InvalidatedByBiometricEnrollment behavior
4. Reproduce recovery using two shards offline

---

## 🤝 Contribution and Governance

- **Open Source Core:** Core LITE components are GPL-3.0
- **Hardware Vendor Hooks:** Documented and optional
- **Contribution Guidelines:** Use issue tracker for feature requests and security disclosures
- **Responsible Disclosure:** Report security issues via repository security policy

---

## 📚 Documentation

- [Hardware Setup Guide](docs/HARDWARE_SETUP.md)
- [AOA Protocol Details](docs/AOA_PROTOCOL.md)
- [Security Prompts](docs/SECURITY_PROMPTS.md)
- [Architecture Diagram](docs/architecture_diagram_text.md)
- [90-Second Pitch Script](docs/pitch_90s_script.md)

---

> **Sentinoid: Secure by Design. Isolated by Choice.**
