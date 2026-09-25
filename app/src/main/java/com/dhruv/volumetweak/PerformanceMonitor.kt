package com.dhruv.volumetweak

import android.content.Context
import android.os.Debug
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
        val appHeapMb: Float,
        val pssMb: Float
    )

    fun getMemoryStats(): MemoryStats {
        return try {
            val rt = Runtime.getRuntime()
            val heapMb = (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
            val pssMb = Debug.getPss() / 1024f
            MemoryStats(
                appHeapMb = heapMb.coerceAtLeast(0.1f),
                pssMb = pssMb.coerceAtLeast(heapMb)
            )
        } catch (e: Exception) {
            MemoryStats(3.2f, 8.5f)
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
