package com.dhruv.volumetweak

data class TweakAction(
    val id: String,
    val title: String,
    val category: String,
    val description: String
)

object ActionRegistry {
    val ALL_ACTIONS = listOf(
        // Media Controls
        TweakAction("PLAY_PAUSE", "Play / Pause", "Media Controls", "Toggle media playback state"),
        TweakAction("NEXT", "Next Track", "Media Controls", "Skip to next track or episode"),
        TweakAction("PREV", "Previous Track", "Media Controls", "Return to previous track or beginning"),
        TweakAction("SKIP_FWD_15", "Fast Forward 15s", "Media Controls", "Skip forward 15 seconds (Spotify, YT Music, Podcasts)"),
        TweakAction("SKIP_BWD_15", "Rewind 15s", "Media Controls", "Rewind 15 seconds (Spotify, YT Music, Podcasts)"),
        TweakAction("STOP", "Stop Playback", "Media Controls", "Halt audio playback completely"),

        // Volume Presets
        TweakAction("MUTE", "Toggle Mute", "Volume Presets", "Mute or unmute media stream"),
        TweakAction("VOL_MAX", "Max Volume (100%)", "Volume Presets", "Jump directly to maximum volume"),
        TweakAction("VOL_50", "Half Volume (50%)", "Volume Presets", "Set media volume to 50%"),
        TweakAction("VOL_MIN", "Quiet Mode (10%)", "Volume Presets", "Lower volume for quiet listening"),

        // Headphone Audio Readouts (TTS)
        TweakAction("TTS_TIME", "Whisper Time in Ear", "Headphone Readouts", "Speaks current time into headphones"),
        TweakAction("TTS_BATTERY", "Whisper Battery in Ear", "Headphone Readouts", "Speaks battery percentage into headphones"),

        // System & Hardware
        TweakAction("FLASHLIGHT_TOGGLE", "Flashlight Torch Toggle", "System & Hardware", "Turn rear LED flashlight ON or OFF"),
        TweakAction("FLASHLIGHT_PULSE", "Flashlight Pulse", "System & Hardware", "Strobe rear flash / Glyph LED"),
        TweakAction("VOICE_ASSISTANT", "Google Assistant", "System & Hardware", "Launch voice assistant / query"),
        TweakAction("TAKE_SCREENSHOT", "Take Screenshot", "System & Hardware", "Instantly capture current screen"),
        TweakAction("LOCK_SCREEN", "Lock Screen", "System & Hardware", "Immediately turn off and lock display"),
        TweakAction("NOTIFICATIONS", "Open Notifications", "System & Hardware", "Pull down notification shade"),
        TweakAction("QUICK_SETTINGS", "Open Quick Settings", "System & Hardware", "Pull down quick toggles panel")
    )

    fun getTitle(actionId: String): String {
        return ALL_ACTIONS.find { it.id == actionId }?.title ?: actionId
    }

    fun getAction(actionId: String): TweakAction? {
        return ALL_ACTIONS.find { it.id == actionId }
    }
}
