package com.dhruv.volumetweak

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSessionManager
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
    private var pendingSinglePressKeyCode: Int = 0

    private var clickCount = 0
    private var pendingGestureRunnable: Runnable? = null

    // Session memory to support resume when music was paused via tweak
    private var lastTweakActionTime: Long = 0

    companion object {
        var testModeEnabled: Boolean = false
        var glyphReactionEnabled: Boolean = false
        var targetAppPackage: String = "ALL" // "ALL", "com.google.android.apps.youtube.music", "com.spotify.music"

        private const val DUAL_PRESS_WINDOW_MS = 140L  // Window for simultaneous press
        private const val DEFER_SINGLE_PRESS_MS = 85L  // Deferral for initial key
        private const val GESTURE_TIMEOUT_MS = 360L    // Multi-click accumulation window
        private const val PAUSE_SESSION_TIMEOUT_MS = 600000L // 10 minutes session retention
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VolumeTweak:WakeLock")
        wakeLock?.setReferenceCounted(false)
        GlyphController.init(this)
        LogBuffer.log("Service created and ready")
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
        val keyCode = event.keyCode
        val action = event.action

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event)
        }

        // When user holds down a volume button, repeatCount > 0
        // Instantly bypass interception and let the OS handle continuous volume ramping
        if (event.repeatCount > 0) {
            cancelPendingSinglePress()
            isVolUpPressed = false
            isVolDownPressed = false
            isDualPressConsumed = false
            return false
        }

        val currentTime = SystemClock.uptimeMillis()
        val isMusicActive = audioManager.isMusicActive
        val isRecentPauseSession = (currentTime - lastTweakActionTime) < PAUSE_SESSION_TIMEOUT_MS
        val isEligibleMediaState = isMusicActive || isRecentPauseSession || testModeEnabled

        // Verify Whitelist if configured
        val passesWhitelist = isAppWhitelisted()

        val isEnabledForAction = isEligibleMediaState && passesWhitelist

        val keyName = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) "Vol UP" else "Vol DOWN"
        val actionName = if (action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"

        if (!isEnabledForAction) {
            if (action == KeyEvent.ACTION_DOWN) {
                LogBuffer.log("$keyName $actionName [Bypass: Music Inactive / Whitelist]")
            }
            isVolUpPressed = false
            isVolDownPressed = false
            isDualPressConsumed = false
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
            if (isVolUpPressed && isVolDownPressed && timeDiff <= DUAL_PRESS_WINDOW_MS) {
                isDualPressConsumed = true
                LogBuffer.log("[DUAL PRESS] Detected (diff: ${timeDiff}ms)")
                cancelPendingSinglePress()
                registerDualClick()
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
        if (targetAppPackage == "ALL" || testModeEnabled) {
            return true
        }
        return try {
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, VolumeTweakService::class.java)
            val sessions = mediaSessionManager.getActiveSessions(component)
            sessions.any { it.packageName.equals(targetAppPackage, ignoreCase = true) }
        } catch (e: Exception) {
            true // Fallback to allow if notification listener is ungranted
        }
    }

    private fun scheduleDeferredSinglePress(keyCode: Int) {
        pendingSinglePressKeyCode = keyCode
        val runnable = Runnable {
            adjustVolume(keyCode)
            pendingSinglePressRunnable = null
        }
        pendingSinglePressRunnable = runnable
        handler.postDelayed(runnable, DEFER_SINGLE_PRESS_MS)
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

        when (clicks) {
            1 -> {
                LogBuffer.log("[ACTION] 1 Click: Play / Pause")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
            2 -> {
                LogBuffer.log("[ACTION] 2 Clicks: Next Track")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            3, 4 -> {
                LogBuffer.log("[ACTION] $clicks Clicks: Previous Track")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            else -> {
                if (clicks > 4) {
                    LogBuffer.log("[ACTION] $clicks Clicks: Previous Track")
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
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
