// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.screens.drives

import adidoo.mateview.AppContainer
import adidoo.mateview.MateViewApplication
import adidoo.mateview.R
import adidoo.mateview.data.repo.DriveDetail
import adidoo.mateview.data.repo.DriveList
import adidoo.mateview.data.repo.DriveSummary
import adidoo.mateview.data.repo.ListPeriod
import adidoo.mateview.data.UnitSettings
import adidoo.mateview.ui.components.formatTime
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material3.Icon
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import adidoo.mateview.ui.components.AdaptiveTiles
import adidoo.mateview.ui.components.CardGrid
import adidoo.mateview.ui.components.DayHeader
import adidoo.mateview.ui.components.Metric
import adidoo.mateview.ui.components.SocBar
import adidoo.mateview.ui.components.StartEndGlyph
import adidoo.mateview.ui.components.dayOf
import adidoo.mateview.ui.components.fullWidth
import adidoo.mateview.ui.components.CategoryBarChart
import adidoo.mateview.ui.components.ChartSeries
import adidoo.mateview.ui.components.DetailHeader
import adidoo.mateview.ui.components.ErrorPanel
import adidoo.mateview.ui.components.ListDetailLayout
import adidoo.mateview.ui.components.MapWithFigures
import adidoo.mateview.ui.components.NO_VALUE
import adidoo.mateview.ui.components.NotConnectedMessage
import adidoo.mateview.ui.components.Panel
import adidoo.mateview.ui.components.PeriodChips
import adidoo.mateview.ui.components.SearchField
import adidoo.mateview.ui.components.StaleDataNote
import adidoo.mateview.ui.components.StatTile
import adidoo.mateview.ui.components.TimeLineChart
import adidoo.mateview.ui.components.TrackMap
import adidoo.mateview.ui.components.formatDayTime
import adidoo.mateview.ui.components.formatMinutes
import adidoo.mateview.ui.components.formatNumber
import adidoo.mateview.ui.components.formatSocRange
import adidoo.mateview.ui.screens.CarListViewModel
import adidoo.mateview.ui.screens.DetailViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

private val ColorSpeed = Color(0xFF4C8DF6)
private val ColorPower = Color(0xFFF2B33D)
private val ColorBattery = Color(0xFF34C38F)
private val ColorEfficient = Color(0xFF34C38F)
private val ColorThirsty = Color(0xFFF2B33D)
private val ColorRange = Color(0xFF7C6FD6)
private val ColorElevation = Color(0xFF9C7B5B)
private val ColorOutside = Color(0xFF4C8DF6)
private val ColorInside = Color(0xFFE5534B)

/** A gap of more than five minutes between two positions is drawn as a break. */
private const val DRIVE_GAP_MS = 5 * 60_000L

class DrivesViewModel(container: AppContainer) : CarListViewModel<DriveList>(container) {
    override suspend fun fetch(carId: Int, period: ListPeriod) = container.drives.list(carId, period)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivesScreen(onOpenSettings: () -> Unit) {
    val container = (LocalContext.current.applicationContext as MateViewApplication).container
    val viewModel: DrivesViewModel = viewModel(factory = viewModelFactory { initializer { DrivesViewModel(container) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    when (state.connected) {
        null -> return
        false -> return Box(Modifier.safeDrawingPadding()) { NotConnectedMessage(onOpenSettings) }
        true -> Unit
    }
    val carId = state.carId ?: return

    ListDetailLayout(
        hasSelection = state.selectedId != null,
        onCloseDetail = { viewModel.select(null) },
        emptyDetail = stringResource(R.string.drives_select),
        list = { modifier ->
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = viewModel::refresh,
                modifier = modifier.safeDrawingPadding(),
            ) {
                val drives = state.data?.drives.orEmpty().filter { it.matches(query) }
                CardGrid {
                    fullWidth { Text(stringResource(R.string.nav_drives), style = MaterialTheme.typography.headlineSmall) }
                    fullWidth { PeriodChips(state.period, viewModel::setPeriod) }
                    fullWidth { SearchField(query) { query = it } }
                    state.data?.takeIf { it.fromCache }?.let { fullWidth { StaleDataNote(it.fetchedAtMs) } }
                    state.error?.let { fullWidth { ErrorPanel(it, viewModel::refresh) } }
                    state.data?.let { data -> fullWidth { DrivesSummary(drives, data) } }
                    if (state.data != null && drives.isEmpty()) {
                        fullWidth { Text(stringResource(R.string.drives_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    val units = state.data?.units
                    if (units != null) {
                        val average = averageConsumption(drives)
                        drives.groupBy { dayOf(it.startMs) }.forEach { (day, ofDay) ->
                            fullWidth(key = "day-$day") {
                                DayHeader(day, formatNumber(ofDay.sumOf { it.distance ?: 0.0 }) + " " + units.length)
                            }
                            items(ofDay, key = { it.id }) { drive ->
                                DriveCard(drive, units, average, selected = drive.id == state.selectedId) {
                                    viewModel.select(drive.id)
                                }
                            }
                        }
                    }
                }
            }
        },
        detail = { modifier, showBack ->
            val id = state.selectedId ?: return@ListDetailLayout
            DriveDetailPane(container, carId, id, showBack, onBack = { viewModel.select(null) }, modifier = modifier)
        },
    )
}

private fun DriveSummary.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return listOfNotNull(startAddress, endAddress).any { it.contains(query.trim(), ignoreCase = true) }
}

@Composable
private fun DrivesSummary(drives: List<DriveSummary>, data: DriveList) {
    val length = data.units.length
    val energy = drives.sumOf { it.energy ?: 0.0 }
    val distance = drives.sumOf { it.distance ?: 0.0 }
    val minutes = drives.sumOf { it.durationMin ?: 0.0 }
    AdaptiveTiles(
        listOf(
            { m -> StatTile(stringResource(R.string.drives_distance), formatNumber(distance), m, length) },
            { m -> StatTile(stringResource(R.string.drives_duration), formatMinutes(minutes), m) },
            { m -> StatTile(stringResource(R.string.drives_energy), formatNumber(energy, 1), m, "kWh") },
            {
                    m ->
                StatTile(
                    stringResource(R.string.drives_consumption),
                    if (distance > 0) formatNumber(energy * 1000 / distance) else NO_VALUE, m, "Wh/$length",
                )
            },
        ),
    )
}

/** Net consumption over all the listed drives, in Wh per distance unit; null without distance. */
private fun averageConsumption(drives: List<DriveSummary>): Double? {
    val distance = drives.sumOf { it.distance ?: 0.0 }
    return if (distance > 0) drives.sumOf { it.energy ?: 0.0 } * 1000 / distance else null
}

/**
 * A drive drawn as a small route: departure on top, arrival below, joined by a
 * line; distance and duration on the right; consumption, temperature and the
 * battery used underneath.
 */
@Composable
private fun DriveCard(drive: DriveSummary, units: UnitSettings, average: Double?, selected: Boolean, onClick: () -> Unit) {
    val routeColor = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Times, aligned with their place.
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(drive.startMs), style = MaterialTheme.typography.labelLarge, color = muted)
                    Text(drive.endMs?.let { formatTime(it) } ?: "…", style = MaterialTheme.typography.labelLarge, color = muted)
                }
                StartEndGlyph(routeColor)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        drive.startAddress ?: NO_VALUE,
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        drive.endAddress ?: stringResource(R.string.in_progress),
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            formatNumber(drive.distance, 1),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(units.length, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    Text(formatMinutes(drive.durationMin), style = MaterialTheme.typography.labelLarge, color = muted)
                }
            }

            // In a narrow card (two columns on a phone in landscape) the temperature gives way to the battery.
            BoxWithConstraints {
            val roomForTemperature = maxWidth >= 330.dp
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Greener than the period's average, or thirstier.
                val consumption = drive.consumption
                val tint = when {
                    consumption == null || average == null -> muted
                    consumption <= average * 0.9 -> ColorEfficient
                    consumption >= average * 1.15 -> ColorThirsty
                    else -> muted
                }
                Metric(Icons.Outlined.Bolt, formatNumber(consumption) + " Wh/" + units.length, tint)
                if (drive.outsideTemp != null && roomForTemperature) {
                    Metric(Icons.Outlined.DeviceThermostat, formatNumber(drive.outsideTemp) + " °" + units.temperature, muted)
                }
                Box(Modifier.weight(1f))
                SocBar(drive.startSoc, drive.endSoc, ColorBattery, gained = false)
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriveDetailPane(
    container: AppContainer,
    carId: Int,
    driveId: Int,
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val viewModel: DetailViewModel<DriveDetail> = viewModel(
        key = "drive-$carId-$driveId",
        factory = viewModelFactory { initializer { DetailViewModel { container.drives.detail(carId, driveId) } } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    PullToRefreshBox(isRefreshing = state.loading, onRefresh = viewModel::refresh, modifier = modifier.safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val detail = state.data
            val drive = detail?.summary
            DetailHeader(
                title = drive?.let { formatDayTime(it.startMs) } ?: stringResource(R.string.nav_drives),
                subtitle = drive?.let { "${it.startAddress ?: NO_VALUE} → ${it.endAddress ?: stringResource(R.string.in_progress)}" },
                showBack = showBack,
                onBack = onBack,
            )
            state.error?.let { ErrorPanel(it, viewModel::refresh) }
            if (state.missing) Text(stringResource(R.string.detail_not_found))
            if (detail == null) {
                if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }
            if (detail.fromCache) StaleDataNote(detail.fetchedAtMs)
            DriveDetailContent(detail)
        }
    }
}

@Composable
private fun DriveDetailContent(detail: DriveDetail) {
    val drive = detail.summary
    val units = detail.units
    val from = drive.startMs
    val to = drive.endMs ?: detail.speed.lastOrNull()?.timeMs ?: from + 60_000L

    val map: (@Composable () -> Unit)? = if (detail.track.isNotEmpty()) ({ TrackMap(detail.track) }) else null
    MapWithFigures(map) {
    AdaptiveTiles(
        listOf(
            { m -> StatTile(stringResource(R.string.drives_distance), formatNumber(drive.distance, 1), m, units.length) },
            { m -> StatTile(stringResource(R.string.drives_duration), formatMinutes(drive.durationMin), m) },
            { m -> StatTile(stringResource(R.string.drive_battery), formatSocRange(drive.startSoc, drive.endSoc), m) },
            { m -> StatTile(stringResource(R.string.drives_consumption), formatNumber(drive.consumption), m, "Wh/${units.length}") },
            { m -> StatTile(stringResource(R.string.drives_energy), formatNumber(drive.energy, 2), m, "kWh") },
            { m -> StatTile(stringResource(R.string.drive_energy_recovered), formatNumber(detail.energyRecovered, 2), m, "kWh") },
            { m -> StatTile(stringResource(R.string.drive_speed_avg), formatNumber(detail.speedAvg), m, units.speed) },
            { m -> StatTile(stringResource(R.string.drive_speed_max), formatNumber(drive.speedMax), m, units.speed) },
            { m -> StatTile(stringResource(R.string.drive_power_max), formatNumber(drive.powerMax), m, "kW") },
            { m -> StatTile(stringResource(R.string.drive_outside_temp), formatNumber(drive.outsideTemp, 1), m, "°" + units.temperature) },
            { m -> StatTile(stringResource(R.string.drive_ascent), formatNumber(detail.ascent), m, units.altitude) },
            { m -> StatTile(stringResource(R.string.drive_descent), formatNumber(detail.descent), m, units.altitude) },
            {
                    m ->
                StatTile(
                    stringResource(R.string.drive_odometer),
                    if (detail.startOdometer == null) NO_VALUE
                    else "${formatNumber(detail.startOdometer)} → ${formatNumber(detail.endOdometer)}",
                    m, units.length,
                )
            },
        ),
    )
    }

    if (detail.speed.isNotEmpty() || detail.power.isNotEmpty()) {
        Panel(stringResource(R.string.chart_speed_power)) {
            TimeLineChart(
                series = listOf(
                    ChartSeries(stringResource(R.string.series_speed, units.speed), ColorSpeed, detail.speed),
                    ChartSeries(stringResource(R.string.series_power), ColorPower, detail.power),
                ),
                fromMs = from, toMs = to, maxGapMs = DRIVE_GAP_MS,
            )
        }
    }
    if (detail.soc.isNotEmpty()) {
        Panel(stringResource(R.string.chart_battery)) {
            TimeLineChart(
                series = listOf(ChartSeries(stringResource(R.string.drive_battery), ColorBattery, detail.soc)),
                fromMs = from, toMs = to, maxGapMs = DRIVE_GAP_MS,
                formatY = { formatNumber(it) + " %" }, formatValue = { formatNumber(it) + " %" },
            )
        }
    }
    if (detail.range.isNotEmpty()) {
        Panel(stringResource(R.string.chart_range)) {
            TimeLineChart(
                series = listOf(ChartSeries(stringResource(R.string.chart_range), ColorRange, detail.range)),
                fromMs = from, toMs = to, maxGapMs = DRIVE_GAP_MS,
                formatValue = { formatNumber(it) + " " + units.length },
            )
        }
    }
    if (detail.elevation.isNotEmpty()) {
        Panel(stringResource(R.string.chart_elevation)) {
            TimeLineChart(
                series = listOf(ChartSeries(stringResource(R.string.chart_elevation), ColorElevation, detail.elevation)),
                fromMs = from, toMs = to, maxGapMs = DRIVE_GAP_MS,
                formatValue = { formatNumber(it) + " " + units.altitude },
            )
        }
    }
    if (detail.outsideTemp.isNotEmpty() || detail.insideTemp.isNotEmpty()) {
        Panel(stringResource(R.string.overview_temperatures)) {
            TimeLineChart(
                series = listOf(
                    ChartSeries(stringResource(R.string.overview_temp_outside), ColorOutside, detail.outsideTemp),
                    ChartSeries(stringResource(R.string.overview_temp_inside), ColorInside, detail.insideTemp),
                ),
                fromMs = from, toMs = to, maxGapMs = DRIVE_GAP_MS,
                formatValue = { formatNumber(it, 1) + " °" + units.temperature },
            )
        }
    }
    if (detail.speedHistogram.isNotEmpty()) {
        Panel(stringResource(R.string.chart_speed_histogram, units.speed)) {
            val minutes = detail.speedHistogram.map { it.second / 60 }
            CategoryBarChart(
                labels = detail.speedHistogram.map { formatNumber(it.first) },
                values = minutes,
                color = ColorSpeed,
                formatY = { formatNumber(it) + " min" },
                formatValue = { index ->
                    "${formatNumber(detail.speedHistogram[index].first)} ${units.speed} : " + formatMinutes(minutes[index])
                },
            )
        }
    }
}
