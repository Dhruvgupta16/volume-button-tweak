package com.dhruv.volumetweak

import android.content.pm.PackageManager

enum class ParameterType {
    NONE,
    SECONDS,
    PERCENTAGE,
    STEP_PERCENT,
    MINUTES,
    PACKAGE_NAME
}

data class TweakAction(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val iconRes: Int,
    val parameterType: ParameterType = ParameterType.NONE,
    val defaultParam: String = ""
)

object ActionRegistry {
    val ALL_ACTIONS = listOf(
        // Media Controls
        TweakAction("PLAY_PAUSE", "Play / Pause", "Media Controls", "Toggle media playback state", R.drawable.ph_play_pause),
        TweakAction("NEXT", "Next Track", "Media Controls", "Skip to next track or episode", R.drawable.ph_skip_forward),
        TweakAction("PREV", "Previous Track", "Media Controls", "Return to previous track or beginning", R.drawable.ph_skip_back),
        TweakAction("SKIP_FORWARD", "Custom Skip Forward", "Media Controls", "Skip forward customizable seconds (YT Music, Spotify, etc.)", R.drawable.ph_fast_forward, ParameterType.SECONDS, "15"),
        TweakAction("SKIP_BACKWARD", "Custom Rewind", "Media Controls", "Rewind customizable seconds (YT Music, Spotify, etc.)", R.drawable.ph_rewind, ParameterType.SECONDS, "15"),
        TweakAction("LIKE_TRACK", "Like / Favorite Song", "Media Controls", "Like current track in YT Music / Spotify", R.drawable.ph_heart),
        TweakAction("STOP", "Stop Playback", "Media Controls", "Halt audio playback completely", R.drawable.ph_power),

        // Volume Controls
        TweakAction("MUTE", "Toggle Mute / Unmute", "Volume Controls", "Mute or unmute media stream", R.drawable.ph_speaker_slash),
        TweakAction("SET_VOLUME", "Set Exact Volume %", "Volume Controls", "Set media stream volume to a specific percentage", R.drawable.ph_speaker_simple_high, ParameterType.PERCENTAGE, "50"),
        TweakAction("STEP_VOLUME", "Step Volume %", "Volume Controls", "Raise or lower volume by custom percentage", R.drawable.ph_speaker_high, ParameterType.STEP_PERCENT, "10"),

        // Headphone Audio Readouts (TTS)
        TweakAction("TTS_TRACK_INFO", "Whisper Song Title in Ear", "Headphone Readouts", "Speaks currently playing song and artist into headphones", R.drawable.ph_music_notes),
        TweakAction("TTS_TIME", "Whisper Time in Ear", "Headphone Readouts", "Speaks current time into headphones", R.drawable.ph_hourglass),
        TweakAction("TTS_BATTERY", "Whisper Battery in Ear", "Headphone Readouts", "Speaks battery percentage into headphones", R.drawable.ph_info),
        TweakAction("TTS_LAST_NOTIFICATION", "Whisper Last Notification", "Headphone Readouts", "Speaks latest incoming message into headphones", R.drawable.ph_bell),

        // App Launchers & Custom Apps
        TweakAction("LAUNCH_APP", "Launch Specific App", "App Launchers", "Open Volume Button Tweak or any installed app", R.drawable.ph_sliders, ParameterType.PACKAGE_NAME, "com.dhruv.volumetweak"),
        TweakAction("OPEN_CAMERA", "Open Camera", "App Launchers", "Launch stock camera app immediately", R.drawable.ph_camera),
        TweakAction("CALCULATOR", "Open Calculator", "App Launchers", "Launch system calculator", R.drawable.ph_calculator),

        // System & Hardware
        TweakAction("TOGGLE_RINGER_MODE", "Toggle Sound Profile", "System & Hardware", "Cycle Silent -> Vibrate -> Ringing with haptic buzz", R.drawable.ph_bell_slash),
        TweakAction("COPY_TRACK_NAME", "Copy Song to Clipboard", "System & Hardware", "Copy current song title and artist to clipboard", R.drawable.ph_copy),
        TweakAction("FLASHLIGHT_TOGGLE", "Flashlight Torch Toggle", "System & Hardware", "Turn rear LED flashlight ON or OFF", R.drawable.ph_flashlight),
        TweakAction("FLASHLIGHT_TIMER", "Flashlight Auto-Off Timer", "System & Hardware", "Turn on flashlight for X seconds then auto-off", R.drawable.ph_flashlight, ParameterType.SECONDS, "30"),
        TweakAction("FLASHLIGHT_PULSE", "Flashlight Pulse", "System & Hardware", "Strobe rear flash / Glyph LED", R.drawable.ph_flashlight),
        TweakAction("VOICE_ASSISTANT", "Google Assistant", "System & Hardware", "Launch voice assistant / query", R.drawable.ph_microphone),
        TweakAction("TAKE_SCREENSHOT", "Take Screenshot", "System & Hardware", "Instantly capture current screen", R.drawable.ph_camera),
        TweakAction("LOCK_SCREEN", "Lock Screen", "System & Hardware", "Immediately turn off and lock display", R.drawable.ph_lock),
        TweakAction("NOTIFICATIONS", "Open Notifications", "System & Hardware", "Pull down notification shade", R.drawable.ph_bell),
        TweakAction("QUICK_SETTINGS", "Open Quick Settings", "System & Hardware", "Pull down quick toggles panel", R.drawable.ph_faders),
        TweakAction("POWER_MENU", "Power Menu", "System & Hardware", "Open Android power off & restart dialog", R.drawable.ph_power),
        TweakAction("SPLIT_SCREEN", "Toggle Split Screen", "System & Hardware", "Enter or exit multi-window split screen", R.drawable.ph_columns),
        TweakAction("TRIGGER_TIMER", "Quick Countdown Timer", "System & Hardware", "Start a countdown timer for X minutes", R.drawable.ph_timer, ParameterType.MINUTES, "5"),
        TweakAction("TRIGGER_ALARM", "Open Alarms", "System & Hardware", "Open clock alarms tab", R.drawable.ph_alarm)
    )

    fun getBaseId(actionStr: String): String = actionStr.substringBefore(":")
    fun getParam(actionStr: String): String? = if (actionStr.contains(":")) actionStr.substringAfter(":") else null
    fun getIntParam(actionStr: String): Int? = getParam(actionStr)?.toIntOrNull()

    fun getTitle(actionStr: String, pm: PackageManager? = null): String {
        val base = getBaseId(actionStr)
        val paramStr = getParam(actionStr)
        val param = paramStr?.toIntOrNull()

        if (base == "LAUNCH_APP") {
            val pkg = paramStr ?: "com.dhruv.volumetweak"
            val appName = try {
                if (pkg == "com.dhruv.volumetweak") {
                    "Volume Tweak"
                } else if (pm != null) {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } else {
                    pkg
                }
            } catch (e: Exception) {
                pkg
            }
            return "Launch $appName"
        }

        if (param != null) {
            when (base) {
                "SKIP_FORWARD", "SKIP_FWD_15" -> return "Fast Forward ${param}s"
                "SKIP_BACKWARD", "SKIP_BWD_15" -> return "Rewind ${param}s"
                "SET_VOLUME" -> return "Set Volume to ${param}%"
                "STEP_VOLUME" -> return "Step Volume ${if (param >= 0) "+$param" else "$param"}%"
                "FLASHLIGHT_TIMER" -> return "Flashlight for ${param}s"
                "TRIGGER_TIMER" -> return "Timer for ${param} min"
            }
        } else {
            // Legacy aliases without param
            if (base == "SKIP_FWD_15") return "Fast Forward 15s"
            if (base == "SKIP_BWD_15") return "Rewind 15s"
        }

        return ALL_ACTIONS.find { it.id == base }?.title ?: actionStr
    }

    fun getIcon(actionStr: String): Int {
        val base = getBaseId(actionStr)
        if (base == "SKIP_FWD_15") return R.drawable.ph_fast_forward
        if (base == "SKIP_BWD_15") return R.drawable.ph_rewind
        return ALL_ACTIONS.find { it.id == base }?.iconRes ?: R.drawable.ph_play
    }

    fun getAction(actionId: String): TweakAction? {
        val base = getBaseId(actionId)
        val normalized = when (base) {
            "SKIP_FWD_15" -> "SKIP_FORWARD"
            "SKIP_BWD_15" -> "SKIP_BACKWARD"
            else -> base
        }
        return ALL_ACTIONS.find { it.id == normalized }
    }
}
