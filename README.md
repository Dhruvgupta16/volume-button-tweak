# Volume Button Tweak

Low-latency hardware key remapping and media arbitration service for Android 8.0+ and Nothing OS.

---

## Architectural Overview

Volume Button Tweak is an Android background accessibility utility that intercepts physical volume button events at the window manager level, arbitrating between normal volume stepping, continuous ramping, and arbitrary multi-key gesture sequences.

```
                              [ Physical Volume Buttons ]
                                           │
                                           ▼
                     [ Android Window Manager / InputManager ]
                                           │
                                           ▼
                      [ AccessibilityService.onKeyEvent() ]
                                           │
                  ┌────────────────────────┴────────────────────────┐
                  │                                                 │
          [ Master Kill Active? ]                           [ In Phone Call? ]
          (Pass through immediately)                        (Route to STREAM_VOICE_CALL)
                  │                                                 │
                  ▼                                                 ▼
     [ Key Down / Up Classifier ]                      [ Immediate Volume Adjustment ]
                  │
                  ├── Single Key Down ────────► [ Key Deferral Window (60 - 400ms) ]
                  │                                       │
                  │                             ┌─────────┴─────────┐
                  │                             │                   │
                  │                      [ Partner Key? ]    [ Timeout Reached ]
                  │                             │                   │
                  │                            YES                  NO
                  │                             │                   │
                  │                             ▼                   ▼
                  │                     [ Gesture Engine ]   [ Normal Step / Ramp ]
                  │                             │
                  └── Partner Key Arrived ──────┘
                                │
                                ▼
                   [ Sequence State Machine ]
                  (Dual Press, Combos, Holds)
                                │
                                ▼
                     [ Command Dispatcher ]
                                │
        ┌───────────────────────┼────────────────────────┐
        ▼                       ▼                        ▼
[ MediaSession Transport ]  [ System Control ]    [ Accessibility / App ]
- Play / Pause              - Safe Volume Cap     - Launch Application
- Skip Forward / Backward   - Step / Set Volume   - Flashlight / Torch
- Cycle Playback Speed      - Mute / Unmute       - Voice Recorder
```

---

## Technical Subsystems

### 1. Hardware Key Interception & Arbitration
- **Event Hooking:** Leverages `AccessibilityService` configured with `FLAG_REQUEST_FILTER_KEY_EVENTS`.
- **Latency Deferral Window:** When a single volume key (`KEYCODE_VOLUME_UP` or `KEYCODE_VOLUME_DOWN`) is depressed, propagation is deferred for a configurable window (default `160ms - 200ms`, adjustable between `60ms` and `400ms`).
- **Partner Key Matching:** If the opposite volume key is depressed within the deferral window, the event is classified as a simultaneous dual-press. The initial event is consumed (`return true`), preventing unwanted volume changes.
- **Runaway Volume Spike Watchdog:** Continuous volume ramping utilizes a hardware freshness watchdog (`SystemClock.uptimeMillis() - lastHardwareKeyEventTime < 300ms`). If Android drops a key-up event due to an asynchronous window focus shift, lock screen transition, or system dialog display, the continuous ramp automatically terminates, preventing runaway volume spikes to 100%. Operating system synthetic repeat events (`event.repeatCount > 0`) on key down are explicitly discarded.

### 2. Telephony & In-Call Volume Routing
- **Multi-Tier Detection:** In-call state is determined by polling:
  1. `AudioManager.mode` (`MODE_IN_CALL`, `MODE_IN_COMMUNICATION`, `MODE_RINGTONE`)
  2. `TelecomManager.isInCall` / `TelecomManager.isInManagedCall` (Android 8.0+)
  3. `TelephonyManager.callState` (`CALL_STATE_OFFHOOK`, `CALL_STATE_RINGING`)
- **Direct Voice Stream Adjustment:** When a call or ring state is active, physical volume button events immediately bypass gesture classification and directly adjust `AudioManager.STREAM_VOICE_CALL` or `AudioManager.STREAM_RING`.

### 3. Media Session Controller Bridge
- **NotificationListenerService Interception:** Operates a companion `MediaNotificationListener` service to acquire active `MediaController` sessions across playback engines including YouTube Music, Spotify, Apple Music, VLC, and local players.
- **Universal Seeking Protocol:**
  - Standard commands: Dispatches `transportControls.fastForward()` and `transportControls.rewind()`.
  - Millisecond-Precision Seeking: Retrieves current playback position via `MediaController.playbackState.position` and calculates `(currentPosition + (seconds * 1000L))`, dispatched directly through `transportControls.seekTo(targetMs)`.
  - App-Specific Whitelist: Supports per-package whitelisting to restrict gesture triggers to user-specified applications.
  - Always-Active Mode: When enabled, gestures function globally even when no active audio session is playing.

### 4. Dynamic Sequence & Multi-Press Engine
- **Composable Token Grammar:** Supports complex button sequences composed of:
  - `DUAL` (simultaneous press of Volume Up and Volume Down)
  - `DUAL_HOLD` (simultaneous hold of Volume Up and Volume Down)
  - `VOL_UP` (single Volume Up click)
  - `VOL_DOWN` (single Volume Down click)
  - `UP_HOLD` (Volume Up hold)
  - `DOWN_HOLD` (Volume Down hold)
- **Token Resolution Pipeline:** Multi-tap sequences (1, 2, 3, and 4 clicks) and custom combo strings are matched against `ActionRegistry` with fallback handling and haptic confirmation.

---

## Action Registry & Customization Matrix

| Identifier | Parameter Type | Range / Options | Description |
| :--- | :--- | :--- | :--- |
| `PLAY_PAUSE` | None | None | Toggles playback state of active media controller. |
| `NEXT` | None | None | Advances to next track. |
| `PREV` | None | None | Returns to previous track. |
| `SKIP_FORWARD` | Seconds | 5s to 120s | Seeks playback forward by exact second interval. |
| `SKIP_BACKWARD` | Seconds | 5s to 120s | Seeks playback backward by exact second interval. |
| `CYCLE_PLAYBACK_SPEED` | None | 1.0x / 1.25x / 1.5x / 1.75x / 2.0x | Cycles playback speed across active media session. |
| `SAFE_VOLUME_CAP` | None | 75% Ear Protection | Instantly clamps stream volume to safe threshold. |
| `SET_VOLUME` | Percentage | 0% to 100% | Sets media volume directly to designated percentage. |
| `CHANGE_VOLUME` | Step Delta | -30% to +30% | Adjusts media volume up or down by relative percentage. |
| `MUTE_UNMUTE` | None | None | Toggles stream mute state without zeroing volume index. |
| `FLASHLIGHT` | None | None | Toggles camera LED torch via `CameraManager`. |
| `LAUNCH_APP` | Package Name | Installed Applications | Starts specific app activity by package name. |
| `LAUNCH_CALCULATOR` | None | None | Dispatches default system calculator intent. |
| `SET_TIMER` | Minutes | 1m to 60m | Sets a system countdown timer via `AlarmClock.ACTION_SET_TIMER`. |
| `TOGGLE_VOICE_RECORDER`| None | None | Launches default voice recording utility. |

---

## Diagnostic Logging & Export Subsystem

The application includes an internal diagnostic logging engine designed to isolate timing issues, key drop anomalies, and audio routing states without external dependencies:

- **Circular Ring Buffer:** Maintains the last 150 events in volatile memory for live UI streaming in the event console.
- **Persistent Disk Journal:** Logs all physical key presses, timing deltas, stream volume adjustments, and package routing to private storage (`diagnostic_logs.txt`) with automated 48-hour retention pruning.
- **Diagnostic Report Generator:** Compiles device metadata (Android SDK version, build brand, model), active service states, configuration parameters, optional user bug notes, and filtered event histories (past 1 hour, past 5 hours, past 24 hours, or all-day).
- **System Sharesheet Export:** Dispatches compiled reports through `Intent.ACTION_SEND` (`text/plain`) to any installed application (email, text editor, cloud storage, messaging).

---

## Theme Engine & Visual Styling

The interface is built around Nothing OS design principles, utilizing an AMOLED pitch-black baseline (`#000000`), geometric card outlines (`#1F1F1F`), and monospace typography.

Available system themes:
- **Nothing Crimson Red (Default):** `#D71921` accent with dark grey tonal backing.
- **Electric Cyan:** `#00E5FF` accent with deep blue-tinted card elevations.
- **Monochrome Minimal:** `#FFFFFF` high-contrast styling with pure grey borders.
- **Amber Glow:** `#FF9100` warm industrial styling.
- **Matrix Green:** `#00E676` high-visibility phosphor terminal palette.

---

## Version Rollback & Update Mechanism

- **Direct GitHub Releases API Integration:** Communicates with `https://api.github.com/repos/Dhruvgupta16/volume-button-tweak/releases` to query semantic releases and published changelogs.
- **In-App Direct Package Installer:** Downloads APK binaries directly with progress streaming, handles 302 CDN redirection chains, generates secure `FileProvider` content URIs, and invokes the Android package installer directly.
- **Discreet Version Rollback:** Provides a release history selector allowing users to downgrade to any previous tagged release (e.g. v1.9.8, v1.9.7) directly from the application interface.

---

## Interface Layout & Screenshots

The application user interface consists of high-contrast cards providing real-time hardware status, gesture bindings, and diagnostic metrics:

### Primary Dashboard
```
┌─────────────────────────────────────────────────────────────┐
│ [Logo] VOLUME TWEAK                  [Rollback] [Check Update]
│        v1.9.9b (Build 20)                                   │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Accessibility Service: ACTIVE                           │ │
│ │ Notification Listener: ACTIVE                           │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ (Power) TWEAK ACTIVE                  [Master Switch: ON]   │
│         Intercepting volume button gestures                 │
└─────────────────────────────────────────────────────────────┘
┌───────────────────────────────┬─────────────────────────────┐
│ [RAM] Heap: 4.2 MB (PSS: 38M) │ [CPU] Load: < 0.1%          │
└───────────────────────────────┴─────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ GESTURE REMAPPER                                            │
│ - Single Press (Up + Down): Play / Pause                    │
│ - Double Press:             Next Track                      │
│ - Triple Press:             Previous Track                  │
│ - Quadruple Press:          Skip Forward (+15s)             │
│ - Timing Sensitivity:       160ms (BALANCED)                │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ INTERFACE THEME                                             │
│ [ Crimson ] [ Electric Cyan ] [ Monochrome ] [ Amber Glow ] │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ EVENT CONSOLE                   [Export Report] [Copy] [Clr]│
│ > Key 24 DOWN (time: 14:02:11.450)                          │
│ > Key 25 DOWN (time: 14:02:11.492) [DELTA: 42ms]           │
│ > GESTURE CONFIRMED: Dual Press -> PLAY_PAUSE               │
└─────────────────────────────────────────────────────────────┘
```

---

## System Permissions & Security Posture

| Permission | Rationale |
| :--- | :--- |
| `android.permission.BIND_ACCESSIBILITY_SERVICE` | Required to intercept hardware volume button key codes (`KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN`) before standard window dispatch. |
| `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | Required to discover active `MediaController` sessions and dispatch transport controls (fast-forward, rewind, seek, speed). |
| `android.permission.VIBRATE` | Required for tactile haptic feedback confirmations. |
| `android.permission.CAMERA` | Required exclusively to toggle the LED flashlight torch. |
| `android.permission.INTERNET` | Required for checking GitHub releases and downloading in-app APK updates. |
| `android.permission.REQUEST_INSTALL_PACKAGES` | Required to launch the system package installer for in-app updates and version rollbacks. |

**Privacy Notice:** The application contains zero third-party analytics SDKs, zero advertising frameworks, and zero remote tracking endpoints. All logs are stored strictly on the local device filesystem within the app sandbox.

---

## Building from Source

### Prerequisites
- JDK 17
- Android SDK Platform 34
- Android Build Tools 34.0.0+

### Compilation
Clone the repository and compile using the Gradle wrapper:

```bash
git clone https://github.com/Dhruvgupta16/volume-button-tweak.git
cd volume-button-tweak
./gradlew assembleRelease
```

The signed release APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`
