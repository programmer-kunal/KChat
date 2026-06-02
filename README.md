<div align="center">

# 💬 KChat

### Modern Real-Time Android Chat Application built with Kotlin & Jetpack Compose

<img src="https://img.shields.io/badge/Kotlin-2.0-blueviolet?style=for-the-badge&logo=kotlin"/>
<img src="https://img.shields.io/badge/Jetpack%20Compose-Modern%20UI-4285F4?style=for-the-badge&logo=jetpackcompose"/>
<img src="https://img.shields.io/badge/Firebase-Realtime-orange?style=for-the-badge&logo=firebase"/>
<img src="https://img.shields.io/badge/MVVM-Architecture-success?style=for-the-badge"/>
<img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android"/>

<br>

> 🚀 A beautifully designed real-time messaging app with modern UI, image sharing, group chats, friend system, and Firebase-powered realtime communication.

</div>

---

# ✨ Features

## 🔐 Authentication
- Firebase Authentication
- Secure Login & Signup
- Persistent Sessions

## 💬 Real-Time Chat
- Instant Messaging
- Real-Time Sync
- One-to-One Chat
- Group Chat Support
- Clear Chat Feature
- Delete for Everyone

## 👥 Friend System
- Add Friends
- Friend Requests
- Accept / Reject Requests
- Unfriend Feature

## 🖼 Media Sharing
- Image Sharing
- Camera & Gallery Support
- Firebase Cloud Storage

## 🔔 Notifications
- Push Notifications using FCM
- Real-Time Message Alerts

## 🎨 Modern UI/UX
- Jetpack Compose UI
- Smooth Dark Theme
- Responsive Layouts
- Modern Bottom Sheets
- Improved Realtime Experience

---

# 📱 App Screenshots

<div align="center">

<table>
<tr>
<td align="center">

## 🔑 Login Screen

<img src="assets/screenshots/LoginScreen.jpg" width="230"/>

</td>

<td align="center">

## 🏠 Home Screen

<img src="assets/screenshots/HomeScreen.jpg" width="230"/>

</td>

<td align="center">

## 💬 Personal Chat

<img src="assets/screenshots/ChattingScreen.jpg" width="230"/>

</td>
</tr>

<tr>
<td align="center">

## 👥 Group Chat

<img src="assets/screenshots/GroupChatScreen.jpg" width="230"/>

</td>

<td align="center">

## ➕ Add Friends

<img src="assets/screenshots/AddFriendScreen.jpg" width="230"/>

</td>

<td align="center">

## 📩 Friend Requests

<img src="assets/screenshots/FriendRequestScreen.jpg" width="230"/>

</td>
</tr>

<tr>
<td align="center" colspan="3">

## ⚙️ Profile Options

<img src="assets/screenshots/ProfileOptionScreen.jpg" width="230"/>

</td>
</tr>

</table>

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

KChat follows modern **MVVM Architecture** for scalable and maintainable development.

```text
UI (Jetpack Compose)
        ↓
     ViewModel
        ↓
     Repository
        ↓
 Firebase Services
