// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.screens.overview

import adidoo.mateview.MateViewApplication
import adidoo.mateview.R
import adidoo.mateview.data.repo.CarState
import adidoo.mateview.data.repo.Overview
import adidoo.mateview.ui.components.ArcGauge
import adidoo.mateview.ui.components.ChartSeries
import adidoo.mateview.ui.components.DailyBarChart
import adidoo.mateview.ui.components.NO_VALUE
import adidoo.mateview.ui.components.Panel
import adidoo.mateview.ui.components.StatTile
import adidoo.mateview.ui.components.StateBand
import adidoo.mateview.ui.components.TileGrid
import adidoo.mateview.ui.components.TimeLineChart
import adidoo.mateview.ui.components.formatNumber
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.util.Date

private const val TWO_PANE_MIN_WIDTH_DP = 600
private const val MINUTE_MS = 60_000L

/** Beyond this span a charge is too short to read as a curve: daily bars are shown instead. */
private const val CURVE_MAX_SPAN_MS = 36 * 60 * MINUTE_MS

private val ColorGood = Color(0xFF34C38F)
private val ColorWarn = Color(0xFFF2B33D)
private val ColorBad = Color(0xFFE5534B)
private val ColorPower = Color(0xFF4C8DF6)
private val ColorEnergy = Color(0xFF34C38F)

private fun stateColor(state: CarState): Color = when (state) {
    CarState.DRIVING -> Color(0xFF4C8DF6)
    CarState.CHARGING -> Color(0xFF34C38F)
    CarState.UPDATING -> Color(0xFFE0559A)
    CarState.ONLINE -> Color(0xFFF2B33D)
    CarState.ASLEEP -> Color(0xFF7C6FD6)
    CarState.OFFLINE -> Color(0xFF9CA3AF)
    CarState.UNKNOWN -> Color(0xFF6B7280)
}

@Composable
private fun stateLabel(state: CarState): String = stringResource(
    when (state) {
        CarState.DRIVING -> R.string.state_driving
        CarState.CHARGING -> R.string.state_charging
        CarState.UPDATING -> R.string.state_updating
        CarState.ONLINE -> R.string.state_online
        CarState.ASLEEP -> R.string.state_asleep
        CarState.OFFLINE -> R.string.state_offline
        CarState.UNKNOWN -> R.string.state_unknown
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(onOpenSettings: () -> Unit) {
    val container = (LocalContext.current.applicationContext as MateViewApplication).container
    val viewModel: OverviewViewModel = viewModel(
        factory = viewModelFactory { initializer { OverviewViewModel(container) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (state.connected) {
        null -> return
        false -> return NotConnected(onOpenSettings)
        true -> Unit
    }

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
    ) {
        val overview = state.overview
        val header: @Composable () -> Unit = { Header(state, viewModel::setPeriod) }
        when {
            overview == null -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                header()
                state.error?.let { ErrorNote(it, viewModel::refresh) }
            }
            LocalConfiguration.current.screenWidthDp >= TWO_PANE_MIN_WIDTH_DP -> Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Wide window: figures on the left, charts on the right, each scrolling on its own.
                Pane(Modifier.weight(1f)) {
                    header()
                    state.error?.let { ErrorNote(it, viewModel::refresh) }
                    Figures(overview, tileColumns = 2)
                }
                Pane(Modifier.weight(1f)) { ChartPanels(overview) }
            }
            else -> Pane(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                header()
                state.error?.let { ErrorNote(it, viewModel::refresh) }
                Figures(overview, tileColumns = 2)
                ChartPanels(overview)
            }
        }
    }
}

@Composable
private fun Pane(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun NotConnected(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_not_connected), textAlign = TextAlign.Center)
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.home_open_settings)) }
    }
}

@Composable
private fun Header(state: OverviewUiState, onPeriod: (OverviewPeriod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(state.carName.orEmpty(), style = MaterialTheme.typography.headlineSmall)
        val overview = state.overview
        if (overview != null && overview.fromCache) {
            val time = DateFormat.getTimeFormat(LocalContext.current).format(Date(overview.fetchedAtMs))
            val day = DateFormat.getMediumDateFormat(LocalContext.current).format(Date(overview.fetchedAtMs))
            Text(
                stringResource(R.string.overview_from_cache, "$day $time"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OverviewPeriod.entries.forEach { period ->
                FilterChip(
                    selected = period == state.period,
                    onClick = { onPeriod(period) },
                    label = {
                        Text(
                            stringResource(
                                when (period) {
                                    OverviewPeriod.DAY -> R.string.period_24h
                                    OverviewPeriod.WEEK -> R.string.period_7d
                                    OverviewPeriod.MONTH -> R.string.period_30d
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ErrorNote(message: String, onRetry: () -> Unit) {
    Panel(stringResource(R.string.overview_error)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/** Current state, gauges, tiles and temperatures. */
@Composable
private fun Figures(overview: Overview, tileColumns: Int) {
    val lengthUnit = overview.units.length
    val tempUnit = "°" + overview.units.temperature

    // Current state: an addition to the Grafana dashboard, which only has the timeline.
    Panel(null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(stateColor(overview.state)))
            Text(stateLabel(overview.state), style = MaterialTheme.typography.titleLarge)
        }
        val since = overview.stateSinceMs?.let {
            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), MINUTE_MS).toString()
        }
        val details = listOfNotNull(since, overview.place?.takeIf { it.isNotBlank() })
        if (details.isNotEmpty()) {
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Panel(null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val level = overview.batteryLevel
            ArcGauge(
                label = stringResource(R.string.overview_battery_level),
                value = formatNumber(level),
                unit = "%",
                fraction = level?.let { (it / 100).toFloat() },
                color = when {
                    level == null -> ColorGood
                    level <= 10 -> ColorBad
                    level <= 20 -> ColorWarn
                    else -> ColorGood
                },
                modifier = Modifier.weight(1f),
            )
            // Not charging reads as zero, as on the Grafana dashboard.
            val power = overview.chargerPowerKw ?: 0.0
            val voltage = overview.chargerVoltage ?: 0.0
            // AC charging tops out around 11 or 22 kW; DC at 170 kW (LFP) or 250 kW.
            val maxPower = when {
                voltage > 0 && power <= 22 -> 22.0
                overview.lfpBattery -> 170.0
                else -> 250.0
            }
            ArcGauge(
                label = stringResource(R.string.overview_charging_power),
                value = formatNumber(power),
                unit = "kW",
                fraction = (power / maxPower).toFloat(),
                color = ColorPower,
                modifier = Modifier.weight(1f),
            )
            ArcGauge(
                label = stringResource(R.string.overview_ac_voltage),
                value = formatNumber(voltage),
                unit = "V",
                fraction = (voltage / 250).toFloat(),
                color = ColorWarn,
                modifier = Modifier.weight(1f),
            )
        }
    }

    TileGrid(
        columns = tileColumns,
        tiles = listOf(
            { m -> StatTile(stringResource(R.string.overview_range), formatNumber(overview.rangeDistance), m, lengthUnit) },
            { m -> StatTile(stringResource(R.string.overview_odometer), formatNumber(overview.odometer), m, lengthUnit) },
            { m -> StatTile(stringResource(R.string.overview_consumption_net), formatNumber(overview.consumptionNet), m, "Wh/$lengthUnit") },
            { m -> StatTile(stringResource(R.string.overview_consumption_gross), formatNumber(overview.consumptionGross), m, "Wh/$lengthUnit") },
            { m -> StatTile(stringResource(R.string.overview_distance), formatNumber(overview.distance), m, lengthUnit) },
            { m -> StatTile(stringResource(R.string.overview_firmware), overview.firmware ?: NO_VALUE, m) },
        ),
    )

    Panel(stringResource(R.string.overview_temperatures)) {
        if (overview.insideTemp == null && overview.outsideTemp == null) {
            Text(
                stringResource(R.string.overview_no_recent_temperature),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Temperature(stringResource(R.string.overview_temp_driver), overview.driverTemp, tempUnit, Modifier.weight(1f))
                Temperature(stringResource(R.string.overview_temp_inside), overview.insideTemp, tempUnit, Modifier.weight(1f))
                Temperature(stringResource(R.string.overview_temp_outside), overview.outsideTemp, tempUnit, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Temperature(label: String, value: Double?, unit: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (value == null) NO_VALUE else formatNumber(value, 1) + " " + unit,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/** Charge level, charging details and the state timeline. */
@Composable
private fun ChartPanels(overview: Overview) {
    val from = overview.range.fromMs
    val to = overview.range.toMs
    val gap = overview.bucketMs * 3

    Panel(stringResource(R.string.overview_chart_charge_level)) {
        if (overview.chargeLevel.isEmpty()) {
            NoData()
        } else {
            TimeLineChart(
                series = listOf(ChartSeries("%", ColorGood, overview.chargeLevel)),
                fromMs = from, toMs = to, yMin = 0.0, yMax = 100.0,
                formatY = { formatNumber(it) + " %" },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (to - from > CURVE_MAX_SPAN_MS) {
        Panel(stringResource(R.string.overview_chart_energy_per_day)) {
            if (overview.chargeSessions.isEmpty()) {
                NoData()
            } else {
                DailyBarChart(
                    points = overview.chargeSessions,
                    fromMs = from, toMs = to,
                    color = ColorEnergy,
                    formatY = { formatNumber(it) + " kWh" },
                )
            }
        }
    } else Panel(stringResource(R.string.overview_chart_charging)) {
        if (overview.chargingPower.isEmpty()) {
            NoData()
        } else {
            TimeLineChart(
                series = listOf(
                    ChartSeries(stringResource(R.string.overview_series_power), ColorPower, overview.chargingPower),
                    ChartSeries(stringResource(R.string.overview_series_energy), ColorEnergy, overview.energyAdded),
                ),
                fromMs = from, toMs = to, yMin = 0.0, maxGapMs = gap,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Panel(stringResource(R.string.overview_chart_states)) {
        StateBand(
            changes = overview.states,
            fromMs = from, toMs = to,
            colorOf = ::stateColor,
            labelOf = { stateLabel(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NoData() {
    Text(
        stringResource(R.string.no_data_for_period),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
