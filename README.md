<div align="center">

# 💬 KChat

### 🚀 From Chat to Context
**Real-Time Communication × Context Intelligence × Modern Android**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Compose-Material_3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase-Auth_|_RTDB_|_FCM-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com)
[![Gemini](https://img.shields.io/badge/AI_Engine-Gemini_3.5_Flash--Lite-8E75C2?style=for-the-badge&logo=googlegemini&logoColor=white)](https://ai.google.dev)
[![ZegoCloud](https://img.shields.io/badge/ZegoCloud-Voice_&_Video-007FFF?style=for-the-badge)](https://www.zegocloud.com)

<br/>

<p align="center">
  <b>KChat</b> is a modern, production-grade Android communication app built with <b>Kotlin</b> and <b>Jetpack Compose</b>.<br/>
  It elevates standard messaging into <b>Context-Aware Communication</b> by pairing low-latency chat and voice/video calling with on-demand conversational intelligence powered by <b>Google Gemini 3.5 Flash-Lite</b> and <b>Firebase AI Logic</b>.
</p>

---

### 📥 Download & Try KChat

<p align="center">
  <a href="https://drive.google.com/drive/folders/1RB-iQOIvo52ucmvXW4rn5lG9Vo4NQUrR" target="_blank">
    <img src="https://img.shields.io/badge/%E2%AD%90%20DOWNLOAD%20LATEST%20RELEASE%20APK%20(Google%20Drive)-34A853?style=for-the-badge&logo=googledrive&logoColor=white&labelColor=1A73E8" alt="Download Latest Release APK" height="46"/>
  </a>
  <br/>
  <sub><b>Direct Download:</b> Signed Release APK (<code>arm64-v8a</code>, Android 8.0+) • Protected by Firebase App Check & reCAPTCHA Enterprise</sub>
</p>

---

</div>

<br/>

## 🌟 The Core Paradigm: *From Chat to Context*

Traditional messaging platforms are strictly message-centric pipes:

$$\mathbf{Send} \;\longrightarrow\; \mathbf{Receive} \;\longrightarrow\; \mathbf{Reply}$$

In modern digital interactions, words alone frequently miss nuance, intent, and subtle conversational context. **KChat transitions from message-centric to context-centric communication**:

$$\mathbf{Send} \;\longrightarrow\; \mathbf{Understand} \;\longrightarrow\; \mathbf{Assist} \;\longrightarrow\; \mathbf{Communicate}$$

| Dimension | 📱 Traditional Messaging | 🚀 KChat (Context-Aware) |
|---|---|---|
| **Core Architecture** | Passive message relay (Text in $\rightarrow$ Text out) | Real-time chat + active conversational assistance |
| **Conversational Context** | Disconnected bubbles; manual interpretation | Multi-message context evaluation on demand |
| **AI Assistance** | Third-party chatbots or generic auto-corrections | Contextual Smart Reply natively embedded in chat |
| **Intelligence Signals** | None; user guesses emotional nuance | Probabilistic estimates of tone, sentiment, intent & urgency |
| **Human Control** | Manual unassisted typing | **100% Human-in-the-Loop**: AI suggests, user reviews, edits, and sends |
| **Privacy & Security** | Continuous background harvesting or cloud training | **Zero background scanning**; on-demand, in-memory processing |

---

## ⚡ KChat Smart Reply (Emotion & Sentiment Intelligence)

**KChat Smart Reply** is an on-demand conversational assistant for 1-to-1 chats. Powered by **Google Gemini 3.5 Flash-Lite** via **Firebase AI Logic**, it evaluates selected conversational context in a single pass to provide real-time understanding and natural reply options.

### 📸 Smart Reply Showcase (Single-Row Experience)

<table align="center" width="100%">
  <tr>
    <td align="center" width="25%">
      <b>Smart Reply Action</b><br/><br/>
      <img src="assets/screenshots/KChatSmartReplyScreen.jpg" alt="Smart Reply Screen" width="195"/>
      <br/><br/>
      <sub>One-tap contextual AI access from chat top bar</sub>
    </td>
    <td align="center" width="25%">
      <b>Message Selection</b><br/><br/>
      <img src="assets/screenshots/SmartReplySelection.jpg" alt="Message Selection" width="195"/>
      <br/><br/>
      <sub>Select single or multiple messages as context</sub>
    </td>
    <td align="center" width="25%">
      <b>Conversation Analysis</b><br/><br/>
      <img src="assets/screenshots/SmartReplyAnalysis.jpg" alt="Conversation Analysis" width="195"/>
      <br/><br/>
      <sub>Probabilistic tone, sentiment, intent & urgency</sub>
    </td>
    <td align="center" width="25%">
      <b>Reply Suggestions</b><br/><br/>
      <img src="assets/screenshots/SmartReplySuggestions.jpg" alt="Reply Suggestions" width="195"/>
      <br/><br/>
      <sub>2–4 concise replies for instant composer insertion</sub>
    </td>
  </tr>
</table>

<br/>

### 🔄 Smart Reply User Flow

```
[User Selects Messages] ──► [Tap Smart Reply] ──► [Gemini 3.5 Flash-Lite Inference]
                                                              │
                                                              ▼
[Manual Send] ◄── [User Reviews / Edits] ◄── [Composer Insertion] ◄── [Tone & Suggestion Sheet]
```

1. **Context Selection**: User long-presses to select 1 to 5 relevant messages in direct chat.
2. **On-Demand Inference**: Tapping the Smart Reply button dispatches context to Gemini 3.5 Flash-Lite.
3. **Multi-Signal Analysis**: The model returns structured JSON with:
   - **Likely Tone**: Probabilistic tonal assessment (*e.g., "Likely friendly", "Likely inquiring", "Likely neutral"*).
   - **Sentiment**: Overall emotional polarity (*Positive, Neutral, or Negative*).
   - **Intent**: Inferred participant goal (*e.g., "Seeking clarification", "Confirming plan"*).
   - **Urgency**: Inferred conversation priority (*Low, Medium, or High*).
   - **Suggestions**: 2 to 4 context-relevant response chips.
4. **Composer Insertion**: Selecting any chip inserts the text into the existing message composer.
5. **Human-in-the-Loop Delivery**: The user reviews, freely edits, and manually presses **Send**.

> [!NOTE]
> **Responsible AI & Privacy Design**:
> - **Never Auto-Sends**: The user always retains 100% final editorial authority.
> - **Zero Background Scanning**: Operates exclusively when the user manually triggers it.
> - **In-Memory & Transient**: AI insights live in `StateFlow` and are never saved to Firebase Realtime Database.
> - **No Embedded API Keys**: Uses Firebase AI Logic authorized via Firebase App Check at **₹0 cost**.
> - **Responsible Language**: Provides probabilistic estimations without claiming mind-reading or definitive emotion detection.

---

## 📱 Application Screenshots Showcase

<table align="center" width="100%">
  <tr>
    <td align="center" width="25%">
      <b>Login & Auth</b><br/><br/>
      <img src="assets/screenshots/LoginScreen.jpg" alt="Login Screen" width="190"/>
      <br/><br/>
      <sub>One-tap Google Sign-In & Firebase Auth</sub>
    </td>
    <td align="center" width="25%">
      <b>Home & Presence</b><br/><br/>
      <img src="assets/screenshots/HomeScreen.jpg" alt="Home Screen" width="190"/>
      <br/><br/>
      <sub>Live user status & recent message previews</sub>
    </td>
    <td align="center" width="25%">
      <b>Personal Chat</b><br/><br/>
      <img src="assets/screenshots/ChattingScreen.jpg" alt="Personal Chat" width="190"/>
      <br/><br/>
      <sub>1-to-1 direct messaging & media sharing</sub>
    </td>
    <td align="center" width="25%">
      <b>Group Chat</b><br/><br/>
      <img src="assets/screenshots/GroupChatScreen.jpg" alt="Group Chat" width="190"/>
      <br/><br/>
      <sub>Multi-user real-time conversation rooms</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="25%">
      <b>Add Friend</b><br/><br/>
      <img src="assets/screenshots/AddFriendScreen.jpg" alt="Add Friend" width="190"/>
      <br/><br/>
      <sub>User search & connection discovery</sub>
    </td>
    <td align="center" width="25%">
      <b>Friend Requests</b><br/><br/>
      <img src="assets/screenshots/FriendRequestScreen.jpg" alt="Friend Requests" width="190"/>
      <br/><br/>
      <sub>Incoming & outgoing request management</sub>
    </td>
    <td align="center" width="25%" colspan="2">
      <b>Profile & Settings</b><br/><br/>
      <img src="assets/screenshots/ProfileOptionScreen.jpg" alt="Profile Screen" width="190"/>
      <br/><br/>
      <sub>Avatar customization, status & account controls</sub>
    </td>
  </tr>
</table>

---

## 💬 Core KChat Features

| Capability | Engineering Highlights |
|---|---|
| **Authentication** | One-tap Google Sign-In & Email/Password via Firebase Auth. |
| **Real-Time 1-to-1 Messaging** | Low-latency WebSocket sync powered by Firebase Realtime Database. |
| **Group Channels** | Multi-user conversation rooms with instant broadcasting. |
| **Online Presence & Read State** | Live connection tracking via `.info/connected` with automated `onDisconnect` cleanup. |
| **Friend System & Discovery** | Search users, send invitations, and manage connection requests. |
| **Media & Image Sharing** | Camera capture & gallery picking uploaded to Supabase Storage, rendered via Coil. |
| **Voice & Video Calling** | HD 1-to-1 audio and video calls powered by ZegoCloud Prebuilt Call UIKit. |
| **Push Notifications** | Background delivery and call alerts via Firebase Cloud Messaging (FCM). |
| **Application Integrity** | Protected by Firebase App Check (reCAPTCHA Enterprise on Release, Debug Provider on Debug). |

---

## 🛠️ Technology Stack

| Layer | Technologies | Purpose |
|---|---|---|
| **Language & Platform** | Kotlin 2.0 • JVM 11 • Android SDK 26–36 | Type-safe modern Android development |
| **UI Framework** | Jetpack Compose • Material 3 | Declarative UI, dynamic theming & animations |
| **Architecture** | MVVM • UDF • Coroutines • StateFlow | Predictable reactive state management |
| **Dependency Injection** | Dagger Hilt (`2.51.1`) | Compile-time dependency inversion |
| **Authentication** | Firebase Auth • Google Play Services Auth | Secure credential federation & session persistence |
| **Realtime Database** | Firebase Realtime Database | Real-time WebSocket messaging & presence tracking |
| **Cloud Storage** | Supabase Storage (`1.4.7`) • Ktor (`2.3.13`) | Media file hosting & authenticated image uploads |
| **Conversational AI** | Firebase AI Logic • Gemini 3.5 Flash-Lite | Fast, structured JSON generation (15 RPM / 500 RPD free tier) |
| **App Security** | Firebase App Check • reCAPTCHA Enterprise | Gateway abuse prevention & certificate attestation |
| **Calling Engine** | ZegoCloud Prebuilt Call UIKit (`3.9.11`) | Hardware-accelerated WebRTC voice & video calls |
| **Image Loading** | Coil Compose (`2.6.0`) | Asynchronous image decoding & memory caching |
| **Networking** | Android Volley • Ktor Android Client | FCM payload dispatch & cloud network calls |
| **Build Optimization** | Gradle Kotlin DSL • R8 Minification • ABI Splits | ProGuard optimization & lean `arm64-v8a` release builds |

---

## 📐 Application Architecture

KChat adheres to modern Android engineering principles with **Unidirectional Data Flow (UDF)**:

```
┌───────────────────────────────────────────────────────────┐
│                 JETPACK COMPOSE UI LAYER                  │
│       ChatScreen  •  HomeScreen  •  SmartReplyBottomSheet │
└─────────────────────────────┬─────────────────────────────┘
                              │ UI Events (Clicks, Message Selection)
                              ▼
┌───────────────────────────────────────────────────────────┐
│                      VIEWMODEL LAYER                      │
│     ChatViewModel • DirectChatViewModel • SmartReplyVM    │
│              (Exposes Immutable StateFlow to UI)          │
└─────────────────────────────┬─────────────────────────────┘
                              │ Coroutine Execution (Dispatchers.IO)
                              ▼
┌───────────────────────────────────────────────────────────┐
│                SERVICE & REPOSITORY LAYER                 │
│        GeminiSmartReplyService • RTDB Sync • Supabase     │
└──────┬──────────────────────┼───────────────────────┬─────┘
       │                      │                       │
       ▼                      ▼                       ▼
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│Firebase RTDB │       │ Firebase AI  │       │  ZegoCloud   │
│Realtime Chat │       │Gemini 3.5 FL │       │Voice & Video │
└──────────────┘       └──────────────┘       └──────────────┘
```

### Smart Reply AI Pipeline

```
Selected Messages ──► SmartReplyViewModel ──► GeminiSmartReplyService
                             │                         │
                             ▼                         ▼
   [Guard: Loading check] ───┘                [Format Dialogue: Me/Other]
                                                       │
                                                       ▼
                      [Firebase AI Logic: GenerativeBackend.googleAI()]
                                                       │
                                                       ▼
                      [Google Gemini 3.5 Flash-Lite (application/json)]
                                                       │
                                                       ▼
                      [Local Android JSONObject Parser (Safe Defaults)]
                                                       │
                                                       ▼
                      [SmartReplyBottomSheet: Insights & Suggestion Chips]
                                                       │
                                                       ▼
                      [User Taps Chip ──► Drop into Composer ──► Send]
```

---

## 📊 Status & Future Roadmap

### ✅ Currently Implemented & Available
- [x] Firebase Authentication (Google Sign-In & Email/Password)
- [x] Real-time 1-to-1 direct messaging and group chat rooms
- [x] Live online/offline presence tracking with `onDisconnect` handlers
- [x] Dynamic unread message counters & conversation previews
- [x] User discovery, friend search, and request workflows
- [x] Camera & gallery image sharing via Supabase Storage
- [x] HD 1-to-1 Voice & Video calling via ZegoCloud
- [x] Background push notifications via FCM
- [x] Firebase App Check (reCAPTCHA Enterprise & Debug Provider)
- [x] KChat Smart Reply (Emotion & Sentiment Intelligence)
- [x] Gemini 3.5 Flash-Lite integration with structured JSON outputs
- [x] 100% Human-in-the-loop composer insertion & manual send flow

### 🚀 Future Roadmap (Planned Innovations)
- [ ] **Long-Term Conversation Memory**: Client-side semantic indexing for past message recall.
- [ ] **Smart Thread Summarization**: One-tap AI summaries to catch up on unread group chats.
- [ ] **Scam & Phishing Guard**: On-device heuristic safety alerts for suspicious links or messages.
- [ ] **Communication Tone Analytics**: Periodic personal insights to help balance conversation tone.
- [ ] **Multimodal Smart Reply**: Image context understanding to generate replies for received photos.

---

## 📥 Getting Started & Building from Source

### Download Pre-built Signed APK
Install the latest production build on any Android device running Android 8.0+ (API 26+):

<p align="center">
  <a href="https://drive.google.com/drive/folders/1RB-iQOIvo52ucmvXW4rn5lG9Vo4NQUrR" target="_blank">
    <img src="https://img.shields.io/badge/%F0%9F%93%A5%20Download%20KChat%20Release%20APK-Google%20Drive-34A853?style=for-the-badge&logo=googledrive&logoColor=white&labelColor=1A73E8" alt="Download APK" height="42"/>
  </a>
</p>

### Building from Source

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/programmer-kunal/KChat.git
   cd KChat
   ```

2. **Configure `local.properties`**:
   Copy `local.properties.example` to `local.properties` and add your keys:
   ```properties
   ZEGO_APP_ID=your_zego_app_id
   ZEGO_APP_SIGN=your_zego_app_sign
   SUPABASE_URL=your_supabase_url
   SUPABASE_ANON_KEY=your_supabase_anon_key
   RECAPTCHA_ENTERPRISE_SITE_KEY=your_recaptcha_site_key
   ```

3. **Add Firebase Configuration**:
   Place your `google-services.json` inside the `app/` directory.

4. **Build APK**:
   ```bash
   # Build Debug APK
   ./gradlew assembleDebug

   # Build Signed Release APK
   ./gradlew assembleRelease
   ```

---

## 👨‍💻 Developer & Maintainer

<table align="center">
  <tr>
    <td align="center">
      <a href="https://github.com/programmer-kunal">
        <img src="https://avatars.githubusercontent.com/u/104118335?v=4" width="90px;" alt="Kunal Gupta"/>
        <br />
        <sub><b>Kunal Gupta</b></sub>
      </a>
      <br />
      <sub>Android Developer & AI Application Builder</sub>
      <br /><br />
      <a href="https://github.com/programmer-kunal" target="_blank">
        <img src="https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white" alt="GitHub"/>
      </a>
    </td>
  </tr>
</table>

---

## ⭐ Support & Feedback

If KChat inspires your Android or AI exploration:
- ⭐ **Star this repository** to support the project!
- 🍴 **Fork it** to experiment with Jetpack Compose, Firebase, and Gemini.
- 🐛 Open an **Issue** if you discover bugs or want to suggest new features!

---

<div align="center">
  <sub>Built by <a href="https://github.com/programmer-kunal">KUNAL</a> • KChat: <i>Don't just deliver the conversation. Understand the conversation.</i></sub>
</div>
