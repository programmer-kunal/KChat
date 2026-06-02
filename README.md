<div align="center">

# 💬 KChat

### Modern Real-Time Android Chat Application built with Kotlin & Jetpack Compose

<img src="https://img.shields.io/badge/Kotlin-2.0-blueviolet?style=for-the-badge&logo=kotlin"/>
<img src="https://img.shields.io/badge/Jetpack%20Compose-Modern%20UI-4285F4?style=for-the-badge&logo=jetpackcompose"/>
<img src="https://img.shields.io/badge/Firebase-Realtime-orange?style=for-the-badge&logo=firebase"/>
<img src="https://img.shields.io/badge/MVVM-Architecture-success?style=for-the-badge"/>
<img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android"/>

<br>

> 🚀 A beautifully designed real-time messaging app with modern UI, image sharing, friend system, group chats, push notifications, and Firebase integration.

</div>

---

# ✨ Features

## 🔐 Authentication
- Firebase Authentication
- Secure Login & Signup
- Persistent User Sessions

## 💬 Real-Time Messaging
- Instant One-to-One Chat
- Real-Time Message Sync
- Modern Chat UI
- Clear Chat Feature
- Delete for Everyone Support

## 👥 Friend System
- Add Friends
- Friend Request System
- Unfriend Feature
- Dynamic Chat Management

## 👨‍👩‍👧‍👦 Group Chat
- Real-Time Group Messaging
- Multiple User Communication
- Dedicated Group Interface

## 🖼 Media Sharing
- Image Sharing Support
- Gallery & Camera Integration
- Firebase Cloud Storage

## 🔔 Notifications
- Push Notifications using FCM
- Real-Time Message Alerts

## 🎨 Modern UI/UX
- Jetpack Compose UI
- Smooth Animations
- Beautiful Dark Theme
- Responsive Layout
- Enhanced Bottom Sheets
- Modern Action Top Bars

---

# 📱 App Screenshots

<div align="center">

## 🔑 Authentication

<img src="assets/screenshots/LoginScreen.jpg" width="250"/>

---

## 🏠 Home Screen

<img src="assets/screenshots/HomeScreen.jpg" width="250"/>

---

## 💬 Personal Chat

<img src="assets/screenshots/ChattingScreen.jpg" width="250"/>

---

## 👥 Group Chat

<img src="assets/screenshots/GroupChatScreen.jpg" width="250"/>

---

## ➕ Add Friends

<img src="assets/screenshots/AddFriendScreen.jpg" width="250"/>

---

## 📩 Friend Requests

<img src="assets/screenshots/FriendRequestScreen.jpg" width="250"/>

---

## ⚙️ Profile Options

<img src="assets/screenshots/ProfileOptionScreen.jpg" width="250"/>

</div>

---

# 🛠 Tech Stack

| Technology | Usage |
|------------|-------|
| Kotlin | Main Programming Language |
| Jetpack Compose | Modern Android UI |
| Firebase Auth | Authentication |
| Firebase Realtime Database | Real-Time Data |
| Firebase Storage | Image Storage |
| Firebase Cloud Messaging | Push Notifications |
| MVVM Architecture | Clean Architecture |
| Coil | Image Loading |
| Hilt | Dependency Injection |

---

# 🏗 Architecture

KChat follows **MVVM (Model-View-ViewModel)** architecture for scalable and maintainable development.

```text
UI (Compose)
   ↓
ViewModel
   ↓
Repository
   ↓
Firebase Services
