# Jarvis - Multilingual Autonomous Local Android Assistant with Relationship Memory

Jarvis is a 100% offline, privacy-first, zero-cloud native Android assistant built with Kotlin. It combines local natural language parsing, local Room database relationship memory, caller announcement, an autonomous answering machine, and a WhatsApp notification reader.

## Key Features
- **Multilingual Intent & Speech Engine**: Supports Bengali, Hindi, English, and Banglish/Hinglish with dynamic TTS voice switching and offline speech recognition.
- **Relationship Memory Database**: Local SQLite/Room storage (`jarvis_memory.db`) for mapping relationships (`bou`, `wife`, `mummy`, `maa`, `boss`) to contacts.
- **Smart Relationship Dialing**: Dial contacts using natural phrases (*"Amar bou ke call koro"*, *"Mummy ko call lagao"*).
- **Caller Announcer**: Announces incoming callers with relationship tags (*"Boss, tomar bou call korche"*).
- **Autonomous Answering Machine**: Auto-picks up unattended calls after 15 seconds, speaks voicemail on speakerphone, and hangs up.
- **WhatsApp Notification Reader**: Intercepts WhatsApp messages and speaks sender + message preview aloud with deduplication.

## Quick Start
1. Open the project in **Android Studio**.
2. Sync Gradle and build the app.
3. Install on your Android 11 to Android 14+ device.
4. Follow [SETUP_GUIDE.md](SETUP_GUIDE.md) to grant runtime permissions, Notification Access, and Accessibility Service.
