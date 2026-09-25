package com.dhruv.volumetweak

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeTweakService : AccessibilityService(), SensorEventListener {

    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var isProximityCovered = false

    private lateinit var audioPromptHelper: AudioPromptHelper

    // Button Physical State
    private var lastVolUpTime: Long = 0
    private var lastVolDownTime: Long = 0
    private var isVolUpPressed = false
    private var isVolDownPressed = false

    // Deferral & Continuous Ramp
    private var pendingSinglePressRunnable: Runnable? = null
    private var continuousRampRunnable: Runnable? = null

    // Universal Sequence Engine State
    private val currentSequence = mutableListOf<String>()
    private var isSequenceActive = false
    private var isDualPressActive = false
    private var isHoldTriggered = false
    private var activeHoldRunnable: Runnable? = null
    private var pendingSequenceTimeoutRunnable: Runnable? = null

    // Session memory to support resume when music was paused via tweak
    private var lastTweakActionTime: Long = 0

    // Actively detected playing media app
    private var detectedPlayingPackage: String? = null

    companion object {
        var isServiceSuspended: Boolean = false
        var testModeEnabled: Boolean = false
        var glyphReactionEnabled: Boolean = false
        var hapticReactionEnabled: Boolean = true
        var proximitySensorEnabled: Boolean = false
        var targetAppPackages: Set<String> = emptySet()

        // Standard Dual-Press Multi-Click Gestures
        var action1Click: String = "PLAY_PAUSE"
        var action2Clicks: String = "NEXT"
        var action3Clicks: String = "PREV"
        var action4Clicks: String = "SKIP_FWD_15"

        // Dynamic Custom Combos
        var comboSequencesEnabled: Boolean = true
        var customCombos: List<CustomCombo> = CustomCombo.getDefaultCombos()

        // Configurable Timings
        var dualPressWindowMs: Long = 140L
        var pauseSessionTimeoutMs: Long = 30000L

        private const val DEFER_SINGLE_PRESS_MS = 85L
        private const val HOLD_THRESHOLD_MS = 450L
        private const val SEQUENCE_STEP_TIMEOUT_MS = 500L
        private const val CONTINUOUS_RAMP_INTERVAL_MS = 110L

        fun reloadPreferences(context: Context) {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            isServiceSuspended = prefs.getBoolean("service_suspended", false)
            glyphReactionEnabled = prefs.getBoolean("glyph_reaction", false)
            hapticReactionEnabled = prefs.getBoolean("haptic_feedback", true)
            proximitySensorEnabled = prefs.getBoolean("proximity_pocket_guard", false)
            testModeEnabled = prefs.getBoolean("test_mode", false)

            action1Click = prefs.getString("action_1_click", "PLAY_PAUSE") ?: "PLAY_PAUSE"
            action2Clicks = prefs.getString("action_2_clicks", "NEXT") ?: "NEXT"
            action3Clicks = prefs.getString("action_3_clicks", "PREV") ?: "PREV"
            action4Clicks = prefs.getString("action_4_clicks", "SKIP_FWD_15") ?: "SKIP_FWD_15"

            comboSequencesEnabled = prefs.getBoolean("combo_sequences_enabled", true)
            val combosJson = prefs.getString("custom_combos_json", null)
            customCombos = CustomCombo.parseList(combosJson)

            dualPressWindowMs = prefs.getLong("dual_press_window", 140L)
            targetAppPackages = prefs.getStringSet("target_apps", emptySet()) ?: emptySet()
            LogBuffer.log("[CONFIG] Preferences reloaded (${customCombos.size} combos loaded)")
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VolumeTweak:WakeLock")
        wakeLock?.setReferenceCounted(false)
        GlyphController.init(this)
        HapticFeedbackController.init(this)
        audioPromptHelper = AudioPromptHelper(this)

        reloadPreferences(this)
        initProximitySensor()
        registerAudioPlaybackMonitoring()
        LogBuffer.log("Universal Sequence Service created and ready")
    }

    private fun initProximitySensor() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            if (proximitySensor != null && proximitySensorEnabled) {
                sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (e: Exception) {
            LogBuffer.log("Proximity init note: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            isProximityCovered = distance < maxRange
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun registerAudioPlaybackMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                audioManager.registerAudioPlaybackCallback(object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                        super.onPlaybackConfigChanged(configs)
                        var foundActivePkg: String? = null
                        for (config in configs) {
                            val isPlaying = try {
                                val method = config.javaClass.getMethod("isActive")
                                (method.invoke(config) as? Boolean) == true
                            } catch (e: Exception) {
                                true
                            }

                            val isMediaUsage = try {
                                config.audioAttributes.usage == AudioAttributes.USAGE_MEDIA
                            } catch (e: Exception) {
                                true
                            }

                            if (isPlaying && isMediaUsage) {
                                val uid = try {
                                    val method = config.javaClass.getMethod("getClientUid")
                                    method.invoke(config) as? Int
                                } catch (e: Exception) {
                                    null
                                }
                                if (uid != null) {
                                    val pkgs = packageManager.getPackagesForUid(uid)
                                    if (!pkgs.isNullOrEmpty()) {
                                        foundActivePkg = pkgs[0]
                                        break
                                    }
                                }
                            }
                        }
                        if (foundActivePkg != null && foundActivePkg != detectedPlayingPackage) {
                            detectedPlayingPackage = foundActivePkg
                            LogBuffer.log("[AUDIO] Active media source: $foundActivePkg")
                        } else if (foundActivePkg == null && detectedPlayingPackage != null) {
                            detectedPlayingPackage = null
                            LogBuffer.log("[AUDIO] Media playback ended")
                        }
                    }
                }, handler)
            } catch (e: Exception) {
                LogBuffer.log("[AUDIO] Callback init note: ${e.message}")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            serviceInfo = info
            LogBuffer.log("Service connected: Key filter active")
        } catch (e: Exception) {
            LogBuffer.log("Service connect notice: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        LogBuffer.log("Service interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Master Kill Switch: If suspended, pass all keys through with 0 overhead
        if (isServiceSuspended) {
            return false
        }

        val keyCode = event.keyCode
        val action = event.action

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event)
        }

        val currentTime = SystemClock.uptimeMillis()
        val isMusicActive = audioManager.isMusicActive
        val timeSinceLastAction = currentTime - lastTweakActionTime
        val isRecentPauseSession = timeSinceLastAction < pauseSessionTimeoutMs
        val isEligibleMediaState = isMusicActive || isRecentPauseSession || testModeEnabled

        val passesWhitelist = isAppWhitelisted()
        val passesProximity = !proximitySensorEnabled || isProximityCovered
        val isEnabledForAction = isEligibleMediaState && passesWhitelist && passesProximity

        if (!isEnabledForAction) {
            // Reset gesture states if media is inactive
            if (isSequenceActive) {
                resetSequenceState()
            }
            isVolUpPressed = false
            isVolDownPressed = false
            isDualPressActive = false
            cancelContinuousVolumeRamp()
            cancelPendingSinglePress()
            return false
        }

        // Keep CPU awake for screen-off pocket execution
        wakeLock?.acquire(1000)

        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                isVolUpPressed = true
                lastVolUpTime = currentTime
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                isVolDownPressed = true
                lastVolDownTime = currentTime
            }

            val timeDiff = Math.abs(lastVolUpTime - lastVolDownTime)

            // 1. Dual-Press Detection (Simultaneous press within timing window)
            if (isVolUpPressed && isVolDownPressed && timeDiff <= dualPressWindowMs) {
                cancelPendingSinglePress()
                cancelContinuousVolumeRamp()
                cancelActiveHoldTimer()

                isDualPressActive = true
                isHoldTriggered = false

                // Start Dual Hold Timer (500ms)
                activeHoldRunnable = Runnable {
                    if (isVolUpPressed && isVolDownPressed) {
                        isHoldTriggered = true
                        HapticFeedbackController.vibrateTick(this@VolumeTweakService)
                        LogBuffer.log("[INPUT] Token: DUAL_HOLD")
                        appendTokenAndEvaluate("DUAL_HOLD")
                    }
                }
                handler.postDelayed(activeHoldRunnable!!, HOLD_THRESHOLD_MS + 50L)
                return true
            }

            // 2. Chained Key During Active Sequence Mode (Combos)
            if (isSequenceActive) {
                cancelActiveHoldTimer()
                isHoldTriggered = false

                val holdToken = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) "UP_HOLD" else "DOWN_HOLD"
                activeHoldRunnable = Runnable {
                    val isStillDown = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) isVolUpPressed else isVolDownPressed
                    if (isStillDown) {
                        isHoldTriggered = true
                        HapticFeedbackController.vibrateTick(this@VolumeTweakService)
                        LogBuffer.log("[INPUT] Token: $holdToken")
                        appendTokenAndEvaluate(holdToken)
                    }
                }
                handler.postDelayed(activeHoldRunnable!!, HOLD_THRESHOLD_MS)
                return true
            }

            // 3. Normal Volume Button Press (Sequence inactive): Defer to check for potential dual press
            if (continuousRampRunnable != null) {
                return true
            }

            cancelPendingSinglePress()
            scheduleDeferredSinglePress(keyCode)
            return true

        } else if (action == KeyEvent.ACTION_UP) {
            val wasVolUp = (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            if (wasVolUp) {
                isVolUpPressed = false
            } else {
                isVolDownPressed = false
            }

            // Cancel any pending hold timer immediately upon release
            cancelActiveHoldTimer()

            // 1. Releasing from Dual Press
            if (isDualPressActive) {
                if (!isHoldTriggered) {
                    LogBuffer.log("[INPUT] Token: DUAL")
                    appendTokenAndEvaluate("DUAL")
                }
                if (!isVolUpPressed && !isVolDownPressed) {
                    isDualPressActive = false
                    isHoldTriggered = false
                }
                return true
            }

            // 2. Releasing from Chained Key in Sequence Mode
            if (isSequenceActive) {
                if (!isHoldTriggered) {
                    val tapToken = if (wasVolUp) "UP" else "DOWN"
                    LogBuffer.log("[INPUT] Token: $tapToken")
                    appendTokenAndEvaluate(tapToken)
                }
                isHoldTriggered = false
                return true
            }

            // 3. Normal Volume Key Release
            cancelContinuousVolumeRamp()
            cancelPendingSinglePress()
            return false
        }

        return false
    }

    private fun cancelActiveHoldTimer() {
        activeHoldRunnable?.let {
            handler.removeCallbacks(it)
            activeHoldRunnable = null
        }
    }

    private fun scheduleDeferredSinglePress(keyCode: Int) {
        val runnable = Runnable {
            adjustVolume(keyCode)
            pendingSinglePressRunnable = null

            val isStillPressed = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) isVolUpPressed else isVolDownPressed
            if (isStillPressed && !isDualPressActive && !isSequenceActive) {
                startContinuousVolumeRamp(keyCode)
            }
        }
        pendingSinglePressRunnable = runnable
        handler.postDelayed(runnable, DEFER_SINGLE_PRESS_MS)
    }

    private fun startContinuousVolumeRamp(keyCode: Int) {
        cancelContinuousVolumeRamp()
        val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        continuousRampRunnable = object : Runnable {
            override fun run() {
                val isStillPressed = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) isVolUpPressed else isVolDownPressed
                if (isStillPressed && !isDualPressActive && !isSequenceActive) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                    handler.postDelayed(this, CONTINUOUS_RAMP_INTERVAL_MS)
                } else {
                    cancelContinuousVolumeRamp()
                }
            }
        }
        handler.postDelayed(continuousRampRunnable!!, CONTINUOUS_RAMP_INTERVAL_MS)
    }

    private fun cancelContinuousVolumeRamp() {
        continuousRampRunnable?.let {
            handler.removeCallbacks(it)
            continuousRampRunnable = null
        }
    }

    private fun cancelPendingSinglePress() {
        pendingSinglePressRunnable?.let {
            handler.removeCallbacks(it)
            pendingSinglePressRunnable = null
        }
    }

    private fun adjustVolume(keyCode: Int) {
        val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            AudioManager.ADJUST_RAISE
        } else {
            AudioManager.ADJUST_LOWER
        }
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    /**
     * Sequence Evaluation Engine
     */
    private fun appendTokenAndEvaluate(token: String) {
        currentSequence.add(token)
        isSequenceActive = true
        lastTweakActionTime = SystemClock.uptimeMillis()

        pendingSequenceTimeoutRunnable?.let { handler.removeCallbacks(it) }

        // Build registered lookup map
        val sequenceActionMap = mutableMapOf<List<String>, String>()

        // 1. Standard Multi-clicks
        sequenceActionMap[listOf("DUAL")] = action1Click
        sequenceActionMap[listOf("DUAL", "DUAL")] = action2Clicks
        sequenceActionMap[listOf("DUAL", "DUAL", "DUAL")] = action3Clicks
        sequenceActionMap[listOf("DUAL", "DUAL", "DUAL", "DUAL")] = action4Clicks

        // 2. Custom Sequences
        if (comboSequencesEnabled) {
            for (combo in customCombos) {
                if (combo.isEnabled && combo.tokens.isNotEmpty()) {
                    sequenceActionMap[combo.tokens] = combo.action
                }
            }
        }

        val exactMatchAction = sequenceActionMap[currentSequence]

        // Check if currentSequence is a prefix of any registered sequence that has more tokens
        val hasLongerExtension = sequenceActionMap.keys.any { key ->
            key.size > currentSequence.size && key.subList(0, currentSequence.size) == currentSequence
        }

        if (!hasLongerExtension) {
            // Leaf node: Cannot be extended any further!
            if (exactMatchAction != null) {
                LogBuffer.log("[SEQUENCE MATCH] ${currentSequence.joinToString(" → ")} -> $exactMatchAction")
                executeConfiguredAction(exactMatchAction)
            } else {
                LogBuffer.log("[SEQUENCE UNMATCHED] ${currentSequence.joinToString(" → ")}")
            }
            resetSequenceState()
        } else {
            // Prefix of longer sequence: wait up to SEQUENCE_STEP_TIMEOUT_MS for follow-up tokens
            pendingSequenceTimeoutRunnable = Runnable {
                if (exactMatchAction != null) {
                    LogBuffer.log("[SEQUENCE TIMEOUT MATCH] ${currentSequence.joinToString(" → ")} -> $exactMatchAction")
                    executeConfiguredAction(exactMatchAction)
                } else {
                    LogBuffer.log("[SEQUENCE TIMEOUT] Incomplete sequence: ${currentSequence.joinToString(" → ")}")
                }
                resetSequenceState()
            }
            handler.postDelayed(pendingSequenceTimeoutRunnable!!, SEQUENCE_STEP_TIMEOUT_MS)
        }
    }

    private fun resetSequenceState() {
        pendingSequenceTimeoutRunnable?.let { handler.removeCallbacks(it) }
        pendingSequenceTimeoutRunnable = null
        currentSequence.clear()
        isSequenceActive = false
    }

    private fun executeConfiguredAction(actionId: String) {
        if (glyphReactionEnabled) {
            GlyphController.pulse(1)
        }
        if (hapticReactionEnabled) {
            HapticFeedbackController.vibrate(this, 1)
        }

        LogBuffer.log("[ACTION EXECUTE] $actionId")

        when (actionId) {
            "PLAY_PAUSE" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "NEXT" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            "PREV" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "SKIP_FWD_15" -> skipForward15()
            "SKIP_BWD_15" -> skipBackward15()
            "FAST_FORWARD" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            "REWIND" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_REWIND)
            "STOP" -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP)

            "MUTE" -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            "VOL_MAX" -> {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, AudioManager.FLAG_SHOW_UI)
            }
            "VOL_50" -> {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol / 2, AudioManager.FLAG_SHOW_UI)
            }
            "VOL_MIN" -> {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val quietVol = (maxVol * 0.12f).toInt().coerceAtLeast(1)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, quietVol, AudioManager.FLAG_SHOW_UI)
            }

            "TTS_TIME" -> audioPromptHelper.speakTime()
            "TTS_BATTERY" -> audioPromptHelper.speakBattery()

            "FLASHLIGHT_TOGGLE" -> {
                val isOn = GlyphController.toggleTorch()
                LogBuffer.log("[ACTION] Flashlight ${if (isOn) "ON" else "OFF"}")
            }
            "FLASHLIGHT_PULSE" -> GlyphController.pulse(2)

            "VOICE_ASSISTANT" -> {
                try {
                    val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    LogBuffer.log("Voice Assistant error: ${e.message}")
                }
            }

            "TAKE_SCREENSHOT" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                }
            }
            "LOCK_SCREEN" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                }
            }
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "QUICK_SETTINGS" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

            else -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    private fun skipForward15() {
        // Dispatch KEYCODE_MEDIA_SKIP_FORWARD (272) for Spotify, YouTube Music, Podcasts
        sendMediaKeyEvent(272)
        // Fallback for legacy media players
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
    }

    private fun skipBackward15() {
        // Dispatch KEYCODE_MEDIA_SKIP_BACKWARD (273) for Spotify, YouTube Music, Podcasts
        sendMediaKeyEvent(273)
        // Fallback for legacy media players
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_REWIND)
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)

        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    private fun isAppWhitelisted(): Boolean {
        if (targetAppPackages.isEmpty() || testModeEnabled) {
            return true
        }
        val pkg = detectedPlayingPackage
        if (pkg != null) {
            return targetAppPackages.contains(pkg)
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        audioPromptHelper.shutdown()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }
}
