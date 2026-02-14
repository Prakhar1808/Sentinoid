AppWatch is an open‑source privacy tool that gives you unprecedented visibility into how installed apps use their permissions.
It empowers you to detect suspicious behaviour, understand what your apps are really doing, and take back control of your data.

### 🛡️ Built with privacy in mind – no cloud, no tracking, full transparency.

### ✨ Features
#### 👁️ Permission Dashboard (No Root Required)

+ List all installed apps with their requested and granted permissions.

+ Colour‑coded risk indicators for dangerous permissions (location, camera, microphone, contacts, etc.).

+ One‑tap shortcuts to Android’s native permission manager to revoke permissions instantly.

+ Search and filter apps by name or permission type.

### 📊 Real‑time Permission Monitor (Root / ADB)

+ Unlock advanced monitoring by granting elevated privileges.

+ Live log of every permission access event (e.g., “Camera accessed by Instagram at 14:32”).

+ Historical timeline of permission usage per app.

+ Alerts for unexpected permission use in the background.

### 🔍 Network & Tracker Detection (Optional VPN Mode)

+ Analyse outgoing network traffic to detect if personal data is being sent to known trackers or unexpected servers.

+ Local on‑device analysis – no data leaves your phone.

### 🧪 Static APK Analysis

+ Scan APK files before installation to review permissions and embedded tracking libraries.

+ Flag apps that request excessive or suspicious permissions.

📸 Screenshots
> to be added

### 🚀 Getting Started
#### Prerequisites

+ Android device running API 24+ (Android 7.0).

+ For full real‑time monitoring: root or ADB access (see below).

#### Installation

+ Download the latest APK from the Releases page.

+ Enable “Install from unknown sources” if sideloading.

+ Open the app and grant the necessary permissions.

#### Enabling Advanced Monitoring
Option A: Using ADB (No Root)

Connect your device to a computer with USB debugging enabled.

Run the following command to grant the required permissions:
```bash

adb shell pm grant com.yourdomain.appwatch android.permission.PACKAGE_USAGE_STATS
adb shell pm grant com.yourdomain.appwatch android.permission.READ_LOGS

Restart the app – the live monitor will now work.
```

Option B: Rooted Device

Simply grant superuser access when prompted by AppWatch.

#### 🛠️ Built With

Kotlin & Jetpack Compose – Modern, reactive UI.

AppOpsManager – Core API for tracking permission usage.

PackageManager – Retrieving app info and permissions.

Room – Local storage for permission history.

WorkManager – Background monitoring tasks.

TensorFlow Lite (planned) – On‑device anomaly detection.

### 🗺️ Roadmap

Basic permission dashboard (phase 1)

Real‑time permission logs (phase 2)

Network traffic analyser (VPN mode)

ML‑based behavioural anomaly detection

Export reports in JSON/CSV

F‑Droid release

### 🤝 Contributing

We welcome contributions! Whether it’s bug reports, feature requests, or pull requests – please read our Contributing Guidelines first.

+ Report bugs via Issues

+ Discuss ideas in Discussions

### 📄 License

This project is licensed under the GNU General Public License v3.0 – see the LICENSE file for details.
### 🙏 Acknowledgments

Inspired by Permission Manager X and Lumen Privacy Monitor.

Thanks to all open‑source contributors who make Android privacy research possible.

### 📬 Contact

> to be added soon
