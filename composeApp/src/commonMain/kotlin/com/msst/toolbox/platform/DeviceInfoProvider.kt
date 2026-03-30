package com.msst.toolbox.platform

import com.msst.toolbox.model.DeviceInfo

interface DeviceInfoProvider {
    suspend fun getDeviceInfo(): DeviceInfo
}

expect fun createDeviceInfoProvider(): DeviceInfoProvider