package com.dhruv.volumetweak

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object CloudLogger {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    interface UploadCallback {
        fun onProgress(message: String)
        fun onSuccess(url: String)
        fun onError(error: String)
    }

    /**
     * Uploads system diagnostics and timestamped event logs to cloud storage.
     * Primary: https://paste.rs (ultra-fast, zero-auth, persistent HTTPS)
     * Fallback: https://dpaste.org/api/
     */
    fun uploadLogs(
        context: Context,
        hoursAgo: Int? = 24,
        userNote: String = "",
        reason: String = "MANUAL_UPLOAD",
        callback: UploadCallback? = null
    ) {
        val appContext = context.applicationContext
        val payload = buildComprehensiveLogPayload(appContext, hoursAgo, userNote, reason)

        callback?.onProgress("Connecting to cloud endpoint...")

        executor.execute {
            var finalUrl: String? = null
            var lastError: String? = null

            // 1. Primary endpoint: paste.rs
            try {
                finalUrl = uploadToPasteRs(payload)
            } catch (e: Exception) {
                lastError = "paste.rs: ${e.message}"
                LogBuffer.log("[CLOUD] Primary paste.rs upload error: ${e.message}")
            }

            // 2. Secondary endpoint fallback: dpaste.org
            if (finalUrl.isNullOrBlank() || !finalUrl.startsWith("http")) {
                try {
                    finalUrl = uploadToDpaste(payload)
                } catch (e: Exception) {
                    lastError = (lastError ?: "") + " | dpaste.org: ${e.message}"
                    LogBuffer.log("[CLOUD] Secondary dpaste upload error: ${e.message}")
                }
            }

            val resultUrl = finalUrl?.trim()
            if (!resultUrl.isNullOrBlank() && resultUrl.startsWith("http")) {
                // Save to SharedPreferences for UI display & fast retrieval
                val prefs = appContext.getSharedPreferences("volume_tweak_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("last_cloud_log_url", resultUrl)
                    .putLong("last_cloud_log_time", System.currentTimeMillis())
                    .apply()

                LogBuffer.log("[CLOUD] Upload successful: $resultUrl ($reason)")

                mainHandler.post {
                    // Auto copy link to clipboard for convenience
                    try {
                        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Volume Tweak Cloud Log", resultUrl)
                        clipboard?.setPrimaryClip(clip)
                    } catch (ignored: Exception) {}

                    callback?.onSuccess(resultUrl)
                }
            } else {
                val err = lastError ?: "Cloud upload failed. Check network connection."
                LogBuffer.log("[CLOUD] Upload failed: $err")
                mainHandler.post {
                    callback?.onError(err)
                }
            }
        }
    }

    private fun uploadToPasteRs(payload: String): String {
        val url = URL("https://paste.rs")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 7000
            conn.readTimeout = 9000
            conn.doOutput = true
            conn.doInput = true
            conn.setRequestProperty("Content-Type", "text/plain; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "VolumeButtonTweak-Android")

            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                return responseText.trim()
            } else {
                val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                throw RuntimeException("HTTP $responseCode: $errText")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun uploadToDpaste(payload: String): String {
        val url = URL("https://dpaste.org/api/")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 7000
            conn.readTimeout = 9000
            conn.doOutput = true
            conn.doInput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "VolumeButtonTweak-Android")

            val postData = "content=" + URLEncoder.encode(payload, "UTF-8") +
                    "&syntax=text" +
                    "&expiry_days=14"

            conn.outputStream.use { os ->
                os.write(postData.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val trimmed = responseText.trim().replace("\"", "")
                return trimmed
            } else {
                val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                throw RuntimeException("HTTP $responseCode: $errText")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun buildComprehensiveLogPayload(
        context: Context,
        hoursAgo: Int?,
        userNote: String,
        reason: String
    ): String {
        val filteredLogs = LogBuffer.getLogsForTimeRange(hoursAgo)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        var versionName = "Unknown"
        var versionCode = 0L
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = pInfo.versionName ?: "Unknown"
            @Suppress("DEPRECATION")
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                pInfo.versionCode.toLong()
            }
            versionCode = vCode
        } catch (ignored: Exception) {}

        val sb = StringBuilder()
        sb.append("======================================================================\n")
        sb.append("             VOLUME BUTTON TWEAK - CLOUD DIAGNOSTIC LOG              \n")
        sb.append("======================================================================\n")
        sb.append("Report Reason    : $reason\n")
        sb.append("Report Timestamp : ${timeFormat.format(Date())}\n")
        sb.append("App Version      : v$versionName (Build $versionCode)\n")
        sb.append("Device Hardware  : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
        sb.append("Android Release  : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Build Display    : ${Build.DISPLAY}\n")
        sb.append("Selected Window  : ${if (hoursAgo == null || hoursAgo <= 0) "All Recorded Logs" else "Past $hoursAgo Hour(s)"}\n")
        sb.append("Total Events     : ${filteredLogs.size}\n")

        sb.append("\n----------------------------------------------------------------------\n")
        sb.append("LIVE SERVICE & AUDIO SYSTEM STATE SNAPSHOT:\n")
        sb.append("----------------------------------------------------------------------\n")
        sb.append("Native Passthrough Mode : ${VolumeTweakService.nativeVolumePassthrough}\n")
        sb.append("Always-Active Mode      : ${VolumeTweakService.alwaysActiveEnabled}\n")
        sb.append("In-Call Stream Mode     : ${audioManager?.mode ?: "UNKNOWN"}\n")
        sb.append("Media Playback Active   : ${audioManager?.isMusicActive ?: false}\n")
        if (audioManager != null) {
            try {
                val musicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val ringVol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                val voiceVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                sb.append("Volume Levels           : Media=$musicVol/$musicMax, Ring=$ringVol, VoiceCall=$voiceVol\n")
            } catch (ignored: Exception) {}
        }

        if (userNote.isNotBlank()) {
            sb.append("\n----------------------------------------------------------------------\n")
            sb.append("USER COMMENTS / GLITCH SUMMARY:\n")
            sb.append(userNote.trim()).append("\n")
            sb.append("----------------------------------------------------------------------\n")
        }

        sb.append("\n----------------------------------------------------------------------\n")
        sb.append("EVENT TRACE LOG (${filteredLogs.size} ENTRIES):\n")
        sb.append("----------------------------------------------------------------------\n")
        if (filteredLogs.isEmpty()) {
            sb.append("No hardware key or service events recorded in this timeframe.\n")
        } else {
            for (entry in filteredLogs) {
                sb.append("[${entry.formattedTime}] ${entry.message}\n")
            }
        }
        sb.append("======================================================================\n")
        sb.append("                         END OF CLOUD REPORT                          \n")
        sb.append("======================================================================\n")

        return sb.toString()
    }
}
