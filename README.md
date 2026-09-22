# Volume Button Tweak 🎵📱

An ultra-lightweight, zero-bloat Android accessibility utility designed specifically for **Nothing Phone (2a)** (and compatible with Android 8.0+ / Nothing OS). 

It converts simultaneous hardware Volume Up + Volume Down key presses ("Dual Click") into media controls **ONLY** while music is playing or active in a session.

---

## ⚡ Features & Gesture Controls

| Gesture | Action | System KeyEvent |
| :--- | :--- | :--- |
| **1 Dual Click** (Vol Up + Down) | Pause / Resume Song | `KEYCODE_MEDIA_PLAY_PAUSE` |
| **2 Dual Clicks** | Next Song | `KEYCODE_MEDIA_NEXT` |
| **3 or 4 Dual Clicks** | Previous Song | `KEYCODE_MEDIA_PREVIOUS` |

---

## 🔒 Smart Execution & Efficiency
- **Active Only When Music Plays**: Uses `AudioManager.isMusicActive` to dynamically bypass volume key interception when no music is playing. Single volume button presses work completely normally!
- **Negligible Resource Usage**: ~10–15 MB RAM consumption, 0% CPU background drain (runs purely on Android's event dispatcher loop without polling).
- **Nothing OS Dark Aesthetic**: UI designed with a clean, dark theme matching Nothing OS styling.

---

## 🚀 Setup & Installation

### Option 1: Build APK with Gradle
1. Clone this repository:
   ```bash
   git clone https://github.com/Dhruvgupta16/volume-button-tweak.git
   cd volume-button-tweak
   ```
2. Open in Android Studio or build via terminal:
   ```bash
   ./gradlew assembleRelease
   ```
3. Install the APK onto your Nothing Phone (2a).

### Option 2: Enable Accessibility Permission
1. Launch the **Volume Button Tweak** app on your phone.
2. Tap **Enable Accessibility Service**.
3. In System Accessibility Settings, locate **Volume Button Tweak** and toggle it **ON**.
4. Play music in your favorite app (Spotify, YouTube Music, Apple Music, etc.) and enjoy dual-click volume button control!

---

## 🛠 Tech Stack
- **Language**: 100% Kotlin
- **SDK Level**: Min SDK 26 (Android 8.0), Target SDK 34 (Android 14)
- **Core API**: Android `AccessibilityService` (`flagRequestFilterKeyEvents`)
