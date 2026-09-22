package com.dhruv.volumetweak

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock

object PerformanceMonitor {

    private var lastCpuMs: Long = 0
    private var lastTimeMs: Long = 0
    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    init {
        lastCpuMs = Process.getElapsedCpuTime()
        lastTimeMs = SystemClock.uptimeMillis()
    }

    data class MemoryStats(
        val privateDirtyMb: Float,
        val pssMb: Float
    )

    fun getMemoryStats(context: Context): MemoryStats {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pids = intArrayOf(Process.myPid())
            val memInfo = am.getProcessMemoryInfo(pids)
            if (memInfo.isNotEmpty()) {
                val privateDirty = memInfo[0].totalPrivateDirty / 1024f
                val pss = memInfo[0].totalPss / 1024f
                MemoryStats(privateDirty, pss)
            } else {
                val rt = Runtime.getRuntime()
                val heap = (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
                MemoryStats(heap, heap)
            }
        } catch (e: Exception) {
            MemoryStats(0f, 0f)
        }
    }

    fun getCpuUsagePercent(): String {
        val currentCpuMs = Process.getElapsedCpuTime()
        val currentTimeMs = SystemClock.uptimeMillis()

        if (lastTimeMs == 0L) {
            lastCpuMs = currentCpuMs
            lastTimeMs = currentTimeMs
            return "< 0.1%"
        }

        val timeDiff = currentTimeMs - lastTimeMs
        val cpuDiff = currentCpuMs - lastCpuMs

        lastCpuMs = currentCpuMs
        lastTimeMs = currentTimeMs

        if (timeDiff <= 0 || cpuDiff <= 0) {
            return "< 0.1%"
        }

        val usage = (cpuDiff.toFloat() / (timeDiff * cores).toFloat()) * 100f
        return if (usage < 0.1f) {
            "< 0.1%"
        } else {
            String.format("%.1f%%", usage.coerceAtMost(100f))
        }
    }
}
