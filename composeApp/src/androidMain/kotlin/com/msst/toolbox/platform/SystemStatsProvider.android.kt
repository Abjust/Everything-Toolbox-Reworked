package com.msst.toolbox.platform

import android.content.Context
import com.msst.toolbox.ToolboxApplication
import com.msst.toolbox.model.SystemStatsSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidSystemStatsProvider(private val context: Context) : SystemStatsProvider {

    private var prevCoreStats: Array<LongArray> = emptyArray()
    private var prevRxBytes = 0L
    private var prevTxBytes = 0L
    private var prevTimestamp = 0L

    override suspend fun sample(): SystemStatsSample = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val elapsedSec = if (prevTimestamp > 0) (now - prevTimestamp) / 1000f else 1f
        prevTimestamp = now

        val perCore = readPerCoreCpu()
        val freqs = readPerCoreCpuFreqMHz()
        val mem = readMemory()
        val gpuUsage = readGpuUsage()
        val cpuTemp = readThermalZone("cpu") ?: readThermalZone("soc") ?: readThermalZone("tsens")
        val gpuTemp = readThermalZone("gpu") ?: readThermalZone("gpu0")
        val (rxMbps, txMbps) = readNetworkSpeed(elapsedSec)

        SystemStatsSample(
            timestamp = now,
            perCoreCpuPercent = perCore,
            perCoreCpuFreqMHz = freqs,
            gpuUsagePercent = gpuUsage,
            cpuTemperatureCelsius = cpuTemp,
            gpuTemperatureCelsius = gpuTemp,
            memUsedMb = mem.memUsedMb,
            swapUsedMb = mem.swapUsedMb,
            memTotalMb = mem.memTotalMb,
            swapTotalMb = mem.swapTotalMb,
            diskReadMbps = 0f,
            diskWriteMbps = 0f,
            netRxMbps = rxMbps,
            netTxMbps = txMbps
        )
    }

    private fun readPerCoreCpu(): List<Float> {
        return try {
            val lines = File("/proc/stat").readLines()
            val coreLines = lines.filter { it.matches(Regex("cpu[0-9]+.*")) }
            if (coreLines.isEmpty()) return emptyList()

            val currentStats = Array(coreLines.size) { LongArray(2) }
            coreLines.forEachIndexed { i, line ->
                val parts = line.trim().split("\\s+".toRegex()).drop(1)
                val values = parts.mapNotNull { it.toLongOrNull() }
                val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }
                currentStats[i][0] = idle
                currentStats[i][1] = values.sum()
            }

            val result = if (prevCoreStats.size == currentStats.size) {
                currentStats.mapIndexed { i, cur ->
                    val deltaIdle = cur[0] - prevCoreStats[i][0]
                    val deltaTotal = cur[1] - prevCoreStats[i][1]
                    if (deltaTotal <= 0L) 0f
                    else ((deltaTotal - deltaIdle).toFloat() / deltaTotal * 100f).coerceIn(0f, 100f)
                }
            } else List(currentStats.size) { 0f }

            prevCoreStats = currentStats
            result
        } catch (_: Exception) { emptyList() }
    }

    private fun readPerCoreCpuFreqMHz(): List<Float> {
        return try {
            val cpuDir = File("/sys/devices/system/cpu")
            cpuDir.listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }
                ?.sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: 0 }
                ?.mapNotNull { cpu ->
                    try {
                        File(cpu, "cpufreq/scaling_cur_freq").readText().trim().toLongOrNull()
                            ?.let { it / 1000f } // kHz → MHz
                    } catch (_: Exception) { null }
                }
                ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private data class MemStats(val memUsedMb: Float, val swapUsedMb: Float, val memTotalMb: Float, val swapTotalMb: Float)

    private fun readMemory(): MemStats {
        return try {
            val lines = File("/proc/meminfo").readLines()
            fun readKb(key: String) = lines.firstOrNull { it.startsWith(key) }
                ?.split(":")?.getOrElse(1) { "0" }?.trim()
                ?.split("\\s+".toRegex())?.firstOrNull()?.toLongOrNull() ?: 0L
            val totalKb = readKb("MemTotal")
            val availKb = readKb("MemAvailable")
            val swapTotalKb = readKb("SwapTotal")
            val swapFreeKb = readKb("SwapFree")
            MemStats(
                memUsedMb = (totalKb - availKb) / 1024f,
                swapUsedMb = (swapTotalKb - swapFreeKb) / 1024f,
                memTotalMb = totalKb / 1024f,
                swapTotalMb = swapTotalKb / 1024f
            )
        } catch (_: Exception) { MemStats(0f, 0f, 0f, 0f) }
    }

    private fun readThermalZone(typeKeyword: String): Float? {
        return try {
            File("/sys/class/thermal").listFiles()
                ?.filter { it.name.startsWith("thermal_zone") }
                ?.mapNotNull { zone ->
                    try {
                        val zoneType = File(zone, "type").readText().trim().lowercase()
                        if (zoneType.contains(typeKeyword)) {
                            File(zone, "temp").readText().trim().toLongOrNull()?.toFloat()?.div(1000f)
                        } else null
                    } catch (_: Exception) { null }
                }
                ?.filter { it > 0f }
                ?.maxOrNull()
        } catch (_: Exception) { null }
    }

    private fun readGpuUsage(): Float? {
        for (path in listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            "/sys/kernel/gpu/gpu_busy"
        )) {
            try {
                val pct = File(path).readText().trim().removeSuffix("%").trim().toFloatOrNull()
                if (pct != null) return pct.coerceIn(0f, 100f)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun readNetworkSpeed(elapsedSec: Float): Pair<Float, Float> {
        return try {
            var totalRx = 0L
            var totalTx = 0L
            // Enumerate interfaces via sysfs to avoid socket() call that Android blocks
            File("/sys/class/net").listFiles()?.forEach { ifaceDir ->
                val name = ifaceDir.name
                if (name == "lo") return@forEach  // skip loopback
                val base = "/sys/class/net/$name/statistics"
                try {
                    totalRx += File("$base/rx_bytes").readText().trim().toLongOrNull() ?: 0L
                    totalTx += File("$base/tx_bytes").readText().trim().toLongOrNull() ?: 0L
                } catch (_: Exception) {}
            }
            val rxMbps = if (prevRxBytes > 0 && elapsedSec > 0)
                ((totalRx - prevRxBytes).toFloat() / elapsedSec / (1024 * 1024) * 8).coerceAtLeast(0f)
            else 0f
            val txMbps = if (prevTxBytes > 0 && elapsedSec > 0)
                ((totalTx - prevTxBytes).toFloat() / elapsedSec / (1024 * 1024) * 8).coerceAtLeast(0f)
            else 0f
            prevRxBytes = totalRx
            prevTxBytes = totalTx
            rxMbps to txMbps
        } catch (_: Exception) { 0f to 0f }
    }
}

actual fun createSystemStatsProvider(): SystemStatsProvider =
    AndroidSystemStatsProvider(ToolboxApplication.instance)