package com.msst.toolbox.platform

import com.msst.toolbox.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

class JvmHardwareInfoProvider : HardwareInfoProvider {

    private val si = SystemInfo()
    private val hal = si.hardware

    override suspend fun getHardwareInfo(): HardwareInfo = withContext(Dispatchers.IO) {
        HardwareInfo(
            cpu = getCpuInfo(),
            gpu = getGpuInfo(),
            screen = getScreenInfo(),
            memory = getMemoryInfo(),
            partitions = getDiskPartitions()
        )
    }

    private fun getCpuInfo(): CpuInfo {
        val proc = hal.processor
        val id = proc.processorIdentifier
        val maxFreq = proc.maxFreq / 1_000_000L  // Hz -> MHz
        return CpuInfo(
            model = id.name.trim(),
            architecture = id.microarchitecture.ifBlank { System.getProperty("os.arch", "") },
            physicalCores = proc.physicalProcessorCount,
            logicalCores = proc.logicalProcessorCount,
            maxFrequencyMHz = maxFreq
        )
    }

    private fun getGpuInfo(): GpuInfo? {
        val cards = hal.graphicsCards
        if (cards.isEmpty()) return null
        val card = cards[0]
        return GpuInfo(
            name = card.name.trim(),
            vendor = card.vendor.trim(),
            vramBytes = card.vRam
        )
    }

    private fun getScreenInfo(): ScreenInfo {
        return try {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val device = ge.defaultScreenDevice
            val mode = device.displayMode
            val dpi = try { Toolkit.getDefaultToolkit().screenResolution.toFloat() } catch (_: Exception) { 96f }
            ScreenInfo(
                widthPx = mode.width,
                heightPx = mode.height,
                refreshRateHz = mode.refreshRate.toFloat(),
                ppiX = dpi,
                ppiY = dpi
            )
        } catch (_: Exception) { ScreenInfo() }
    }

    private fun getMemoryInfo(): MemoryInfo {
        val mem = hal.memory
        return MemoryInfo(
            totalBytes = mem.total,
            availableBytes = mem.available,
            swapTotalBytes = mem.virtualMemory.swapTotal,
            swapUsedBytes = mem.virtualMemory.swapUsed
        )
    }

    private fun getDiskPartitions(): List<DiskPartition> {
        return try {
            si.operatingSystem.fileSystem.fileStores.map { fs ->
                DiskPartition(
                    mountPoint = fs.mount,
                    label = fs.label.ifBlank { fs.name },
                    totalBytes = fs.totalSpace,
                    freeBytes = fs.freeSpace
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}

actual fun createHardwareInfoProvider(): HardwareInfoProvider = JvmHardwareInfoProvider()