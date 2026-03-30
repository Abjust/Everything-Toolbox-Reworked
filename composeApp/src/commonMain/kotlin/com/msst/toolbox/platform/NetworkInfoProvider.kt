package com.msst.toolbox.platform

import com.msst.toolbox.model.NetworkInfo
import com.msst.toolbox.model.NetworkInterfaceItem

interface NetworkInfoProvider {
    suspend fun getNetworkInfo(): NetworkInfo
    fun getAvailableInterfaces(): List<NetworkInterfaceItem>
    fun setSelectedInterface(name: String?)
}

expect fun createNetworkInfoProvider(): NetworkInfoProvider