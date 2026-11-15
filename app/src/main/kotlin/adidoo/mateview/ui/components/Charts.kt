// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.components

import adidoo.mateview.data.repo.CarState
import adidoo.mateview.data.repo.Point
import adidoo.mateview.data.repo.StateChange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class ChartSeries(val label: String, val color: Color, val points: List<Point>)

private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Top of a zero-based axis with three intervals, chosen so the grid lines fall
 * on round values (steps such as 1, 1.5, 2, 2.5 or 5 times a power of ten).
 */
internal fun niceTop(max: Double): Double {
    if (max <= 0.0) return 3.0
    val raw = max / 3
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0).first { it * magnitude >= raw } * magnitude
    return step * 3
}

/** Labels of a time axis: hours for short ranges, dates for long ones. */
private fun timeLabel(timeMs: Long, spanMs: Long): String {
    val pattern = if (spanMs <= 2 * DAY_MS) "HH:mm" else "d MMM"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timeMs))
}

/**
 * Line chart over a time range. A line is interrupted when two points are more
 * than [maxGapMs] apart, so a period without data is not drawn as a slope.
 * [yMin] and [yMax] fix the vertical scale; by default it follows the data.
 */
@Composable
fun TimeLineChart(
    series: List<ChartSeries>,
    fromMs: Long,
    toMs: Long,
    modifier: Modifier = Modifier,
    yMin: Double? = null,
    yMax: Double? = null,
    maxGapMs: Long = Long.MAX_VALUE,
    formatY: (Double) -> String = { formatNumber(it) },
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val values = series.flatMap { it.points }.map { it.value }
    val low = yMin ?: floor(values.minOrNull() ?: 0.0)
    val high = (yMax ?: if (low == 0.0) niceTop(values.maxOrNull() ?: 0.0) else ceil(values.maxOrNull() ?: 1.0))
        .let { if (it <= low) low + 1 else it }
    val span = (toMs - fromMs).coerceAtLeast(1)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val yLabels = (0..3).map { low + (high - low) * it / 3 }
            val measured = yLabels.map { textMeasurer.measure(formatY(it), labelStyle) }
            val gutter = measured.maxOf { it.size.width } + 8.dp.toPx()
            val bottom = size.height - measured.first().size.height - 6.dp.toPx()
            val top = measured.first().size.height / 2f
            val plotWidth = size.width - gutter

            fun x(timeMs: Long) = gutter + plotWidth * (timeMs - fromMs).toFloat() / span
            fun y(value: Double) = bottom - (bottom - top) * ((value - low) / (high - low)).toFloat()

            // Horizontal grid with its labels.
            yLabels.forEachIndexed { index, value ->
                val lineY = y(value)
                drawLine(gridColor, Offset(gutter, lineY), Offset(size.width, lineY), strokeWidth = 1f)
                drawText(measured[index], topLeft = Offset(0f, lineY - measured[index].size.height / 2f))
            }
            // Time labels, kept inside the plot.
            (0..3).forEach { index ->
                val time = fromMs + span * index / 3
                val label = textMeasurer.measure(timeLabel(time, span), labelStyle)
                val labelX = (x(time) - label.size.width / 2f).coerceIn(gutter, size.width - label.size.width)
                drawText(label, topLeft = Offset(labelX, bottom + 6.dp.toPx()))
            }

            clipRect(left = gutter, top = 0f, right = size.width, bottom = bottom + 1f) {
                series.forEach { line ->
                    val path = Path()
                    var previous: Point? = null
                    line.points.forEach { point ->
                        val px = x(point.timeMs)
                        val py = y(point.value)
                        if (previous == null || point.timeMs - previous!!.timeMs > maxGapMs) {
                            path.moveTo(px, py)
                            // A lone point would otherwise be invisible.
                            drawCircle(line.color, radius = 1.5.dp.toPx(), center = Offset(px, py))
                        } else {
                            path.lineTo(px, py)
                        }
                        previous = point
                    }
                    drawPath(path, line.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        if (series.size > 1) Legend(series.map { it.label to it.color })
    }
}

/**
 * One bar per calendar day (in the phone's time zone) over a time range, each
 * the sum of the [points] falling on that day. Suited to events that are short
 * compared with the range, such as charges over a month.
 */
@Composable
fun DailyBarChart(
    points: List<Point>,
    fromMs: Long,
    toMs: Long,
    color: Color,
    modifier: Modifier = Modifier,
    formatY: (Double) -> String = { formatNumber(it) },
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val zone = ZoneId.systemDefault()
    fun dayOf(timeMs: Long): LocalDate = Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate()
    val firstDay = dayOf(fromMs)
    val dayCount = (ChronoUnit.DAYS.between(firstDay, dayOf(toMs)).toInt() + 1).coerceAtLeast(1)
    val totals = DoubleArray(dayCount)
    points.forEach { point ->
        val index = ChronoUnit.DAYS.between(firstDay, dayOf(point.timeMs)).toInt()
        if (index in 0 until dayCount) totals[index] += point.value
    }
    val high = niceTop(totals.maxOrNull() ?: 0.0)

    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val yLabels = (0..3).map { high * it / 3 }
        val measured = yLabels.map { textMeasurer.measure(formatY(it), labelStyle) }
        val gutter = measured.maxOf { it.size.width } + 8.dp.toPx()
        val bottom = size.height - measured.first().size.height - 6.dp.toPx()
        val top = measured.first().size.height / 2f
        val slot = (size.width - gutter) / dayCount
        val barWidth = (slot * 0.7f).coerceAtMost(28.dp.toPx())

        fun y(value: Double) = bottom - (bottom - top) * (value / high).toFloat()

        yLabels.forEachIndexed { index, value ->
            val lineY = y(value)
            drawLine(gridColor, Offset(gutter, lineY), Offset(size.width, lineY), strokeWidth = 1f)
            drawText(measured[index], topLeft = Offset(0f, lineY - measured[index].size.height / 2f))
        }
        totals.forEachIndexed { index, total ->
            if (total <= 0.0) return@forEachIndexed
            val left = gutter + slot * index + (slot - barWidth) / 2
            val barTop = y(total)
            drawRoundRect(
                color,
                topLeft = Offset(left, barTop),
                size = Size(barWidth, bottom - barTop),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        // Date labels under four evenly spread days, centred on their bar.
        val formatter = SimpleDateFormat("d MMM", Locale.getDefault())
        val labelled = (0..3).map { it * (dayCount - 1) / 3 }.distinct()
        labelled.forEach { index ->
            val dayStart = firstDay.plusDays(index.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            val label = textMeasurer.measure(formatter.format(Date(dayStart)), labelStyle)
            val center = gutter + slot * index + slot / 2
            val labelX = (center - label.size.width / 2f).coerceIn(gutter, size.width - label.size.width)
            drawText(label, topLeft = Offset(labelX, bottom + 6.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A coloured band showing which state the car was in over a time range.
 * [changes] must be sorted by time and may start before [fromMs].
 */
@Composable
fun StateBand(
    changes: List<StateChange>,
    fromMs: Long,
    toMs: Long,
    colorOf: (CarState) -> Color,
    labelOf: @Composable (CarState) -> String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val span = (toMs - fromMs).coerceAtLeast(1)

    // Turn the changes into segments clipped to the visible range.
    val segments = buildList {
        changes.forEachIndexed { index, change ->
            val end = changes.getOrNull(index + 1)?.timeMs ?: toMs
            val start = change.timeMs.coerceAtLeast(fromMs)
            if (end > fromMs && start < toMs && change.state != CarState.UNKNOWN) {
                add(Triple(start, end.coerceAtMost(toMs), change.state))
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(52.dp)) {
            val barHeight = 28.dp.toPx()
            fun x(timeMs: Long) = size.width * (timeMs - fromMs).toFloat() / span
            clipRect(right = size.width, bottom = barHeight) {
                segments.forEach { (start, end, state) ->
                    // At least one pixel wide, so a short drive still shows up.
                    val width = (x(end) - x(start)).coerceAtLeast(1f)
                    drawRect(colorOf(state), topLeft = Offset(x(start), 0f), size = Size(width, barHeight))
                }
            }
            if (segments.isEmpty()) {
                drawRoundRect(
                    Color.Gray.copy(alpha = 0.2f), size = Size(size.width, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            (0..3).forEach { index ->
                val time = fromMs + span * index / 3
                val label = textMeasurer.measure(timeLabel(time, span), labelStyle)
                val labelX = (x(time) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(labelX, barHeight + 6.dp.toPx()))
            }
        }
        val present = CarState.entries.filter { state -> segments.any { it.third == state } }
        Legend(present.map { labelOf(it) to colorOf(it) })
    }
}
