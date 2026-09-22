package com.dhruv.volumetweak

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import java.io.RandomAccessFile

object PerformanceMonitor {

    fun getMemoryUsageMB(context: Context): Float {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pids = intArrayOf(Process.myPid())
            val memoryInfoArray = activityManager.getProcessMemoryInfo(pids)
            if (memoryInfoArray.isNotEmpty()) {
                val totalPssKb = memoryInfoArray[0].totalPss
                totalPssKb / 1024f
            } else {
                val runtime = Runtime.getRuntime()
                (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
            }
        } catch (e: Exception) {
            0.0f
        }
    }

    private var lastCpuTime: Long = 0
    private var lastSampleTime: Long = 0

    fun getCpuUsagePercent(): String {
        return try {
            val reader = RandomAccessFile("/proc/self/stat", "r")
            val line = reader.readLine()
            reader.close()

            val tokens = line.split(" ")
            val utime = tokens[13].toLong()
            val stime = tokens[14].toLong()
            val currentCpuTime = utime + stime
            val currentTime = System.currentTimeMillis()

            if (lastSampleTime == 0L || currentTime == lastSampleTime) {
                lastCpuTime = currentCpuTime
                lastSampleTime = currentTime
                return "< 0.1%"
            }

            val cpuDiff = currentCpuTime - lastCpuTime
            val timeDiff = (currentTime - lastSampleTime) / 10 // approx clock ticks (100Hz)

            lastCpuTime = currentCpuTime
            lastSampleTime = currentTime

            if (timeDiff > 0 && cpuDiff > 0) {
                val percent = (cpuDiff.toFloat() / timeDiff.toFloat()) * 100f
                String.format("%.1f%%", Math.min(percent, 100f))
            } else {
                "< 0.1%"
            }
        } catch (e: Exception) {
            "< 0.1%"
        }
    }
}
