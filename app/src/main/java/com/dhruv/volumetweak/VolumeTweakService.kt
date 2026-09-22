package com.dhruv.volumetweak

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeTweakService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var lastVolUpTime: Long = 0
    private var lastVolDownTime: Long = 0

    private var isVolUpPressed = false
    private var isVolDownPressed = false

    private var isDualPressConsumed = false
    private var clickCount = 0
    private var pendingGestureRunnable: Runnable? = null

    // Session memory to support resume when music was paused via tweak
    private var lastTweakActionTime: Long = 0

    companion object {
        private const val TAG = "VolumeTweakService"
        private const val DUAL_PRESS_WINDOW_MS = 110L  // Max ms between Vol Up and Vol Down presses
        private const val GESTURE_TIMEOUT_MS = 380L    // Timeout window to count multi-clicks
        private const val PAUSE_SESSION_TIMEOUT_MS = 600000L // 10 minutes session retention for resume
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d(TAG, "VolumeTweakService initialized")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Pure key event service, no accessibility event processing needed
    }

    override fun onInterrupt() {
        Log.d(TAG, "VolumeTweakService interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event)
        }

        val currentTime = SystemClock.uptimeMillis()
        val isMusicActive = audioManager.isMusicActive
        val isRecentPauseSession = (currentTime - lastTweakActionTime) < PAUSE_SESSION_TIMEOUT_MS

        // Only intercept key presses if music is playing or within an active media session
        if (!isMusicActive && !isRecentPauseSession) {
            isVolUpPressed = false
            isVolDownPressed = false
            isDualPressConsumed = false
            return false
        }

        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                isVolUpPressed = true
                lastVolUpTime = currentTime
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                isVolDownPressed = true
                lastVolDownTime = currentTime
            }

            val timeDiff = Math.abs(lastVolUpTime - lastVolDownTime)

            // Dual press condition: both buttons currently down within threshold
            if (isVolUpPressed && isVolDownPressed && timeDiff <= DUAL_PRESS_WINDOW_MS) {
                isDualPressConsumed = true
                registerDualClick()
                return true // Consume event to prevent system volume change
            }

            // Check if the current press is close to previous opposite key press
            if (isDualPressConsumed) {
                return true
            }

        } else if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                isVolUpPressed = false
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                isVolDownPressed = false
            }

            if (isDualPressConsumed) {
                // When both keys are released, reset consumption flag
                if (!isVolUpPressed && !isVolDownPressed) {
                    isDualPressConsumed = false
                }
                return true // Consume UP event of dual press
            }
        }

        // Single key press passed through normally
        return false
    }

    private fun registerDualClick() {
        clickCount++
        lastTweakActionTime = SystemClock.uptimeMillis()

        // Cancel previous pending gesture evaluation
        pendingGestureRunnable?.let { handler.removeCallbacks(it) }

        val runnable = Runnable {
            dispatchGestureAction(clickCount)
            clickCount = 0
        }
        pendingGestureRunnable = runnable
        handler.postDelayed(runnable, GESTURE_TIMEOUT_MS)
    }

    private fun dispatchGestureAction(clicks: Int) {
        when (clicks) {
            1 -> {
                Log.d(TAG, "Triggered 1 Click: Play/Pause Media")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
            2 -> {
                Log.d(TAG, "Triggered 2 Clicks: Next Track")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            3, 4 -> {
                Log.d(TAG, "Triggered $clicks Clicks: Previous Track")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            else -> {
                if (clicks > 4) {
                    Log.d(TAG, "Triggered $clicks Clicks: Previous Track")
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
