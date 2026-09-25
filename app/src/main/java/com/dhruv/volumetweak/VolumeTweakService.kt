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

    private var lastVolUpTime: Long = 0
    private var lastVolDownTime: Long = 0

    private var isVolUpPressed = false
    private var isVolDownPressed = false
    private var isDualPressConsumed = false

    private var pendingSinglePressRunnable: Runnable? = null
    private var continuousRampRunnable: Runnable? = null
    private var pendingSinglePressKeyCode: Int = 0

    private var clickCount = 0
    private var pendingGestureRunnable: Runnable? = null

    // Combo Sequence Engine
    private var isAwaitingCombo = false
    private var pendingComboTimeoutRunnable: Runnable? = null
    private var pendingComboFallbackAction: String = ""

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

        // Configurable Gestures
        var action1Click: String = "PLAY_PAUSE"
        var action2Clicks: String = "NEXT"
        var action3Clicks: String = "PREV"
        var action4Clicks: String = "FAST_FORWARD"

        // Combo Sequences
        var comboSequencesEnabled: Boolean = false
        var comboDualThenUp: String = "FAST_FORWARD"
        var comboDualThenDown: String = "REWIND"

        // Configurable Timings
        var dualPressWindowMs: Long = 140L
        var pauseSessionTimeoutMs: Long = 30000L
        private const val DEFER_SINGLE_PRESS_MS = 80L
        private const val GESTURE_TIMEOUT_MS = 360L
        private const val CONTINUOUS_RAMP_INTERVAL_MS = 110L
        private const val COMBO_TIMEOUT_MS = 450L
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VolumeTweak:WakeLock")
        wakeLock?.setReferenceCounted(false)
        GlyphController.init(this)
        HapticFeedbackController.init(this)

        initProximitySensor()
        registerAudioPlaybackMonitoring()
        LogBuffer.log("Service created and ready")
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

        // Combo Sequence Key Interception
        if (isAwaitingCombo && action == KeyEvent.ACTION_DOWN) {
            cancelAwaitingCombo()
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                LogBuffer.log("[COMBO] Dual Press + Vol UP -> $comboDualThenUp")
                executeConfiguredAction(comboDualThenUp, 1)
                isDualPressConsumed = true
                return true
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                LogBuffer.log("[COMBO] Dual Press + Vol DOWN -> $comboDualThenDown")
                executeConfiguredAction(comboDualThenDown, 1)
                isDualPressConsumed = true
                return true
            }
        }

        val currentTime = SystemClock.uptimeMillis()
        val isMusicActive = audioManager.isMusicActive
        val timeSinceLastAction = currentTime - lastTweakActionTime
        val isRecentPauseSession = timeSinceLastAction < pauseSessionTimeoutMs
        val isEligibleMediaState = isMusicActive || isRecentPauseSession || testModeEnabled

        // Verify Whitelist against detected playing app or fallback
        val passesWhitelist = isAppWhitelisted()
        val passesProximity = !proximitySensorEnabled || isProximityCovered
        val isEnabledForAction = isEligibleMediaState && passesWhitelist && passesProximity

        val keyName = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) "Vol UP" else "Vol DOWN"
        val actionName = if (action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"

        if (!isEnabledForAction) {
            if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val reason = when {
                    !isEligibleMediaState -> "Audio inactive"
                    !passesWhitelist -> "App not whitelisted"
                    !passesProximity -> "Proximity sensor clear"
                    else -> "Bypassed"
                }
                LogBuffer.log("[BYPASS] $keyName $actionName ($reason)")
            }
            isVolUpPressed = false
            isVolDownPressed = false
            isDualPressConsumed = false
            cancelContinuousVolumeRamp()
            return false
        }

        // Acquire brief wake lock for screen-off pocket mode execution
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

            // Simultaneous dual-press detection
            if (isVolUpPressed && isVolDownPressed && timeDiff <= dualPressWindowMs) {
                isDualPressConsumed = true
                LogBuffer.log("[DUAL PRESS] Detected (diff: ${timeDiff}ms)")
                cancelPendingSinglePress()
                cancelContinuousVolumeRamp()
                registerDualClick()
                return true
            }

            // If user is holding a button for continuous volume adjustment, don't restart deferral
            if (continuousRampRunnable != null) {
                return true
            }

            // First key of a potential pair: defer single press briefly
            cancelPendingSinglePress()
            scheduleDeferredSinglePress(keyCode)
            return true

        } else if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                isVolUpPressed = false
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                isVolDownPressed = false
            }

            // Cancel any continuous ramping immediately when key is released
            cancelContinuousVolumeRamp()
            cancelPendingSinglePress()

            // Only consume UP if it belonged to an intercepted dual press
            if (isDualPressConsumed) {
                if (!isVolUpPressed && !isVolDownPressed) {
                    isDualPressConsumed = false
                }
                return true
            }
            return false
        }

        return false
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

    private fun scheduleDeferredSinglePress(keyCode: Int) {
        pendingSinglePressKeyCode = keyCode
        val runnable = Runnable {
            adjustVolume(keyCode)
            pendingSinglePressRunnable = null

            val isStillPressed = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) isVolUpPressed else isVolDownPressed
            if (isStillPressed && !isDualPressConsumed) {
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
                if (isStillPressed && !isDualPressConsumed) {
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

    private fun registerDualClick() {
        clickCount++
        lastTweakActionTime = SystemClock.uptimeMillis()

        pendingGestureRunnable?.let { handler.removeCallbacks(it) }

        val runnable = Runnable {
            dispatchGestureAction(clickCount)
            clickCount = 0
        }
        pendingGestureRunnable = runnable
        handler.postDelayed(runnable, GESTURE_TIMEOUT_MS)
    }

    private fun dispatchGestureAction(clicks: Int) {
        if (glyphReactionEnabled) {
            GlyphController.pulse(Math.min(clicks, 4))
        }

        if (hapticReactionEnabled) {
            HapticFeedbackController.vibrate(this, clicks)
        }

        val actionName = when (clicks) {
            1 -> action1Click
            2 -> action2Clicks
            3 -> action3Clicks
            4 -> action4Clicks
            else -> action4Clicks
        }

        // If combo sequences are enabled and this was 1 click, wait for follow-up key
        if (comboSequencesEnabled && clicks == 1) {
            isAwaitingCombo = true
            pendingComboFallbackAction = actionName
            cancelAwaitingCombo()

            val comboTimeout = Runnable {
                isAwaitingCombo = false
                executeConfiguredAction(pendingComboFallbackAction, 1)
            }
            pendingComboTimeoutRunnable = comboTimeout
            handler.postDelayed(comboTimeout, COMBO_TIMEOUT_MS)
            return
        }

        executeConfiguredAction(actionName, clicks)
    }

    private fun cancelAwaitingCombo() {
        pendingComboTimeoutRunnable?.let {
            handler.removeCallbacks(it)
            pendingComboTimeoutRunnable = null
        }
    }

    private fun executeConfiguredAction(action: String, clicks: Int) {
        when (action) {
            "PLAY_PAUSE" -> {
                LogBuffer.log("[ACTION] $clicks Click: Play / Pause")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
            "NEXT" -> {
                LogBuffer.log("[ACTION] $clicks Click: Next Track")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            "PREV" -> {
                LogBuffer.log("[ACTION] $clicks Click: Previous Track")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            "FAST_FORWARD" -> {
                LogBuffer.log("[ACTION] $clicks Click: Fast Forward 15s")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            }
            "REWIND" -> {
                LogBuffer.log("[ACTION] $clicks Click: Rewind 15s")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_REWIND)
            }
            "MUTE" -> {
                LogBuffer.log("[ACTION] $clicks Click: Toggle Mute")
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            }
            "VOL_MAX" -> {
                LogBuffer.log("[ACTION] $clicks Click: Max Volume (100%)")
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, AudioManager.FLAG_SHOW_UI)
            }
            "VOL_MIN" -> {
                LogBuffer.log("[ACTION] $clicks Click: Quiet Mode (10%)")
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val quietVol = (maxVol * 0.15f).toInt().coerceAtLeast(1)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, quietVol, AudioManager.FLAG_SHOW_UI)
            }
            "FLASHLIGHT_TOGGLE" -> {
                val isOn = GlyphController.toggleTorch()
                LogBuffer.log("[ACTION] $clicks Click: Flashlight ${if (isOn) "ON" else "OFF"}")
            }
            "FLASHLIGHT" -> {
                LogBuffer.log("[ACTION] $clicks Click: Flashlight Pulse")
                GlyphController.pulse(2)
            }
            "VOICE_ASSISTANT" -> {
                LogBuffer.log("[ACTION] $clicks Click: Voice Assistant")
                try {
                    val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    LogBuffer.log("Voice Assistant unavailable: ${e.message}")
                }
            }
            else -> {
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)

        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }
}
