package com.msst.toolbox.platform

import com.msst.toolbox.model.SystemStatus

interface SystemInfoProvider {
    suspend fun getSystemStatus(): SystemStatus
}

expect fun createSystemInfoProvider(): SystemInfoProvider