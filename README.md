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

## Architectural Details

### Input Dispatch & Key Filtering
- Utilizes the Android Accessibility Service framework (`flagRequestFilterKeyEvents`, `canRequestFilterKeyEvents="true"`).
- Evaluates key events with a 140ms simultaneous press threshold.
- Normal press-and-hold volume ramping is preserved by checking `KeyEvent.repeatCount > 0` and delegating unhandled events directly to the operating system.

### Resource Utilization
- Average Process Memory: 10 to 14 MB (PSS).
- Background CPU Utilization: Below 0.1% (event-driven execution without active polling threads).
- Screen-Off Pocket Operation: Employs a transient partial wake lock during media key dispatch to guarantee responsiveness while the display is powered off.

### Hardware Reaction
- Integrated with Nothing Phone (2a) rear light interface via `CameraManager` torch control to deliver visual feedback pulses on successful gesture execution.

### App Target Filtering
- Optional target filtering restricts gesture handling exclusively to designated applications (such as YouTube Music or Spotify) or all active media sessions.

---

## Installation & Setup

1. Download the latest release APK from GitHub Actions artifacts or releases.
2. Install the package on the device.
3. Launch the application and select **Enable Accessibility Service**.
4. In system settings, navigate to **Installed apps > Volume Button Tweak** and toggle the service on.
