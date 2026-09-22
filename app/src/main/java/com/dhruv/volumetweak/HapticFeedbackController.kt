package com.dhruv.volumetweak

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

object HapticFeedbackController {

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        if (vibrator == null) {
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun vibrate(context: Context, clicks: Int) {
        init(context)
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) {
            LogBuffer.log("[HAPTIC] Device reports no vibrator hardware")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ / Nothing OS: Use USAGE_MEDIA to prevent OS from suppressing haptics
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_MEDIA)
                    .build()

                val effect = when (clicks) {
                    1 -> VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
                    2 -> VibrationEffect.createWaveform(
                        longArrayOf(0, 60, 80, 60),
                        intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
                        -1
                    )
                    else -> VibrationEffect.createWaveform(
                        longArrayOf(0, 60, 80, 60, 80, 60),
                        intArrayOf(
                            0, VibrationEffect.DEFAULT_AMPLITUDE,
                            0, VibrationEffect.DEFAULT_AMPLITUDE,
                            0, VibrationEffect.DEFAULT_AMPLITUDE
                        ),
                        -1
                    )
                }
                vib.vibrate(effect, attrs)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (clicks) {
                    1 -> VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
                    2 -> VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1)
                    else -> VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60, 80, 60), -1)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (clicks) {
                    1 -> vib.vibrate(70)
                    2 -> vib.vibrate(longArrayOf(0, 60, 80, 60), -1)
                    else -> vib.vibrate(longArrayOf(0, 60, 80, 60, 80, 60), -1)
                }
            }
            LogBuffer.log("[HAPTIC] Vibration triggered ($clicks pulse${if (clicks > 1) "s" else ""})")
        } catch (e: Exception) {
            LogBuffer.log("[HAPTIC] Error: ${e.message}")
        }
    }
}
