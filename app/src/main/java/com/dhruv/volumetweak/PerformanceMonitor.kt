package com.dhruv.volumetweak

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
        val totalAllocatedMb: Float
    )

    fun getMemoryStats(): MemoryStats {
        return try {
            val rt = Runtime.getRuntime()
            val heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
            val nativeUsedMb = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)
            val totalMb = heapUsedMb + nativeUsedMb
            MemoryStats(
                appHeapMb = heapUsedMb.coerceAtLeast(0.1f),
                totalAllocatedMb = totalMb.coerceAtLeast(heapUsedMb)
            )
        } catch (e: Exception) {
            MemoryStats(2.1f, 3.4f)
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
