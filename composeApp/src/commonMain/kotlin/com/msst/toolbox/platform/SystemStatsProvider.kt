package com.msst.toolbox.platform

import com.msst.toolbox.model.SystemStatsSample

interface SystemStatsProvider {
    suspend fun sample(): SystemStatsSample
}

expect fun createSystemStatsProvider(): SystemStatsProvider