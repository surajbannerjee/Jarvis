# J.A.R.V.I.S. Android Assistant - Comprehensive Setup & Operational Guide

## 1. Overview
**Jarvis** is a 100% offline, privacy-focused, zero-cloud native Android background assistant built in Kotlin. It features:
- **Multilingual Intent & Speech Engine**: Native speech recognition and TTS dynamically supporting Bengali, Hindi, and English (and Romanized Banglish/Hinglish).
- **Relationship Memory Database**: Local SQLite/Room storage (`jarvis_memory.db`) to train and map personal relationships (`bou`, `wife`, `mummy`, `maa`, `baba`, `boss`) to actual Android contacts.
- **Relationship Caller Announcement**: Cross-references incoming callers with trained memory to announce calls personally (e.g., *"Boss, tomar bou call korche"*).
- **Autonomous Answering Machine**: If an incoming call rings unanswered for a configurable duration (default: 15 seconds / ~4 rings), Jarvis automatically answers the call, routes audio through the speakerphone, plays a multilingual voicemail message, and terminates the call.
- **WhatsApp Notification Reader**: Intercepts, deduplicates, and reads incoming WhatsApp messages aloud without overlapping.

---

## 2. Requirements & Prerequisites
- **Target OS**: Android 11 (API 30) through Android 14+ (API 34).
- **Build Tools**:
  - Android Studio Iguana / Jellyfish / Koala (or later).
  - JDK 17.
  - Android SDK Platform 34.
- **Offline Speech Packs (Crucial for 100% Offline Capability)**:
  - On the Android device, go to **Settings > System > Languages & Input > Speech > Speech Recognition / Offline speech recognition**.
  - Download the offline language packs for:
    - **English (India / US)**
    - **Bengali (India - বাংলা)**
    - **Hindi (India - हिन्दी)**
  - Also ensure Google Text-to-Speech (or device native TTS) has offline voice data downloaded for Bengali, Hindi, and English.

---

## 3. Step-by-Step Permission & Service Activation

Jarvis operates in the background and interacts with the telephony stack and notifications. Follow these setup steps once you install the APK on your device:

### Step 1: Launch the App & Grant Core Permissions
1. Open the **Jarvis** app on your phone.
2. In the **System Permissions & Services** section, tap the **Grant** button next to **Core Phone & Audio Permissions**.
3. Allow the requested system dialogs:
   - **Microphone**: Allow while using the app (required for voice commands).
   - **Phone & Call Management**: Allow (required for detecting incoming calls and dialing numbers).
   - **Contacts**: Allow (required for resolving caller names and looking up numbers).
   - **Notifications**: Allow (for Android 13+ foreground/service notifications).

### Step 2: Enable WhatsApp Notification Access
1. In the Jarvis dashboard, tap **Enable** next to **WhatsApp Notification Access**.
2. Android will open the **Device & app notifications / Notification access** settings page.
3. Locate **Jarvis** (or *Jarvis WhatsApp Notification Reader*) in the list.
4. Toggle the switch to **ON** and confirm the system security prompt.

### Step 3: Enable Accessibility Service (For Auto-Answer & Auto-Hangup)
> Modern Android versions (Android 11–14+) restrict apps from directly answering phone calls without an interactive dialer interface. Jarvis utilizes a lightweight accessibility service as an OEM-agnostic bridge to press the Answer and End Call buttons automatically.

1. In the Jarvis dashboard, tap **Enable** next to **Call Answering Accessibility**.
2. Android will open the **Accessibility** settings.
3. Tap **Installed apps / Downloaded services**.
4. Select **Jarvis Autonomous Call Assistant**.
5. Turn the toggle **ON** and tap **Allow** on the permission confirmation dialog.
   *(Note: On Android 13/14, if it says "Restricted setting", go to App Info for Jarvis > tap the 3 dots in the top right > tap "Allow restricted settings", then return to Accessibility).*

### Step 4: Bypass Battery Optimization (Keep Jarvis Alive in Background)
To prevent OEMs (Samsung OneUI, Xiaomi MIUI/HyperOS, Oppo/Vivo ColorOS/Funtouch) from killing Jarvis in the background:
1. In the Jarvis dashboard, tap **Enable** next to **Battery Optimization Bypass**.
2. In the system prompt, choose **Allow / Don't optimize**.
3. **Additional OEM Steps (if applicable)**:
   - **Xiaomi / Redmi**: Enable **Autostart** in App Settings > Set Battery Saver to **No Restrictions**.
   - **Samsung**: Add Jarvis to **Never sleeping apps**.
   - **Oppo / Realme / Vivo**: Enable **Allow background activity** and **Auto-launch**.

---

## 4. How to Use & Train Jarvis

### A. Training Relationships (Voice & Dashboard)

#### Via Voice Command:
Tap the glowing **Microphone** button on the dashboard and speak naturally:
- **Bengali**:
  - *"Jarvis, amar bou er nam Priya"*
  - *"Amar bou holo Priya Sharma"*
  - *"Amar boss holo Rahul Sir"*
- **Hindi**:
  - *"Meri mummy ka naam Sunita Sharma hai"*
  - *"Mera bhai Rahul hai"*
- **English**:
  - *"Remember my wife is Priya"*
  - *"Remember my boss is Rahul"*

Jarvis will instantly auto-resolve the phone number from your device contacts, save it to `jarvis_memory.db`, and reply in the same language:
- Bengali: *"Mone rakhlam, tomar bou holo Priya."*
- Hindi: *"Yaad rakha, aapka mummy Sunita Sharma hai."*
- English: *"I have remembered that your wife is Priya."*

#### Via Dashboard UI:
1. Tap the **+ Add Relationship** button in the dashboard.
2. Enter the relationship tag (e.g. `bou`, `wife`, `mummy`, `boss`).
3. Enter the contact name as saved in your phone contacts.
4. (Optional) Provide direct phone number or leave blank to auto-resolve.
5. Tap **Save to Memory**.

---

### B. Smart Relationship Dialing
Tap the **Microphone** button or trigger voice recognition and say:
- **Bengali**: *"Amar bou ke call koro"* or *"Boss ke phone lagao"*
- **Hindi**: *"Mummy ko call lagao"* or *"Bhai ko phone karo"*
- **English**: *"Call my wife"* or *"Call my boss"*

**Execution Flow**:
1. Jarvis looks up the relationship in local memory.
2. Resolves `bou` -> `Priya Sharma` -> retrieves `+919876543210`.
3. Replies: *"Priya Sharma ke call korchi..."* and triggers `ACTION_CALL`.
4. If not found in memory, Jarvis prompts: *"Ami bou er number jani na. Kar sathe connect korbo?"*.

---

### C. Caller Announcement & Autonomous Answering Machine

1. **Incoming Call Arrival**:
   - When a call arrives, `JarvisCallReceiver` intercepts the ringing state.
   - If the caller's number or contact name matches a trained alias (`bou`):
     - Ringtone ducks and Jarvis speaks: *"Boss, tomar bou call korche."*
   - If it's an unmapped contact:
     - Jarvis speaks: *"Boss, incoming call from [Name]."*

2. **Unattended Ringing (Autonomous Pickup)**:
   - Jarvis starts a countdown timer (default: **15 seconds**, configurable in Settings).
   - If you answer or dismiss the call manually, the timer cancels immediately.
   - If the call remains unanswered after 15 seconds:
     - Jarvis autonomously answers the call (`TelecomManager.acceptRingingCall()` or via `JarvisAccessibilityService`).
     - Activates **Speakerphone** (`audioManager.isSpeakerphoneOn = true`).
     - Speaks the multilingual answering message through `STREAM_VOICE_CALL`:
       > *"Namaskar, ami boss er assistant Jarvis bolchi. Boss akhon bayre achen ba phone dhorche paren ni. Kono dorkari kotha thakle WhatsApp-e message pathan. Dhonnobad."*
     - Waits 2 seconds, then terminates the call (`telecomManager.endCall()` or Accessibility action).

---

### D. WhatsApp Notification Reader
- Whenever a WhatsApp message arrives from `com.whatsapp`, `JarvisNotificationService` intercepts the message.
- Intelligent deduplication ensures group chats and rapidly sent consecutive messages don't overlap or spam your speakers.
- Jarvis speaks:
  > *"[Sender Name] ekta WhatsApp message pathiyeche: [Message Preview]."*

---

## 5. Architecture Summary
```
+-----------------------------------------------------------+
|                        JARVIS APP                         |
+-----------------------------------------------------------+
          |                        |                      |
          v                        v                      v
+------------------+     +-------------------+   +--------------------+
|  JarvisDatabase  |     |   CommandParser   |   | JarvisSpeechManager|
| (Room Memory DB) |     |  (Bengali/Hindi/  |   |  (Native STT &     |
|   AliasEntity    |     |      English)     |   |   Dynamic TTS)     |
+------------------+     +-------------------+   +--------------------+
          |                        |                      |
          +------------+-----------+                      |
                       |                                  |
                       v                                  v
+------------------------------------+   +------------------------------------+
|      Telephony & Autonomous        |   |       WhatsApp Notification        |
|          Answering Machine         |   |               Reader               |
|  - JarvisCallReceiver              |   |  - JarvisNotificationService       |
|  - AnsweringMachineManager         |   |  - Deduplication Cache             |
|  - JarvisAccessibilityService      |   +------------------------------------+
+------------------------------------+
```
