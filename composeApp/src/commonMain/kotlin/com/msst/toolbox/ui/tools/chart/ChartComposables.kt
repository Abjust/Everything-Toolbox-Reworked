package com.msst.toolbox.ui.tools.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

// ── Formatting helpers ──────────────────────────────────────────────────────

private fun formatAxisLabel(value: Float, unit: String): String = when (unit) {
    "%" -> "${"%.0f".format(value)}%"
    "MB" -> if (value >= 1024f) "${"%.1f".format(value / 1024f)} GB" else "${"%.0f".format(value)} MB"
    "MHz" -> if (value >= 1000f) "${"%.2f".format(value / 1000f)} GHz" else "${"%.0f".format(value)} MHz"
    "MB/s" -> if (value >= 1024f) "${"%.1f".format(value / 1024f)} GB/s" else "${"%.1f".format(value)} MB/s"
    "Mbps" -> if (value >= 1000f) "${"%.2f".format(value / 1000f)} Gbps" else "${"%.1f".format(value)} Mbps"
    else -> when {
        value >= 1f -> "${"%.2f".format(value)} $unit"
        else -> "${"%.3f".format(value)} $unit"
    }
}

private fun formatValue(value: Float, unit: String): String = when (unit) {
    "%" -> "${"%.1f".format(value)}%"
    "MB" -> if (value >= 1024f) "${"%.2f".format(value / 1024f)} GB" else "${"%.1f".format(value)} MB"
    "MHz" -> if (value >= 1000f) "${"%.3f".format(value / 1000f)} GHz" else "${"%.0f".format(value)} MHz"
    "MB/s" -> if (value >= 1024f) "${"%.2f".format(value / 1024f)} GB/s" else "${"%.2f".format(value)} MB/s"
    "Mbps" -> if (value >= 1000f) "${"%.2f".format(value / 1000f)} Gbps" else "${"%.2f".format(value)} Mbps"
    else -> "${"%.2f".format(value)} $unit"
}

private fun formatTimestamp(ts: Long): String {
    val totalSec = ts / 1000L
    val h = (totalSec / 3600) % 24
    val m = (totalSec / 60) % 60
    val s = totalSec % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

// ── Legend (balanced rows) ──────────────────────────────────────────────────

@Composable
private fun BalancedLegend(labels: List<String>, colors: List<Color>) {
    if (labels.isEmpty()) return
    val maxPerRow = 4
    val numRows = ceil(labels.size.toDouble() / maxPerRow).toInt().coerceAtLeast(1)
    val itemsPerRow = ceil(labels.size.toDouble() / numRows).toInt()

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (rowStart in labels.indices step itemsPerRow) {
            val rowEnd = minOf(rowStart + itemsPerRow, labels.size)
            Row(modifier = Modifier.fillMaxWidth()) {
                for (idx in rowStart until rowEnd) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            Modifier.size(8.dp)
                                .background(colors.getOrElse(idx) { Color.Gray }, RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = labels[idx],
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
                // Pad tail to keep row alignment
                repeat(itemsPerRow - (rowEnd - rowStart)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── Tooltip info ────────────────────────────────────────────────────────────

@Composable
private fun TooltipInfo(
    idx: Int,
    seriesValues: List<List<Float>>,
    seriesLabels: List<String>,
    seriesColors: List<Color>,
    unit: String,
    timestamps: List<Long>
) {
    val ts = timestamps.getOrNull(idx)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "采样时间：${if (ts != null) formatTimestamp(ts) else "第 $idx 个点"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val maxPerRow = 4
        val numRows = ceil(seriesValues.size.toDouble() / maxPerRow).toInt().coerceAtLeast(1)
        val perRow = ceil(seriesValues.size.toDouble() / numRows).toInt()
        for (rowStart in seriesValues.indices step perRow) {
            val rowEnd = minOf(rowStart + perRow, seriesValues.size)
            Row(modifier = Modifier.fillMaxWidth()) {
                for (s in rowStart until rowEnd) {
                    val v = seriesValues[s].getOrElse(idx) { 0f }
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            Modifier.size(7.dp)
                                .background(seriesColors.getOrElse(s) { Color.Gray }, RoundedCornerShape(1.dp))
                        )
                        Text(
                            text = "${seriesLabels[s]}: ${formatValue(v, unit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
                repeat(perRow - (rowEnd - rowStart)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── Stacked area chart ──────────────────────────────────────────────────────

/**
 * Stacked area chart. seriesValues[seriesIndex][timeIndex].
 * Tap a point to see its exact values and timestamp.
 */
@Composable
fun StackedAreaChart(
    seriesValues: List<List<Float>>,
    seriesColors: List<Color>,
    seriesLabels: List<String>,
    maxValue: Float,
    unit: String,
    timestamps: List<Long> = emptyList(),
    modifier: Modifier = Modifier
) {
    val effectiveMax = maxValue.coerceAtLeast(0.001f)
    val numPoints = seriesValues.maxOfOrNull { it.size } ?: 0
    val numSeries = seriesValues.size
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val selectionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    var selectedPoint by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedPoint) {
        if (selectedPoint != null) {
            delay(5000)
            selectedPoint = null
        }
    }

    // Pre-compute stacked cumulative values (used both in Canvas and for tooltip dots)
    val stacked: Array<FloatArray>? = remember(seriesValues) {
        if (numPoints < 2 || numSeries == 0) return@remember null
        val s = Array(numSeries) { si ->
            FloatArray(numPoints) { t -> seriesValues[si].getOrElse(t) { 0f }.coerceAtLeast(0f) }
        }
        for (t in 0 until numPoints) {
            var cum = 0f
            for (si in 0 until numSeries) { cum += s[si][t]; s[si][t] = cum }
        }
        s
    }

    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Y-axis labels
            Column(
                modifier = Modifier.fillMaxHeight().wrapContentWidth().padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 4 downTo 0) {
                    Text(
                        text = formatAxisLabel(effectiveMax * i / 4, unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(numPoints) {
                        detectTapGestures { offset ->
                            if (numPoints > 1 && size.width > 0) {
                                val idx = ((offset.x / size.width.toFloat()) * (numPoints - 1))
                                    .roundToInt().coerceIn(0, numPoints - 1)
                                selectedPoint = if (selectedPoint == idx) null else idx
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    if (stacked == null || numPoints < 2) return@Canvas

                    fun xOf(t: Int) = t.toFloat() / (numPoints - 1) * w
                    fun yOf(v: Float) = (h - v / effectiveMax * h).coerceIn(0f, h)

                    // Grid lines
                    for (i in 0..4) {
                        drawLine(gridColor, Offset(0f, h * (4 - i) / 4), Offset(w, h * (4 - i) / 4), strokeWidth = 1f)
                    }

                    // Draw areas from top series down
                    for (s in numSeries - 1 downTo 0) {
                        val fillPath = Path()
                        fillPath.moveTo(xOf(0), yOf(stacked[s][0]))
                        for (t in 1 until numPoints) fillPath.lineTo(xOf(t), yOf(stacked[s][t]))
                        for (t in numPoints - 1 downTo 0) {
                            fillPath.lineTo(xOf(t), if (s > 0) yOf(stacked[s - 1][t]) else h)
                        }
                        fillPath.close()
                        drawPath(fillPath, seriesColors[s].copy(alpha = 0.65f))

                        val linePath = Path()
                        linePath.moveTo(xOf(0), yOf(stacked[s][0]))
                        for (t in 1 until numPoints) linePath.lineTo(xOf(t), yOf(stacked[s][t]))
                        drawPath(linePath, seriesColors[s], style = Stroke(width = 1.5.dp.toPx()))
                    }

                    // Selection indicator
                    selectedPoint?.let { idx ->
                        val x = idx.toFloat() / (numPoints - 1) * w
                        drawLine(
                            selectionColor, Offset(x, 0f), Offset(x, h),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                        )
                        for (s in 0 until numSeries) {
                            drawCircle(
                                color = seriesColors[s],
                                radius = 4.dp.toPx(),
                                center = Offset(x, yOf(stacked[s][idx]))
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.8f),
                                radius = 2.dp.toPx(),
                                center = Offset(x, yOf(stacked[s][idx]))
                            )
                        }
                    }
                }
            }
        }

        // Tooltip
        selectedPoint?.let { idx ->
            TooltipInfo(idx, seriesValues, seriesLabels, seriesColors, unit, timestamps)
        }

        // Legend (balanced rows)
        BalancedLegend(seriesLabels, seriesColors)
    }
}

// ── Multi-line chart (overlapping, no fill) ─────────────────────────────────

/**
 * Multi-series overlapping line chart — no area fill, each series plotted independently
 * against the same [maxValue]. Tap a point to see values and timestamp.
 */
@Composable
fun MultiLineChart(
    seriesValues: List<List<Float>>,
    seriesColors: List<Color>,
    seriesLabels: List<String>,
    maxValue: Float,
    unit: String,
    timestamps: List<Long> = emptyList(),
    modifier: Modifier = Modifier
) {
    val effectiveMax = maxValue.coerceAtLeast(0.001f)
    val numPoints = seriesValues.maxOfOrNull { it.size } ?: 0
    val numSeries = seriesValues.size
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val selectionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    var selectedPoint by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedPoint) {
        if (selectedPoint != null) {
            delay(5000)
            selectedPoint = null
        }
    }

    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Y-axis labels
            Column(
                modifier = Modifier.fillMaxHeight().wrapContentWidth().padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 4 downTo 0) {
                    Text(
                        text = formatAxisLabel(effectiveMax * i / 4, unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(numPoints) {
                        detectTapGestures { offset ->
                            if (numPoints > 1 && size.width > 0) {
                                val idx = ((offset.x / size.width.toFloat()) * (numPoints - 1))
                                    .roundToInt().coerceIn(0, numPoints - 1)
                                selectedPoint = if (selectedPoint == idx) null else idx
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    if (numPoints < 2) return@Canvas

                    fun xOf(t: Int) = t.toFloat() / (numPoints - 1) * w
                    fun yOf(v: Float) = (h - v / effectiveMax * h).coerceIn(0f, h)

                    // Grid lines
                    for (i in 0..4) {
                        drawLine(gridColor, Offset(0f, h * (4 - i) / 4), Offset(w, h * (4 - i) / 4), strokeWidth = 1f)
                    }

                    // Draw each series as a plain line, no fill
                    for (s in 0 until numSeries) {
                        val pts = seriesValues.getOrNull(s) ?: continue
                        if (pts.size < 2) continue
                        val color = seriesColors.getOrElse(s) { Color.Gray }
                        val linePath = Path()
                        linePath.moveTo(xOf(0), yOf(pts[0]))
                        for (t in 1 until pts.size) linePath.lineTo(xOf(t), yOf(pts[t]))
                        drawPath(linePath, color, style = Stroke(width = 1.5.dp.toPx()))
                    }

                    // Selection indicator
                    selectedPoint?.let { idx ->
                        if (idx >= numPoints) return@let
                        val x = idx.toFloat() / (numPoints - 1) * w
                        drawLine(
                            selectionColor, Offset(x, 0f), Offset(x, h),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                        )
                        for (s in 0 until numSeries) {
                            val v = seriesValues.getOrNull(s)?.getOrElse(idx) { 0f } ?: continue
                            val color = seriesColors.getOrElse(s) { Color.Gray }
                            val y = yOf(v)
                            drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(x, y))
                            drawCircle(color = Color.White.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }
            }
        }

        // Tooltip
        selectedPoint?.let { idx ->
            TooltipInfo(idx, seriesValues, seriesLabels, seriesColors, unit, timestamps)
        }

        // Legend
        BalancedLegend(seriesLabels, seriesColors)
    }
}

// ── Simple line chart ───────────────────────────────────────────────────────

/**
 * Single-series filled line chart. Tap a point to see its value and timestamp.
 */
@Composable
fun SimpleLineChart(
    values: List<Float>,
    maxValue: Float,
    lineColor: Color,
    unit: String,
    label: String = "",
    timestamps: List<Long> = emptyList(),
    modifier: Modifier = Modifier
) {
    val effectiveMax = maxValue.coerceAtLeast(0.001f)
    val n = values.size
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val selectionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    var selectedPoint by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedPoint) {
        if (selectedPoint != null) {
            delay(5000)
            selectedPoint = null
        }
    }

    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Y-axis labels
            Column(
                modifier = Modifier.fillMaxHeight().wrapContentWidth().padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 4 downTo 0) {
                    Text(
                        text = formatAxisLabel(effectiveMax * i / 4, unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(n) {
                        detectTapGestures { offset ->
                            if (n > 1 && size.width > 0) {
                                val idx = ((offset.x / size.width.toFloat()) * (n - 1))
                                    .roundToInt().coerceIn(0, n - 1)
                                selectedPoint = if (selectedPoint == idx) null else idx
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    if (n < 2) return@Canvas

                    fun xOf(t: Int) = t.toFloat() / (n - 1) * w
                    fun yOf(v: Float) = (h - v / effectiveMax * h).coerceIn(0f, h)

                    for (i in 0..4) {
                        drawLine(gridColor, Offset(0f, h * (4 - i) / 4), Offset(w, h * (4 - i) / 4), strokeWidth = 1f)
                    }

                    val fillPath = Path()
                    fillPath.moveTo(xOf(0), yOf(values[0]))
                    for (t in 1 until n) fillPath.lineTo(xOf(t), yOf(values[t]))
                    fillPath.lineTo(xOf(n - 1), h); fillPath.lineTo(0f, h); fillPath.close()
                    drawPath(fillPath, lineColor.copy(alpha = 0.3f))

                    val linePath = Path()
                    linePath.moveTo(xOf(0), yOf(values[0]))
                    for (t in 1 until n) linePath.lineTo(xOf(t), yOf(values[t]))
                    drawPath(linePath, lineColor, style = Stroke(width = 2.dp.toPx()))

                    selectedPoint?.let { idx ->
                        val x = xOf(idx)
                        drawLine(
                            selectionColor, Offset(x, 0f), Offset(x, h),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                        )
                        drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x, yOf(values[idx])))
                        drawCircle(Color.White.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = Offset(x, yOf(values[idx])))
                    }
                }
            }
        }

        // Tooltip
        selectedPoint?.let { idx ->
            val ts = timestamps.getOrNull(idx)
            val v = values.getOrElse(idx) { 0f }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "采样时间：${if (ts != null) formatTimestamp(ts) else "第 $idx 个点"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (label.isNotEmpty()) "$label：${formatValue(v, unit)}" else formatValue(v, unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = lineColor
                    )
                }
            }
        }
    }
}