package com.msst.toolbox.platform

import android.content.Context
import com.msst.toolbox.ToolboxApplication
import com.msst.toolbox.model.SystemStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class AndroidSystemInfoProvider(private val context: Context) : SystemInfoProvider {

    private var prevIdleTime = 0L
    private var prevTotalTime = 0L
    private var isFirstRead = true

    private fun readCpuUsage(): Float {
        return try {
            val line = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return 0f
            // format: cpu  user nice system idle iowait irq softirq steal guest guest_nice
            val parts = line.trim().split("\\s+".toRegex()).drop(1)
            val values = parts.mapNotNull { it.toLongOrNull() }
            val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L } // idle + iowait
            val total = values.sum()

            if (isFirstRead) {
                prevIdleTime = idle
                prevTotalTime = total
                isFirstRead = false
                return 0f
            }

            val deltaIdle = idle - prevIdleTime
            val deltaTotal = total - prevTotalTime
            prevIdleTime = idle
            prevTotalTime = total

            if (deltaTotal <= 0L) 0f
            else ((deltaTotal - deltaIdle).toFloat() / deltaTotal * 100f).coerceIn(0f, 100f)
        } catch (e: Exception) {
            0f
        }
    }

    private fun readMemInfoLine(lines: List<String>, key: String): Long {
        return lines.firstOrNull { it.startsWith(key) }
            ?.split(":")?.getOrElse(1) { "0" }
            ?.trim()?.split("\\s+".toRegex())?.firstOrNull()
            ?.toLongOrNull() ?: 0L
    }

    private fun readMemoryAndSwap(): Pair<Float, Float> {
        return try {
            val lines = File("/proc/meminfo").readLines()
            val memTotal = readMemInfoLine(lines, "MemTotal")
            val memAvailable = readMemInfoLine(lines, "MemAvailable")
            val swapTotal = readMemInfoLine(lines, "SwapTotal")
            val swapFree = readMemInfoLine(lines, "SwapFree")

            val memUsage = if (memTotal > 0) (memTotal - memAvailable).toFloat() / memTotal * 100f else 0f
            val swapUsage = if (swapTotal > 0) (swapTotal - swapFree).toFloat() / swapTotal * 100f else 0f
            memUsage to swapUsage
        } catch (e: Exception) {
            0f to 0f
        }
    }

    private fun readThermalZone(typeKeyword: String): Float? {
        return try {
            val thermalDir = File("/sys/class/thermal")
            thermalDir.listFiles()
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
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getSystemStatus(): SystemStatus = withContext(Dispatchers.IO) {
        if (isFirstRead) {
            readCpuUsage() // initialize prev counters, result discarded
            delay(300L)    // wait for a meaningful delta
        }
        val cpuUsage = readCpuUsage()
        val (memUsage, swapUsage) = readMemoryAndSwap()

        // 尝试不同的温度区域标签（不同 SoC 厂商命名不同）
        val cpuTemp = readThermalZone("cpu")
            ?: readThermalZone("soc")
            ?: readThermalZone("tsens")

        val gpuTemp = readThermalZone("gpu")
            ?: readThermalZone("gpu0")

        SystemStatus(
            cpuUsagePercent = cpuUsage,
            memoryUsagePercent = memUsage,
            swapUsagePercent = swapUsage,
            cpuTemperatureCelsius = cpuTemp,
            gpuTemperatureCelsius = gpuTemp
        )
    }
}

actual fun createSystemInfoProvider(): SystemInfoProvider =
    AndroidSystemInfoProvider(ToolboxApplication.instance)