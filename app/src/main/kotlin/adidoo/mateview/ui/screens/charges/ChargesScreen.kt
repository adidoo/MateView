// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.screens.charges

import adidoo.mateview.AppContainer
import adidoo.mateview.MateViewApplication
import adidoo.mateview.R
import adidoo.mateview.data.repo.ChargeDetail
import adidoo.mateview.data.repo.ChargeList
import adidoo.mateview.data.repo.ChargeSummary
import adidoo.mateview.data.repo.ListPeriod
import adidoo.mateview.ui.components.AdaptiveTiles
import adidoo.mateview.ui.components.ChartSeries
import adidoo.mateview.ui.components.DetailHeader
import adidoo.mateview.ui.components.ErrorPanel
import adidoo.mateview.ui.components.ListDetailLayout
import adidoo.mateview.ui.components.NO_VALUE
import adidoo.mateview.ui.components.NotConnectedMessage
import adidoo.mateview.ui.components.Panel
import adidoo.mateview.ui.components.PeriodChips
import adidoo.mateview.ui.components.ScatterChart
import adidoo.mateview.ui.components.SearchField
import adidoo.mateview.ui.components.StaleDataNote
import adidoo.mateview.ui.components.StatTile
import adidoo.mateview.ui.components.TimeLineChart
import adidoo.mateview.ui.components.TrackMap
import adidoo.mateview.ui.components.formatCost
import adidoo.mateview.ui.components.formatDayTime
import adidoo.mateview.ui.components.formatMinutes
import adidoo.mateview.ui.components.formatNumber
import adidoo.mateview.ui.components.formatSocRange
import adidoo.mateview.ui.screens.CarListViewModel
import adidoo.mateview.ui.screens.DetailViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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

private val ColorPower = Color(0xFF4C8DF6)
private val ColorBattery = Color(0xFF34C38F)
private val ColorVoltage = Color(0xFFF2B33D)
private val ColorCurrent = Color(0xFFE0559A)
private val ColorPlace = Color(0xFF34C38F)

/** A gap of more than five minutes between two samples is drawn as a break. */
private const val CHARGE_GAP_MS = 5 * 60_000L

private enum class TypeFilter { ALL, AC, DC }

class ChargesViewModel(container: AppContainer) : CarListViewModel<ChargeList>(container) {
    override suspend fun fetch(carId: Int, period: ListPeriod) = container.charges.list(carId, period)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargesScreen(onOpenSettings: () -> Unit) {
    val container = (LocalContext.current.applicationContext as MateViewApplication).container
    val viewModel: ChargesViewModel = viewModel(factory = viewModelFactory { initializer { ChargesViewModel(container) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(TypeFilter.ALL) }

    when (state.connected) {
        null -> return
        false -> return Box(Modifier.safeDrawingPadding()) { NotConnectedMessage(onOpenSettings) }
        true -> Unit
    }
    val carId = state.carId ?: return

    ListDetailLayout(
        hasSelection = state.selectedId != null,
        onCloseDetail = { viewModel.select(null) },
        emptyDetail = stringResource(R.string.charges_select),
        list = { modifier ->
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = viewModel::refresh,
                modifier = modifier.safeDrawingPadding(),
            ) {
                val charges = state.data?.charges.orEmpty().filter { charge ->
                    (query.isBlank() || charge.address?.contains(query.trim(), ignoreCase = true) == true) &&
                        when (type) {
                            TypeFilter.ALL -> true
                            TypeFilter.AC -> !charge.isDc
                            TypeFilter.DC -> charge.isDc
                        }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Text(stringResource(R.string.nav_charges), style = MaterialTheme.typography.headlineSmall) }
                    item { PeriodChips(state.period, viewModel::setPeriod) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TypeFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = filter == type,
                                    onClick = { type = filter },
                                    label = {
                                        Text(
                                            when (filter) {
                                                TypeFilter.ALL -> stringResource(R.string.charges_type_all)
                                                TypeFilter.AC -> "AC"
                                                TypeFilter.DC -> "DC"
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                    item { SearchField(query) { query = it } }
                    state.data?.takeIf { it.fromCache }?.let { item { StaleDataNote(it.fetchedAtMs) } }
                    state.error?.let { item { ErrorPanel(it, viewModel::refresh) } }
                    if (state.data != null) item { ChargesSummary(charges) }
                    if (state.data != null && charges.isEmpty()) {
                        item { Text(stringResource(R.string.charges_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(charges, key = { it.id }) { charge ->
                        ChargeCard(charge, selected = charge.id == state.selectedId) { viewModel.select(charge.id) }
                    }
                }
            }
        },
        detail = { modifier, showBack ->
            val id = state.selectedId ?: return@ListDetailLayout
            ChargeDetailPane(container, carId, id, showBack, onBack = { viewModel.select(null) }, modifier = modifier)
        },
    )
}

@Composable
private fun ChargesSummary(charges: List<ChargeSummary>) {
    val added = charges.sumOf { it.energyAdded ?: 0.0 }
    val used = charges.sumOf { it.energyUsed ?: 0.0 }
    val costs = charges.mapNotNull { it.cost }
    val durations = charges.mapNotNull { it.durationMin }
    AdaptiveTiles(
        listOf(
            { m -> StatTile(stringResource(R.string.charges_added), formatNumber(added, 1), m, "kWh") },
            { m -> StatTile(stringResource(R.string.charges_used), formatNumber(used, 1), m, "kWh") },
            { m -> StatTile(stringResource(R.string.charges_cost), if (costs.isEmpty()) NO_VALUE else formatCost(costs.sum()), m) },
            {
                    m ->
                StatTile(
                    stringResource(R.string.charges_avg_duration),
                    if (durations.isEmpty()) NO_VALUE else formatMinutes(durations.average()), m,
                )
            },
        ),
    )
}

@Composable
private fun ChargeCard(charge: ChargeSummary, selected: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatDayTime(charge.startMs), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(formatNumber(charge.energyAdded, 1) + " kWh", style = MaterialTheme.typography.titleMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onClick, label = { Text(if (charge.isDc) "DC" else "AC") })
                Text(
                    charge.address ?: NO_VALUE,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                listOfNotNull(
                    if (charge.endMs == null) stringResource(R.string.in_progress) else formatMinutes(charge.durationMin),
                    formatSocRange(charge.startSoc, charge.endSoc),
                    charge.cost?.let { formatCost(it) },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChargeDetailPane(
    container: AppContainer,
    carId: Int,
    chargeId: Int,
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val viewModel: DetailViewModel<ChargeDetail> = viewModel(
        key = "charge-$carId-$chargeId",
        factory = viewModelFactory { initializer { DetailViewModel { container.charges.detail(carId, chargeId) } } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    PullToRefreshBox(isRefreshing = state.loading, onRefresh = viewModel::refresh, modifier = modifier.safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val detail = state.data
            val charge = detail?.summary
            DetailHeader(
                title = charge?.let { formatDayTime(it.startMs) } ?: stringResource(R.string.nav_charges),
                subtitle = charge?.let { listOfNotNull(it.address, if (it.isDc) "DC" else "AC").joinToString(" · ") },
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
            ChargeDetailContent(detail)
        }
    }
}

@Composable
private fun ChargeDetailContent(detail: ChargeDetail) {
    val charge = detail.summary
    val units = detail.units
    val from = detail.power.firstOrNull()?.timeMs ?: charge.startMs
    val to = charge.endMs ?: detail.power.lastOrNull()?.timeMs ?: from + 60_000L

    detail.location?.let { TrackMap(listOf(it), color = ColorPlace) }

    AdaptiveTiles(
        listOf(
            { m -> StatTile(stringResource(R.string.charges_added), formatNumber(charge.energyAdded, 2), m, "kWh") },
            { m -> StatTile(stringResource(R.string.charges_used), formatNumber(charge.energyUsed, 2), m, "kWh") },
            { m -> StatTile(stringResource(R.string.charge_efficiency), formatNumber(detail.efficiency), m, "%") },
            { m -> StatTile(stringResource(R.string.charge_cost), formatCost(charge.cost), m) },
            { m -> StatTile(stringResource(R.string.drives_duration), formatMinutes(charge.durationMin), m) },
            { m -> StatTile(stringResource(R.string.drive_battery), formatSocRange(charge.startSoc, charge.endSoc), m) },
            {
                    m ->
                StatTile(
                    stringResource(R.string.chart_range),
                    if (detail.startRange == null) NO_VALUE
                    else "${formatNumber(detail.startRange)} → ${formatNumber(detail.endRange)}",
                    m, units.length,
                )
            },
            { m -> StatTile(stringResource(R.string.charge_power_avg), formatNumber(detail.powerAvg, 1), m, "kW") },
            { m -> StatTile(stringResource(R.string.drive_power_max), formatNumber(detail.powerMax, 1), m, "kW") },
            { m -> StatTile(stringResource(R.string.drive_outside_temp), formatNumber(charge.outsideTemp, 1), m, "°" + units.temperature) },
        ),
    )

    if (detail.power.isNotEmpty()) {
        Panel(stringResource(R.string.chart_power)) {
            TimeLineChart(
                series = listOf(ChartSeries(stringResource(R.string.series_power), ColorPower, detail.power)),
                fromMs = from, toMs = to, yMin = 0.0, maxGapMs = CHARGE_GAP_MS,
                formatValue = { formatNumber(it, 1) + " kW" },
            )
        }
    }
    if (detail.soc.isNotEmpty()) {
        Panel(stringResource(R.string.chart_battery)) {
            TimeLineChart(
                series = listOf(ChartSeries(stringResource(R.string.drive_battery), ColorBattery, detail.soc)),
                fromMs = from, toMs = to, yMin = 0.0, yMax = 100.0, maxGapMs = CHARGE_GAP_MS,
                formatY = { formatNumber(it) + " %" }, formatValue = { formatNumber(it) + " %" },
            )
        }
    }
    if (detail.voltage.isNotEmpty()) {
        Panel(stringResource(R.string.chart_ac_voltage)) {
            TimeLineChart(
                series = listOf(ChartSeries(stringResource(R.string.chart_ac_voltage), ColorVoltage, detail.voltage)),
                fromMs = from, toMs = to, yMin = 0.0, maxGapMs = CHARGE_GAP_MS,
                formatValue = { formatNumber(it) + " V" },
            )
        }
    }
    if (detail.current.isNotEmpty()) {
        Panel(stringResource(R.string.chart_ac_current)) {
            TimeLineChart(
                series = listOf(ChartSeries(stringResource(R.string.chart_ac_current), ColorCurrent, detail.current)),
                fromMs = from, toMs = to, yMin = 0.0, maxGapMs = CHARGE_GAP_MS,
                formatValue = { formatNumber(it) + " A" },
            )
        }
    }
    if (detail.curve.isNotEmpty()) {
        Panel(stringResource(R.string.chart_charging_curve)) {
            ScatterChart(
                points = detail.curve,
                color = ColorPower,
                formatX = { formatNumber(it) + " %" },
                formatY = { formatNumber(it) + " kW" },
                formatReading = { "${formatNumber(it.first)} % · ${formatNumber(it.second, 1)} kW" },
            )
        }
    }
}
