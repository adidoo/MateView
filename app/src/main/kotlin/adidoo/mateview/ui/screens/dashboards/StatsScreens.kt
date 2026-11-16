// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.screens.dashboards

import adidoo.mateview.AppContainer
import adidoo.mateview.R
import adidoo.mateview.data.net.Table
import adidoo.mateview.data.repo.GeoPoint
import adidoo.mateview.data.repo.Granularity
import adidoo.mateview.data.repo.ListPeriod
import adidoo.mateview.data.repo.Point
import adidoo.mateview.data.repo.StatsData
import adidoo.mateview.data.repo.doubleOrNull
import adidoo.mateview.data.repo.firstDouble
import adidoo.mateview.data.repo.firstLong
import adidoo.mateview.data.repo.longOrNull
import adidoo.mateview.data.repo.points
import adidoo.mateview.data.repo.stringOrNull
import adidoo.mateview.ui.components.AdaptiveTiles
import adidoo.mateview.ui.components.CategoryBarChart
import adidoo.mateview.ui.components.ChartSeries
import adidoo.mateview.ui.components.DailyRangeChart
import adidoo.mateview.ui.components.DayRange
import adidoo.mateview.ui.components.NO_VALUE
import adidoo.mateview.ui.components.Panel
import adidoo.mateview.ui.components.PeriodChips
import adidoo.mateview.ui.components.ScatterChart
import adidoo.mateview.ui.components.StaleDataNote
import adidoo.mateview.ui.components.StatTile
import adidoo.mateview.ui.components.TimeLineChart
import adidoo.mateview.ui.components.TrackMap
import adidoo.mateview.ui.components.formatCost
import adidoo.mateview.ui.components.formatMinutes
import adidoo.mateview.ui.components.formatNumber
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val ColorDrive = Color(0xFF4C8DF6)
private val ColorCharge = Color(0xFF34C38F)
private val ColorWarm = Color(0xFFE5534B)
private val ColorCold = Color(0xFF4C8DF6)
private val ColorOdometer = Color(0xFF9C7B5B)
private val ColorAmber = Color(0xFFF2B33D)

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** A statistics dashboard: loads [queries] over the chosen period and hands the tables to [content]. */
@Composable
internal fun StatsPage(
    container: AppContainer,
    carId: Int,
    name: String,
    title: String,
    onBack: () -> Unit,
    queries: Map<String, String>,
    initialPeriod: ListPeriod,
    granularity: Granularity = Granularity.MONTH,
    periods: List<ListPeriod> = listOf(ListPeriod.MONTH, ListPeriod.QUARTER, ListPeriod.YEAR, ListPeriod.ALL),
    filters: @Composable () -> Unit = {},
    content: @Composable (StatsData) -> Unit,
) {
    val viewModel: PeriodViewModel<StatsData> = viewModel(
        key = "$name-$carId-$granularity",
        factory = viewModelFactory {
            initializer { PeriodViewModel(initialPeriod) { container.stats.load(name, carId, it, queries, granularity) } }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardPage(title, onBack, state.loading, state.error, viewModel::refresh) {
        PeriodChips(state.period, viewModel::setPeriod, periods)
        filters()
        val data = state.data ?: return@DashboardPage
        if (data.fromCache) StaleDataNote(data.fetchedAtMs)
        content(data)
    }
}

/** Small labelled values that wrap onto as many lines as the width needs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Facts(facts: List<Pair<String, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.forEach { (label, value) ->
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
internal fun FactCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** A ranking: name, value, and a bar proportional to the largest value. */
@Composable
internal fun RankList(items: List<Pair<String, Double>>, color: Color, format: (Double) -> String) {
    val max = items.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (name, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(format(value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier.fillMaxWidth((value / max).toFloat().coerceIn(0.01f, 1f)).height(6.dp)
                        .clip(RoundedCornerShape(3.dp)).background(color),
                )
            }
        }
    }
}

/** Plain lines: a name on the left, its value on the right. */
@Composable
internal fun NameValueRows(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (name, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Table.rows(): IntRange = 0 until rowCount

/** Charts over "all time" start at the first point instead of 1970. */
private fun chartFrom(data: StatsData, vararg series: List<Point>): Long =
    if (data.fromMs > 0) data.fromMs else series.flatMap { it }.minOfOrNull { it.timeMs } ?: (data.toMs - DAY_MS)

// ---------------------------------------------------------------- Statistics

@Composable
fun StatisticsScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    var granularity by rememberSaveable { mutableStateOf(Granularity.MONTH) }
    StatsPage(
        container, carId, "statistics", stringResource(R.string.dash_statistics), onBack,
        queries = mapOf("rows" to "statistics.sql", "gross" to "statistics_gross.sql"),
        initialPeriod = ListPeriod.YEAR,
        granularity = granularity,
        filters = {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Granularity.entries.forEach { value ->
                    FilterChip(
                        selected = value == granularity,
                        onClick = { granularity = value },
                        label = {
                            Text(
                                stringResource(
                                    when (value) {
                                        Granularity.DAY -> R.string.per_day
                                        Granularity.WEEK -> R.string.per_week
                                        Granularity.MONTH -> R.string.per_month
                                        Granularity.YEAR -> R.string.per_year
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        },
    ) { data ->
        val table = data["rows"]
        val length = data.units.length
        if (table.rowCount == 0) NoData()
        val gross = data["gross"].let { g -> g.rows().associate { g.longOrNull(it, "time") to g.doubleOrNull(it, "consumption_gross") } }
        // The query shifts dates to local time, so they are formatted as if they were UTC.
        val pattern = when (granularity) {
            Granularity.DAY -> "EEEE d MMMM yyyy"
            Granularity.WEEK -> "'" + stringResource(R.string.week_of) + "' d MMMM yyyy"
            Granularity.MONTH -> "MMMM yyyy"
            Granularity.YEAR -> "yyyy"
        }
        val format = SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
        for (row in table.rows()) {
            val time = table.longOrNull(row, "time") ?: continue
            FactCard(format.format(Date(time)).replaceFirstChar { it.titlecase(Locale.getDefault()) }) {
                val drives = table.longOrNull(row, "drives")
                if (drives != null) {
                    Facts(
                        listOfNotNull(
                            stringResource(R.string.nav_drives) to drives.toString(),
                            stringResource(R.string.drives_distance) to formatNumber(table.doubleOrNull(row, "distance")) + " " + length,
                            stringResource(R.string.drives_duration) to formatMinutes(table.doubleOrNull(row, "duration_min")),
                            stringResource(R.string.drives_consumption) to formatNumber(table.doubleOrNull(row, "consumption")) + " Wh/" + length,
                            gross[time]?.let { stringResource(R.string.stats_gross) to formatNumber(it) + " Wh/" + length },
                            stringResource(R.string.drives_energy) to formatNumber(table.doubleOrNull(row, "energy"), 1) + " kWh",
                            stringResource(R.string.drive_outside_temp) to formatNumber(table.doubleOrNull(row, "outside_temp"), 1) + " °" + data.units.temperature,
                        ),
                    )
                }
                val charges = table.longOrNull(row, "charges")
                if (charges != null) {
                    Facts(
                        listOfNotNull(
                            stringResource(R.string.nav_charges) to charges.toString(),
                            stringResource(R.string.charges_added) to formatNumber(table.doubleOrNull(row, "energy_added"), 1) + " kWh",
                            stringResource(R.string.charges_used) to formatNumber(table.doubleOrNull(row, "energy_used"), 1) + " kWh",
                            table.doubleOrNull(row, "cost")?.let { stringResource(R.string.charge_cost) to formatCost(it) },
                        ),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Drive Stats

@Composable
fun DriveStatsScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "drive-stats", stringResource(R.string.dash_drive_stats), onBack,
        queries = mapOf("summary" to "drive_summary.sql", "histogram" to "speed_histogram.sql", "destinations" to "destinations.sql"),
        initialPeriod = ListPeriod.YEAR,
    ) { data ->
        val summary = data["summary"]
        val length = data.units.length
        val distance = summary.firstDouble("distance")
        val energy = summary.firstDouble("energy")
        val mileage = summary.firstDouble("mileage")
        // Days actually covered: the period, or since the first drive when it is more recent.
        val first = summary.firstLong("first_drive")
        val days = first?.let { ((data.toMs - maxOf(data.fromMs, it)) / DAY_MS.toDouble()).coerceAtLeast(1.0) }
        fun perDay(value: Double?) = if (value == null || days == null) null else value / days

        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.nav_drives), summary.firstLong("drives")?.toString() ?: NO_VALUE, m) },
                { m -> StatTile(stringResource(R.string.drives_distance), formatNumber(distance), m, length) },
                { m -> StatTile(stringResource(R.string.drives_energy), formatNumber(energy, 1), m, "kWh") },
                { m -> StatTile(stringResource(R.string.drives_duration), formatMinutes(summary.firstDouble("duration_min")), m) },
                { m -> StatTile(stringResource(R.string.stats_median_distance), formatNumber(summary.firstDouble("median_distance"), 1), m, length) },
                { m -> StatTile(stringResource(R.string.drive_speed_max), formatNumber(summary.firstDouble("speed_max")), m, data.units.speed) },
                { m -> StatTile(stringResource(R.string.stats_distance_per_day), formatNumber(perDay(distance), 1), m, length) },
                { m -> StatTile(stringResource(R.string.stats_energy_per_day), formatNumber(perDay(energy), 1), m, "kWh") },
                { m -> StatTile(stringResource(R.string.stats_monthly_mileage), formatNumber(perDay(mileage)?.let { it * 365 / 12 }), m, length) },
                { m -> StatTile(stringResource(R.string.stats_annual_mileage), formatNumber(perDay(mileage)?.let { it * 365 }), m, length) },
            ),
        )

        val histogram = data["histogram"]
        if (histogram.rowCount > 0) {
            val speeds = histogram.rows().map { histogram.doubleOrNull(it, "speed") ?: 0.0 }
            val minutes = histogram.rows().map { (histogram.doubleOrNull(it, "seconds") ?: 0.0) / 60 }
            val total = minutes.sum().takeIf { it > 0 } ?: 1.0
            Panel(stringResource(R.string.chart_speed_histogram, data.units.speed)) {
                CategoryBarChart(
                    labels = speeds.map { formatNumber(it) },
                    values = minutes.map { it * 100 / total },
                    color = ColorDrive,
                    formatY = { formatNumber(it) + " %" },
                    formatValue = { "${formatNumber(speeds[it])} ${data.units.speed} : ${formatMinutes(minutes[it])}" },
                )
            }
        }

        val destinations = data["destinations"]
        if (destinations.rowCount > 0) {
            Panel(stringResource(R.string.stats_top_destinations)) {
                RankList(
                    destinations.rows().map { (destinations.stringOrNull(it, "name") ?: NO_VALUE) to (destinations.doubleOrNull(it, "visited") ?: 0.0) },
                    ColorDrive,
                ) { formatNumber(it) }
            }
        }
    }
}

// ---------------------------------------------------------------- Charging Stats

@Composable
fun ChargingStatsScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "charging-stats", stringResource(R.string.dash_charging_stats), onBack,
        queries = mapOf(
            "summary" to "charging_summary.sql", "ac_dc" to "charging_ac_dc.sql",
            "stations" to "charging_stations.sql", "soc" to "charging_soc.sql",
            "costs" to "charging_costs.sql", "map" to "charging_map.sql", "stations_cost" to "charging_stations_cost.sql",
            "dc_curve" to "charging_dc_curve.sql", "soc_start" to "charging_soc_start.sql", "soc_end" to "charging_soc_end.sql",
        ),
        initialPeriod = ListPeriod.ALL,
    ) { data ->
        val summary = data["summary"]
        val length = data.units.length
        val added = summary.firstDouble("energy_added")
        val used = summary.firstDouble("energy_used")
        val cost = summary.firstDouble("cost")
        val distance = summary.firstDouble("distance")
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.nav_charges), summary.firstLong("charges")?.toString() ?: NO_VALUE, m) },
                { m -> StatTile(stringResource(R.string.charges_added), formatNumber(added, 1), m, "kWh") },
                { m -> StatTile(stringResource(R.string.charges_used), formatNumber(used, 1), m, "kWh") },
                { m -> StatTile(stringResource(R.string.charge_efficiency), formatNumber(if (added != null && used != null && used > 0) added * 100 / used else null, 1), m, "%") },
                { m -> StatTile(stringResource(R.string.drives_duration), formatMinutes(summary.firstDouble("duration_min")), m) },
                { m -> StatTile(stringResource(R.string.charges_cost), formatCost(cost), m) },
                { m -> StatTile(stringResource(R.string.stats_cost_per_kwh), formatNumber(if (cost != null && used != null && used > 0) cost / used else null, 3), m) },
                {
                        m ->
                    StatTile(
                        stringResource(R.string.stats_cost_per_100, length),
                        formatNumber(if (cost != null && distance != null && distance > 0) cost / distance * 100 else null, 2), m,
                    )
                },
            ),
        )

        // Only when costs were entered in TeslaMate.
        val costs = data["costs"]
        if (cost != null && cost > 0) {
            AdaptiveTiles(
                listOf(
                    { m -> StatTile(stringResource(R.string.stats_supercharger_cost), formatCost(costs.firstDouble("supercharger_cost")), m) },
                    { m -> StatTile(stringResource(R.string.stats_cost_per_kwh_ac), formatNumber(costs.firstDouble("cost_per_kwh_ac"), 3), m) },
                    { m -> StatTile(stringResource(R.string.stats_cost_per_kwh_dc), formatNumber(costs.firstDouble("cost_per_kwh_dc"), 3), m) },
                ),
            )
        }

        val map = data["map"]
        val places = map.rows().mapNotNull { row ->
            val latitude = map.doubleOrNull(row, "latitude") ?: return@mapNotNull null
            val longitude = map.doubleOrNull(row, "longitude") ?: return@mapNotNull null
            GeoPoint(latitude, longitude) to (map.doubleOrNull(row, "energy") ?: 0.0)
        }
        if (places.isNotEmpty()) {
            Panel(stringResource(R.string.stats_charging_map)) {
                TrackMap(places.map { it.first }, color = ColorCharge, showLine = false, weights = places.map { it.second })
            }
        }

        val acDc = data["ac_dc"]
        if (acDc.rowCount > 0) {
            Panel(stringResource(R.string.stats_ac_dc)) {
                acDc.rows().forEach { row ->
                    Text(acDc.stringOrNull(row, "kind").orEmpty(), style = MaterialTheme.typography.titleSmall)
                    Facts(
                        listOfNotNull(
                            stringResource(R.string.nav_charges) to (acDc.longOrNull(row, "charges")?.toString() ?: NO_VALUE),
                            stringResource(R.string.charges_used) to formatNumber(acDc.doubleOrNull(row, "energy"), 1) + " kWh",
                            stringResource(R.string.drives_duration) to formatMinutes(acDc.doubleOrNull(row, "duration_min")),
                            acDc.doubleOrNull(row, "cost")?.let { stringResource(R.string.charge_cost) to formatCost(it) },
                        ),
                    )
                }
            }
        }

        val start = data["soc"].points("time", "start_battery_level")
        val end = data["soc"].points("time", "end_battery_level")
        if (start.isNotEmpty()) {
            Panel(stringResource(R.string.stats_charge_delta)) {
                TimeLineChart(
                    series = listOf(
                        ChartSeries(stringResource(R.string.stats_soc_start), ColorAmber, start),
                        ChartSeries(stringResource(R.string.stats_soc_end), ColorCharge, end),
                    ),
                    fromMs = chartFrom(data, start), toMs = data.toMs, yMin = 0.0, yMax = 100.0,
                    formatY = { formatNumber(it) + " %" }, formatValue = { formatNumber(it) + " %" },
                )
            }
        }

        for ((name, title, color) in listOf(
            Triple("soc_start", R.string.stats_soc_at_start, ColorAmber),
            Triple("soc_end", R.string.stats_soc_at_end, ColorCharge),
        )) {
            val bands = data[name]
            if (bands.rowCount == 0) continue
            // Every 5 % band between the lowest and the highest, including the empty ones.
            val found = bands.rows().associate { (bands.doubleOrNull(it, "soc") ?: 0.0).toInt() to (bands.doubleOrNull(it, "n") ?: 0.0) }
            val levels = (found.keys.min()..found.keys.max() step 5).map { it.toDouble() }
            val counts = levels.map { found[it.toInt()] ?: 0.0 }
            Panel(stringResource(title)) {
                CategoryBarChart(
                    labels = levels.map { formatNumber(it) },
                    values = counts,
                    color = color,
                    formatValue = { "${formatNumber(levels[it])} % : ${formatNumber(counts[it])}" },
                )
            }
        }

        val curve = data["dc_curve"]
        if (curve.rowCount > 0) {
            Panel(stringResource(R.string.stats_dc_curve)) {
                ScatterChart(
                    points = curve.rows().mapNotNull { row ->
                        (curve.doubleOrNull(row, "soc") ?: return@mapNotNull null) to (curve.doubleOrNull(row, "power") ?: return@mapNotNull null)
                    },
                    color = ColorCharge,
                    formatX = { formatNumber(it) + " %" },
                    formatY = { formatNumber(it) + " kW" },
                )
            }
        }

        val stations = data["stations"]
        if (stations.rowCount > 0) {
            Panel(stringResource(R.string.stats_top_stations)) {
                RankList(
                    stations.rows().map { (stations.stringOrNull(it, "location") ?: NO_VALUE) to (stations.doubleOrNull(it, "energy") ?: 0.0) },
                    ColorCharge,
                ) { formatNumber(it, 1) + " kWh" }
            }
        }

        val byCost = data["stations_cost"]
        if (byCost.rowCount > 0) {
            Panel(stringResource(R.string.stats_top_stations_cost)) {
                RankList(
                    byCost.rows().map { (byCost.stringOrNull(it, "location") ?: NO_VALUE) to (byCost.doubleOrNull(it, "cost") ?: 0.0) },
                    ColorAmber,
                ) { formatCost(it) }
            }
        }
    }
}

// ---------------------------------------------------------------- Efficiency

@Composable
fun EfficiencyScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "efficiency", stringResource(R.string.dash_efficiency), onBack,
        queries = mapOf(
            "summary" to "efficiency_summary.sql", "by_temp" to "efficiency_by_temp.sql", "derived" to "derived_efficiency.sql",
            "gross" to "overview/consumption_gross.sql",
        ),
        initialPeriod = ListPeriod.ALL,
    ) { data ->
        val summary = data["summary"]
        val length = data.units.length
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.overview_consumption_net), formatNumber(summary.firstDouble("consumption_net")), m, "Wh/$length") },
                { m -> StatTile(stringResource(R.string.overview_consumption_gross), formatNumber(data["gross"].firstDouble("consumption_gross")), m, "Wh/$length") },
                { m -> StatTile(stringResource(R.string.health_efficiency), formatNumber(summary.firstDouble("car_efficiency")), m, "Wh/$length") },
                { m -> StatTile(stringResource(R.string.health_logged), formatNumber(summary.firstDouble("distance")), m, length) },
            ),
        )

        val byTemp = data["by_temp"]
        if (byTemp.rowCount > 0) {
            Text(stringResource(R.string.stats_by_temperature), style = MaterialTheme.typography.titleMedium)
            val temps = byTemp.rows().map { byTemp.doubleOrNull(it, "outside_temp") ?: 0.0 }
            val consumption = byTemp.rows().map { byTemp.doubleOrNull(it, "consumption") ?: 0.0 }
            Panel(stringResource(R.string.stats_consumption_by_temp, length)) {
                // Coldest on the left.
                CategoryBarChart(
                    labels = temps.reversed().map { formatNumber(it) + "°" },
                    values = consumption.reversed(),
                    color = ColorDrive,
                    formatValue = { index ->
                        "${formatNumber(temps.reversed()[index])} °${data.units.temperature} : ${formatNumber(consumption.reversed()[index])} Wh/$length"
                    },
                )
            }
            for (row in byTemp.rows()) {
                FactCard(formatNumber(temps[row]) + " °" + data.units.temperature) {
                    Facts(
                        listOf(
                            stringResource(R.string.drives_consumption) to formatNumber(consumption[row]) + " Wh/" + length,
                            stringResource(R.string.dash_efficiency) to formatNumber(byTemp.doubleOrNull(row, "efficiency")?.let { it * 100 }, 1) + " %",
                            stringResource(R.string.drives_distance) to formatNumber(byTemp.doubleOrNull(row, "distance")) + " " + length,
                            stringResource(R.string.drive_speed_avg) to formatNumber(byTemp.doubleOrNull(row, "speed_avg")) + " " + data.units.speed,
                        ),
                    )
                }
            }
        }

        val derived = data["derived"]
        if (derived.rowCount > 0) {
            Panel(stringResource(R.string.stats_derived_efficiency)) {
                derived.rows().forEach { row ->
                    Text(
                        stringResource(
                            R.string.stats_derived_line,
                            formatNumber(derived.doubleOrNull(row, "efficiency")) + " Wh/" + length,
                            derived.longOrNull(row, "count")?.toString() ?: NO_VALUE,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Mileage

@Composable
fun MileageScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "mileage", stringResource(R.string.dash_mileage), onBack,
        queries = mapOf("mileage" to "battery/mileage.sql"),
        initialPeriod = ListPeriod.ALL,
    ) { data ->
        val points = data["mileage"].points("time", "odometer")
        val length = data.units.length
        if (points.isEmpty()) return@StatsPage NoData()
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.overview_odometer), formatNumber(points.last().value), m, length) },
                { m -> StatTile(stringResource(R.string.stats_driven_in_period), formatNumber(points.last().value - points.first().value), m, length) },
            ),
        )
        Panel(stringResource(R.string.dash_mileage)) {
            TimeLineChart(
                series = listOf(ChartSeries(stringResource(R.string.overview_odometer), ColorOdometer, points)),
                fromMs = chartFrom(data, points), toMs = data.toMs, yMin = 0.0,
                formatValue = { formatNumber(it) + " " + length },
            )
        }
    }
}

// ---------------------------------------------------------------- Temperatures

@Composable
fun TemperaturesScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "temperatures", stringResource(R.string.dash_temperatures), onBack,
        queries = mapOf(
            "summary" to "temperature_summary.sql", "series" to "temperature_series.sql", "daily" to "temperature_daily.sql",
            "charging" to "temperature_charging.sql",
        ),
        initialPeriod = ListPeriod.MONTH,
    ) { data ->
        val summary = data["summary"]
        val unit = "°" + data.units.temperature
        fun range(prefix: String) = listOf("min", "avg", "max").map { summary.firstDouble("${prefix}_$it") }
        @Composable
        fun rangePanel(title: String, values: List<Double?>) {
            Panel(title) {
                Facts(
                    listOf(
                        stringResource(R.string.stats_min) to formatNumber(values[0], 1) + " " + unit,
                        stringResource(R.string.stats_avg) to formatNumber(values[1], 1) + " " + unit,
                        stringResource(R.string.stats_max) to formatNumber(values[2], 1) + " " + unit,
                    ),
                )
            }
        }
        rangePanel(stringResource(R.string.overview_temp_outside), range("outside"))
        rangePanel(stringResource(R.string.overview_temp_inside), range("inside"))

        val inside = data["series"].points("time", "inside_temp")
        val outside = data["series"].points("time", "outside_temp")
        val setpoint = data["series"].points("time", "setpoint")
        Panel(stringResource(R.string.stats_temperature_history)) {
            if (inside.isEmpty() && outside.isEmpty()) NoData() else TimeLineChart(
                series = listOfNotNull(
                    ChartSeries(stringResource(R.string.overview_temp_outside), ColorCold, outside),
                    ChartSeries(stringResource(R.string.overview_temp_inside), ColorWarm, inside),
                    ChartSeries(stringResource(R.string.stats_setpoint), ColorAmber, setpoint).takeIf { setpoint.isNotEmpty() },
                ),
                fromMs = chartFrom(data, inside, outside), toMs = data.toMs,
                formatValue = { formatNumber(it, 1) + " " + unit },
            )
        }

        val charging = data["charging"].points("time", "outside_temp")
        if (charging.isNotEmpty()) {
            Panel(stringResource(R.string.stats_temp_charging)) {
                TimeLineChart(
                    series = listOf(ChartSeries(stringResource(R.string.overview_temp_outside), ColorCharge, charging)),
                    fromMs = chartFrom(data, charging), toMs = data.toMs,
                    formatValue = { formatNumber(it, 1) + " " + unit },
                )
            }
        }

        val daily = data["daily"]
        val days = daily.rows().mapNotNull { row ->
            DayRange(
                timeMs = daily.longOrNull(row, "time") ?: return@mapNotNull null,
                min = daily.doubleOrNull(row, "outside_min") ?: return@mapNotNull null,
                max = daily.doubleOrNull(row, "outside_max") ?: return@mapNotNull null,
                low = daily.doubleOrNull(row, "outside_p15") ?: return@mapNotNull null,
                high = daily.doubleOrNull(row, "outside_p85") ?: return@mapNotNull null,
            )
        }
        Panel(stringResource(R.string.stats_daily_outside)) {
            if (days.isEmpty()) NoData() else DailyRangeChart(
                days = days,
                fromMs = if (data.fromMs > 0) data.fromMs else days.first().timeMs, toMs = data.toMs,
                color = ColorCold,
                rangeLabel = stringResource(R.string.stats_daily_range),
                bandLabel = stringResource(R.string.stats_daily_band),
                formatValue = { formatNumber(it, 1) + " " + unit },
            )
        }
    }
}
