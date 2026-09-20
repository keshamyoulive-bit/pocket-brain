#!/usr/bin/env bash
# Pushes a MediaPipe .task model file to the device for on-device LLM inference.
# Usage: ./push_model.sh [path/to/model.task]
set -euo pipefail

# Prevent Git Bash/MSYS on Windows from rewriting the device-side POSIX path
# (e.g. /data/local/tmp/llm/) into a Windows path before it reaches adb.
export MSYS_NO_PATHCONV=1

MODEL_FILE="${1:-gemma3-1b-it-int4.task}"
DEVICE_DIR="/data/local/tmp/llm/"

if [ ! -f "$MODEL_FILE" ]; then
    echo "Model file not found: $MODEL_FILE" >&2
    exit 1
fi

adb shell mkdir -p "$DEVICE_DIR"
adb push "$MODEL_FILE" "$DEVICE_DIR"
