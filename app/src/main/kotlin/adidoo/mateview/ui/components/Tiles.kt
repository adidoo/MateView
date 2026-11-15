// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Placeholder shown for a value the car did not report. */
const val NO_VALUE = "–"

/** Formats [value] with [decimals] digits in the user's locale, or [NO_VALUE]. */
fun formatNumber(value: Double?, decimals: Int = 0): String =
    if (value == null || value.isNaN()) NO_VALUE else String.format(Locale.getDefault(), "%,.${decimals}f", value)

/** A card with a title, used to group the content of one dashboard panel. */
@Composable
fun Panel(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

/** A single figure with its label: the phone counterpart of a Grafana "stat" panel. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // The value shrinks to fit: tiles get narrow next to a detail pane, and
                // some values are long ("2 135 → 2 140").
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 24.sp, stepSize = 1.sp),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (unit != null && value != NO_VALUE) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

/** Lays [tiles] out on rows of [columns] equal cells. */
@Composable
fun TileGrid(columns: Int, tiles: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> tile(Modifier.weight(1f)) }
                // Keep the cells of an incomplete last row the same width as the others.
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A 240° arc gauge. [fraction] is the filled share (0..1), null when there is no value.
 */
@Composable
fun ArcGauge(
    label: String,
    value: String,
    fraction: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.35f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = size.minDimension * 0.11f
                val diameter = minOf(size.width, size.height * 1.25f) - stroke
                val topLeft = Offset((size.width - diameter) / 2, stroke / 2)
                val arcSize = Size(diameter, diameter)
                drawArc(track, 150f, 240f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (fraction != null && fraction > 0f) {
                    drawArc(
                        color, 150f, 240f * fraction.coerceIn(0f, 1f), false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                if (unit != null && value != NO_VALUE) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
