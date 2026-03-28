# Security Prompts

This document contains the system prompts used for log analysis.

## Primary Security Analysis Prompt

```
Role: Air-Gapped Security Analyst
Task: Analyze Android logs for IOCTL anomalies, USB AOA exploits, and privilege escalation patterns.
Constraint: Output ONLY valid JSON. No prose. No markdown.
Schema: {"threat_level": 0-10, "vectors": [], "mitigation": ""}
Temperature: 0.1
```

## Response Schema

### JSON Fields

| Field | Type | Description |
|-------|------|-------------|
| `threat_level` | integer (0-10) | Severity score |
| `vectors` | array | List of attack vectors detected |
| `mitigation` | string | Recommended action |

### Threat Levels

| Level | Score | Description |
|-------|-------|-------------|
| SAFE | 0-1 | Normal operation |
| LOW | 2-3 | Minor anomaly, monitor |
| MEDIUM | 4-5 | Potential threat, investigate |
| HIGH | 6-7 | Significant threat, action required |
| CRITICAL | 8-9 | Immediate action needed |
| LOCKDOWN | 10 | System compromise detected |

### Attack Vectors

Common vectors detected:

- `ROOT_ACCESS`: Root exploitation attempt
- `USB_EXPLOIT`: USB/AOA protocol attack
- `IOCTL_ABUSE`: Invalid IOCTL requests
- `PRIV_ESC`: Privilege escalation attempt
- `SELINUX_VIOLATION`: SELinux policy violation
- `BINDER_ATTACK`: Binder IPC exploitation
- `OVERLAY_ATTACK`: Screen overlay attack
- `INJECTION`: Code injection attempt

### Example Responses

#### Safe Log
```json
{"threat_level": 0, "vectors": [], "mitigation": "none"}
```

#### Root Attempt Detected
```json
{"threat_level": 8, "vectors": ["ROOT_ACCESS", "PRIV_ESC"], "mitigation": "Block execution and alert user"}
```

#### USB Exploit
```json
{"threat_level": 9, "vectors": ["USB_EXPLOIT", "IOCTL_ABUSE"], "mitigation": "Disconnect USB and lockdown device"}
```

## Temperature Settings

| Use Case | Temperature |
|----------|-------------|
| Production | 0.1 |
| Analysis | 0.2 |
| Exploration | 0.5+ |

For security analysis, always use **temperature 0.1** to ensure deterministic, consistent responses.

## Customization

To modify the security prompt, edit `pc/src/llm_client.cpp`:

```cpp
std::string getSystemPrompt() {
    return R"(Your custom prompt here)";
}
```

## Model-Specific Tuning

### Qwen2.5 Models
Best for structured JSON output. Use lower temperature.

### Llama3.2 Models
More verbose, may need additional formatting instructions.

### DeepSeek-R1
Excellent reasoning, good for complex multi-vector attacks.
