package com.dhruv.volumetweak

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogBuffer {
    private val logs = mutableListOf<String>()
    private var listener: ((List<String>) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private const val MAX_LOGS = 30 // Capped to reduce memory footprint

    @Synchronized
    fun log(message: String) {
        val timestamp = timeFormat.format(Date())
        val entry = "[$timestamp] $message"
        logs.add(entry)
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
        val currentLogs = ArrayList(logs)
        mainHandler.post {
            listener?.invoke(currentLogs)
        }
    }

    @Synchronized
    fun getAllLogsText(): String {
        return logs.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        logs.clear()
        mainHandler.post {
            listener?.invoke(ArrayList())
        }
    }

    @Synchronized
    fun setListener(l: ((List<String>) -> Unit)?) {
        listener = l
        if (l != null) {
            val currentLogs = ArrayList(logs)
            mainHandler.post {
                l.invoke(currentLogs)
            }
        }
    }
}
