# Lumi: your AI chat and personal agent for Android

Lumi is a native Android app (Kotlin, Jetpack Compose, **Material 3 Expressive**) that chats with any model you bring a key for. It can also run models fully on your phone, and it can act for you.

## What it does
- **Cloud models with your own API keys.** DeepSeek, **OpenCode Go**, OpenCode Zen, OpenAI, Anthropic, Gemini, OpenRouter, Groq, Mistral and xAI are built in, and you can add any other OpenAI-compatible endpoint.
- **On-device models** through Google LiteRT-LM (GPU, falls back to CPU). You can download Gemma 4 E2B/E4B (which also take images and use agent tools), Qwen 2.5 1.5B, DeepSeek R1 Distill 1.5B and Gemma 3 1B, or import any `.litertlm` file.
- **Ollama / LM Studio on your PC** over Wi-Fi.
- **Chat features:** streaming Markdown, code blocks with copy, tables, collapsible reasoning ("thinking"), stop, regenerate, edit and resend, chat history with search, pin and rename, image, PDF and text-file attachments, voice input, read aloud, share into Lumi from other apps, and JSON export.
- **Personal agent tools:** web search, reading web pages, a calculator, long-term memory, notes, alarms, timers, reminders (as notifications), calendar events, SMS and email drafts, the dialer, opening apps or links, device status, and the clipboard.
- **Personalization:** "About you", response style, memory, dynamic color, and light, dark or system theme.

## Build
1. Install the latest **Android Studio**, open this folder, and let Gradle sync. It downloads AGP 9.1.1, Gradle 9.3.1 and Android SDK 37.
2. Press **Run** with your phone plugged in (USB debugging on). The phone needs Android 12 or newer.

Alternatively, push this folder to a GitHub repo. The included workflow (`.github/workflows/build.yml`) builds a debug APK, which you can download from the run's *Artifacts*.

## First run
- Open **Settings › Cloud providers**, tap **DeepSeek** or **OpenCode Go**, paste your key and press **Fetch models**.
- Or open **Settings › On-device models** and download *Gemma 4 E2B* (2.6 GB, needs about 8 GB RAM). Gated models need a Hugging Face token.
- Tap the model chip at the top of the chat to switch models at any time.

## Notes
- Keys, chats and memory are kept in the app's private storage. Nothing else leaves the phone except requests to the provider you pick.
- Web search reads DuckDuckGo's HTML results page, so it can break if that page changes.
- Reminders use inexact alarms. Alarms, timers and calendar events are handed to your Clock and Calendar apps.
- Model downloads run while the app is open.
