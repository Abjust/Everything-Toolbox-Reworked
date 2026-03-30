package com.msst.toolbox.model

enum class NetworkType { ETHERNET, WIFI, MOBILE, NONE }

data class NetworkInterfaceItem(val name: String, val displayName: String)

data class WifiDetails(
    val ssid: String = "",
    val bssid: String = "",
    val rssiDbm: Int = 0,
    val frequencyMHz: Int = 0,
    val channelNumber: Int = 0,
    val standardName: String = "",
    val linkSpeedMbps: Int = 0
)

data class MobileDetails(
    val operatorName: String = "",
    val technology: String = "",
    val rssiDbm: Int? = null
)

data class NetworkAddresses(
    val ipv4: String = "",
    val subnetMask: String = "",
    val ipv6List: List<String> = emptyList(),
    val gateway: String = "",
    val dhcpServer: String = "",
    val dns4List: List<String> = emptyList(),
    val dns6List: List<String> = emptyList()
)

data class NetworkInfo(
    val type: NetworkType = NetworkType.NONE,
    val name: String = "",
    val wifi: WifiDetails? = null,
    val mobile: MobileDetails? = null,
    val addresses: NetworkAddresses = NetworkAddresses()
)