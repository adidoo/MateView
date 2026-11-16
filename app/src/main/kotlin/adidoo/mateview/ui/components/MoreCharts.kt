// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Scatter plot of y against x, such as charging power against battery level.
 * The x axis is fixed to [xMin]..[xMax]; touch to read the closest point.
 */
@Composable
fun ScatterChart(
    points: List<Pair<Double, Double>>,
    color: Color,
    modifier: Modifier = Modifier,
    xMin: Double = 0.0,
    xMax: Double = 100.0,
    formatX: (Double) -> String = { formatNumber(it) },
    formatY: (Double) -> String = { formatNumber(it) },
    formatReading: (Pair<Double, Double>) -> String = { "${formatX(it.first)} · ${formatY(it.second)}" },
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipStyle: TextStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val high = niceTop(points.maxOfOrNull { it.second } ?: 0.0)
    var touchX by remember(points) { mutableStateOf<Float?>(null) }
    // Geometry of the last draw, used to turn a touch into an x value.
    val plot = remember { FloatArray(2) }

    Canvas(
        modifier.fillMaxWidth().height(200.dp).pointerInput(points) {
            detectTapGestures { touchX = it.x }
        },
    ) {
        val yLabels = (0..3).map { high * it / 3 }
        val measured = yLabels.map { textMeasurer.measure(formatY(it), labelStyle) }
        val gutter = measured.maxOf { it.size.width } + 8.dp.toPx()
        val bottom = size.height - measured.first().size.height - 6.dp.toPx()
        val top = measured.first().size.height / 2f
        val width = size.width - gutter
        plot[0] = gutter
        plot[1] = width

        fun x(value: Double) = gutter + width * ((value - xMin) / (xMax - xMin)).toFloat()
        fun y(value: Double) = bottom - (bottom - top) * (value / high).toFloat()

        yLabels.forEachIndexed { index, value ->
            val lineY = y(value)
            drawLine(gridColor, Offset(gutter, lineY), Offset(size.width, lineY), strokeWidth = 1f)
            drawText(measured[index], topLeft = Offset(0f, lineY - measured[index].size.height / 2f))
        }
        (0..4).forEach { index ->
            val value = xMin + (xMax - xMin) * index / 4
            val label = textMeasurer.measure(formatX(value), labelStyle)
            val labelX = (x(value) - label.size.width / 2f).coerceIn(gutter, size.width - label.size.width)
            drawText(label, topLeft = Offset(labelX, bottom + 6.dp.toPx()))
        }
        points.forEach { (px, py) ->
            drawCircle(color.copy(alpha = 0.6f), radius = 2.5.dp.toPx(), center = Offset(x(px), y(py)))
        }

        val touched = touchX ?: return@Canvas
        val value = xMin + (xMax - xMin) * ((touched - gutter) / width).coerceIn(0f, 1f)
        val nearest = points.minByOrNull { abs(it.first - value) } ?: return@Canvas
        val center = Offset(x(nearest.first), y(nearest.second))
        drawCircle(color, radius = 5.dp.toPx(), center = center)
        drawTooltip(
            textMeasurer, listOf(TooltipLine(formatReading(nearest), color)),
            center.x, top, gutter, size.width, tooltipStyle, tooltipBackground,
        )
    }
}

/** Vertical bars with a label under each, such as time spent per speed band. Touch a bar to read it. */
@Composable
fun CategoryBarChart(
    labels: List<String>,
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    formatY: (Double) -> String = { formatNumber(it) },
    formatValue: (Int) -> String = { formatNumber(values[it], 1) },
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipStyle: TextStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val high = niceTop(values.maxOrNull() ?: 0.0)
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val slots = remember { FloatArray(2) }

    Canvas(
        modifier.fillMaxWidth().height(180.dp).pointerInput(values) {
            detectTapGestures { offset ->
                val index = ((offset.x - slots[0]) / slots[1]).toInt()
                selected = index.takeIf { it in values.indices }
            }
        },
    ) {
        if (values.isEmpty()) return@Canvas
        val yLabels = (0..3).map { high * it / 3 }
        val measured = yLabels.map { textMeasurer.measure(formatY(it), labelStyle) }
        val gutter = measured.maxOf { it.size.width } + 8.dp.toPx()
        val bottom = size.height - measured.first().size.height - 6.dp.toPx()
        val top = measured.first().size.height / 2f
        val slot = (size.width - gutter) / values.size
        val barWidth = (slot * 0.7f).coerceAtMost(28.dp.toPx())
        slots[0] = gutter
        slots[1] = slot

        fun y(value: Double) = bottom - (bottom - top) * (value / high).toFloat()

        yLabels.forEachIndexed { index, value ->
            val lineY = y(value)
            drawLine(gridColor, Offset(gutter, lineY), Offset(size.width, lineY), strokeWidth = 1f)
            drawText(measured[index], topLeft = Offset(0f, lineY - measured[index].size.height / 2f))
        }
        // Label every bar when there is room, otherwise every other one.
        val every = if (slot < 28.dp.toPx()) 2 else 1
        values.forEachIndexed { index, value ->
            val left = gutter + slot * index + (slot - barWidth) / 2
            val barTop = y(value)
            drawRoundRect(
                if (selected == null || selected == index) color else color.copy(alpha = 0.4f),
                topLeft = Offset(left, barTop),
                size = Size(barWidth, bottom - barTop),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            if (index % every == 0) {
                val label = textMeasurer.measure(labels[index], labelStyle)
                drawText(label, topLeft = Offset(left + barWidth / 2 - label.size.width / 2f, bottom + 6.dp.toPx()))
            }
        }
        selected?.let { index ->
            drawTooltip(
                textMeasurer, listOf(TooltipLine(formatValue(index), color)),
                gutter + slot * index + slot / 2, top, gutter, size.width, tooltipStyle, tooltipBackground,
            )
        }
    }
}

/** One day of a [DailyRangeChart]: the extremes, and the band holding most of the day. */
class DayRange(val timeMs: Long, val min: Double, val max: Double, val low: Double, val high: Double)

/**
 * One candle per day: a thin line from the minimum to the maximum, and a thick
 * bar over the band [DayRange.low]..[DayRange.high]. Touch a day to read it.
 */
@Composable
fun DailyRangeChart(
    days: List<DayRange>,
    fromMs: Long,
    toMs: Long,
    color: Color,
    rangeLabel: String,
    bandLabel: String,
    modifier: Modifier = Modifier,
    formatY: (Double) -> String = { formatNumber(it) },
    formatValue: (Double) -> String = { formatNumber(it, 1) },
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipStyle: TextStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dateFormat = remember { java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()) }
    val dayMs = 24 * 60 * 60 * 1000L
    // The scale is rounded outwards to multiples of five, so the labels are round numbers.
    val steps = 4
    val low = kotlin.math.floor((days.minOfOrNull { it.min } ?: 0.0) / 5) * 5
    val step = (kotlin.math.ceil(((days.maxOfOrNull { it.max } ?: 0.0) - low) / steps / 5) * 5).coerceAtLeast(5.0)
    val high = low + step * steps
    val span = (toMs - fromMs).coerceAtLeast(dayMs).toDouble()
    var selected by remember(days) { mutableStateOf<DayRange?>(null) }
    val plot = remember { FloatArray(2) }

    Canvas(
        modifier.fillMaxWidth().height(200.dp).pointerInput(days) {
            detectTapGestures { offset ->
                val time = fromMs + span * ((offset.x - plot[0]) / plot[1]).coerceIn(0f, 1f)
                selected = days.minByOrNull { abs(it.timeMs + dayMs / 2 - time) }
            }
        },
    ) {
        if (days.isEmpty()) return@Canvas
        val yLabels = (0..steps).map { low + (high - low) * it / steps }
        val measured = yLabels.map { textMeasurer.measure(formatY(it), labelStyle) }
        val gutter = measured.maxOf { it.size.width } + 8.dp.toPx()
        val bottom = size.height - measured.first().size.height - 6.dp.toPx()
        val top = measured.first().size.height / 2f
        val width = size.width - gutter
        plot[0] = gutter
        plot[1] = width

        fun x(timeMs: Long) = gutter + width * ((timeMs + dayMs / 2 - fromMs) / span).toFloat()
        fun y(value: Double) = bottom - (bottom - top) * ((value - low) / (high - low)).toFloat()

        yLabels.forEachIndexed { index, value ->
            val lineY = y(value)
            drawLine(gridColor, Offset(gutter, lineY), Offset(size.width, lineY), strokeWidth = 1f)
            drawText(measured[index], topLeft = Offset(0f, lineY - measured[index].size.height / 2f))
        }
        (0..3).forEach { index ->
            val label = textMeasurer.measure(dateFormat.format(java.util.Date(fromMs + (span * index / 3).toLong())), labelStyle)
            val labelX = (gutter + width * index / 3 - label.size.width / 2f).coerceIn(gutter, size.width - label.size.width)
            drawText(label, topLeft = Offset(labelX, bottom + 6.dp.toPx()))
        }

        val body = (width * (dayMs / span).toFloat() * 0.6f).coerceIn(2.dp.toPx(), 14.dp.toPx())
        days.forEach { day ->
            val centre = x(day.timeMs)
            if (centre < gutter || centre > size.width) return@forEach
            val tint = if (selected == null || selected === day) color else color.copy(alpha = 0.4f)
            drawLine(tint, Offset(centre, y(day.max)), Offset(centre, y(day.min)), strokeWidth = 1.5.dp.toPx())
            val bandTop = y(day.high)
            drawRoundRect(
                tint,
                topLeft = Offset(centre - body / 2, bandTop),
                size = Size(body, (y(day.low) - bandTop).coerceAtLeast(1.dp.toPx())),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
        }
        selected?.let { day ->
            drawTooltip(
                textMeasurer,
                listOf(
                    TooltipLine(dateFormat.format(java.util.Date(day.timeMs + dayMs / 2))),
                    TooltipLine("$rangeLabel : ${formatValue(day.min)} – ${formatValue(day.max)}", color),
                    TooltipLine("$bandLabel : ${formatValue(day.low)} – ${formatValue(day.high)}", color),
                ),
                x(day.timeMs), top, gutter, size.width, tooltipStyle, tooltipBackground,
            )
        }
    }
}
