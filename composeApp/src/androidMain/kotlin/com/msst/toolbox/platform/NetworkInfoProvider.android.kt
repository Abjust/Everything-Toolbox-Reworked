package com.msst.toolbox.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import com.msst.toolbox.ToolboxApplication
import com.msst.toolbox.model.*
import com.msst.toolbox.model.NetworkInterfaceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

class AndroidNetworkInfoProvider(private val context: Context) : NetworkInfoProvider {

    override fun getAvailableInterfaces(): List<NetworkInterfaceItem> = emptyList()
    override fun setSelectedInterface(name: String?) {}

    override suspend fun getNetworkInfo(): NetworkInfo = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return@withContext NetworkInfo(type = NetworkType.NONE)
        val caps = cm.getNetworkCapabilities(network) ?: return@withContext NetworkInfo(type = NetworkType.NONE)

        return@withContext when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> buildWifiInfo(cm, network)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> buildMobileInfo(cm, network)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> buildEthernetInfo(cm, network)
            else -> NetworkInfo(type = NetworkType.NONE)
        }
    }

    private fun buildWifiInfo(cm: ConnectivityManager, network: android.net.Network): NetworkInfo {
        @Suppress("DEPRECATION")
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val wifiInfo = wm.connectionInfo

        val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: ""
        val bssid = wifiInfo.bssid ?: ""
        val rssi = wifiInfo.rssi
        val linkSpeed = wifiInfo.linkSpeed // Mbps
        val freqMHz = wifiInfo.frequency // MHz
        val channel = frequencyToChannel(freqMHz)
        val standard = getWifiStandard(wifiInfo)

        val lp = cm.getLinkProperties(network)
        val addresses = buildAddresses(lp)

        return NetworkInfo(
            type = NetworkType.WIFI,
            name = ssid,
            wifi = WifiDetails(
                ssid = ssid,
                bssid = bssid,
                rssiDbm = rssi,
                frequencyMHz = freqMHz,
                channelNumber = channel,
                standardName = standard,
                linkSpeedMbps = linkSpeed
            ),
            addresses = addresses
        )
    }

    @Suppress("DEPRECATION")
    private fun getWifiStandard(wifiInfo: android.net.wifi.WifiInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Integer values: 1=LEGACY, 4=11N, 5=11AC, 6=11AX, 7=11AX_6GHz, 8=11BE
            return when (wifiInfo.wifiStandard) {
                1 -> "802.11b/g"
                4 -> "Wi-Fi 4 (802.11n)"
                5 -> "Wi-Fi 5 (802.11ac)"
                6 -> "Wi-Fi 6 (802.11ax)"
                7 -> "Wi-Fi 6E (802.11ax)"
                8 -> "Wi-Fi 7 (802.11be)"
                else -> "未知"
            }
        }
        // Guess from frequency
        return when {
            wifiInfo.frequency > 5000 -> "802.11ac/ax"
            else -> "802.11n"
        }
    }

    private fun buildMobileInfo(cm: ConnectivityManager, network: android.net.Network): NetworkInfo {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val operatorName = tm.networkOperatorName ?: ""
        val technology = networkTypeName(tm.networkType)
        val lp = cm.getLinkProperties(network)
        val addresses = buildAddresses(lp)
        return NetworkInfo(
            type = NetworkType.MOBILE,
            name = operatorName,
            mobile = MobileDetails(operatorName = operatorName, technology = technology),
            addresses = addresses
        )
    }

    private fun buildEthernetInfo(cm: ConnectivityManager, network: android.net.Network): NetworkInfo {
        val lp = cm.getLinkProperties(network)
        val ifName = lp?.interfaceName ?: ""
        val addresses = buildAddresses(lp)
        return NetworkInfo(
            type = NetworkType.ETHERNET,
            name = ifName,
            addresses = addresses
        )
    }

    private fun buildAddresses(lp: android.net.LinkProperties?): NetworkAddresses {
        if (lp == null) return NetworkAddresses()
        var ipv4 = ""
        var mask = ""
        val ipv6List = mutableListOf<String>()
        for (li in lp.linkAddresses) {
            val addr = li.address
            when (addr) {
                is Inet4Address -> {
                    ipv4 = addr.hostAddress ?: ""
                    mask = prefixToMask(li.prefixLength)
                }
                is Inet6Address -> ipv6List.add(addr.hostAddress?.removePrefix("/") ?: "")
            }
        }
        val gateway = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress ?: ""
        val dhcp = lp.dhcpServerAddress?.hostAddress ?: ""
        val dnsAddrs = lp.dnsServers
        val dns4 = dnsAddrs.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
        val dns6 = dnsAddrs.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress?.removePrefix("/") }

        return NetworkAddresses(
            ipv4 = ipv4,
            subnetMask = mask,
            ipv6List = ipv6List,
            gateway = gateway,
            dhcpServer = dhcp,
            dns4List = dns4,
            dns6List = dns6
        )
    }

    private fun prefixToMask(prefix: Int): String {
        if (prefix <= 0) return "0.0.0.0"
        val mask = if (prefix >= 32) -1 else (-1 shl (32 - prefix))
        return "${(mask ushr 24) and 0xFF}.${(mask ushr 16) and 0xFF}.${(mask ushr 8) and 0xFF}.${mask and 0xFF}"
    }

    private fun frequencyToChannel(freq: Int): Int {
        return when {
            freq in 2412..2484 -> (freq - 2412) / 5 + 1
            freq in 5170..5825 -> (freq - 5170) / 5 + 34
            freq in 5955..7115 -> (freq - 5955) / 5 + 1
            else -> 0
        }
    }

    @Suppress("DEPRECATION")
    private fun networkTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT -> "2G"
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
        TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
        else -> "未知"
    }
}

actual fun createNetworkInfoProvider(): NetworkInfoProvider =
    AndroidNetworkInfoProvider(ToolboxApplication.instance)