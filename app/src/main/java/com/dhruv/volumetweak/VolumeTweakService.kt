package com.dhruv.volumetweak

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
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

class VolumeTweakService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

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

    // Session memory to support resume when music was paused via tweak
    private var lastTweakActionTime: Long = 0

    // Actively detected playing media app
    private var detectedPlayingPackage: String? = null

    companion object {
        var isServiceSuspended: Boolean = false
        var testModeEnabled: Boolean = false
        var glyphReactionEnabled: Boolean = false
        var hapticReactionEnabled: Boolean = true
        var targetAppPackages: Set<String> = emptySet()

        // Configurable Gestures
        var action1Click: String = "PLAY_PAUSE" // "PLAY_PAUSE", "NEXT", "PREV", "MUTE", "FLASHLIGHT"
        var action2Clicks: String = "NEXT"      // "NEXT", "PLAY_PAUSE", "PREV"
        var action3Clicks: String = "PREV"      // "PREV", "NEXT", "PLAY_PAUSE"

        // Configurable Timings
        var dualPressWindowMs: Long = 140L      // 100ms, 140ms, 180ms
        var pauseSessionTimeoutMs: Long = 30000L // 15s, 30s, 60s
        private const val DEFER_SINGLE_PRESS_MS = 80L  // Deferral for initial key
        private const val GESTURE_TIMEOUT_MS = 360L    // Multi-click accumulation window
        private const val CONTINUOUS_RAMP_INTERVAL_MS = 110L // Stock Android volume hold repeat rate
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VolumeTweak:WakeLock")
        wakeLock?.setReferenceCounted(false)
        GlyphController.init(this)
        HapticFeedbackController.init(this)
        registerAudioPlaybackMonitoring()
        LogBuffer.log("Service created and ready")
    }

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

        // Verify Whitelist against detected playing app or fallback
        val passesWhitelist = isAppWhitelisted()
        val isEnabledForAction = isEligibleMediaState && passesWhitelist

        val keyName = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) "Vol UP" else "Vol DOWN"
        val actionName = if (action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"

        if (!isEnabledForAction) {
            if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val reason = when {
                    !isEligibleMediaState -> "Audio inactive"
                    !passesWhitelist -> "App not whitelisted"
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

            // If user is already holding a button for continuous volume adjustment, don't restart deferral
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

        // Fallback: If detectedPlayingPackage is temporarily null but music is active, allow
        return true
    }

    private fun scheduleDeferredSinglePress(keyCode: Int) {
        pendingSinglePressKeyCode = keyCode
        val runnable = Runnable {
            // First single volume step
            adjustVolume(keyCode)
            pendingSinglePressRunnable = null

            // Start continuous ramping if the button is still being held down!
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
            GlyphController.pulse(Math.min(clicks, 3))
        }

        if (hapticReactionEnabled) {
            HapticFeedbackController.vibrate(this, clicks)
        }

        val actionName = when (clicks) {
            1 -> action1Click
            2 -> action2Clicks
            3, 4 -> action3Clicks
            else -> action3Clicks
        }

        executeConfiguredAction(actionName, clicks)
    }

    private fun executeConfiguredAction(action: String, clicks: Int) {
        when (action) {
            "PLAY_PAUSE" -> {
                LogBuffer.log("[ACTION] $clicks Click(s): Play / Pause")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
            "NEXT" -> {
                LogBuffer.log("[ACTION] $clicks Click(s): Next Track")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            "PREV" -> {
                LogBuffer.log("[ACTION] $clicks Click(s): Previous Track")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            "MUTE" -> {
                LogBuffer.log("[ACTION] $clicks Click(s): Toggle Mute")
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            }
            "FLASHLIGHT" -> {
                LogBuffer.log("[ACTION] $clicks Click(s): Flashlight Pulse")
                GlyphController.pulse(2)
            }
            else -> {
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val downEvent = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0)

        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }
}
