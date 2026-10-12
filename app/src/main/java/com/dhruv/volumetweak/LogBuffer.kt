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
    private const val MAX_MEMORY_LOGS = 300
    private var logFile: File? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            logFile = File(context.filesDir, "diagnostic_logs.txt")
            loadLogsFromDisk()
            isInitialized = true
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
            val cutoff = now - (48 * 3600 * 1000L) // Retain last 48 hours

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

        val displayList = memoryLogs.takeLast(35).map { "[${displayFormat.format(Date(it.timestampMs))}] ${it.message}" }
        mainHandler.post {
            listener?.invoke(displayList)
        }
    }

    @Synchronized
    fun getLogsForTimeRange(hoursAgo: Int?): List<LogEntry> {
        if (hoursAgo == null || hoursAgo <= 0) return ArrayList(memoryLogs)
        val cutoff = System.currentTimeMillis() - (hoursAgo * 3600 * 1000L)
        return memoryLogs.filter { it.timestampMs >= cutoff }
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
        } catch (e: Exception) {
            // Ignore delete error
        }
        mainHandler.post {
            listener?.invoke(ArrayList())
        }
    }

    @Synchronized
    fun setListener(l: ((List<String>) -> Unit)?) {
        listener = l
        if (l != null) {
            val displayList = memoryLogs.takeLast(35).map { "[${displayFormat.format(Date(it.timestampMs))}] ${it.message}" }
            mainHandler.post {
                l.invoke(displayList)
            }
        }
    }

    @Synchronized
    fun generateDiagnosticReport(context: Context, hoursAgo: Int?, userNote: String): String {
        val filteredLogs = getLogsForTimeRange(hoursAgo)
        val sb = StringBuilder()
        sb.append("====================================================\n")
        sb.append("      VOLUME BUTTON TWEAK - DIAGNOSTIC REPORT       \n")
        sb.append("====================================================\n")
        sb.append("Report Timestamp : ${timeFormat.format(Date())}\n")
        sb.append("Device Hardware  : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
        sb.append("Android Version  : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Build Display ID : ${Build.DISPLAY}\n")
        sb.append("App Version      : v1.9.9b (Build 20)\n")
        sb.append("Selected Window  : ${if (hoursAgo == null || hoursAgo <= 0) "All Available Logs" else "Past $hoursAgo Hour(s)"}\n")
        sb.append("Total Events     : ${filteredLogs.size}\n")

        if (userNote.isNotBlank()) {
            sb.append("\n----------------------------------------------------\n")
            sb.append("USER NOTES / WHAT WENT WRONG:\n")
            sb.append(userNote.trim()).append("\n")
            sb.append("----------------------------------------------------\n")
        }

        sb.append("\n----------------------------------------------------\n")
        sb.append("TIMESTAMPTED EVENT LOG (${filteredLogs.size} ENTRIES):\n")
        sb.append("----------------------------------------------------\n")
        if (filteredLogs.isEmpty()) {
            sb.append("No hardware or service events recorded in this time range.\n")
        } else {
            for (entry in filteredLogs) {
                sb.append("[${entry.formattedTime}] ${entry.message}\n")
            }
        }
        sb.append("====================================================\n")
        sb.append("End of Diagnostic Report\n")

        val reportStr = sb.toString()

        // Also persist report to disk locally for easy retrieval
        try {
            val reportFile = File(context.filesDir, "latest_diagnostic_report.txt")
            reportFile.writeText(reportStr)
        } catch (e: Exception) {
            // Ignore file write error
        }

        return reportStr
    }
}
