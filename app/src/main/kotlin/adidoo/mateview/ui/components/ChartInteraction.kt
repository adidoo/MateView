// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.components

import adidoo.mateview.data.repo.Point
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

/**
 * The part of a time range a chart currently shows, and the time the user is
 * reading (the cursor). Shared by the drawing code and the gesture handler.
 */
@Stable
class ChartViewport(val fullFrom: Long, val fullTo: Long, val zoomable: Boolean) {
    var from by mutableLongStateOf(fullFrom)
        private set
    var to by mutableLongStateOf(fullTo)
        private set
    var cursorTime by mutableStateOf<Long?>(null)

    /** Where the plot area sits in the canvas, updated at each draw. */
    internal var plotLeft = 0f
    internal var plotWidth = 1f

    val zoomed: Boolean get() = from != fullFrom || to != fullTo

    private val fullSpan get() = (fullTo - fullFrom).coerceAtLeast(1)

    /** Never zoom in closer than ten minutes, or a two-hundredth of the range. */
    private val minSpan get() = max(10 * 60_000L, fullSpan / 200)

    fun timeAt(x: Float): Long {
        val fraction = ((x - plotLeft) / plotWidth).coerceIn(0f, 1f)
        return from + ((to - from) * fraction.toDouble()).toLong()
    }

    fun xOf(time: Long): Float = plotLeft + plotWidth * ((time - from).toFloat() / (to - from).coerceAtLeast(1))

    /** Applies a pinch: [zoom] around [centroidX], then a horizontal [panX], both in pixels. */
    fun transform(zoom: Float, panX: Float, centroidX: Float) {
        if (!zoomable) return
        val span = (to - from).toDouble()
        val newSpan = (span / zoom).coerceIn(minSpan.toDouble(), fullSpan.toDouble())
        val anchor = ((centroidX - plotLeft) / plotWidth).coerceIn(0f, 1f).toDouble()
        val anchorTime = from + anchor * span
        val newFrom = (anchorTime - anchor * newSpan - panX / plotWidth * newSpan)
            .coerceIn(fullFrom.toDouble(), fullTo - newSpan)
        from = newFrom.toLong()
        to = (newFrom + newSpan).toLong()
    }

    fun reset() {
        from = fullFrom
        to = fullTo
        cursorTime = null
    }
}

@Composable
fun rememberChartViewport(fromMs: Long, toMs: Long, zoomable: Boolean): ChartViewport =
    remember(fromMs, toMs, zoomable) { ChartViewport(fromMs, toMs, zoomable) }

private const val DOUBLE_TAP_MS = 300L

/**
 * Chart gestures that coexist with a vertically scrolling page:
 *  - tap, or slide a finger horizontally: read the values under the finger;
 *  - pinch with two fingers: zoom and pan the time axis;
 *  - double tap: back to the whole range.
 * A vertical slide is left to the page, which scrolls as usual.
 */
fun Modifier.chartGestures(viewport: ChartViewport): Modifier = pointerInput(viewport) {
    var lastTapMs = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var dragX = 0f
        var dragY = 0f
        var scrubbing = false
        var pinching = false
        var moved = false

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            if (pressed.size >= 2 && viewport.zoomable) {
                pinching = true
                moved = true
                viewport.transform(event.calculateZoom(), event.calculatePan().x, event.calculateCentroid().x)
                event.changes.forEach { it.consume() }
                continue
            }
            if (pinching) {
                // One finger left after a pinch: ignore it until it is lifted too.
                event.changes.forEach { it.consume() }
                continue
            }

            val change = pressed.first()
            if (!scrubbing) {
                val delta = change.positionChange()
                dragX += delta.x
                dragY += delta.y
                if (abs(dragX) > slop && abs(dragX) > abs(dragY)) {
                    scrubbing = true
                } else if (abs(dragY) > slop) {
                    moved = true
                    break
                }
            }
            if (scrubbing) {
                moved = true
                viewport.cursorTime = viewport.timeAt(change.position.x)
                change.consume()
            }
        }

        if (!moved) {
            val now = down.uptimeMillis
            if (now - lastTapMs < DOUBLE_TAP_MS) {
                viewport.reset()
                lastTapMs = 0L
            } else {
                viewport.cursorTime = viewport.timeAt(down.position.x)
                lastTapMs = now
            }
        }
    }
}

/** The point of a time-sorted list closest to [time], or null when the list is empty. */
internal fun List<Point>.nearest(time: Long): Point? {
    if (isEmpty()) return null
    var low = 0
    var high = size - 1
    while (low < high) {
        val middle = (low + high) / 2
        if (this[middle].timeMs < time) low = middle + 1 else high = middle
    }
    val before = getOrNull(low - 1)
    val after = this[low]
    return if (before != null && time - before.timeMs < after.timeMs - time) before else after
}

/** One line of a tooltip, with an optional colour dot. */
internal data class TooltipLine(val text: String, val color: Color? = null)

/**
 * Draws a tooltip next to [anchorX], inside [left]..[right], flipping to the
 * left of the anchor when it would overflow.
 */
internal fun DrawScope.drawTooltip(
    textMeasurer: TextMeasurer,
    lines: List<TooltipLine>,
    anchorX: Float,
    top: Float,
    left: Float,
    right: Float,
    style: TextStyle,
    background: Color,
) {
    if (lines.isEmpty()) return
    val padding = 8.dp.toPx()
    val dot = 8.dp.toPx()
    val gap = 6.dp.toPx()
    val measured = lines.map { textMeasurer.measure(it.text, style) }
    val dotSpace = if (lines.any { it.color != null }) dot + gap else 0f
    val width = measured.maxOf { it.size.width } + dotSpace + padding * 2
    val height = measured.sumOf { it.size.height } + padding * 2
    val margin = 10.dp.toPx()
    val x = if (anchorX + margin + width <= right) anchorX + margin else (anchorX - margin - width).coerceAtLeast(left)

    drawRoundRect(background, topLeft = Offset(x, top), size = Size(width, height), cornerRadius = CornerRadius(6.dp.toPx()))
    var y = top + padding
    lines.forEachIndexed { index, line ->
        val text = measured[index]
        line.color?.let { drawCircle(it, radius = dot / 2, center = Offset(x + padding + dot / 2, y + text.size.height / 2f)) }
        drawText(text, topLeft = Offset(x + padding + if (line.color != null) dotSpace else 0f, y))
        y += text.size.height
    }
}
