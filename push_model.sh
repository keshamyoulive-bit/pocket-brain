#!/usr/bin/env bash
# Pushes MediaPipe .task model files to the device for on-device LLM inference.
# Usage: ./push_model.sh <model.task> [another.task ...]
set -euo pipefail

# Prevent Git Bash/MSYS on Windows from rewriting the device-side POSIX path
# (e.g. /data/local/tmp/llm/) into a Windows path before it reaches adb.
export MSYS_NO_PATHCONV=1

DEVICE_DIR="/data/local/tmp/llm/"

if [ "$#" -eq 0 ]; then
    echo "Usage: $0 <model.task> [another.task ...]" >&2
    exit 1
fi

# Validate everything up front so a bad argument doesn't leave a partial push.
for model_file in "$@"; do
    if [ ! -f "$model_file" ]; then
        echo "Model file not found: $model_file" >&2
        exit 1
    fi
done

adb shell mkdir -p "$DEVICE_DIR"

for model_file in "$@"; do
    echo "Pushing $(basename "$model_file") ..."
    adb push "$model_file" "$DEVICE_DIR"
done

echo "Done. Models on device:"
adb shell ls -la "$DEVICE_DIR"
