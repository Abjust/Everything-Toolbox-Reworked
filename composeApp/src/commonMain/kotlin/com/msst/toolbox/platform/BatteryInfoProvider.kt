package com.msst.toolbox.platform

import com.msst.toolbox.model.BatteryInfo

interface BatteryInfoProvider {
    suspend fun getBatteryInfo(): BatteryInfo
}

expect fun createBatteryInfoProvider(): BatteryInfoProvider