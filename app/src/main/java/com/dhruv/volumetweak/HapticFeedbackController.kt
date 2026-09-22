package com.dhruv.volumetweak

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticFeedbackController {

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun vibrate(clicks: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            when (clicks) {
                1 -> {
                    // Single crisp pulse
                    vib.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                2 -> {
                    // Double pulse: 0ms delay, 40ms on, 50ms off, 40ms on
                    val timings = longArrayOf(0, 40, 50, 40)
                    val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                    vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
                3, 4 -> {
                    // Triple pulse
                    val timings = longArrayOf(0, 40, 50, 40, 50, 40)
                    val amplitudes = intArrayOf(
                        0, VibrationEffect.DEFAULT_AMPLITUDE,
                        0, VibrationEffect.DEFAULT_AMPLITUDE,
                        0, VibrationEffect.DEFAULT_AMPLITUDE
                    )
                    vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
                else -> {
                    vib.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (e: Exception) {
            // Ignore if vibration fails
        }
    }
}
