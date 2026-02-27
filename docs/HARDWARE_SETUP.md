# Hardware Setup Guide

Sentinoid supports multiple hardware tiers, automatically detecting and optimizing for your available compute resources.

## Hardware Tiers

| Tier | Hardware | Recommended Models | Expected Performance |
|------|----------|-------------------|---------------------|
| T1 | RTX 5060+ (NVIDIA) | Qwen2.5-7B | ~50ms latency |
| T2 | AMD GPU (ROCm) | Qwen2.5-7B | ~80ms latency |
| T3 | Older NVIDIA GPU | Qwen2.5-4B | ~150ms latency |
| T4 | Integrated Graphics | Llama3.2-3B | ~500ms latency |
| T5 | CPU Only | Qwen2.5-1.8B | ~2s latency |

## Installation

### Prerequisites

#### Arch Linux
```bash
sudo pacman -Syu
sudo pacman -S base-devel cmake libusb curl jsoncpp
```

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install build-essential cmake libusb-1.0-dev libcurl4-openssl-dev libjsoncpp-dev
```

#### macOS
```bash
brew install cmake libusb jsoncpp curl
```

### Install Ollama

#### Arch Linux
```bash
yay -S ollama
```

#### Ubuntu/Debian
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

#### macOS
```bash
brew install ollama
```

### Pull Recommended Models

```bash
# For RTX 5060+
ollama pull qwen2.5:7b

# For older GPUs
ollama pull qwen2.5:4b

# For CPU fallback
ollama pull qwen2.5:1.8b
```

## Building the PC Engine

```bash
cd pc
make install-deps  # Show dependency install commands
make               # Build the project
```

## Running

### Start Ollama (in another terminal)
```bash
ollama serve
```

### Run Sentinoid
```bash
# Auto-detect hardware and use appropriate model
./bin/sentinoid-pc

# Test mode (analyzes sample logs)
./bin/sentinoid-pc --test

# Specify hardware tier
./bin/sentinoid-pc --tier rtx-high

# Run without LLM (log capture only)
./bin/sentinoid-pc --no-llm
```

## Hardware Detection

The engine automatically detects your hardware by:

1. **NVIDIA**: Checking `nvidia-smi` for GPU name
2. **AMD**: Checking `rocminfo` for GPU info
3. **Fallback**: Defaulting to CPU mode

## Performance Tuning
>_only do advanced stuff if you know what you're doing, best of luck!_

### NVIDIA GPU
```bash
# For RTX 50/40/30 series - uses CUDA
nvidia-smi -pl 300  # Increase power limit for better performance (very much optional)
```

### AMD GPU
```bash
# Ensure ROCm is installed
rocm-smi
```

## Troubleshooting

### Ollama Not Available
```bash
# Check if Ollama is running
curl http://localhost:11434/api/tags

# Start Ollama
ollama serve
```

### USB Device Not Detected
```bash
# Check USB devices
lsusb

# Ensure you have permissions
sudo usermod -aG plugdev $USER
# Log out and back in
```

### Model Download Issues
```bash
# List available models
ollama list

# Pull specific model
ollama pull <model-name>
```
