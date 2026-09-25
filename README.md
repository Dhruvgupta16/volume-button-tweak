# Volume Button Tweak

A low-footprint Android accessibility utility designed for Nothing OS and Android 8.0+. It maps simultaneous hardware Volume Up and Volume Down key presses to media control commands during active audio playback.

---

## Control Mapping & Customization

| Action | Hardware Trigger | Configurable Default |
| :--- | :--- | :--- |
| Single Click | 1 Simultaneous Press (Vol Up + Down) | Play / Pause (Reassignable to Next, Prev, Mute, Flashlight) |
| Double Click | 2 Simultaneous Presses | Next Track (Reassignable to Play/Pause, Prev) |
| Triple Click | 3 Simultaneous Presses | Previous Track (Reassignable to Next, Play/Pause) |
| Volume Ramping | Press and Hold | Continuous volume step adjustments (smooth stock Android ramping) |

---

## Architecture & Features

### Continuous Volume Ramping
- Fixes accessibility key repeat suppression by running a localized continuous stepper (`110ms` interval) while a single volume key is physically held down.
- Single clicks adjust volume by 1 notch after the initial deferral window, and immediately ramp continuously if held down.
- Simultaneous presses are intercepted within the sensitivity window and dispatched to configured media commands without affecting volume.

### Master Kill Switch
- Allows suspending all gesture interception in 1 tap without disabling the Android Accessibility Service in system settings.
- When suspended, key events bypass all logic with zero latency.

### In-App Update Engine
- Direct integration with the GitHub Releases API (`https://api.github.com/repos/Dhruvgupta16/volume-button-tweak/releases/latest`).
- Detects newer semantic releases and provides a 1-tap download prompt.

### Multi-App Whitelist Picker
- Select any installed media applications via an interactive multi-choice picker (e.g. YouTube Music, Spotify, Apple Music, VLC, Audible). When active, volume controls only trigger if the playing media originated from one of the designated apps.

### Haptic Feedback & Glyph Interface
- Tactile feedback: 1 pulse (40ms) for Play/Pause, 2 pulses for Next, 3 pulses for Previous via `VibrationAttributes.USAGE_MEDIA`.
- Nothing Phone (2a) Glyph pulse support: Rear LED flash pulses on gesture confirmation.

### Resource Footprint
- Average Process Memory: 3 to 5 MB Private RAM (~40 MB total PSS with shared framework mappings).
- CPU Utilization: Below 0.1% using event-driven kernel counters.

---

## Installation & Setup

1. Download the latest release APK from GitHub Actions artifacts or releases.
2. Install the package on the device.
3. Launch the application and select **Enable Accessibility Service**.
4. In system settings, navigate to **Installed apps > Volume Button Tweak** and toggle the service on.
