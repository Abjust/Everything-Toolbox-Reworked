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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
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
import com.msst.toolbox.model.BatteryInfo
import com.msst.toolbox.platform.createBatteryInfoProvider
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryInfoScreen(
    onBack: () -> Unit,
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    val provider = remember { createBatteryInfoProvider() }
    var info by remember { mutableStateOf(BatteryInfo()) }

    LaunchedEffect(Unit) {
        while (true) {
            info = provider.getBatteryInfo()
            delay(3000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        TopAppBar(
            title = { Text("电池信息") },
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
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (info.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("电池", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        if (!info.hasBattery) {
                            Text("此设备无电池", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            InfoRow("电量", "${"%.1f".format(info.levelPercent)}%")
                            InfoRow("充电状态", if (info.isCharging) "充电中" else "未充电")
                            InfoRow("电池容量", info.capacityMah?.let { "${it} mAh" } ?: "—")
                            InfoRow("温度", info.temperatureCelsius?.let { "${"%.1f".format(it)} °C" } ?: "—")
                            InfoRow("电压", info.voltageV?.let { "${"%.3f".format(it)} V" } ?: "—")
                            InfoRow(
                                if (info.isCharging) "充电功率" else "放电功率",
                                info.powerWatts?.let { "${"%.2f".format(it)} W" } ?: "—"
                            )
                        }
                    }
                }
            }
        }
    }
}