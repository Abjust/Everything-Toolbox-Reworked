package com.msst.toolbox.model

data class BatteryInfo(
    val hasBattery: Boolean = false,
    val levelPercent: Float = 0f,
    val capacityMah: Int? = null,
    val temperatureCelsius: Float? = null,
    val voltageV: Float? = null,
    val powerWatts: Float? = null,
    val isCharging: Boolean = false
)