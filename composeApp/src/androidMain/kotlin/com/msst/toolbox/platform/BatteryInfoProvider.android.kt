package com.msst.toolbox.platform

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.msst.toolbox.ToolboxApplication
import com.msst.toolbox.model.BatteryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidBatteryInfoProvider(private val context: Context) : BatteryInfoProvider {

    override suspend fun getBatteryInfo(): BatteryInfo = withContext(Dispatchers.IO) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return@withContext BatteryInfo(hasBattery = false)

        val present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
        if (!present) return@withContext BatteryInfo(hasBattery = false)

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val levelPercent = if (level >= 0 && scale > 0) level.toFloat() / scale * 100f else 0f

        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val tempC = if (tempRaw >= 0) tempRaw / 10f else null

        val voltageRaw = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val voltageV = if (voltageRaw > 0) voltageRaw / 1000f else null

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val currentUa = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val powerW = if (currentUa != Long.MIN_VALUE && voltageV != null && currentUa != 0L) {
            // current in µA, voltage in V → power = V × I(A)
            (voltageV * currentUa.toFloat() / 1_000_000f).let { if (it < 0) -it else it }
        } else null

        val capacityMah = readCapacityMah(bm)

        BatteryInfo(
            hasBattery = true,
            levelPercent = levelPercent,
            capacityMah = capacityMah,
            temperatureCelsius = tempC,
            voltageV = voltageV,
            powerWatts = powerW,
            isCharging = isCharging
        )
    }

    private fun readCapacityMah(bm: BatteryManager): Int? {
        // 1. BatteryManager property (API 28+)
        val cap = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (cap != Long.MIN_VALUE && cap > 0) return (cap / 1000).toInt() // µAh -> mAh

        // 2. /sys/class/power_supply/battery/charge_full_design
        for (path in listOf(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/Battery/charge_full_design"
        )) {
            try {
                val v = File(path).readText().trim().toLongOrNull() ?: continue
                if (v > 0) return (v / 1000).toInt() // µAh -> mAh
            } catch (_: Exception) {}
        }
        return null
    }
}

actual fun createBatteryInfoProvider(): BatteryInfoProvider =
    AndroidBatteryInfoProvider(ToolboxApplication.instance)