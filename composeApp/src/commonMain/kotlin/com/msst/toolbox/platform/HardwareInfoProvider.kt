package com.msst.toolbox.platform

import com.msst.toolbox.model.HardwareInfo

interface HardwareInfoProvider {
    suspend fun getHardwareInfo(): HardwareInfo
}

expect fun createHardwareInfoProvider(): HardwareInfoProvider