// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.screens.dashboards

import adidoo.mateview.AppContainer
import adidoo.mateview.R
import adidoo.mateview.data.repo.BatteryHealth
import adidoo.mateview.data.repo.BatterySeries
import adidoo.mateview.data.repo.IdlePeriod
import adidoo.mateview.data.repo.ListPeriod
import adidoo.mateview.data.repo.VampireDrain
import adidoo.mateview.ui.components.AdaptiveTiles
import adidoo.mateview.ui.components.ArcGauge
import adidoo.mateview.ui.components.ChartSeries
import adidoo.mateview.ui.components.DetailHeader
import adidoo.mateview.ui.components.ErrorPanel
import adidoo.mateview.ui.components.NO_VALUE
import adidoo.mateview.ui.components.Panel
import adidoo.mateview.ui.components.PeriodChips
import adidoo.mateview.ui.components.ScatterChart
import adidoo.mateview.ui.components.StaleDataNote
import adidoo.mateview.ui.components.StatTile
import adidoo.mateview.ui.components.TimeLineChart
import adidoo.mateview.ui.components.formatDayTime
import adidoo.mateview.ui.components.formatNumber
import adidoo.mateview.ui.components.formatSocRange
import adidoo.mateview.ui.components.formatSpan
import adidoo.mateview.ui.components.niceTop
import adidoo.mateview.ui.screens.DetailViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

private val ColorBattery = Color(0xFF34C38F)
private val ColorUsable = Color(0xFF4C8DF6)
private val ColorRange = Color(0xFF7C6FD6)
private val ColorTemp = Color(0xFFF2B33D)
private val ColorOdometer = Color(0xFF9C7B5B)
private val ColorDegradation = Color(0xFFF2B33D)

data class PeriodUiState<T>(val period: ListPeriod, val loading: Boolean = true, val data: T? = null, val error: String? = null)

/** Loads the data of a dashboard over a period the user can change. */
class PeriodViewModel<T>(initial: ListPeriod, private val fetch: suspend (ListPeriod) -> T) : ViewModel() {
    private val _state = MutableStateFlow(PeriodUiState<T>(initial))
    val state: StateFlow<PeriodUiState<T>> = _state.asStateFlow()
    private var job: Job? = null

    init {
        refresh()
    }

    fun setPeriod(period: ListPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        refresh()
    }

    fun refresh() {
        val period = _state.value.period
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val data = fetch(period)
                _state.update { it.copy(loading = false, data = data) }
            } catch (e: IOException) {
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }
}

/** The frame shared by every dashboard: title with a back arrow, pull to refresh, scrolling content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardPage(
    title: String,
    onBack: () -> Unit,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    PullToRefreshBox(isRefreshing = loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailHeader(title, null, showBack = true, onBack = onBack)
            error?.let { ErrorPanel(it, onRefresh) }
            content()
        }
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

// ---------------------------------------------------------------- Battery Health

@Composable
fun BatteryHealthScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    val viewModel: DetailViewModel<BatteryHealth> = viewModel(
        key = "battery-health-$carId",
        factory = viewModelFactory { initializer { DetailViewModel<BatteryHealth> { container.battery.health(carId) } } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardPage(stringResource(R.string.dash_battery_health), onBack, state.loading, state.error, viewModel::refresh) {
        val health = state.data ?: return@DashboardPage
        if (health.fromCache) StaleDataNote(health.fetchedAtMs)
        val length = health.units.length

        Panel(null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArcGauge(
                    label = stringResource(R.string.health_health),
                    value = formatNumber(health.health, 1), unit = "%",
                    fraction = health.health?.let { (it / 100).toFloat() },
                    color = ColorBattery, modifier = Modifier.weight(1f),
                )
                ArcGauge(
                    label = stringResource(R.string.health_degradation),
                    value = formatNumber(health.degradation, 1), unit = "%",
                    // Full scale at 30 %: the range that matters on a traction battery.
                    fraction = health.degradation?.let { (it / 30).toFloat() },
                    color = ColorDegradation, modifier = Modifier.weight(1f),
                )
                ArcGauge(
                    label = stringResource(R.string.health_level),
                    value = formatNumber(health.usableLevel), unit = "%",
                    fraction = health.usableLevel?.let { (it / 100).toFloat() },
                    color = ColorUsable, modifier = Modifier.weight(1f),
                )
            }
        }

        Text(stringResource(R.string.health_capacity), style = MaterialTheme.typography.titleMedium)
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.health_when_new), formatNumber(health.maxCapacity, 1), m, "kWh") },
                { m -> StatTile(stringResource(R.string.health_now), formatNumber(health.currentCapacity, 1), m, "kWh") },
                { m -> StatTile(stringResource(R.string.health_stored), formatNumber(health.storedEnergy, 1), m, "kWh") },
                { m -> StatTile(stringResource(R.string.health_efficiency), formatNumber(health.ratedEfficiency), m, "Wh/$length") },
            ),
        )

        Text(stringResource(R.string.health_range), style = MaterialTheme.typography.titleMedium)
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.health_when_new), formatNumber(health.maxRange), m, length) },
                { m -> StatTile(stringResource(R.string.health_now), formatNumber(health.currentRange), m, length) },
                {
                        m ->
                    StatTile(
                        stringResource(R.string.health_range_lost),
                        if (health.maxRange == null || health.currentRange == null) NO_VALUE
                        else formatNumber(health.maxRange - health.currentRange, 1),
                        m, length,
                    )
                },
            ),
        )

        Text(stringResource(R.string.health_charging), style = MaterialTheme.typography.titleMedium)
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.health_charges), health.charges?.toString() ?: NO_VALUE, m) },
                { m -> StatTile(stringResource(R.string.health_cycles), formatNumber(health.cycles), m) },
                { m -> StatTile(stringResource(R.string.charges_added), formatNumber(health.energyAdded), m, "kWh") },
                { m -> StatTile(stringResource(R.string.charges_used), formatNumber(health.energyUsed), m, "kWh") },
                { m -> StatTile(stringResource(R.string.charge_efficiency), formatNumber(health.chargingEfficiency, 1), m, "%") },
                { m -> StatTile("AC", formatNumber(health.acEnergy), m, "kWh") },
                { m -> StatTile("DC", formatNumber(health.dcEnergy), m, "kWh") },
            ),
        )

        Text(stringResource(R.string.health_driving), style = MaterialTheme.typography.titleMedium)
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.overview_odometer), formatNumber(health.odometer), m, length) },
                { m -> StatTile(stringResource(R.string.health_logged), formatNumber(health.logged), m, length) },
                { m -> StatTile(stringResource(R.string.health_not_logged), formatNumber(health.notLogged), m, length) },
            ),
        )

        if (health.capacityByMileage.isNotEmpty()) {
            Panel(stringResource(R.string.health_capacity_by_mileage)) {
                ScatterChart(
                    points = health.capacityByMileage,
                    color = ColorBattery,
                    xMin = 0.0,
                    xMax = niceTop(health.capacityByMileage.maxOf { it.first }),
                    formatX = { formatNumber(it) },
                    formatY = { formatNumber(it) + " kWh" },
                    formatReading = { "${formatNumber(it.first)} $length · ${formatNumber(it.second, 1)} kWh" },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Charge Level

@Composable
fun ChargeLevelScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    val viewModel: PeriodViewModel<BatterySeries> = viewModel(
        key = "charge-level-$carId",
        factory = viewModelFactory {
            initializer { PeriodViewModel(ListPeriod.QUARTER) { container.battery.chargeLevel(carId, it) } }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardPage(stringResource(R.string.dash_charge_level), onBack, state.loading, state.error, viewModel::refresh) {
        PeriodChips(state.period, viewModel::setPeriod)
        val data = state.data ?: return@DashboardPage
        if (data.fromCache) StaleDataNote(data.fetchedAtMs)
        Panel(stringResource(R.string.dash_charge_level)) {
            if (data.level.isEmpty()) NoData() else TimeLineChart(
                series = listOf(
                    ChartSeries(stringResource(R.string.drive_battery), ColorBattery, data.level),
                    ChartSeries(stringResource(R.string.level_usable), ColorUsable, data.usableLevel),
                ),
                fromMs = data.fromMs, toMs = data.toMs, yMin = 0.0, yMax = 100.0,
                formatY = { formatNumber(it) + " %" }, formatValue = { formatNumber(it) + " %" },
            )
        }
        Text(
            stringResource(R.string.level_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- Projected Range

@Composable
fun ProjectedRangeScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    val viewModel: PeriodViewModel<BatterySeries> = viewModel(
        key = "projected-range-$carId",
        factory = viewModelFactory {
            initializer { PeriodViewModel(ListPeriod.QUARTER) { container.battery.projectedRange(carId, it) } }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardPage(stringResource(R.string.dash_projected_range), onBack, state.loading, state.error, viewModel::refresh) {
        PeriodChips(state.period, viewModel::setPeriod)
        val data = state.data ?: return@DashboardPage
        if (data.fromCache) StaleDataNote(data.fetchedAtMs)
        val length = data.units.length

        @Composable
        fun chart(title: String, color: Color, points: List<adidoo.mateview.data.repo.Point>, unit: String, decimals: Int = 0) {
            Panel(title) {
                if (points.isEmpty()) NoData() else TimeLineChart(
                    series = listOf(ChartSeries(title, color, points)),
                    fromMs = data.fromMs, toMs = data.toMs,
                    formatValue = { formatNumber(it, decimals) + " " + unit },
                )
            }
        }
        chart(stringResource(R.string.projected_title), ColorRange, data.projectedRange, length)
        chart(stringResource(R.string.drive_battery), ColorBattery, data.level, "%")
        chart(stringResource(R.string.overview_temp_outside), ColorTemp, data.outsideTemp, "°" + data.units.temperature, 1)
        chart(stringResource(R.string.overview_odometer), ColorOdometer, data.odometer, length)
    }
}

// ---------------------------------------------------------------- Vampire Drain

private val IDLE_HOURS = listOf(1, 6, 12, 24)

@Composable
fun VampireDrainScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    val viewModel: PeriodViewModel<VampireDrain> = viewModel(
        key = "vampire-$carId",
        factory = viewModelFactory {
            initializer { PeriodViewModel(ListPeriod.QUARTER) { container.battery.vampireDrain(carId, it) } }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var minHours by rememberSaveable { mutableIntStateOf(6) }
    val dayUnit = stringResource(R.string.unit_day_short)

    DashboardPage(stringResource(R.string.dash_vampire_drain), onBack, state.loading, state.error, viewModel::refresh) {
        PeriodChips(state.period, viewModel::setPeriod)
        Text(stringResource(R.string.vampire_min_idle), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IDLE_HOURS.forEach { hours ->
                FilterChip(selected = hours == minHours, onClick = { minHours = hours }, label = { Text("$hours h") })
            }
        }
        val data = state.data ?: return@DashboardPage
        if (data.fromCache) StaleDataNote(data.fetchedAtMs)
        val length = data.units.length
        val periods = data.periods.filter { it.durationS >= minHours * 3600 }

        val measured = periods.filter { it.energy != null }
        val hours = measured.sumOf { it.durationS } / 3600
        val energy = measured.sumOf { it.energy ?: 0.0 }
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.vampire_periods), periods.size.toString(), m) },
                { m -> StatTile(stringResource(R.string.vampire_energy), formatNumber(energy, 1), m, "kWh") },
                { m -> StatTile(stringResource(R.string.vampire_avg_power), if (hours > 0) formatNumber(energy * 1000 / hours) else NO_VALUE, m, "W") },
                {
                        m ->
                    StatTile(
                        stringResource(R.string.vampire_per_day),
                        if (hours > 0) formatNumber(measured.sumOf { it.rangeLoss ?: 0.0 } / hours * 24, 1) else NO_VALUE, m, length,
                    )
                },
            ),
        )
        if (periods.isEmpty()) NoData()
        periods.forEach { IdleCard(it, length, dayUnit) }
    }
}

@Composable
private fun IdleCard(period: IdlePeriod, length: String, dayUnit: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDayTime(period.startMs), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(formatSpan((period.durationS * 1000).toLong(), dayUnit), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                listOfNotNull(
                    formatSocRange(period.startSoc, period.endSoc),
                    period.standby?.let { stringResource(R.string.vampire_standby, formatNumber(it * 100)) },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium, color = muted,
            )
            Text(
                if (period.reducedRange) stringResource(R.string.vampire_reduced_range)
                else listOf(
                    "−" + formatNumber(period.rangeLoss, 1) + " " + length,
                    formatNumber(period.energy, 2) + " kWh",
                    formatNumber(period.avgPower) + " W",
                    formatNumber(period.rangeLossPerHour, 2) + " $length/h",
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
