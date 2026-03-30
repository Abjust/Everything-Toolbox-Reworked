package com.msst.toolbox.model

data class SystemStatus(
    val cpuUsagePercent: Float = 0f,
    val memoryUsagePercent: Float = 0f,
    val swapUsagePercent: Float = 0f,
    val cpuTemperatureCelsius: Float? = null,
    val gpuTemperatureCelsius: Float? = null
)