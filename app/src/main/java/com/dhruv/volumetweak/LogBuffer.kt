package com.dhruv.volumetweak

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class LogEntry(val timestampMs: Long, val formattedTime: String, val message: String)

object LogBuffer {
    private val memoryLogs = mutableListOf<LogEntry>()
    private var listener: ((List<String>) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val diskExecutor = Executors.newSingleThreadExecutor()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val displayFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private const val MAX_MEMORY_LOGS = 50
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
        diskExecutor.execute {
            try {
                val lines = file.readLines()
                val now = System.currentTimeMillis()
                val cutoff = now - (24 * 3600 * 1000L) // Retain last 24 hours on disk

                val loaded = mutableListOf<LogEntry>()
                for (line in lines) {
                    if (line.isBlank()) continue
                    val parts = line.split(" | ", limit = 3)
                    if (parts.size == 3) {
                        val ts = parts[0].toLongOrNull() ?: continue
                        if (ts >= cutoff) {
                            loaded.add(LogEntry(ts, parts[1], parts[2]))
                        }
                    }
                }

                synchronized(memoryLogs) {
                    memoryLogs.clear()
                    memoryLogs.addAll(loaded.takeLast(MAX_MEMORY_LOGS))
                }
            } catch (e: Exception) {
                // Ignore disk parse errors
            }
        }
    }

    private fun appendLogToDisk(entry: LogEntry) {
        val file = logFile ?: return
        diskExecutor.execute {
            try {
                FileWriter(file, true).use { writer ->
                    writer.append("${entry.timestampMs} | ${entry.formattedTime} | ${entry.message}\n")
                }
            } catch (e: Exception) {
                // Ignore disk write errors
            }
        }
    }

    fun log(message: String) {
        val now = System.currentTimeMillis()
        val timestampStr = timeFormat.format(Date(now))
        val entry = LogEntry(now, timestampStr, message)

        var displayList: List<String>? = null
        synchronized(memoryLogs) {
            memoryLogs.add(entry)
            while (memoryLogs.size > MAX_MEMORY_LOGS) {
                memoryLogs.removeAt(0)
            }
            if (listener != null) {
                displayList = memoryLogs.takeLast(25).map { "[${displayFormat.format(Date(it.timestampMs))}] ${it.message}" }
            }
        }

        appendLogToDisk(entry)

        if (displayList != null) {
            mainHandler.post {
                listener?.invoke(displayList!!)
            }
        }
    }

    fun trimMemory() {
        synchronized(memoryLogs) {
            if (memoryLogs.size > 20) {
                val trimmed = memoryLogs.takeLast(20)
                memoryLogs.clear()
                memoryLogs.addAll(trimmed)
            }
        }
    }

    @Synchronized
    fun getLogsForTimeRange(hoursAgo: Int?): List<LogEntry> {
        val file = logFile
        if (file != null && file.exists()) {
            try {
                val lines = file.readLines()
                val cutoff = if (hoursAgo != null && hoursAgo > 0) System.currentTimeMillis() - (hoursAgo * 3600 * 1000L) else 0L
                val results = mutableListOf<LogEntry>()
                for (line in lines) {
                    if (line.isBlank()) continue
                    val parts = line.split(" | ", limit = 3)
                    if (parts.size == 3) {
                        val ts = parts[0].toLongOrNull() ?: continue
                        if (ts >= cutoff) {
                            results.add(LogEntry(ts, parts[1], parts[2]))
                        }
                    }
                }
                return results
            } catch (e: Exception) {
                // Fallback to memory
            }
        }
        synchronized(memoryLogs) {
            if (hoursAgo == null || hoursAgo <= 0) return ArrayList(memoryLogs)
            val cutoff = System.currentTimeMillis() - (hoursAgo * 3600 * 1000L)
            return memoryLogs.filter { it.timestampMs >= cutoff }
        }
    }

    @Synchronized
    fun getAllLogsText(): String {
        synchronized(memoryLogs) {
            return memoryLogs.joinToString("\n") { "[${it.formattedTime}] ${it.message}" }
        }
    }

    @Synchronized
    fun clear() {
        synchronized(memoryLogs) {
            memoryLogs.clear()
        }
        diskExecutor.execute {
            try {
                logFile?.delete()
            } catch (e: Exception) {
                // Ignore delete error
            }
        }
        mainHandler.post {
            listener?.invoke(emptyList())
        }
    }

    fun setListener(l: ((List<String>) -> Unit)?) {
        synchronized(memoryLogs) {
            listener = l
            if (l != null) {
                val displayList = memoryLogs.takeLast(25).map { "[${displayFormat.format(Date(it.timestampMs))}] ${it.message}" }
                mainHandler.post {
                    l.invoke(displayList)
                }
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
        var appVer = "v1.9.12"
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appVer = "v${pInfo.versionName} (Build ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode})"
        } catch (ignored: Exception) {}
        sb.append("App Version      : $appVer\n")
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
        sb.append("                  END OF REPORT                     \n")
        sb.append("====================================================\n")
        return sb.toString()
    }
}
