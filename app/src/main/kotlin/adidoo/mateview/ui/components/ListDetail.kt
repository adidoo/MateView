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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** From this window width, the list and the selected item are shown side by side. */
private const val LIST_DETAIL_MIN_WIDTH_DP = 800

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
