# PocketBrain

PocketBrain is an offline, on-device LLM chat app for Android. It runs a
quantized Gemma 3 model entirely on-device via
[MediaPipe's LLM Inference API](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference)
(`com.google.mediapipe:tasks-genai`), using the phone's GPU when available and
falling back to CPU automatically if GPU initialization fails.

**Zero internet required at chat time.** Once the model file is on the
device, prompts and responses never leave the phone — there's no network
call, no API key, no cloud inference.

> 📸 **Screenshot placeholder** — add a screenshot of the chat screen here,
> e.g. `![PocketBrain screenshot](docs/screenshot.png)`.

## Setup

### 1. Clone the repo

```bash
git clone https://github.com/keshamyoulive-bit/pocket-brain.git
cd pocket-brain
```

### 2. Download the model

PocketBrain ships configured for **Gemma 3 1B IT (int4 quantized)**, a small
model that runs comfortably on-device. It's a gated model on Hugging Face, so
you'll need an account that has accepted Google's license:

1. Log into [huggingface.co](https://huggingface.co) and visit
   [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT).
2. Click **"Acknowledge license"** to accept the
   [Gemma license](https://ai.google.dev/gemma/terms) (usually approved
   instantly).
3. Generate a [read-scoped access token](https://huggingface.co/settings/tokens).
4. Download the file:

   ```bash
   curl -L -H "Authorization: Bearer <YOUR_HF_TOKEN>" \
     -o gemma3-1b-it-int4.task \
     https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task
   ```

   (~530 MB — this file is gitignored, do not commit it.)

### 3. Push the model to your device

With a physical Android device connected over USB (developer mode + USB
debugging enabled), either run the included script:

```bash
./push_model.sh gemma3-1b-it-int4.task
```

or do it manually with `adb`:

```bash
adb shell mkdir -p /data/local/tmp/llm/
adb push gemma3-1b-it-int4.task /data/local/tmp/llm/
```

### 4. Build and run

```bash
./gradlew installDebug
```

Or open the project in Android Studio and run the `app` module on your
device. On first launch, select **GEMMA_3_1B_IT_GPU** (or the CPU variant) —
since the file already exists on-device at the expected path, PocketBrain
loads it directly instead of downloading.

## Notes

- Requires a physical Android device (SDK 24+) — the emulator doesn't expose
  a usable GPU backend for the LLM Inference API.
- If GPU initialization fails on your device, PocketBrain automatically
  retries on CPU and shows which backend is active in the chat header.
- Running a local LLM is GPU/CPU-intensive; expect the device to warm up
  during longer conversations.
