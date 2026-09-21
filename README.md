# PocketBrain

PocketBrain is an offline, on-device LLM chat app for Android. It runs quantized
models entirely on-device via
[MediaPipe's LLM Inference API](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference)
(`com.google.mediapipe:tasks-genai`), using the phone's GPU when available and
falling back to CPU automatically if GPU initialization fails.

**Zero internet required.** Models are side-loaded over `adb`; there is no
in-app download, no API key, and no cloud inference. The app doesn't even
request the `INTERNET` permission — prompts and responses never leave the phone.

> 📸 **Screenshot placeholder** — add a screenshot of the chat screen here,
> e.g. `![PocketBrain screenshot](docs/screenshot.png)`.

## Automatic model routing

There's no model picker. PocketBrain inspects each message with a small
keyword/regex classifier and loads the model best suited to it, swapping the
resident engine when the route changes:

| Route | Model | Triggers |
| --- | --- | --- |
| **Default** (fast chat) | Gemma 3 1B IT (int4) | Anything that doesn't match below |
| **Reasoning** | DeepSeek-R1-Distill-Qwen-1.5B | "why", "explain", "step by step", "solve", arithmetic, long multi-clause questions |
| **Coding** | Phi-4-mini-instruct | Backticks/code formatting, "function", "bug", "error", "stack trace", language names (Kotlin, Python, SQL, …) |

Coding signals take precedence over reasoning ones, so *"why does this function
throw a null pointer exception?"* routes to the coding model.

While a swap is in progress the chat shows a **"Switching to <model>…"** pill,
and the header shows the resident model and its backend, e.g. `DeepSeek-R1 · CPU`.
If a specialist model isn't present on the device, PocketBrain answers with the
default model instead and says so in the chat.

## Setup

### 1. Clone the repo

```bash
git clone https://github.com/keshamyoulive-bit/pocket-brain.git
cd pocket-brain
```

### 2. Download the models

All three `.task` files come from
[litert-community](https://huggingface.co/litert-community) on Hugging Face.
**Total storage required: ~6.3 GB.**

| Model | File | Size | Gated |
| --- | --- | --- | --- |
| Gemma 3 1B | `gemma3-1b-it-int4.task` | ~0.53 GB | Yes — Gemma license |
| DeepSeek-R1-Distill | `DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task` | ~1.86 GB | No |
| Phi-4-mini | `Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task` | ~3.94 GB | No |

**Gemma 3 1B** is gated, so it needs an account that has accepted the licence:

1. Log into [huggingface.co](https://huggingface.co) and open
   [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT).
2. Click **"Acknowledge license"** to accept the
   [Gemma license](https://ai.google.dev/gemma/terms) (usually instant).
3. Generate a [read-scoped access token](https://huggingface.co/settings/tokens).
4. Download it:

   ```bash
   curl -L -H "Authorization: Bearer <YOUR_HF_TOKEN>" \
     -o gemma3-1b-it-int4.task \
     https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task
   ```

**DeepSeek-R1-Distill** and **Phi-4-mini** are ungated — no token needed:

```bash
curl -L -O https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task
curl -L -O https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task
```

`.task` and `.bin` files are gitignored — don't commit them.

### 3. Push the models to your device

With a physical Android device connected over USB (developer mode + USB
debugging enabled), `push_model.sh` takes any number of files and pushes them
all to `/data/local/tmp/llm/`:

```bash
./push_model.sh gemma3-1b-it-int4.task \
                DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task \
                Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task
```

Or do it manually:

```bash
adb shell mkdir -p /data/local/tmp/llm/
adb push <file>.task /data/local/tmp/llm/
```

You can push just the Gemma file to get started — routing falls back to it when
a specialist model is missing.

### 4. Build and run

```bash
./gradlew installDebug
```

Or open the project in Android Studio and run the `app` module. PocketBrain
opens straight into the chat screen and loads Gemma 3 1B in the background.

## Notes

- Requires a physical Android device (SDK 24+) — the emulator doesn't expose a
  usable GPU backend for the LLM Inference API.
- Each AI response shows its generation speed in tokens/sec below the bubble.
- Running local LLMs is GPU/CPU-intensive; expect the device to warm up during
  longer conversations, and note that aggressive OEM thermal managers may kill
  the app under sustained load.
