package com.msst.toolbox.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class ToolDest { LIST, HARDWARE, BATTERY, NETWORK, SYSTEM_STATUS }

@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    var current by remember { mutableStateOf(ToolDest.LIST) }

    when (current) {
        ToolDest.LIST -> ToolListScreen(modifier, paddingValues) { current = it }
        ToolDest.HARDWARE -> HardwareInfoScreen(onBack = { current = ToolDest.LIST }, paddingValues = paddingValues)
        ToolDest.BATTERY -> BatteryInfoScreen(onBack = { current = ToolDest.LIST }, paddingValues = paddingValues)
        ToolDest.NETWORK -> NetworkInfoScreen(onBack = { current = ToolDest.LIST }, paddingValues = paddingValues)
        ToolDest.SYSTEM_STATUS -> SystemStatusToolScreen(onBack = { current = ToolDest.LIST }, paddingValues = paddingValues)
    }
}

@Composable
private fun ToolListScreen(
    modifier: Modifier,
    paddingValues: PaddingValues,
    onNavigate: (ToolDest) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ToolItem(Icons.Default.Speed, "系统状态", "CPU、内存、磁盘、网络实时折线图") { onNavigate(ToolDest.SYSTEM_STATUS) }
        }
        item {
            ToolItem(Icons.Default.Memory, "硬件信息", "CPU、GPU、屏幕、内存、存储") { onNavigate(ToolDest.HARDWARE) }
        }
        item {
            ToolItem(Icons.Default.BatteryFull, "电池信息", "电量、容量、温度、电压、功率") { onNavigate(ToolDest.BATTERY) }
        }
        item {
            ToolItem(Icons.Default.Wifi, "网络信息", "类型、SSID、IP、DNS、网关") { onNavigate(ToolDest.NETWORK) }
        }
    }
}

@Composable
private fun ToolItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}