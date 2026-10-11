package com.dhruv.volumetweak

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(val timestampMs: Long, val formattedTime: String, val message: String)

object LogBuffer {
    private val memoryLogs = mutableListOf<LogEntry>()
    private var listener: ((List<String>) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val displayFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private const val MAX_MEMORY_LOGS = 200
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            logFile = File(context.filesDir, "diagnostic_logs.txt")
            loadLogsFromDisk()
        } catch (e: Exception) {
            // Ignore init errors
        }
    }

    private fun loadLogsFromDisk() {
        val file = logFile ?: return
        if (!file.exists()) return
        try {
            val lines = file.readLines()
            memoryLogs.clear()
            val now = System.currentTimeMillis()
            val cutoff = now - (48 * 3600 * 1000L) // Retain last 48h

            for (line in lines) {
                if (line.isBlank()) continue
                val parts = line.split(" | ", limit = 3)
                if (parts.size == 3) {
                    val ts = parts[0].toLongOrNull() ?: continue
                    if (ts >= cutoff) {
                        memoryLogs.add(LogEntry(ts, parts[1], parts[2]))
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore disk parse errors
        }
    }

    private fun saveLogsToDisk() {
        val file = logFile ?: return
        try {
            val content = memoryLogs.joinToString("\n") { "${it.timestampMs} | ${it.formattedTime} | ${it.message}" }
            file.writeText(content)
        } catch (e: Exception) {
            // Ignore disk write errors
        }
    }

    @Synchronized
    fun log(message: String) {
        val now = System.currentTimeMillis()
        val timestampStr = timeFormat.format(Date(now))
        val entry = LogEntry(now, timestampStr, message)
        memoryLogs.add(entry)

        while (memoryLogs.size > MAX_MEMORY_LOGS) {
            memoryLogs.removeAt(0)
        }

        saveLogsToDisk()

        val displayList = memoryLogs.takeLast(30).map { "[${displayFormat.format(Date(it.timestampMs))}] ${it.message}" }
        mainHandler.post {
            listener?.invoke(displayList)
        }
    }

    @Synchronized
    fun getLogsForTimeRange(hoursAgo: Int): List<LogEntry> {
        if (hoursAgo <= 0) return ArrayList(memoryLogs)
        val cutoff = System.currentTimeMillis() - (hoursAgo * 3600 * 1000L)
        return memoryLogs.filter { it.timestampMs >= cutoff }
    }

    @Synchronized
    fun generateDiagnosticReport(context: Context, hoursAgo: Int, userNote: String): String {
        val filteredLogs = getLogsForTimeRange(hoursAgo)
        val sb = StringBuilder()
        sb.append("========== VOLUME BUTTON TWEAK DIAGNOSTIC REPORT ==========\n")
        sb.append("Generated At: ${timeFormat.format(Date())}\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n")
        sb.append("App Version: v1.9.8 (Build 18)\n")
        sb.append("Time Range Filter: ${if (hoursAgo <= 0) "All Available Logs" else "Past $hoursAgo Hour(s)"}\n")

        if (userNote.isNotBlank()) {
            sb.append("\n--- USER CUSTOM NOTE / ISSUE DESCRIPTION ---\n")
            sb.append(userNote.trim()).append("\n")
        }

        sb.append("\n--- LOG EVENTS HISTORY (${filteredLogs.size} entries) ---\n")
        if (filteredLogs.isEmpty()) {
            sb.append("No log entries captured during this time window.\n")
        } else {
            for (entry in filteredLogs) {
                sb.append("[${entry.formattedTime}] ${entry.message}\n")
            }
        }
        sb.append("===========================================================\n")
        return sb.toString()
    }

    @Synchronized
    fun getAllLogsText(): String {
        return memoryLogs.joinToString("\n") { "[${it.formattedTime}] ${it.message}" }
    }

    @Synchronized
    fun clear() {
        memoryLogs.clear()
        try {
            logFile?.delete()
        } catch (e: Exception) {}
        mainHandler.post {
            listener?.invoke(ArrayList())
        }
    }

    @Synchronized
    fun setListener(l: ((List<String>) -> Unit)?) {
        listener = l
        if (l != null) {
            val displayList = memoryLogs.takeLast(30).map { "[${displayFormat.format(Date(it.timestampMs))}] ${it.message}" }
            mainHandler.post {
                l.invoke(displayList)
            }
        }
    }
}
