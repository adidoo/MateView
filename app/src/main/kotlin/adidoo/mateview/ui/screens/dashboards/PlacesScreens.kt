// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.screens.dashboards

import adidoo.mateview.AppContainer
import adidoo.mateview.R
import adidoo.mateview.data.repo.CarState
import adidoo.mateview.data.repo.GeoPoint
import adidoo.mateview.data.repo.ListPeriod
import adidoo.mateview.data.repo.StateChange
import adidoo.mateview.data.repo.doubleOrNull
import adidoo.mateview.data.repo.firstDouble
import adidoo.mateview.data.repo.firstLong
import adidoo.mateview.data.repo.firstString
import adidoo.mateview.data.repo.longOrNull
import adidoo.mateview.data.repo.points
import adidoo.mateview.data.repo.stringOrNull
import adidoo.mateview.ui.components.AdaptiveTiles
import adidoo.mateview.ui.components.ChartSeries
import adidoo.mateview.ui.components.DayHeader
import adidoo.mateview.ui.components.NO_VALUE
import adidoo.mateview.ui.components.Panel
import adidoo.mateview.ui.components.StatTile
import adidoo.mateview.ui.components.StateBand
import adidoo.mateview.ui.components.TimeLineChart
import adidoo.mateview.ui.components.TrackMap
import adidoo.mateview.ui.components.dayOf
import adidoo.mateview.ui.components.formatCost
import adidoo.mateview.ui.components.formatDayTime
import adidoo.mateview.ui.components.formatFullDateTime
import adidoo.mateview.ui.components.formatMinutes
import adidoo.mateview.ui.components.formatNumber
import adidoo.mateview.ui.components.formatSocRange
import adidoo.mateview.ui.components.formatSpan
import adidoo.mateview.ui.components.formatTime
import adidoo.mateview.ui.screens.overview.stateColor
import adidoo.mateview.ui.screens.overview.stateLabel
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val SHORT_PERIODS = listOf(ListPeriod.DAY, ListPeriod.WEEK, ListPeriod.MONTH)
private val ColorBattery = Color(0xFF34C38F)
private val ColorRange = Color(0xFF4C8DF6)
private val ColorElevation = Color(0xFF9C7B5B)
private val ColorPlaces = Color(0xFF4C8DF6)

private const val MINUTE_MS = 60_000L
private const val DAY_MS = 24 * 60 * MINUTE_MS

/** A row of the timeline: an icon, what happened, and how long it lasted. */
@Composable
private fun EventCard(icon: ImageVector, tint: Color, time: String, title: String, detail: String?, duration: String?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(time, style = MaterialTheme.typography.labelMedium, color = muted)
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = muted)
            }
            if (duration != null) Text(duration, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------------------------------------------------------------- Timeline

private class Event(
    val kind: String, val startMs: Long, val endMs: Long?, val startPlace: String?, val endPlace: String?,
    val distance: Double?, val energy: Double?, val startSoc: Double?, val endSoc: Double?,
)

@Composable
fun TimelineScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "timeline", stringResource(R.string.dash_timeline), onBack,
        queries = mapOf("events" to "places/timeline.sql"),
        initialPeriod = ListPeriod.WEEK, periods = SHORT_PERIODS,
    ) { data ->
        val table = data["events"]
        val length = data.units.length
        val dayUnit = stringResource(R.string.unit_day_short)
        val events = (0 until table.rowCount).mapNotNull { row ->
            Event(
                kind = table.stringOrNull(row, "kind") ?: return@mapNotNull null,
                startMs = table.longOrNull(row, "start_date") ?: return@mapNotNull null,
                endMs = table.longOrNull(row, "end_date"),
                startPlace = table.stringOrNull(row, "start_place")?.takeIf { it.isNotBlank() },
                endPlace = table.stringOrNull(row, "end_place")?.takeIf { it.isNotBlank() },
                distance = table.doubleOrNull(row, "distance"),
                energy = table.doubleOrNull(row, "energy"),
                startSoc = table.doubleOrNull(row, "start_soc"),
                endSoc = table.doubleOrNull(row, "end_soc"),
            )
        }
        if (events.isEmpty()) return@StatsPage NoData()

        var shownDay: java.time.LocalDate? = null
        events.forEachIndexed { index, event ->
            val day = dayOf(event.startMs)
            if (day != shownDay) {
                shownDay = day
                val driven = events.filter { it.kind == "drive" && dayOf(it.startMs) == day }.sumOf { it.distance ?: 0.0 }
                DayHeader(day, formatNumber(driven) + " " + length)
            }
            val time = formatTime(event.startMs) + " – " + (event.endMs?.let { formatTime(it) } ?: "…")
            val duration = event.endMs?.let { formatSpan(it - event.startMs, dayUnit) }
            when (event.kind) {
                "drive" -> EventCard(
                    Icons.Outlined.DirectionsCar, stateColor(CarState.DRIVING), time,
                    "${event.startPlace ?: NO_VALUE} → ${event.endPlace ?: stringResource(R.string.in_progress)}",
                    listOf(
                        formatNumber(event.distance, 1) + " " + length,
                        formatNumber(event.energy, 2) + " kWh",
                        formatSocRange(event.startSoc, event.endSoc),
                    ).joinToString("  ·  "),
                    duration,
                )
                "charge" -> EventCard(
                    Icons.Outlined.EvStation, stateColor(CarState.CHARGING), time,
                    event.startPlace ?: stringResource(R.string.state_charging),
                    if (event.endMs == null) stringResource(R.string.in_progress)
                    else listOf("+" + formatNumber(event.energy, 1) + " kWh", formatSocRange(event.startSoc, event.endSoc)).joinToString("  ·  "),
                    duration,
                )
                else -> EventCard(
                    Icons.Outlined.SystemUpdate, stateColor(CarState.UPDATING), time,
                    stringResource(R.string.state_updating) + " " + event.startPlace.orEmpty(), null, duration,
                )
            }
            // Parked between this event and the previous one (the list runs from newest to oldest).
            val older = events.getOrNull(index + 1)
            val olderEnd = older?.endMs
            if (older != null && olderEnd != null && event.startMs - olderEnd > 2 * MINUTE_MS) {
                EventCard(
                    Icons.Outlined.LocalParking, stateColor(CarState.OFFLINE),
                    // Parked overnight or longer: say which day it started.
                    (if (dayOf(olderEnd) == day) formatTime(olderEnd) else formatDayTime(olderEnd)) + " – " + formatTime(event.startMs),
                    older.endPlace ?: older.startPlace ?: stringResource(R.string.timeline_parked),
                    null, formatSpan(event.startMs - olderEnd, dayUnit),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Trip

@Composable
fun TripScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "trip", stringResource(R.string.dash_trip), onBack,
        queries = mapOf(
            "summary" to "places/trip_summary.sql", "track" to "places/trip_track.sql", "level" to "places/trip_level.sql",
            "elevation" to "places/trip_elevation.sql", "gross" to "overview/consumption_gross.sql",
            "drives" to "drives/list.sql", "charges" to "charges/list.sql",
        ),
        initialPeriod = ListPeriod.DAY, periods = SHORT_PERIODS,
    ) { data ->
        val summary = data["summary"]
        val length = data.units.length
        val track = data["track"].let { table ->
            (0 until table.rowCount).mapNotNull { row ->
                GeoPoint(table.doubleOrNull(row, "latitude") ?: return@mapNotNull null, table.doubleOrNull(row, "longitude") ?: return@mapNotNull null)
            }
        }
        if (track.isNotEmpty()) TrackMap(track)

        val distance = summary.firstDouble("distance")
        val energy = summary.firstDouble("energy")
        val driveMin = summary.firstDouble("drive_min")
        val cost = summary.firstDouble("cost")
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.drives_distance), formatNumber(distance, 1), m, length) },
                { m -> StatTile(stringResource(R.string.trip_driving), formatMinutes(driveMin), m) },
                { m -> StatTile(stringResource(R.string.trip_charging), formatMinutes(summary.firstDouble("charge_min")), m) },
                {
                        m ->
                    StatTile(
                        stringResource(R.string.drive_speed_avg),
                        formatNumber(if (distance != null && driveMin != null && driveMin > 0) distance / driveMin * 60 else null),
                        m, data.units.speed,
                    )
                },
                { m -> StatTile(stringResource(R.string.drives_energy), formatNumber(energy, 1), m, "kWh") },
                {
                        m ->
                    StatTile(
                        stringResource(R.string.drives_consumption),
                        formatNumber(if (distance != null && energy != null && distance > 0) energy * 1000 / distance else null),
                        m, "Wh/$length",
                    )
                },
                { m -> StatTile(stringResource(R.string.overview_consumption_gross), formatNumber(data["gross"].firstDouble("consumption_gross")), m, "Wh/$length") },
                { m -> StatTile(stringResource(R.string.charges_added), formatNumber(summary.firstDouble("energy_added"), 1), m, "kWh") },
                { m -> StatTile(stringResource(R.string.charges_cost), formatCost(cost), m) },
                { m -> StatTile(stringResource(R.string.nav_drives), summary.firstLong("drives")?.toString() ?: NO_VALUE, m) },
                { m -> StatTile(stringResource(R.string.nav_charges), summary.firstLong("charges")?.toString() ?: NO_VALUE, m) },
            ),
        )

        val level = data["level"].points("time", "battery_level")
        Panel(stringResource(R.string.chart_battery)) {
            if (level.isEmpty()) NoData() else TimeLineChart(
                series = listOf(ChartSeries(stringResource(R.string.drive_battery), ColorBattery, level)),
                fromMs = data.fromMs, toMs = data.toMs, yMin = 0.0, yMax = 100.0,
                formatY = { formatNumber(it) + " %" }, formatValue = { formatNumber(it) + " %" },
            )
        }
        val range = data["level"].points("time", "range")
        if (range.isNotEmpty()) {
            Panel(stringResource(R.string.chart_range)) {
                TimeLineChart(
                    series = listOf(ChartSeries(stringResource(R.string.overview_range), ColorRange, range)),
                    fromMs = data.fromMs, toMs = data.toMs, yMin = 0.0,
                    formatValue = { formatNumber(it) + " " + length },
                )
            }
        }
        val elevation = data["elevation"].points("time", "elevation")
        if (elevation.isNotEmpty()) {
            Panel(stringResource(R.string.chart_elevation)) {
                TimeLineChart(
                    series = listOf(ChartSeries(stringResource(R.string.chart_elevation), ColorElevation, elevation)),
                    fromMs = data.fromMs, toMs = data.toMs,
                    // Only drives have an elevation: no line across the time spent parked.
                    maxGapMs = ((data.toMs - data.fromMs) / 100).coerceAtLeast(15 * MINUTE_MS),
                    formatValue = { formatNumber(it) + " " + data.units.altitude },
                )
            }
        }

        // Over more than a day, the day is part of the time.
        val manyDays = data.toMs - data.fromMs > DAY_MS
        fun span(start: Long, end: Long?) =
            (if (manyDays) formatDayTime(start) else formatTime(start)) + " – " + (end?.let { formatTime(it) } ?: "…")

        val drives = data["drives"]
        if (drives.rowCount > 0) Text(stringResource(R.string.nav_drives), style = MaterialTheme.typography.titleMedium)
        for (row in 0 until drives.rowCount) {
            val start = drives.longOrNull(row, "start_date") ?: continue
            EventCard(
                Icons.Outlined.DirectionsCar, stateColor(CarState.DRIVING), span(start, drives.longOrNull(row, "end_date")),
                "${drives.stringOrNull(row, "start_address") ?: NO_VALUE} → ${drives.stringOrNull(row, "end_address") ?: stringResource(R.string.in_progress)}",
                listOf(
                    formatNumber(drives.doubleOrNull(row, "distance"), 1) + " " + length,
                    formatNumber(drives.doubleOrNull(row, "consumption")) + " Wh/" + length,
                    formatSocRange(drives.doubleOrNull(row, "start_battery_level"), drives.doubleOrNull(row, "end_battery_level")),
                ).joinToString(" · "),
                drives.doubleOrNull(row, "duration_min")?.let { formatMinutes(it) },
            )
        }

        val charges = data["charges"]
        if (charges.rowCount > 0) Text(stringResource(R.string.nav_charges), style = MaterialTheme.typography.titleMedium)
        for (row in 0 until charges.rowCount) {
            val start = charges.longOrNull(row, "start_date") ?: continue
            EventCard(
                Icons.Outlined.EvStation, stateColor(CarState.CHARGING), span(start, charges.longOrNull(row, "end_date")),
                charges.stringOrNull(row, "address")?.takeIf { it.isNotBlank() } ?: stringResource(R.string.state_charging),
                listOfNotNull(
                    "+" + formatNumber(charges.doubleOrNull(row, "charge_energy_added"), 1) + " kWh",
                    formatSocRange(charges.doubleOrNull(row, "start_battery_level"), charges.doubleOrNull(row, "end_battery_level")),
                    charges.stringOrNull(row, "charge_type"),
                    charges.doubleOrNull(row, "cost")?.let { formatCost(it) },
                ).joinToString(" · "),
                charges.doubleOrNull(row, "duration_min")?.let { formatMinutes(it) },
            )
        }
    }
}

// ---------------------------------------------------------------- States

@Composable
fun StatesScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "states", stringResource(R.string.dash_states), onBack,
        queries = mapOf("summary" to "places/states_summary.sql", "states" to "overview/states.sql"),
        initialPeriod = ListPeriod.WEEK, periods = SHORT_PERIODS,
    ) { data ->
        val summary = data["summary"]
        val current = when (summary.firstString("state")) {
            "online" -> CarState.ONLINE
            "asleep" -> CarState.ASLEEP
            "offline" -> CarState.OFFLINE
            else -> CarState.UNKNOWN
        }
        val since = summary.firstLong("since")
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.states_current), stateLabel(current), m) },
                {
                        m ->
                    StatTile(
                        stringResource(R.string.states_since),
                        since?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), MINUTE_MS).toString() } ?: NO_VALUE, m,
                    )
                },
                { m -> StatTile(stringResource(R.string.states_parked), formatNumber(summary.firstDouble("parked")?.let { it * 100 }, 1), m, "%") },
            ),
        )

        // Code 0 ends a drive, charge or update: the car goes back to its last connectivity state.
        val table = data["states"]
        var resting = CarState.ONLINE
        val changes = (0 until table.rowCount).mapNotNull { row ->
            val time = table.longOrNull(row, "time") ?: return@mapNotNull null
            val state = when (table.longOrNull(row, "state")) {
                0L -> resting
                1L -> CarState.DRIVING
                2L -> CarState.CHARGING
                3L -> CarState.OFFLINE
                4L -> CarState.ASLEEP
                5L -> CarState.ONLINE
                6L -> CarState.UPDATING
                else -> CarState.UNKNOWN
            }
            if (state == CarState.ONLINE || state == CarState.ASLEEP || state == CarState.OFFLINE) resting = state
            StateChange(time, state)
        }
        Panel(stringResource(R.string.overview_chart_states)) {
            StateBand(changes, data.fromMs, data.toMs, colorOf = ::stateColor, labelOf = { stateLabel(it) })
        }

        // Time spent in each state over the period.
        val totals = mutableMapOf<CarState, Long>()
        changes.forEachIndexed { index, change ->
            val start = change.timeMs.coerceAtLeast(data.fromMs)
            val end = (changes.getOrNull(index + 1)?.timeMs ?: data.toMs).coerceAtMost(data.toMs)
            if (end > start) totals[change.state] = (totals[change.state] ?: 0L) + (end - start)
        }
        val whole = totals.values.sum().takeIf { it > 0 } ?: return@StatsPage
        val dayUnit = stringResource(R.string.unit_day_short)
        val labels = totals.keys.associateWith { stateLabel(it) }
        Panel(stringResource(R.string.states_time_spent)) {
            RankList(
                totals.entries.sortedByDescending { it.value }.map { labels.getValue(it.key) to it.value.toDouble() },
                ColorPlaces,
            ) { formatSpan(it.toLong(), dayUnit) + "  ·  " + formatNumber(it * 100 / whole, 1) + " %" }
        }
    }
}

// ---------------------------------------------------------------- Updates

@Composable
fun UpdatesScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "updates", stringResource(R.string.dash_updates), onBack,
        queries = mapOf("updates" to "places/updates.sql"),
        initialPeriod = ListPeriod.ALL,
    ) { data ->
        val table = data["updates"]
        if (table.rowCount == 0) return@StatsPage NoData()
        val times = (0 until table.rowCount).mapNotNull { table.longOrNull(it, "time") }
        val gaps = times.zipWithNext { newer, older -> newer - older }.sorted()
        val dayUnit = stringResource(R.string.unit_day_short)
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.dash_updates), table.rowCount.toString(), m) },
                { m -> StatTile(stringResource(R.string.overview_firmware), table.stringOrNull(0, "version") ?: NO_VALUE, m) },
                { m -> StatTile(stringResource(R.string.updates_median_gap), gaps.getOrNull(gaps.size / 2)?.let { formatNumber(it / DAY_MS.toDouble(), 1) } ?: NO_VALUE, m, dayUnit) },
            ),
        )
        for (row in 0 until table.rowCount) {
            val time = times.getOrNull(row) ?: continue
            val previous = times.getOrNull(row + 1)
            EventCard(
                Icons.Outlined.SystemUpdate, stateColor(CarState.UPDATING), formatFullDateTime(time),
                table.stringOrNull(row, "version") ?: NO_VALUE,
                previous?.let { stringResource(R.string.updates_after, formatSpan(time - it, dayUnit)) },
                table.doubleOrNull(row, "duration_s")?.let { formatMinutes(it / 60) },
            )
        }
    }
}

// ---------------------------------------------------------------- Locations

@Composable
fun LocationsScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "locations", stringResource(R.string.dash_locations), onBack,
        queries = mapOf(
            "summary" to "places/locations_summary.sql", "cities" to "places/locations_cities.sql", "last" to "places/locations_last.sql",
            "geofences" to "places/geofences.sql",
        ),
        initialPeriod = ListPeriod.YEAR,
    ) { data ->
        val summary = data["summary"]
        fun count(column: String) = summary.firstLong(column)?.toString() ?: NO_VALUE
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.locations_addresses), count("addresses"), m) },
                { m -> StatTile(stringResource(R.string.locations_cities), count("cities"), m) },
                { m -> StatTile(stringResource(R.string.locations_regions), count("states"), m) },
                { m -> StatTile(stringResource(R.string.locations_countries), count("countries"), m) },
            ),
        )
        val cities = data["cities"]
        if (cities.rowCount > 0) {
            Panel(stringResource(R.string.locations_top_cities)) {
                RankList(
                    (0 until cities.rowCount).map { (cities.stringOrNull(it, "city") ?: NO_VALUE) to (cities.doubleOrNull(it, "addresses") ?: 0.0) },
                    ColorPlaces,
                ) { formatNumber(it) }
            }
        }
        val geofences = data["geofences"]
        if (geofences.rowCount > 0) {
            Panel(stringResource(R.string.locations_geofences)) {
                NameValueRows(
                    (0 until geofences.rowCount).map {
                        (geofences.stringOrNull(it, "name") ?: NO_VALUE) to formatNumber(geofences.doubleOrNull(it, "radius")) + " m"
                    },
                )
            }
        }
        val last = data["last"]
        if (last.rowCount > 0) Text(stringResource(R.string.locations_last_visited), style = MaterialTheme.typography.titleMedium)
        for (row in 0 until last.rowCount) {
            EventCard(
                Icons.Outlined.LocalParking, ColorPlaces,
                last.longOrNull(row, "time")?.let { formatDayTime(it) }.orEmpty(),
                last.stringOrNull(row, "address") ?: NO_VALUE,
                last.stringOrNull(row, "city"),
                last.longOrNull(row, "visits")?.let { "× $it" },
            )
        }
    }
}

// ---------------------------------------------------------------- Visited

@Composable
fun VisitedScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "visited", stringResource(R.string.dash_visited), onBack,
        queries = mapOf("places" to "places/visited.sql", "countries" to "places/visited_countries.sql"),
        initialPeriod = ListPeriod.QUARTER,
    ) { data ->
        val table = data["places"]
        if (table.rowCount == 0) return@StatsPage NoData()
        val rows = 0 until table.rowCount
        val points = rows.mapNotNull { row ->
            GeoPoint(table.doubleOrNull(row, "latitude") ?: return@mapNotNull null, table.doubleOrNull(row, "longitude") ?: return@mapNotNull null)
        }
        if (points.isNotEmpty()) TrackMap(points, showLine = false)
        val countries = rows.mapNotNull { table.stringOrNull(it, "country") }.distinct()
        AdaptiveTiles(
            listOf(
                { m -> StatTile(stringResource(R.string.locations_countries), countries.size.toString(), m) },
                { m -> StatTile(stringResource(R.string.locations_cities), table.rowCount.toString(), m) },
            ),
        )
        val byCountry = data["countries"]
        for (row in 0 until byCountry.rowCount) {
            FactCard(byCountry.stringOrNull(row, "country") ?: NO_VALUE) {
                Facts(
                    listOfNotNull(
                        stringResource(R.string.locations_cities) to (byCountry.longOrNull(row, "cities")?.toString() ?: NO_VALUE),
                        stringResource(R.string.visited_day_count) to (byCountry.longOrNull(row, "days")?.toString() ?: NO_VALUE),
                        byCountry.longOrNull(row, "first_visit")?.let { stringResource(R.string.visited_first) to formatDayTime(it) },
                        byCountry.longOrNull(row, "last_visit")?.let { stringResource(R.string.visited_last) to formatDayTime(it) },
                    ),
                )
            }
        }
        Text(stringResource(R.string.locations_cities), style = MaterialTheme.typography.titleMedium)
        val daysLabel = stringResource(R.string.visited_days)
        for (row in rows) {
            val first = table.longOrNull(row, "first_visit")
            val last = table.longOrNull(row, "last_visit")
            EventCard(
                Icons.Outlined.LocalParking, ColorPlaces,
                listOfNotNull(first?.let { formatDayTime(it) }, last?.let { formatDayTime(it) }).joinToString(" → "),
                table.stringOrNull(row, "city") ?: NO_VALUE,
                table.stringOrNull(row, "country"),
                table.longOrNull(row, "days")?.let { "$it $daysLabel" },
            )
        }
    }
}

// ---------------------------------------------------------------- Database

@Composable
fun DatabaseScreen(container: AppContainer, carId: Int, onBack: () -> Unit) {
    StatsPage(
        container, carId, "database", stringResource(R.string.dash_database), onBack,
        queries = mapOf("info" to "system/database.sql", "tables" to "system/tables.sql", "indexes" to "system/indexes.sql"),
        initialPeriod = ListPeriod.ALL, periods = emptyList(),
    ) { data ->
        val info = data["info"]
        val megabyte = stringResource(R.string.unit_megabyte)
        fun megabytes(bytes: Double?) = formatNumber(bytes?.let { it / 1_048_576 }, 1)
        AdaptiveTiles(
            listOf(
                { m -> StatTile("PostgreSQL", info.firstString("postgres_version") ?: NO_VALUE, m) },
                { m -> StatTile(stringResource(R.string.database_size), megabytes(info.firstDouble("database_bytes")), m, megabyte) },
                { m -> StatTile(stringResource(R.string.database_timezone), info.firstString("timezone") ?: NO_VALUE, m) },
                { m -> StatTile(stringResource(R.string.database_schema), info.firstLong("schema_version")?.toString() ?: NO_VALUE, m) },
                { m -> StatTile(stringResource(R.string.overview_firmware), info.firstString("firmware") ?: NO_VALUE, m) },
                { m -> StatTile(stringResource(R.string.nav_drives), info.firstLong("drives")?.toString() ?: NO_VALUE, m) },
                { m -> StatTile(stringResource(R.string.nav_charges), info.firstLong("charges")?.toString() ?: NO_VALUE, m) },
                { m -> StatTile(stringResource(R.string.dash_updates), info.firstLong("updates")?.toString() ?: NO_VALUE, m) },
                {
                        m ->
                    StatTile(
                        stringResource(R.string.database_incomplete),
                        ((info.firstLong("open_drives") ?: 0) + (info.firstLong("open_charges") ?: 0)).toString(), m,
                    )
                },
            ),
        )
        val tables = data["tables"]
        if (tables.rowCount > 0) {
            val rowsLabel = stringResource(R.string.database_rows)
            val counts = (0 until tables.rowCount).associate {
                (tables.stringOrNull(it, "name") ?: NO_VALUE) to (tables.doubleOrNull(it, "rows") ?: 0.0)
            }
            Panel(stringResource(R.string.database_tables)) {
                RankList(
                    (0 until tables.rowCount).map { (tables.stringOrNull(it, "name") ?: NO_VALUE) to (tables.doubleOrNull(it, "bytes") ?: 0.0) },
                    ColorPlaces,
                ) { megabytes(it) + " " + megabyte }
                Text(
                    counts.entries.filter { it.value > 0 }.joinToString("  ·  ") { "${it.key} ${formatNumber(it.value)} $rowsLabel" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val indexes = data["indexes"]
        if (indexes.rowCount > 0) {
            val scans = stringResource(R.string.database_scans)
            Panel(stringResource(R.string.database_indexes)) {
                NameValueRows(
                    (0 until minOf(indexes.rowCount, 15)).map {
                        (indexes.stringOrNull(it, "index_name") ?: NO_VALUE) to
                            formatNumber(indexes.doubleOrNull(it, "scans")) + " " + scans + "  ·  " +
                            megabytes(indexes.doubleOrNull(it, "bytes")) + " " + megabyte
                    },
                )
            }
        }
    }
}
