package com.msst.toolbox.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msst.toolbox.model.DiskPartition
import com.msst.toolbox.model.GpuInfo
import com.msst.toolbox.model.HardwareInfo
import com.msst.toolbox.platform.createHardwareInfoProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareInfoScreen(
    onBack: () -> Unit,
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    val provider = remember { createHardwareInfoProvider() }
    var info by remember { mutableStateOf(HardwareInfo()) }

    LaunchedEffect(Unit) {
        info = provider.getHardwareInfo()
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        TopAppBar(
            title = { Text("硬件信息") },
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
            item {
                SectionCard(title = "处理器 (CPU)", icon = { Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary) }) {
                    val cpu = info.cpu
                    InfoRow("型号", cpu.model.ifEmpty { "获取中..." })
                    InfoRow("架构", cpu.architecture.ifEmpty { "—" })
                    InfoRow("物理核心", if (cpu.physicalCores > 0) "${cpu.physicalCores} 核" else "—")
                    InfoRow("逻辑核心", if (cpu.logicalCores > 0) "${cpu.logicalCores} 线程" else "—")
                    InfoRow("最高频率", if (cpu.maxFrequencyMHz > 0) "${cpu.maxFrequencyMHz} MHz" else "—")
                }
            }
            item {
                val gpu = info.gpu
                SectionCard(title = "图形处理器 (GPU)", icon = { Icon(Icons.Default.Tv, null, tint = MaterialTheme.colorScheme.primary) }) {
                    if (gpu != null) {
                        InfoRow("型号", gpu.name.ifEmpty { "—" })
                        InfoRow("厂商", gpu.vendor.ifEmpty { "—" })
                        InfoRow("显存", if (gpu.vramBytes > 0) formatBytes(gpu.vramBytes) else "—")
                    } else {
                        Text("暂无信息", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SectionCard(title = "屏幕", icon = { Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary) }) {
                    val s = info.screen
                    InfoRow("分辨率", if (s.widthPx > 0) "${s.widthPx} × ${s.heightPx}" else "获取中...")
                    InfoRow("刷新率", if (s.refreshRateHz > 0) "${"%.0f".format(s.refreshRateHz)} Hz" else "—")
                    InfoRow("PPI", if (s.ppiX > 0) "${"%.1f".format(s.ppiX)}" else "—")
                }
            }
            item {
                SectionCard(title = "内存与交换", icon = { Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary) }) {
                    val m = info.memory
                    InfoRow("总内存", if (m.totalBytes > 0) formatBytes(m.totalBytes) else "获取中...")
                    InfoRow("可用内存", if (m.availableBytes > 0) formatBytes(m.availableBytes) else "—")
                    InfoRow("Swap 总量", if (m.swapTotalBytes > 0) formatBytes(m.swapTotalBytes) else "无")
                    if (m.swapTotalBytes > 0) {
                        InfoRow("Swap 已用", formatBytes(m.swapUsedBytes))
                    }
                }
            }
            item {
                SectionCard(title = "存储分区", icon = { Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary) }) {
                    if (info.partitions.isEmpty()) {
                        Text("获取中...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        info.partitions.forEachIndexed { index, partition ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            PartitionSection(partition)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartitionSection(p: DiskPartition) {
    val used = p.totalBytes - p.freeBytes
    val usedPct = if (p.totalBytes > 0) used.toFloat() / p.totalBytes * 100f else 0f
    InfoRow("挂载点", p.mountPoint)
    if (p.label.isNotEmpty() && p.label != p.mountPoint) InfoRow("标签", p.label)
    InfoRow("总容量", formatBytes(p.totalBytes))
    InfoRow("已用", "${formatBytes(used)} (${"%.1f".format(usedPct)}%)")
    InfoRow("可用", formatBytes(p.freeBytes))
}

@Composable
private fun SectionCard(
    title: String,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon()
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            content()
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.38f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.62f))
    }
}

internal fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1L shl 40 -> "${"%.2f".format(bytes.toDouble() / (1L shl 40))} TB"
        bytes >= 1L shl 30 -> "${"%.2f".format(bytes.toDouble() / (1L shl 30))} GB"
        bytes >= 1L shl 20 -> "${"%.1f".format(bytes.toDouble() / (1L shl 20))} MB"
        bytes >= 1L shl 10 -> "${"%.1f".format(bytes.toDouble() / (1L shl 10))} KB"
        else -> "$bytes B"
    }
}