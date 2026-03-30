package com.msst.toolbox.platform

import com.msst.toolbox.model.SystemStatsSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo

class JvmSystemStatsProvider : SystemStatsProvider {

    private val si = SystemInfo()
    private val hal = si.hardware
    private val proc = hal.processor
    private var prevCpuTicks: Array<LongArray>? = null
    private var prevDiskRead = 0L
    private var prevDiskWrite = 0L
    private var prevNetRx = 0L
    private var prevNetTx = 0L
    private var prevTimestamp = 0L

    override suspend fun sample(): SystemStatsSample = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val elapsedSec = if (prevTimestamp > 0) (now - prevTimestamp) / 1000.0 else 1.0
        prevTimestamp = now

        // Per-core CPU load
        val currentTicks = proc.processorCpuLoadTicks
        val cpuLoads: List<Float> = if (prevCpuTicks != null) {
            proc.getProcessorCpuLoadBetweenTicks(prevCpuTicks).map { (it * 100.0).toFloat().coerceIn(0f, 100f) }
        } else {
            List(proc.logicalProcessorCount) { 0f }
        }
        prevCpuTicks = currentTicks

        // Per-core frequencies (Hz → MHz)
        val freqsMHz: List<Float> = try {
            proc.currentFreq.map { (it / 1_000_000.0).toFloat() }
        } catch (_: Exception) { emptyList() }

        // Temperatures
        val cpuTemp = try {
            hal.sensors.cpuTemperature.toFloat().takeIf { it > 0f }
        } catch (_: Exception) { null }

        // Memory
        val mem = hal.memory
        val memTotalMb = mem.total / (1024f * 1024f)
        val memUsedMb = (mem.total - mem.available) / (1024f * 1024f)
        val swapTotalMb = mem.virtualMemory.swapTotal / (1024f * 1024f)
        val swapUsedMb = mem.virtualMemory.swapUsed / (1024f * 1024f)

        // Disk I/O (aggregate all physical drives)
        val diskStores = hal.diskStores
        diskStores.forEach { it.updateAttributes() }
        val totalRead = diskStores.sumOf { it.readBytes }
        val totalWrite = diskStores.sumOf { it.writeBytes }
        val diskReadMbps = if (prevDiskRead > 0 && elapsedSec > 0)
            ((totalRead - prevDiskRead) / elapsedSec / (1024 * 1024)).toFloat().coerceAtLeast(0f)
        else 0f
        val diskWriteMbps = if (prevDiskWrite > 0 && elapsedSec > 0)
            ((totalWrite - prevDiskWrite) / elapsedSec / (1024 * 1024)).toFloat().coerceAtLeast(0f)
        else 0f
        prevDiskRead = totalRead
        prevDiskWrite = totalWrite

        // Network speed
        val netIfs = hal.networkIFs.filter { it.queryNetworkInterface()?.isLoopback == false }
        netIfs.forEach { it.updateAttributes() }
        val totalRx = netIfs.sumOf { it.bytesRecv }
        val totalTx = netIfs.sumOf { it.bytesSent }
        val netRxMbps = if (prevNetRx > 0 && elapsedSec > 0)
            ((totalRx - prevNetRx) * 8 / elapsedSec / (1024 * 1024)).toFloat().coerceAtLeast(0f)
        else 0f
        val netTxMbps = if (prevNetTx > 0 && elapsedSec > 0)
            ((totalTx - prevNetTx) * 8 / elapsedSec / (1024 * 1024)).toFloat().coerceAtLeast(0f)
        else 0f
        prevNetRx = totalRx
        prevNetTx = totalTx

        SystemStatsSample(
            timestamp = now,
            perCoreCpuPercent = cpuLoads,
            perCoreCpuFreqMHz = freqsMHz,
            gpuUsagePercent = null,  // OSHI 6.x doesn't expose GPU usage
            cpuTemperatureCelsius = cpuTemp,
            gpuTemperatureCelsius = null,
            memUsedMb = memUsedMb,
            swapUsedMb = swapUsedMb,
            memTotalMb = memTotalMb,
            swapTotalMb = swapTotalMb,
            diskReadMbps = diskReadMbps,
            diskWriteMbps = diskWriteMbps,
            netRxMbps = netRxMbps,
            netTxMbps = netTxMbps
        )
    }
}

actual fun createSystemStatsProvider(): SystemStatsProvider = JvmSystemStatsProvider()