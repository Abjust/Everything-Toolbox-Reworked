package com.msst.toolbox.platform

import com.msst.toolbox.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo

class JvmNetworkInfoProvider : NetworkInfoProvider {

    private val si = SystemInfo()
    private var selectedInterfaceName: String? = null

    override fun getAvailableInterfaces(): List<NetworkInterfaceItem> {
        return try {
            si.hardware.networkIFs.mapNotNull { nif ->
                val jif = nif.queryNetworkInterface() ?: return@mapNotNull null
                if (jif.isLoopback) return@mapNotNull null
                val displayName = nif.displayName.ifBlank { nif.name }
                NetworkInterfaceItem(nif.name, displayName)
            }
        } catch (_: Exception) { emptyList() }
    }

    override fun setSelectedInterface(name: String?) {
        selectedInterfaceName = name
    }

    override suspend fun getNetworkInfo(): NetworkInfo = withContext(Dispatchers.IO) {
        val netIfs = si.hardware.networkIFs

        val activeIf = if (selectedInterfaceName != null) {
            netIfs.firstOrNull { it.name == selectedInterfaceName }
                ?.takeIf { it.queryNetworkInterface()?.isUp == true }
        } else {
            netIfs.firstOrNull { nif ->
                val jif = nif.queryNetworkInterface()
                jif != null && !jif.isLoopback && jif.isUp &&
                        (nif.iPv4addr.isNotEmpty() || nif.iPv6addr.isNotEmpty())
            }
        } ?: return@withContext NetworkInfo(type = NetworkType.NONE)

        val javaIf = activeIf.queryNetworkInterface()
        val ifType = activeIf.ifType
        val type = when {
            ifType.toLong() == 71L -> NetworkType.WIFI
            javaIf?.name?.startsWith("wl", ignoreCase = true) == true -> NetworkType.WIFI
            else -> NetworkType.ETHERNET
        }

        val ipv4 = activeIf.iPv4addr.firstOrNull() ?: ""
        val mask = activeIf.subnetMasks.firstOrNull()?.let { prefixToMask(it.toInt()) } ?: ""
        val ipv6List = activeIf.iPv6addr.toList()

        val osNet = si.operatingSystem.networkParams
        val gateway = osNet.ipv4DefaultGateway ?: ""
        val dnsServers = osNet.dnsServers?.toList() ?: emptyList()
        val dns4 = dnsServers.filter { isIPv4(it) }
        val dns6 = dnsServers.filter { isIPv6(it) }

        val addresses = NetworkAddresses(
            ipv4 = ipv4,
            subnetMask = mask,
            ipv6List = ipv6List,
            gateway = gateway,
            dhcpServer = "",
            dns4List = dns4,
            dns6List = dns6
        )

        NetworkInfo(
            type = type,
            name = activeIf.displayName.ifBlank { activeIf.name },
            wifi = null,
            mobile = null,
            addresses = addresses
        )
    }

    private fun prefixToMask(prefix: Int): String {
        if (prefix <= 0) return "0.0.0.0"
        val mask = if (prefix >= 32) -1 else (-1 shl (32 - prefix))
        return "${(mask ushr 24) and 0xFF}.${(mask ushr 16) and 0xFF}.${(mask ushr 8) and 0xFF}.${mask and 0xFF}"
    }

    private fun isIPv4(addr: String) = addr.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"))
    private fun isIPv6(addr: String) = addr.contains(":")
}

actual fun createNetworkInfoProvider(): NetworkInfoProvider = JvmNetworkInfoProvider()