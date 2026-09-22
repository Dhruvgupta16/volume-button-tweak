package com.dhruv.volumetweak

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

object GlyphController {
    private val handler = Handler(Looper.getMainLooper())
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

    fun init(context: Context) {
        try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager?.let { manager ->
                for (id in manager.cameraIdList) {
                    val characteristics = manager.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = id
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // Camera permission or camera manager unavailable
        }
    }

    fun pulse(pulses: Int) {
        val cid = cameraId ?: return
        val cm = cameraManager ?: return

        var delay = 0L
        for (i in 0 until pulses) {
            handler.postDelayed({
                try {
                    cm.setTorchMode(cid, true)
                } catch (ignored: Exception) {}
            }, delay)

            delay += 75L

            handler.postDelayed({
                try {
                    cm.setTorchMode(cid, false)
                } catch (ignored: Exception) {}
            }, delay)

            delay += 60L
        }
    }
}
