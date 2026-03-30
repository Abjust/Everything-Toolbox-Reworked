package com.msst.toolbox.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msst.toolbox.model.NetworkInfo
import com.msst.toolbox.model.NetworkInterfaceItem
import com.msst.toolbox.model.NetworkType
import com.msst.toolbox.platform.createNetworkInfoProvider
import com.msst.toolbox.platform.rememberLocationPermState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkInfoScreen(
    onBack: () -> Unit,
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    val provider = remember { createNetworkInfoProvider() }
    var info by remember { mutableStateOf(NetworkInfo()) }
    val locationPerm = rememberLocationPermState()

    // Interface selector state (for desktop)
    val availableInterfaces = remember { provider.getAvailableInterfaces() }
    var selectedIf by remember { mutableStateOf<NetworkInterfaceItem?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIf) {
        provider.setSelectedInterface(selectedIf?.name)
    }

    LaunchedEffect(selectedIf, locationPerm.isGranted) {
        while (true) {
            info = provider.getNetworkInfo()
            delay(5000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        TopAppBar(
            title = { Text("网络信息") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Interface selector (desktop only, when >1 interface available)
            if (availableInterfaces.size > 1) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("选择网络接口", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded,
                                onExpandedChange = { dropdownExpanded = it },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = selectedIf?.displayName ?: "自动（${info.name.ifEmpty { "—" }}）",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("自动") },
                                        onClick = { selectedIf = null; dropdownExpanded = false }
                                    )
                                    availableInterfaces.forEach { iface ->
                                        DropdownMenuItem(
                                            text = { Text("${iface.displayName} (${iface.name})") },
                                            onClick = { selectedIf = iface; dropdownExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Location permission banner (Android WiFi SSID)
            if (!locationPerm.isGranted && info.type == NetworkType.WIFI) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.LocationOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("需要位置权限才能显示 Wi-Fi 名称", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Button(onClick = locationPerm.requestPermission) { Text("授予") }
                        }
                    }
                }
            }

            item { NetworkTypeCard(info) }

            if (info.type == NetworkType.WIFI && info.wifi != null) {
                item {
                    NetSectionCard(title = "Wi-Fi 详情", icon = Icons.Default.SignalWifi4Bar) {
                        val w = info.wifi!!
                        InfoRow("SSID", w.ssid.ifEmpty { if (!locationPerm.isGranted) "需要位置权限" else "—" })
                        InfoRow("BSSID", w.bssid.ifEmpty { "—" })
                        InfoRow("信号强度", if (w.rssiDbm != 0) "${w.rssiDbm} dBm" else "—")
                        InfoRow("链路速率", if (w.linkSpeedMbps > 0) "${w.linkSpeedMbps} Mbps" else "—")
                        InfoRow("频率", if (w.frequencyMHz > 0) "${w.frequencyMHz} MHz" else "—")
                        InfoRow("信道", if (w.channelNumber > 0) "${w.channelNumber}" else "—")
                        InfoRow("Wi-Fi 标准", w.standardName.ifEmpty { "—" })
                    }
                }
            }
            if (info.type == NetworkType.MOBILE && info.mobile != null) {
                item {
                    NetSectionCard(title = "移动网络详情", icon = Icons.Default.NetworkCell) {
                        val m = info.mobile!!
                        InfoRow("运营商", m.operatorName.ifEmpty { "—" })
                        InfoRow("网络类型", m.technology.ifEmpty { "—" })
                        m.rssiDbm?.let { InfoRow("信号强度", "$it dBm") }
                    }
                }
            }
            item {
                NetSectionCard(title = "IP 地址", icon = Icons.Default.Lan) {
                    val a = info.addresses
                    InfoRow("IPv4", a.ipv4.ifEmpty { "—" })
                    InfoRow("子网掩码", a.subnetMask.ifEmpty { "—" })
                    if (a.ipv6List.isNotEmpty()) {
                        a.ipv6List.forEachIndexed { i, ip -> InfoRow(if (i == 0) "IPv6" else "", ip) }
                    } else {
                        InfoRow("IPv6", "—")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow("网关", a.gateway.ifEmpty { "—" })
                    InfoRow("DHCP 服务器", a.dhcpServer.ifEmpty { "—" })
                    if (a.dns4List.isNotEmpty()) {
                        a.dns4List.forEachIndexed { i, dns -> InfoRow(if (i == 0) "DNS (IPv4)" else "", dns) }
                    } else {
                        InfoRow("DNS (IPv4)", "—")
                    }
                    if (a.dns6List.isNotEmpty()) {
                        a.dns6List.forEachIndexed { i, dns -> InfoRow(if (i == 0) "DNS (IPv6)" else "", dns) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkTypeCard(info: NetworkInfo) {
    val (icon, label) = when (info.type) {
        NetworkType.WIFI -> Icons.Default.SignalWifi4Bar to "Wi-Fi"
        NetworkType.MOBILE -> Icons.Default.NetworkCell to "移动网络"
        NetworkType.ETHERNET -> Icons.Default.Lan to "以太网"
        NetworkType.NONE -> Icons.Default.WifiOff to "无网络连接"
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            if (info.type != NetworkType.NONE && info.name.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                InfoRow("接口名称", info.name)
            }
        }
    }
}

@Composable
private fun NetSectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            content()
        }
    }
}