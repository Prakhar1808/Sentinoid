# 🔌 Feature: USB Bridge (AOA)

## Overview

This branch implements secure, air-gapped communication between Sentinoid and a trusted workstation via USB. Using Android Open Accessory (AOA) protocol, it enables encrypted log export, AI model updates, and forensic data retrieval without internet connectivity.

## What's Included

### Core Components
- **BridgeMode.kt** - USB AOA communication handler
- **UsbReceiver.kt** - BroadcastReceiver for USB events
- **SilentAlarmManager.kt** - Log buffering and export coordination

## Architecture

```
┌─────────────────────┐         USB AOA          ┌─────────────────────┐
│   Sentinoid Device  │◄─────────────────────────►│  Trusted Workstation│
│  (Android Phone)    │   (No Internet Required) │  (Air-Gapped PC)    │
└─────────────────────┘                         └─────────────────────┘
         │                                              │
         ▼                                              ▼
┌─────────────────────┐                         ┌─────────────────────┐
│  BridgeMode.kt      │                         │  Custom Client App  │
│  ├─ initiateHandshake│                         │  (Python/C++)       │
│  ├─ handleIncoming  │                         │  ├─ Open AOA        │
│  └─ sendTacticalLogs  │                         │  └─ Stream Data     │
└─────────────────────┘                         └─────────────────────┘
```

## How It Works

### 1. USB Attachment Detection
```kotlin
class UsbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_USB_ACCESSORY_ATTACHED) {
            val accessory = intent.getParcelableExtra<UsbAccessory>(EXTRA_ACCESSORY)
            BridgeMode.initiateHandshake(usbManager, accessory)
        }
    }
}
```

### 2. AOA Connection
```kotlin
fun initiateHandshake(manager: UsbManager, accessory: UsbAccessory) {
    fileDescriptor = manager.openAccessory(accessory)
    inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    outputStream = FileOutputStream(fileDescriptor.fileDescriptor)
    
    // Start listening for workstation commands
    thread { handleIncomingData() }
}
```

### 3. Command Processing
```kotlin
private fun handleIncomingData() {
    val buffer = ByteArray(16384)
    while (true) {
        val bytesRead = inputStream?.read(buffer) ?: -1
        if (bytesRead > 0) {
            val message = String(buffer, 0, bytesRead)
            when {
                message.contains("GET_LOGS") -> sendTacticalLogs()
                message.contains("PUSH_AI_MODEL") -> receiveModel()
                message.contains("STATUS") -> sendStatus()
            }
        }
    }
}
```

## Supported Commands

| Command | Description | Response |
|---------|-------------|----------|
| `GET_LOGS` | Request encrypted logs | Stream logs back |
| `PUSH_AI_MODEL` | Upload new TFLite model | Acknowledge receipt |
| `STATUS` | Get security status | JSON status report |
| `LOCKDOWN` | Remote trigger lockdown | Confirmation |

## AndroidManifest.xml Configuration

```xml
<uses-permission android:name="android.permission.USB_PERMISSION" />
<uses-feature android:name="android.hardware.usb.accessory" />

<receiver android:name=".UsbReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_ACCESSORY_ATTACHED" />
    </intent-filter>
</receiver>

<service android:name=".BridgeMode" android:enabled="true" android:exported="false"/>
```

## Why Air-Gapped?

| Aspect | Internet | USB AOA |
|--------|----------|---------|
| Attack Surface | Global | Physical only |
| Data Exfiltration | Possible | Requires physical access |
| Remote Attacks | Possible | Impossible |
| Malware Entry | Network-based | Physical only |

## Log Export

```kotlin
fun sendTacticalLogs(encryptedLogs: ByteArray) {
    outputStream?.write(encryptedLogs)
    outputStream?.flush()
    Log.i("BridgeMode", "Tactical logs offloaded to workstation")
}
```

## Workstation Client (Python)

```python
import usb.core

dev = usb.core.find(idVendor=0x18D1, idProduct=0x2D01)
dev.write(1, b"GET_LOGS\n")
logs = dev.read(0x81, 16384)
```

## UI Indicator

```kotlin
val isBridgeConnected by SilentAlarmManager.isBridgeConnected.collectAsState()
Icon(
    imageVector = if (isBridgeConnected) Icons.Default.Usb else Icons.Default.UsbOff,
    contentDescription = "Bridge Status"
)
```

## Integration with Main

- **Vault**: Exports encrypted logs for forensics
- **Honeypot**: Sends intrusion evidence
- **Watchdog**: Status reports for monitoring

## Testing

```bash
# Verify USB permission
adb shell dumpsys usb | grep sentinoid

# Trigger accessory mode
adb shell am broadcast -a android.hardware.usb.action.USB_ACCESSORY_ATTACHED

# Check bridge logs
adb logcat -d | grep "BridgeMode"
```

## Security Properties

- Zero internet attack surface
- Physical presence required for data access
- Encrypted end-to-end communication
- All commands can require user confirmation

## Merge Checklist

- [ ] BridgeMode declared in AndroidManifest.xml
- [ ] UsbReceiver handles attachment events
- [ ] AOA tunnel established correctly
- [ ] Log export working via SilentAlarmManager
- [ ] UI indicator shows connection status
- [ ] Works with workstation client
