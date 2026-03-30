package com.msst.toolbox.model

data class CpuInfo(
    val model: String = "",
    val architecture: String = "",
    val physicalCores: Int = 0,
    val logicalCores: Int = 0,
    val maxFrequencyMHz: Long = 0
)

data class GpuInfo(
    val name: String = "",
    val vendor: String = "",
    val vramBytes: Long = 0
)

data class ScreenInfo(
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val refreshRateHz: Float = 0f,
    val ppiX: Float = 0f,
    val ppiY: Float = 0f
)

data class MemoryInfo(
    val totalBytes: Long = 0,
    val availableBytes: Long = 0,
    val swapTotalBytes: Long = 0,
    val swapUsedBytes: Long = 0
)

data class DiskPartition(
    val mountPoint: String = "",
    val label: String = "",
    val totalBytes: Long = 0,
    val freeBytes: Long = 0
)

data class HardwareInfo(
    val cpu: CpuInfo = CpuInfo(),
    val gpu: GpuInfo? = null,
    val screen: ScreenInfo = ScreenInfo(),
    val memory: MemoryInfo = MemoryInfo(),
    val partitions: List<DiskPartition> = emptyList()
)