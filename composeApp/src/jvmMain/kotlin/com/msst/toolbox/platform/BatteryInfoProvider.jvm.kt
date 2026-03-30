package com.msst.toolbox.platform

import com.msst.toolbox.model.BatteryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo

class JvmBatteryInfoProvider : BatteryInfoProvider {

    private val si = SystemInfo()

    override suspend fun getBatteryInfo(): BatteryInfo = withContext(Dispatchers.IO) {
        val sources = si.hardware.powerSources
        if (sources.isEmpty()) return@withContext BatteryInfo(hasBattery = false)

        val ps = sources[0]
        ps.updateAttributes()

        val level = (ps.remainingCapacityPercent * 100.0).toFloat()
        val voltV = if (ps.voltage > 0) ps.voltage.toFloat() else null
        val tempC = if (ps.temperature > 0) ps.temperature.toFloat() else null
        val powerW = if (ps.powerUsageRate != 0.0) ps.powerUsageRate.toFloat().let { if (it < 0) -it else it } else null
        // designCapacity is in µWh, convert to mAh: (µWh / voltage_µV) → too complex; use µWh / 3600 as rough mAh if ~3.7V assumed
        val capacityMah = if (ps.designCapacity > 0) (ps.designCapacity / 3600).toInt() else null

        BatteryInfo(
            hasBattery = true,
            levelPercent = level,
            capacityMah = capacityMah,
            temperatureCelsius = tempC,
            voltageV = voltV,
            powerWatts = powerW,
            isCharging = ps.isCharging
        )
    }
}

actual fun createBatteryInfoProvider(): BatteryInfoProvider = JvmBatteryInfoProvider()