// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.components

import adidoo.mateview.R
import adidoo.mateview.data.repo.ListPeriod
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * From this window width, the list and the selected item are shown side by side.
 * A phone in landscape stays below it: two panes there leave too little room for either.
 */
private const val LIST_DETAIL_MIN_WIDTH_DP = 1000

/**
 * A list with a detail page: side by side on a wide window, one after the
 * other on a narrow one (the system back gesture then returns to the list).
 */
@Composable
fun ListDetailLayout(
    hasSelection: Boolean,
    onCloseDetail: () -> Unit,
    list: @Composable (Modifier) -> Unit,
    detail: @Composable (modifier: Modifier, showBack: Boolean) -> Unit,
    emptyDetail: String,
) {
    if (LocalConfiguration.current.screenWidthDp >= LIST_DETAIL_MIN_WIDTH_DP) {
        Row(Modifier.fillMaxSize()) {
            list(Modifier.weight(0.42f).fillMaxHeight())
            VerticalDivider()
            if (hasSelection) {
                detail(Modifier.weight(0.58f).fillMaxHeight(), false)
            } else {
                Box(Modifier.weight(0.58f).fillMaxHeight().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(emptyDetail, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        BackHandler(enabled = hasSelection, onBack = onCloseDetail)
        if (hasSelection) detail(Modifier.fillMaxSize(), true) else list(Modifier.fillMaxSize())
    }
}

/** Title of a detail page, with a back arrow when the list is not visible next to it. */
@Composable
fun DetailHeader(title: String, subtitle: String?, showBack: Boolean, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PeriodChips(selected: ListPeriod, onSelect: (ListPeriod) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ListPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = {
                    Text(
                        stringResource(
                            when (period) {
                                ListPeriod.MONTH -> R.string.period_30d
                                ListPeriod.QUARTER -> R.string.period_90d
                                ListPeriod.YEAR -> R.string.period_1y
                                ListPeriod.ALL -> R.string.period_all
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.search_place)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Tiles laid out on as many columns as the width allows, from two to four. */
@Composable
fun AdaptiveTiles(tiles: List<@Composable (Modifier) -> Unit>) {
    BoxWithConstraints {
        TileGrid(columns = (maxWidth / 160.dp).toInt().coerceIn(2, 4), tiles = tiles)
    }
}

/** Shown when the data comes from the cache because the server could not be reached. */
@Composable
fun StaleDataNote(fetchedAtMs: Long) {
    Text(
        stringResource(R.string.overview_from_cache, formatFullDateTime(fetchedAtMs)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Panel(stringResource(R.string.overview_error)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun NotConnectedMessage(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_not_connected), textAlign = TextAlign.Center)
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.home_open_settings)) }
    }
}

/**
 * A scrolling list of cards: one column on a narrow window, two or more when
 * each card can keep a comfortable width (a phone in landscape gets two).
 */
@Composable
fun CardGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** An item that takes the whole width of a [CardGrid]: a title, filters, a day header... */
fun LazyGridScope.fullWidth(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** The calendar day of a moment, in the phone's time zone. */
fun dayOf(timeMs: Long): LocalDate = Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).toLocalDate()

/** "Samedi 14 novembre" on the left, the day's total on the right. */
@Composable
fun DayHeader(day: LocalDate, total: String) {
    val label = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(total, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A ring, a line and a dot: the start and the end of something, one above the other. */
@Composable
fun StartEndGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.width(12.dp).fillMaxHeight()) {
        val radius = 5.dp.toPx()
        val x = size.width / 2
        val top = 9.dp.toPx()
        val bottom = size.height - 9.dp.toPx()
        drawLine(color.copy(alpha = 0.5f), Offset(x, top + radius), Offset(x, bottom - radius), strokeWidth = 2.dp.toPx())
        drawCircle(color, radius - 1.dp.toPx(), Offset(x, top), style = Stroke(2.dp.toPx()))
        drawCircle(color, radius, Offset(x, bottom))
    }
}

/** A small icon followed by a value. */
@Composable
fun Metric(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint, maxLines = 1)
    }
}

/**
 * A small battery bar with its two levels. The part between them is drawn
 * lighter after a drive (energy used) and brighter after a charge (energy
 * added), where [gained] is true.
 */
@Composable
fun SocBar(start: Double?, end: Double?, color: Color, gained: Boolean) {
    if (start == null || end == null) return
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.width(56.dp).height(8.dp)) {
            val corner = CornerRadius(size.height / 2)
            fun bar(level: Double, alpha: Float) = drawRoundRect(
                color.copy(alpha = alpha), size = Size(size.width * (level / 100).toFloat().coerceIn(0f, 1f), size.height),
                cornerRadius = corner,
            )
            drawRoundRect(track, cornerRadius = corner)
            if (gained) {
                bar(end, 1f)
                // Dim the level the charge started from, so that the gain stands out.
                drawRoundRect(
                    track.copy(alpha = 0.55f), size = Size(size.width * (start / 100).toFloat().coerceIn(0f, 1f), size.height),
                    cornerRadius = corner,
                )
            } else {
                bar(start, 0.4f)
                bar(end, 1f)
            }
        }
        Text(
            "${formatNumber(start)} → ${formatNumber(end)} %",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The map and the figures of a detail page: side by side when the page is wide
 * (a phone in landscape), so that figures are visible without scrolling past
 * the map; one above the other otherwise. [map] is skipped when null.
 */
@Composable
fun MapWithFigures(map: (@Composable () -> Unit)?, figures: @Composable () -> Unit) {
    BoxWithConstraints {
        if (map != null && maxWidth >= 600.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(0.42f)) { map() }
                Box(Modifier.weight(0.58f)) { figures() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                map?.invoke()
                figures()
            }
        }
    }
}
