package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BatteryHistoryLog
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberOrange
import java.text.SimpleDateFormat
import java.util.*

enum class ChartMetric {
    SOC,
    VOLTAGE,
    CURRENT,
    TEMPERATURE
}

@Composable
fun BmsTelemetryChart(
    logs: List<BatteryHistoryLog>,
    metric: ChartMetric,
    modifier: Modifier = Modifier
) {
    if (logs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = "No history data logged yet.\nTurn on charger or load simulation to log telemetry.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return;
    }

    // Sort chronologically
    val sortedLogs = remember(logs) { logs.sortedBy { it.timestamp } }

    val metricLabel = when (metric) {
        ChartMetric.SOC -> "State of Charge"
        ChartMetric.VOLTAGE -> "Pack Voltage"
        ChartMetric.CURRENT -> "Pack Current"
        ChartMetric.TEMPERATURE -> "Pack Temp"
    }

    val metricUnit = when (metric) {
        ChartMetric.SOC -> "%"
        ChartMetric.VOLTAGE -> "V"
        ChartMetric.CURRENT -> "A"
        ChartMetric.TEMPERATURE -> "°C"
    }

    val chartColor = when (metric) {
        ChartMetric.SOC -> CyberBlue
        ChartMetric.VOLTAGE -> CyberGreen
        ChartMetric.CURRENT -> CyberOrange
        ChartMetric.TEMPERATURE -> Color(0xFFFF1744)
    }

    // Extract values
    val values = sortedLogs.map { log ->
        when (metric) {
            ChartMetric.SOC -> log.soc
            ChartMetric.VOLTAGE -> log.voltage
            ChartMetric.CURRENT -> log.current
            ChartMetric.TEMPERATURE -> log.temperaturePack
        }
    }

    val minVal = (values.minOrNull() ?: 0f) - 0.5f
    val maxVal = (values.maxOrNull() ?: 100f) + 0.5f
    val range = maxOf(0.1f, maxVal - minVal)

    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        fontSize = 10.sp
    )

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$metricLabel ($metricUnit)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            selectedIndex?.let { idx ->
                if (idx < sortedLogs.size) {
                    val log = sortedLogs[idx]
                    val value = values[idx]
                    val timeStr = dateFormat.format(Date(log.timestamp))
                    Text(
                        text = "Value: ${String.format("%.2f", value)}$metricUnit @ $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = chartColor
                    )
                }
            } ?: run {
                val latest = values.lastOrNull() ?: 0f
                Text(
                    text = "Live: ${String.format("%.2f", latest)}$metricUnit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.medium
                )
                .pointerInput(sortedLogs) {
                    detectTapGestures(
                        onPress = { offset ->
                            val width = size.width
                            val paddingLeft = 50f
                            val paddingRight = 15f
                            val chartWidth = width - paddingLeft - paddingRight
                            val xRatio = (offset.x - paddingLeft) / chartWidth
                            val index = (xRatio * (sortedLogs.size - 1))
                                .coerceIn(0f, (sortedLogs.size - 1).toFloat())
                                .toInt()
                            selectedIndex = index
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height

            // Padding inside canvas
            val paddingLeft = 110f
            val paddingRight = 30f
            val paddingTop = 40f
            val paddingBottom = 40f

            val chartWidth = width - paddingLeft - paddingRight
            val chartHeight = height - paddingTop - paddingBottom

            // 1. Draw Grid Lines & Y Axis Labels
            val gridCount = 4
            for (i in 0..gridCount) {
                val fraction = i.toFloat() / gridCount
                val y = paddingTop + chartHeight * (1f - fraction)
                val gridVal = minVal + fraction * range

                // Grid line
                drawLine(
                    color = Color.Gray.copy(alpha = 0.15f),
                    start = Offset(paddingLeft, y),
                    end = Offset(width - paddingRight, y),
                    strokeWidth = 1.dp.toPx()
                )

                // Label
                val labelText = String.format("%.1f %s", gridVal, metricUnit)
                val textLayoutResult = textMeasurer.measure(labelText, style = textStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = labelText,
                    style = textStyle,
                    topLeft = Offset(15f, y - textLayoutResult.size.height / 2f)
                )
            }

            if (sortedLogs.size >= 2) {
                val path = Path()
                val fillPath = Path()

                sortedLogs.forEachIndexed { index, _ ->
                    val xFraction = index.toFloat() / (sortedLogs.size - 1)
                    val x = paddingLeft + xFraction * chartWidth

                    val yFraction = (values[index] - minVal) / range
                    val y = paddingTop + chartHeight * (1f - yFraction)

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                // Close fill path
                fillPath.lineTo(paddingLeft + chartWidth, paddingTop + chartHeight)
                fillPath.lineTo(paddingLeft, paddingTop + chartHeight)
                fillPath.close()

                // 2. Draw Area Gradient under curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            chartColor.copy(alpha = 0.35f),
                            chartColor.copy(alpha = 0.0f)
                        ),
                        startY = paddingTop,
                        endY = paddingTop + chartHeight
                    )
                )

                // 3. Draw Main Stroke Path
                drawPath(
                    path = path,
                    color = chartColor,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 4. Draw Interactive Coordinate Tooltip Line & Dot
                selectedIndex?.let { idx ->
                    if (idx < sortedLogs.size) {
                        val xFraction = idx.toFloat() / (sortedLogs.size - 1)
                        val x = paddingLeft + xFraction * chartWidth

                        val yFraction = (values[idx] - minVal) / range
                        val y = paddingTop + chartHeight * (1f - yFraction)

                        // Vertical guide line
                        drawLine(
                            color = chartColor.copy(alpha = 0.5f),
                            start = Offset(x, paddingTop),
                            end = Offset(x, paddingTop + chartHeight),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Outer glowing dot
                        drawCircle(
                            color = chartColor.copy(alpha = 0.3f),
                            radius = 8.dp.toPx(),
                            center = Offset(x, y)
                        )

                        // Inner solid dot
                        drawCircle(
                            color = chartColor,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }

            // Draw X Axis Timeline labels
            if (sortedLogs.isNotEmpty()) {
                val firstTime = dateFormat.format(Date(sortedLogs.first().timestamp))
                val lastTime = dateFormat.format(Date(sortedLogs.last().timestamp))

                // Left label
                drawText(
                    textMeasurer = textMeasurer,
                    text = firstTime,
                    style = textStyle,
                    topLeft = Offset(paddingLeft, height - paddingBottom + 8f)
                )

                // Right label
                val rightTextLayout = textMeasurer.measure(lastTime, style = textStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = lastTime,
                    style = textStyle,
                    topLeft = Offset(width - paddingRight - rightTextLayout.size.width, height - paddingBottom + 8f)
                )
            }
        }
    }
}
