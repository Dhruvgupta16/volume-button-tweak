package com.dhruv.volumetweak

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeTweakService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var lastVolUpTime: Long = 0
    private var lastVolDownTime: Long = 0

    private var isVolUpPressed = false
    private var isVolDownPressed = false

    private var pendingSinglePressRunnable: Runnable? = null
    private var pendingSinglePressKeyCode: Int = 0

    private var clickCount = 0
    private var pendingGestureRunnable: Runnable? = null

    // Session memory to support resume when music was paused via tweak
    private var lastTweakActionTime: Long = 0

    companion object {
        private const val DUAL_PRESS_WINDOW_MS = 140L  // Window to register simultaneous press
        private const val DEFER_SINGLE_PRESS_MS = 90L   // Time window to wait before processing single volume key
        private const val GESTURE_TIMEOUT_MS = 380L    // Timeout window to count multi-clicks
        private const val PAUSE_SESSION_TIMEOUT_MS = 600000L // 10 min session memory
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        LogBuffer.log("Service created and ready")
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

        val currentTime = SystemClock.uptimeMillis()
        val isMusicActive = audioManager.isMusicActive
        val isRecentPauseSession = (currentTime - lastTweakActionTime) < PAUSE_SESSION_TIMEOUT_MS

        // If music is NOT active and no active session, let normal volume control work completely untouched
        if (!isMusicActive && !isRecentPauseSession) {
            isVolUpPressed = false
            isVolDownPressed = false
            return false
        }

        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                isVolUpPressed = true
                lastVolUpTime = currentTime
                LogBuffer.log("Vol UP Pressed")
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                isVolDownPressed = true
                lastVolDownTime = currentTime
                LogBuffer.log("Vol DOWN Pressed")
            }

            val timeDiff = Math.abs(lastVolUpTime - lastVolDownTime)

            // Check if BOTH keys are pressed within DUAL_PRESS_WINDOW_MS
            if (isVolUpPressed && isVolDownPressed && timeDiff <= DUAL_PRESS_WINDOW_MS) {
                LogBuffer.log("⚡ DUAL PRESS DETECTED (diff: ${timeDiff}ms)")
                
                // Cancel any pending single volume press action
                cancelPendingSinglePress()

                // Register dual click
                registerDualClick()
                return true
            }

            // This is the FIRST key of a potential dual press.
            // Cancel previous pending single press, and schedule a deferred single volume action
            cancelPendingSinglePress()
            scheduleDeferredSinglePress(keyCode)
            return true

        } else if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                isVolUpPressed = false
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                isVolDownPressed = false
            }
            return true
        }

        return true
    }

    private fun scheduleDeferredSinglePress(keyCode: Int) {
        pendingSinglePressKeyCode = keyCode
        val runnable = Runnable {
            LogBuffer.log("Single Vol (${if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) "UP" else "DOWN"})")
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
        when (clicks) {
            1 -> {
                LogBuffer.log("▶️ 1 Click: Play / Pause")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
            2 -> {
                LogBuffer.log("⏭️ 2 Clicks: Next Track")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            3, 4 -> {
                LogBuffer.log("⏮️ $clicks Clicks: Previous Track")
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            else -> {
                if (clicks > 4) {
                    LogBuffer.log("⏮️ $clicks Clicks: Previous Track")
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
