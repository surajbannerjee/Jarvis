# Jarvis - Multilingual Autonomous Local Android Assistant with Relationship Memory

Jarvis is a 100% offline, privacy-first, zero-cloud native Android assistant built with Kotlin. It combines local natural language parsing, local Room database relationship memory, caller announcement, an autonomous answering machine, and a WhatsApp notification reader.

## Key Features
- **Stark AI Persona Voice System**: Switch freely between 10 iconic Tony Stark AIs:
  1. **J.A.R.V.I.S.** (Calm, British English, sophisticated butler)
  2. **F.R.I.D.A.Y.** (Tactical, energetic, warm Irish female voice)
  3. **K.A.R.E.N.** (Gentle, soothing Spider-Man suit lady)
  4. **E.D.I.T.H.** (High-tech orbital satellite defense & tactical glasses AI)
  5. **U.L.T.R.O.N.** (Deep, cold, menacing robotic resonance)
  6. **V.E.R.O.N.I.C.A.** (Hulkbuster satellite deployment & armor interface)
  7. **J.O.C.A.S.T.A.** (Ethicist AI, elegant, logical synthetic voice)
  8. **H.O.M.E.R.** (Classic Stark estate & armory security system)
  9. **P.L.A.T.O.** (Fast, analytical, background protocol supervisor)
  10. **D.U.M.-E** (Playful, robotic chime tone with fire extinguisher readiness)
- **Multilingual Intent & Speech Engine**: Supports Bengali, Hindi, English, and Banglish/Hinglish with dynamic TTS voice switching and offline speech recognition.
- **Relationship Memory Database**: Local SQLite/Room storage (`jarvis_memory.db`) for mapping relationships (`bou`, `wife`, `mummy`, `maa`, `boss`) to contacts.
- **Smart Relationship Dialing**: Dial contacts using natural phrases (*"Amar bou ke call koro"*, *"Mummy ko call lagao"*).
- **Caller Announcer**: Announces incoming callers with persona-specific relationship tags (*"Boss, tomar bou call korche"*).
- **Autonomous Answering Machine**: Auto-picks up unattended calls after 15 seconds, speaks voicemail on speakerphone using the active AI voice, and hangs up.
- **WhatsApp Notification Reader**: Intercepts WhatsApp messages and speaks sender + message preview aloud with deduplication.

## Quick Start
1. Open the project in **Android Studio** or download prebuilt APK from GitHub Releases.
2. Install on your Android 11 to Android 14+ device.
3. Follow [SETUP_GUIDE.md](SETUP_GUIDE.md) to grant runtime permissions, Notification Access, and Accessibility Service.
