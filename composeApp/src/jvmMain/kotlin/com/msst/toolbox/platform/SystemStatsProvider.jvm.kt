package com.msst.toolbox.platform

import com.msst.toolbox.model.SystemStatsSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo
import java.io.File
import java.util.concurrent.TimeUnit

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

        val (gpuUsage, gpuTemp) = readGpuStats()

        SystemStatsSample(
            timestamp = now,
            perCoreCpuPercent = cpuLoads,
            perCoreCpuFreqMHz = freqsMHz,
            gpuUsagePercent = gpuUsage,
            cpuTemperatureCelsius = cpuTemp,
            gpuTemperatureCelsius = gpuTemp,
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
    // ── GPU stats ──────────────────────────────────────────────────────────────

    private val osName = System.getProperty("os.name").lowercase()
    private val isLinux = osName.contains("linux")
    private val isWindows = osName.contains("windows")

    // Cache Windows GPU results — typeperf takes ~200ms so avoid calling every second
    @Volatile private var cachedWinGpuUsage: Float? = null
    @Volatile private var cachedWinGpuTemp: Float? = null
    @Volatile private var lastWinGpuUpdate = 0L

    /** Returns (gpuUsagePercent, gpuTemperatureCelsius). */
    private fun readGpuStats(): Pair<Float?, Float?> {
        if (isLinux) {
            val amd = readAmdGpuStatsLinux()
            if (amd.first != null || amd.second != null) return amd
            return readNvidiaSmi()
        }
        if (isWindows) {
            return readGpuStatsWindows()
        }
        return null to null
    }

    // ── Linux ─────────────────────────────────────────────────────────────────

    private fun readAmdGpuStatsLinux(): Pair<Float?, Float?> {
        val cards = File("/sys/class/drm")
            .listFiles { f -> f.name.matches(Regex("card\\d+")) }
            ?.sortedBy { it.name } ?: return null to null

        var usage: Float? = null
        var temp: Float? = null
        for (card in cards) {
            val dev = File("${card.path}/device").takeIf { it.exists() } ?: continue
            if (usage == null) usage = try {
                File("$dev/gpu_busy_percent").readText().trim().toFloatOrNull()
            } catch (_: Exception) { null }
            if (temp == null) try {
                File("$dev/hwmon").listFiles()?.forEach { hwmon ->
                    if (temp != null) return@forEach
                    temp = File("${hwmon.path}/temp1_input").readText().trim()
                        .toLongOrNull()?.let { it / 1000f }
                }
            } catch (_: Exception) {}
            if (usage != null && temp != null) break
        }
        return usage to temp
    }

    // ── Windows ───────────────────────────────────────────────────────────────

    private fun readGpuStatsWindows(): Pair<Float?, Float?> {
        val now = System.currentTimeMillis()
        if (now - lastWinGpuUpdate < 2000L) {
            return cachedWinGpuUsage to cachedWinGpuTemp
        }
        // GPU usage via Windows Performance Counters — works for AMD, NVIDIA, Intel
        val usage = readWindowsGpuUsageTypeperf()
        // Temperature: try NVIDIA first, then AMD via LibreHardwareMonitor WMI
        val temp = readNvidiaGpuTempOnly() ?: readAmdGpuTempLhm()
        cachedWinGpuUsage = usage
        cachedWinGpuTemp = temp
        lastWinGpuUpdate = now
        return usage to temp
    }

    /**
     * Reads GPU 3D engine utilization via typeperf (Windows Performance Counters).
     * The WDDM driver exposes "\GPU Engine(*engtype_3D*)\Utilization Percentage"
     * for every GPU vendor. Values for all engine instances of phys_0 are summed
     * to approximate total GPU load.
     */
    private fun readWindowsGpuUsageTypeperf(): Float? {
        return try {
            val proc = ProcessBuilder(
                "typeperf",
                "\\GPU Engine(*engtype_3D*)\\Utilization Percentage",
                "-sc", "1", "-y"
            ).start()
            val finished = proc.waitFor(2000, TimeUnit.MILLISECONDS)
            if (!finished) { proc.destroyForcibly(); return null }
            if (proc.exitValue() != 0) return null
            val lines = proc.inputStream.bufferedReader().readLines()
            // First line is PDH header; second line has timestamp + values
            val dataLine = lines.firstOrNull { !it.startsWith("\"(PDH") && it.isNotBlank() }
                ?: return null
            // Sum utilization across all engine instances (approximate total load)
            val values = dataLine.split(",").drop(1)
                .mapNotNull { it.trim().removeSurrounding("\"").toFloatOrNull() }
            if (values.isEmpty()) null else values.sum().coerceIn(0f, 100f)
        } catch (_: Exception) { null }
    }

    /** nvidia-smi temperature only (usage already covered by typeperf). */
    private fun readNvidiaGpuTempOnly(): Float? {
        return try {
            val proc = ProcessBuilder(
                "nvidia-smi",
                "--query-gpu=temperature.gpu",
                "--format=csv,noheader,nounits"
            ).start()
            val finished = proc.waitFor(400, TimeUnit.MILLISECONDS)
            if (!finished) { proc.destroyForcibly(); return null }
            if (proc.exitValue() != 0) return null
            proc.inputStream.bufferedReader().readLine()?.trim()?.toFloatOrNull()
        } catch (_: Exception) { null }
    }

    /**
     * Reads AMD GPU temperature from LibreHardwareMonitor's WMI provider.
     * Only works if LibreHardwareMonitor is running with "Run as administrator"
     * and its WMI provider is enabled (default when run as admin).
     */
    private fun readAmdGpuTempLhm(): Float? {
        return try {
            val proc = ProcessBuilder(
                "wmic",
                "/namespace:\\\\root\\LibreHardwareMonitor",
                "path", "Sensor",
                "where", "SensorType='Temperature' and Name like '%GPU%'",
                "get", "Value",
                "/format:value"
            ).start()
            val finished = proc.waitFor(800, TimeUnit.MILLISECONDS)
            if (!finished) { proc.destroyForcibly(); return null }
            if (proc.exitValue() != 0) return null
            proc.inputStream.bufferedReader().readLines()
                .firstOrNull { it.startsWith("Value=") }
                ?.removePrefix("Value=")?.trim()?.toFloatOrNull()
        } catch (_: Exception) { null }
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    private fun readNvidiaSmi(): Pair<Float?, Float?> {
        return try {
            val proc = ProcessBuilder(
                "nvidia-smi",
                "--query-gpu=utilization.gpu,temperature.gpu",
                "--format=csv,noheader,nounits"
            ).start()
            val finished = proc.waitFor(400, TimeUnit.MILLISECONDS)
            if (!finished) { proc.destroyForcibly(); return null to null }
            if (proc.exitValue() != 0) return null to null
            val line = proc.inputStream.bufferedReader().readLine() ?: return null to null
            val parts = line.split(",").map { it.trim() }
            parts.getOrNull(0)?.toFloatOrNull() to parts.getOrNull(1)?.toFloatOrNull()
        } catch (_: Exception) { null to null }
    }
}

actual fun createSystemStatsProvider(): SystemStatsProvider = JvmSystemStatsProvider()