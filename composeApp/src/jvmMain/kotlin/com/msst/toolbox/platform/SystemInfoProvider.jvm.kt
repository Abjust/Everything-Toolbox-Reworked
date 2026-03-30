package com.msst.toolbox.platform

import com.msst.toolbox.model.SystemStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo

class JvmSystemInfoProvider : SystemInfoProvider {

    private val si = SystemInfo()
    private val hal = si.hardware
    private val cpu = hal.processor
    private val mem = hal.memory

    // 用于 CPU 负载计算的上一次 tick 快照
    private var prevTicks = cpu.systemCpuLoadTicks

    override suspend fun getSystemStatus(): SystemStatus = withContext(Dispatchers.IO) {
        val currentTicks = cpu.systemCpuLoadTicks
        val cpuLoad = (cpu.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0).toFloat()
        prevTicks = currentTicks

        val totalMem = mem.total
        val availMem = mem.available
        val memUsage = if (totalMem > 0) (totalMem - availMem).toFloat() / totalMem * 100f else 0f

        val swapTotal = mem.virtualMemory.swapTotal
        val swapUsed = mem.virtualMemory.swapUsed
        val swapUsage = if (swapTotal > 0) swapUsed.toFloat() / swapTotal * 100f else 0f

        val cpuTemp = try {
            val t = hal.sensors.cpuTemperature
            if (t > 0.0) t.toFloat() else null
        } catch (_: Exception) { null }

        // OSHI 6.x 中 GPU 温度需要平台特定 API，暂不实现
        val gpuTemp: Float? = null

        SystemStatus(
            cpuUsagePercent = cpuLoad.coerceIn(0f, 100f),
            memoryUsagePercent = memUsage.coerceIn(0f, 100f),
            swapUsagePercent = swapUsage.coerceIn(0f, 100f),
            cpuTemperatureCelsius = cpuTemp,
            gpuTemperatureCelsius = gpuTemp
        )
    }
}

actual fun createSystemInfoProvider(): SystemInfoProvider = JvmSystemInfoProvider()