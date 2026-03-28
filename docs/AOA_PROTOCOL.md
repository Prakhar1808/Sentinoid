# Android Open Accessory (AOA) Protocol

This document describes the USB communication protocol between the Android device and PC in Sentinoid.

## Overview

The Android Open Accessory (AOA) protocol allows an Android device to act as a USB accessory when connected to a PC, enabling communication without requiring USB debugging or network connectivity.

## Protocol Specification

### USB Identifiers

| Field | Value |
|-------|-------|
| Vendor ID | 0x18D1 (Google) |
| Product ID (Accessory) | 0x2D00 |
| Product ID (Accessory + ADB) | 0x2D01 |

### Connection Flow

1. **Device Detection**: PC scans for Android device in AOA mode
2. **Handshake**: PC sends accessory identification strings
3. **Accessory Mode**: Android switches to accessory mode
4. **Data Transfer**: Bulk transfers for log streaming

### Data Format

```
[Header: 4 bytes][Payload: variable][Checksum: 2 bytes]
```

#### Header Structure
```
Byte 0: Message Type
  - 0x01: LOG_DATA
  - 0x02: COMMAND
  - 0x03: STATUS
  - 0x04: PING
  - 0x05: PONG
  
Bytes 1-3: Payload Length (24-bit big-endian)
```

#### Message Types

##### LOG_DATA (0x01)
Sent from Android to PC with captured log entries.

```
Header: 0x01 [LL LL HH]
Payload: UTF-8 encoded log string
```

##### COMMAND (0x02)
Sent from PC to Android for control commands.

```
Header: 0x02 [LL LL HH]
Payload: Command string (e.g., "START", "STOP", "STATUS")
```

##### STATUS (0x03)
Response to status queries.

```
Header: 0x03 [LL LL HH]
Payload: Status JSON
```

### Example Communication

#### Android → PC: Sending Logs
```
[0x01][0x00][0x00][0x1F][AUTH][Failed root access attempt...]
```

#### PC → Android: Command
```
[0x02][0x00][0x00][0x05][STATUS]
```

#### Android → PC: Status Response
```
[0x03][0x00][0x00][0x15]{"connected":true,"logs_captured":42}
```

## Security Considerations

1. **No Internet Required**: AOA operates entirely over USB
2. **No ADB**: Doesn't require USB debugging enabled
3. **Local Only**: All analysis happens on the PC
4. **Air-Gap Compatible**: Can be used in isolated environments

## Implementation Details

### PC Side (C++)

```cpp
// Sending a command
void sendCommand(const std::string& cmd) {
    uint8_t header[4] = {0x02, 
                        (uint8_t)(cmd.length() >> 16),
                        (uint8_t)(cmd.length() >> 8),
                        (uint8_t)(cmd.length())};
    sendData(header, 4);
    sendData((uint8_t*)cmd.c_str(), cmd.length());
}
```

### Android Side (Kotlin)

```kotlin
// Sending log data
fun sendLogs(logData: String) {
    val header = byteArrayOf(0x01) + toLengthBytes(logData.length)
    outputStream.write(header)
    outputStream.write(logData.toByteArray(Charsets.UTF_8))
}
```

## Troubleshooting

### Device Not Detected
1. Check USB cable supports data transfer
2. Ensure Android device supports AOA (Android 3.1+)
3. Try different USB port

### Connection Drops
1. Check USB power delivery
2. Reduce log buffer size
3. Increase retry timeout

### Permission Denied
```bash
# Linux - add user to plugdev group
sudo usermod -aG plugdev $USER
# Log out and back in
```
