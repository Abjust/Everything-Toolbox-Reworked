package com.msst.toolbox.model

data class SystemStatsSample(
    val timestamp: Long = System.currentTimeMillis(),
    val perCoreCpuPercent: List<Float> = emptyList(),    // 0-100% per logical core
    val perCoreCpuFreqMHz: List<Float> = emptyList(),   // current freq per core in MHz
    val gpuUsagePercent: Float? = null,
    val cpuTemperatureCelsius: Float? = null,
    val gpuTemperatureCelsius: Float? = null,
    val memUsedMb: Float = 0f,
    val swapUsedMb: Float = 0f,
    val memTotalMb: Float = 0f,
    val swapTotalMb: Float = 0f,
    val diskReadMbps: Float = 0f,    // desktop only
    val diskWriteMbps: Float = 0f,   // desktop only
    val netRxMbps: Float = 0f,
    val netTxMbps: Float = 0f
)