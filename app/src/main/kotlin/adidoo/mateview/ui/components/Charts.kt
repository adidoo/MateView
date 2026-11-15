// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.components

import adidoo.mateview.R
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
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

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

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

private fun format(pattern: String, timeMs: Long): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timeMs))

/** Labels of a time axis: hours for short ranges, dates for long ones. */
private fun axisLabel(timeMs: Long, spanMs: Long): String = format(if (spanMs <= 2 * DAY_MS) "HH:mm" else "d MMM", timeMs)

/** Date and time of a reading, with the date only when the range spans several days. */
private fun readingLabel(timeMs: Long, spanMs: Long): String =
    format(if (spanMs <= DAY_MS) "HH:mm" else "EEE d MMM, HH:mm", timeMs)

/** "2 h 35", "45 min", "3 d 4 h" (with the local abbreviation for days). */
internal fun formatSpan(ms: Long, dayUnit: String): String {
    val minutes = ms / MINUTE_MS
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 24 * 60 -> "%d h %02d".format(minutes / 60, minutes % 60)
        else -> "${minutes / (24 * 60)} $dayUnit ${(minutes % (24 * 60)) / 60} h"
    }
}

/** Grid lines and time labels shared by the time-based charts. */
private class Axes(val gutter: Float, val top: Float, val bottom: Float)

/** Offers to go back to the whole range once the user has zoomed in. */
@Composable
private fun ResetZoom(viewport: ChartViewport) {
    if (viewport.zoomed) {
        TextButton(onClick = viewport::reset) { Text(stringResource(R.string.chart_reset_zoom)) }
    }
}

/**
 * Line chart over a time range. A line is interrupted when two points are more
 * than [maxGapMs] apart, so a period without data is not drawn as a slope.
 * [yMin] and [yMax] fix the vertical scale; by default it follows the data.
 * Touch to read the values, pinch to zoom, double tap to see the whole range.
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
    formatValue: (Double) -> String = { formatNumber(it, 1) },
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipStyle: TextStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val cursorColor = MaterialTheme.colorScheme.onSurfaceVariant
    val viewport = rememberChartViewport(fromMs, toMs, zoomable = true)

    val values = series.flatMap { it.points }.map { it.value }
    val low = yMin ?: floor(values.minOrNull() ?: 0.0)
    val high = (yMax ?: if (low == 0.0) niceTop(values.maxOrNull() ?: 0.0) else ceil(values.maxOrNull() ?: 1.0))
        .let { if (it <= low) low + 1 else it }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(180.dp).chartGestures(viewport)) {
            val from = viewport.from
            val to = viewport.to
            val span = (to - from).coerceAtLeast(1)
            val yLabels = (0..3).map { low + (high - low) * it / 3 }
            val measured = yLabels.map { textMeasurer.measure(formatY(it), labelStyle) }
            val axes = Axes(
                gutter = measured.maxOf { it.size.width } + 8.dp.toPx(),
                top = measured.first().size.height / 2f,
                bottom = size.height - measured.first().size.height - 6.dp.toPx(),
            )
            viewport.plotLeft = axes.gutter
            viewport.plotWidth = size.width - axes.gutter

            fun y(value: Double) = axes.bottom - (axes.bottom - axes.top) * ((value - low) / (high - low)).toFloat()

            yLabels.forEachIndexed { index, value ->
                val lineY = y(value)
                drawLine(gridColor, Offset(axes.gutter, lineY), Offset(size.width, lineY), strokeWidth = 1f)
                drawText(measured[index], topLeft = Offset(0f, lineY - measured[index].size.height / 2f))
            }
            (0..3).forEach { index ->
                val time = from + span * index / 3
                val label = textMeasurer.measure(axisLabel(time, span), labelStyle)
                val labelX = (viewport.xOf(time) - label.size.width / 2f).coerceIn(axes.gutter, size.width - label.size.width)
                drawText(label, topLeft = Offset(labelX, axes.bottom + 6.dp.toPx()))
            }

            clipRect(left = axes.gutter, top = 0f, right = size.width, bottom = axes.bottom + 1f) {
                series.forEach { line ->
                    val path = Path()
                    var previous: Point? = null
                    line.points.forEach { point ->
                        val px = viewport.xOf(point.timeMs)
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

            // Reading under the finger: the nearest point of each series, if close enough.
            val cursor = viewport.cursorTime ?: return@Canvas
            if (cursor !in from..to) return@Canvas
            val cursorX = viewport.xOf(cursor)
            drawLine(cursorColor, Offset(cursorX, axes.top), Offset(cursorX, axes.bottom), strokeWidth = 1.dp.toPx())
            val tolerance = minOf(maxGapMs, span / 10)
            val readings = series.mapNotNull { line ->
                val point = line.points.nearest(cursor)?.takeIf { abs(it.timeMs - cursor) <= tolerance }
                point?.let { line to it }
            }
            readings.forEach { (line, point) ->
                drawCircle(line.color, radius = 4.dp.toPx(), center = Offset(viewport.xOf(point.timeMs), y(point.value)))
            }
            val lines = buildList {
                add(TooltipLine(readingLabel(readings.firstOrNull()?.second?.timeMs ?: cursor, span)))
                readings.forEach { (line, point) -> add(TooltipLine("${line.label} : ${formatValue(point.value)}", line.color)) }
            }
            drawTooltip(textMeasurer, lines, cursorX, axes.top, axes.gutter, size.width, tooltipStyle, tooltipBackground)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (series.size > 1) Legend(series.map { it.label to it.color }, Modifier.weight(1f))
            else Box(Modifier.weight(1f))
            ResetZoom(viewport)
        }
    }
}

private fun abs(value: Long) = if (value < 0) -value else value

/**
 * One bar per calendar day (in the phone's time zone) over a time range, each
 * the sum of the [points] falling on that day. Suited to events that are short
 * compared with the range, such as charges over a month. Touch a bar to read it.
 */
@Composable
fun DailyBarChart(
    points: List<Point>,
    fromMs: Long,
    toMs: Long,
    color: Color,
    modifier: Modifier = Modifier,
    formatY: (Double) -> String = { formatNumber(it) },
    formatValue: (Double) -> String = { formatNumber(it, 1) },
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipStyle: TextStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val zone = ZoneId.systemDefault()
    fun dayOf(timeMs: Long): LocalDate = Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate()
    fun startOf(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val firstDay = dayOf(fromMs)
    val dayCount = (ChronoUnit.DAYS.between(firstDay, dayOf(toMs)).toInt() + 1).coerceAtLeast(1)
    val totals = DoubleArray(dayCount)
    points.forEach { point ->
        val index = ChronoUnit.DAYS.between(firstDay, dayOf(point.timeMs)).toInt()
        if (index in 0 until dayCount) totals[index] += point.value
    }
    val high = niceTop(totals.maxOrNull() ?: 0.0)
    // The viewport covers whole days, so that each slot is exactly one day.
    val viewport = rememberChartViewport(startOf(firstDay), startOf(firstDay.plusDays(dayCount.toLong())), zoomable = false)

    Canvas(modifier.fillMaxWidth().height(180.dp).chartGestures(viewport)) {
        val yLabels = (0..3).map { high * it / 3 }
        val measured = yLabels.map { textMeasurer.measure(formatY(it), labelStyle) }
        val gutter = measured.maxOf { it.size.width } + 8.dp.toPx()
        val bottom = size.height - measured.first().size.height - 6.dp.toPx()
        val top = measured.first().size.height / 2f
        val slot = (size.width - gutter) / dayCount
        val barWidth = (slot * 0.7f).coerceAtMost(28.dp.toPx())
        viewport.plotLeft = gutter
        viewport.plotWidth = size.width - gutter

        val selected = viewport.cursorTime
            ?.let { ChronoUnit.DAYS.between(firstDay, dayOf(it)).toInt() }
            ?.takeIf { it in 0 until dayCount }

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
                if (selected == null || selected == index) color else color.copy(alpha = 0.4f),
                topLeft = Offset(left, barTop),
                size = Size(barWidth, bottom - barTop),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        val labelled = (0..3).map { it * (dayCount - 1) / 3 }.distinct()
        labelled.forEach { index ->
            val label = textMeasurer.measure(format("d MMM", startOf(firstDay.plusDays(index.toLong()))), labelStyle)
            val center = gutter + slot * index + slot / 2
            val labelX = (center - label.size.width / 2f).coerceIn(gutter, size.width - label.size.width)
            drawText(label, topLeft = Offset(labelX, bottom + 6.dp.toPx()))
        }

        if (selected != null) {
            val day = startOf(firstDay.plusDays(selected.toLong()))
            drawTooltip(
                textMeasurer,
                listOf(TooltipLine(format("EEE d MMM", day)), TooltipLine(formatValue(totals[selected]), color)),
                gutter + slot * selected + slot / 2, top, gutter, size.width, tooltipStyle, tooltipBackground,
            )
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
 * Touch to see the state and its duration below the band, pinch to zoom.
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
    val cursorColor = MaterialTheme.colorScheme.onSurface
    val labels = CarState.entries.associateWith { labelOf(it) }
    val dayUnit = stringResource(R.string.unit_day_short)
    val viewport = rememberChartViewport(fromMs, toMs, zoomable = true)

    // The changes as segments clipped to the range; the last one runs until now.
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
        Canvas(Modifier.fillMaxWidth().height(52.dp).chartGestures(viewport)) {
            val bandHeight = 28.dp.toPx()
            val from = viewport.from
            val to = viewport.to
            val span = (to - from).coerceAtLeast(1)
            viewport.plotLeft = 0f
            viewport.plotWidth = size.width

            clipRect(right = size.width, bottom = bandHeight) {
                segments.forEach { (start, end, state) ->
                    if (end < from || start > to) return@forEach
                    // At least one pixel wide, so a short drive still shows up.
                    val width = (viewport.xOf(end) - viewport.xOf(start)).coerceAtLeast(1f)
                    drawRect(colorOf(state), topLeft = Offset(viewport.xOf(start), 0f), size = Size(width, bandHeight))
                }
            }
            if (segments.isEmpty()) {
                drawRoundRect(
                    Color.Gray.copy(alpha = 0.2f), size = Size(size.width, bandHeight),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            (0..3).forEach { index ->
                val time = from + span * index / 3
                val label = textMeasurer.measure(axisLabel(time, span), labelStyle)
                val labelX = (viewport.xOf(time) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(labelX, bandHeight + 6.dp.toPx()))
            }
            viewport.cursorTime?.takeIf { it in from..to }?.let { cursor ->
                val cursorX = viewport.xOf(cursor)
                drawLine(cursorColor, Offset(cursorX, 0f), Offset(cursorX, bandHeight), strokeWidth = 2.dp.toPx())
            }
        }

        // The state under the cursor, written out below the band.
        val selected = viewport.cursorTime?.let { cursor -> segments.firstOrNull { cursor >= it.first && cursor < it.second } }
        if (selected != null) {
            val (start, end, state) = selected
            val whole = toMs - fromMs
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf(state)))
                Text(
                    "${labels.getValue(state)} · ${readingLabel(start, whole)} → ${readingLabel(end, whole)} · " +
                        formatSpan(end - start, dayUnit),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val present = CarState.entries.filter { state -> segments.any { it.third == state } }
            Legend(present.map { labels.getValue(it) to colorOf(it) }, Modifier.weight(1f))
            ResetZoom(viewport)
        }
    }
}
