package com.msst.toolbox.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msst.toolbox.model.SystemStatsSample
import com.msst.toolbox.platform.createSystemStatsProvider
import com.msst.toolbox.ui.tools.chart.MultiLineChart
import com.msst.toolbox.ui.tools.chart.SimpleLineChart
import com.msst.toolbox.ui.tools.chart.StackedAreaChart
import kotlinx.coroutines.delay

private const val MAX_HISTORY = 120 // 2 minutes at 1s interval

private val coreColors = listOf(
    Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFE91E63),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722), Color(0xFF795548),
    Color(0xFF607D8B), Color(0xFF8BC34A), Color(0xFFFFC107), Color(0xFF03A9F4),
    Color(0xFF673AB7), Color(0xFF009688), Color(0xFFCDDC39), Color(0xFFF44336)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemStatusToolScreen(
    onBack: () -> Unit,
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    val provider = remember { createSystemStatsProvider() }
    var history by remember { mutableStateOf(listOf<SystemStatsSample>()) }

    LaunchedEffect(Unit) {
        provider.sample()  // baseline for delta calculations
        delay(1000L)
        while (true) {
            val s = provider.sample()
            history = (history + s).takeLast(MAX_HISTORY)
            delay(1000L)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        TopAppBar(
            title = { Text("系统状态") },
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

        if (history.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("正在采集数据...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        val timestamps = history.map { it.timestamp }
        val last = history.last()
        val numCores = history.maxOf { it.perCoreCpuPercent.size }
        val hasFreq = history.any { it.perCoreCpuFreqMHz.isNotEmpty() }
        val hasDisk = history.any { it.diskReadMbps > 0 || it.diskWriteMbps > 0 }
        val hasSwap = history.any { it.swapTotalMb > 0 }

        val cpuSeries = (0 until numCores).map { idx -> history.map { it.perCoreCpuPercent.getOrElse(idx) { 0f } } }
        val cpuColors = (0 until numCores).map { coreColors[it % coreColors.size] }
        val cpuLabels = (0 until numCores).map { "核心 $it" }
        val avgCpuPercent = last.perCoreCpuPercent.averageOrZero()

        val freqNumCores = if (hasFreq) history.maxOf { it.perCoreCpuFreqMHz.size } else 0
        val freqSeries = (0 until freqNumCores).map { idx -> history.map { it.perCoreCpuFreqMHz.getOrElse(idx) { 0f } } }
        val freqColors = (0 until freqNumCores).map { coreColors[it % coreColors.size] }
        val freqMaxMHz = if (hasFreq) history.flatMap { it.perCoreCpuFreqMHz }.maxOrNull()?.coerceAtLeast(1f) ?: 1f else 1f
        val avgFreqMHz = last.perCoreCpuFreqMHz.averageOrZero()
        val cpuTemp = last.cpuTemperatureCelsius

        val memSeries = buildList {
            add(history.map { it.memUsedMb })
            if (hasSwap) add(history.map { it.swapUsedMb })
        }
        val memColors = listOf(Color(0xFF2196F3), Color(0xFFFF9800))
        val memTotalMb = last.memTotalMb + last.swapTotalMb
        val memLabels = buildList {
            add("内存 (${formatMb(last.memTotalMb)})")
            if (hasSwap) add("Swap (${formatMb(last.swapTotalMb)})")
        }

        val netSeries = listOf(history.map { it.netRxMbps }, history.map { it.netTxMbps })
        val netMax = (history.flatMap { listOf(it.netRxMbps, it.netTxMbps) }.maxOrNull() ?: 0f).coerceAtLeast(1f)

        val diskSeries = listOf(history.map { it.diskReadMbps }, history.map { it.diskWriteMbps })
        val diskMax = (history.flatMap { listOf(it.diskReadMbps, it.diskWriteMbps) }.maxOrNull() ?: 0f).coerceAtLeast(1f)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── CPU 占用率 ──────────────────────────────────────────────────
            item {
                ChartCard(title = "CPU 占用率（每核心）") {
                    MultiLineChart(
                        seriesValues = cpuSeries,
                        seriesColors = cpuColors,
                        seriesLabels = cpuLabels,
                        maxValue = 100f,
                        unit = "%",
                        timestamps = timestamps,
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    )
                    // Summary stat (req 3)
                    ChartStat("平均占用率", "${"%.1f".format(avgCpuPercent)}%")
                }
            }

            // ── CPU 频率 ────────────────────────────────────────────────────
            if (hasFreq) {
                item {
                    ChartCard(title = "CPU 频率（每核心）") {
                        MultiLineChart(
                            seriesValues = freqSeries,
                            seriesColors = freqColors,
                            seriesLabels = (0 until freqNumCores).map { "核心 $it" },
                            maxValue = freqMaxMHz,
                            unit = "MHz",
                            timestamps = timestamps,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                        // Summary stats (req 2)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ChartStat(
                                label = "平均频率",
                                value = if (avgFreqMHz >= 1000f) "${"%.2f".format(avgFreqMHz / 1000f)} GHz"
                                        else "${"%.0f".format(avgFreqMHz)} MHz",
                                modifier = Modifier.weight(1f)
                            )
                            ChartStat(
                                label = "CPU 温度",
                                value = if (cpuTemp != null) "${"%.1f".format(cpuTemp)} °C" else "不可用",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── GPU 占用率 ──────────────────────────────────────────────────
            item {
                val gpuTemp = last.gpuTemperatureCelsius
                val gpuAvailable = history.any { it.gpuUsagePercent != null }
                ChartCard(title = "GPU 占用率") {
                    SimpleLineChart(
                        values = history.map { it.gpuUsagePercent ?: 0f },
                        maxValue = 100f,
                        lineColor = Color(0xFF9C27B0),
                        unit = "%",
                        label = if (gpuAvailable) "GPU" else "GPU（不可用）",
                        timestamps = timestamps,
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    ChartStat(
                        label = "GPU 温度",
                        value = if (gpuTemp != null) "${"%.1f".format(gpuTemp)} °C" else "不可用"
                    )
                }
            }

            // ── 内存与 Swap ─────────────────────────────────────────────────
            item {
                ChartCard(title = "内存与 Swap 占用") {
                    StackedAreaChart(
                        seriesValues = memSeries,
                        seriesColors = memColors,
                        seriesLabels = memLabels,
                        maxValue = memTotalMb,
                        unit = "MB",
                        timestamps = timestamps,
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                }
            }

            // ── 磁盘读写（桌面端） ───────────────────────────────────────────
            if (hasDisk) {
                item {
                    ChartCard(title = "磁盘读写速度") {
                        StackedAreaChart(
                            seriesValues = diskSeries,
                            seriesColors = listOf(Color(0xFF4CAF50), Color(0xFFFF5722)),
                            seriesLabels = listOf("读取", "写入"),
                            maxValue = diskMax * 2,
                            unit = "MB/s",
                            timestamps = timestamps,
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        )
                    }
                }
            }

            // ── 网络速度 ────────────────────────────────────────────────────
            item {
                ChartCard(title = "网络速度") {
                    StackedAreaChart(
                        seriesValues = netSeries,
                        seriesColors = listOf(Color(0xFF2196F3), Color(0xFFFF9800)),
                        seriesLabels = listOf("下行", "上行"),
                        maxValue = netMax * 2,
                        unit = "Mbps",
                        timestamps = timestamps,
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                }
            }
        }
    }
}

// ── Shared composables ──────────────────────────────────────────────────────

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            content()
        }
    }
}

@Composable
private fun ChartStat(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun List<Float>.averageOrZero(): Float =
    if (isEmpty()) 0f else (sum() / size)

private fun formatMb(mb: Float): String = when {
    mb >= 1024f -> "${"%.1f".format(mb / 1024f)} GB"
    else -> "${"%.0f".format(mb)} MB"
}