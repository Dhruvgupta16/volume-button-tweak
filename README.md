# Volume Tweak

## Overview
Volume Tweak is an Android Accessibility Service that intercepts hardware volume button events and maps them to a flexible set of user‑defined actions.  It works system‑wide, even when the target media app does not expose standard playback controls.

## Features
- **Gesture remapping** – single, double, triple and quadruple volume‑button presses, plus dual‑press and dual‑hold combos.
- **Custom actions** – media control, app launch, system utilities, ear‑protection caps, playback‑speed cycling, voice recorder shortcut, etc.
- **Safety enhancements** – `SAFE_VOLUME_CAP` limits media volume to 75 % to protect hearing.
- **Call handling** – volume keys adjust call volume directly when a cellular or VoIP call is active.
- **Diagnostic logging** – persistent rolling log with a one‑tap “Send Diagnostic Report” UI.
- **Open‑app shortcut** – quick button to launch the tweak UI itself.
- **Configurable sensitivity** – dual‑press detection window adjustable from the UI.
- **Custom combo sequences** – user‑defined token chains mapped to any action.

## Installation
1. Clone or download the repository.
2. Open the project in Android Studio (minimum API 23).
3. Build the app (`./gradlew assembleDebug`).
4. Install the generated APK on your device.
5. Open **Settings → Accessibility** and enable **Volume Tweak**.
6. Open **Settings → Notification access** and enable **Volume Tweak** for media control.

## Usage
- Open the app to configure gestures, choose actions, and toggle hardware preferences.
- The UI reflects the current configuration; changes are saved automatically.
- Use the **Send Diagnostic Report** button to share logs for troubleshooting.

## Development
- The core service lives in `VolumeTweakService.kt`.
- All actions are defined in `ActionRegistry.kt` – add new actions by inserting a `TweakAction` entry.
- UI bindings are in `MainActivity.kt`; layout is `activity_main.xml`.
- Logging is handled by `LogBuffer.kt`, which writes a rolling file to internal storage.

## License
This project is released under the MIT License. See the `LICENSE` file for details.
