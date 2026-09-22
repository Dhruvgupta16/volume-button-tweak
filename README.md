# Volume Button Tweak

A low-footprint Android accessibility utility designed for Nothing OS and Android 8.0+. It maps simultaneous hardware Volume Up and Volume Down key presses to media control commands during active audio playback.

---

## Control Mapping

| Action | Hardware Trigger | Android KeyEvent |
| :--- | :--- | :--- |
| Play / Pause | 1 Simultaneous Press (Vol Up + Down) | `KEYCODE_MEDIA_PLAY_PAUSE` |
| Next Track | 2 Simultaneous Presses | `KEYCODE_MEDIA_NEXT` |
| Previous Track | 3 Simultaneous Presses | `KEYCODE_MEDIA_PREVIOUS` |
| Volume Adjustment | Normal Hold or Single Tap | Stock Android Volume Handling |

---

## Features

- **Multi-App Whitelist Picker**: Select any installed media applications via an interactive multi-choice picker (e.g. YouTube Music, Spotify, Apple Music, VLC, Audible). When active, volume controls only trigger if the playing media originated from one of the designated apps.
- **Haptic Vibration Feedback**: Subtle haptic pulses confirm when gestures are registered (1 pulse for Play/Pause, 2 for Next, 3 for Previous).
- **Screen-Off Pocket Operation**: Transient partial wake locks ensure media controls remain responsive while the phone is locked in a pocket.
- **Nothing Phone (2a) Glyph Light Reaction**: Optional visual pulses on the rear LED interface upon successful gesture execution.
- **Low Memory Footprint**: Average private memory allocation is approximately 3 to 5 MB (total PSS including shared framework mappings is ~40 MB). CPU utilization remains below 0.1% during operation.

---

## Installation & Setup

1. Download the latest release APK from GitHub Actions artifacts or releases.
2. Install the package on the device.
3. Launch the application and select **Enable Accessibility Service**.
4. In system settings, navigate to **Installed apps > Volume Button Tweak** and toggle the service on.
