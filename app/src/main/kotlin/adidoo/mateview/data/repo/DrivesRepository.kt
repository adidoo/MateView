// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.repo

import adidoo.mateview.data.DataAccess
import adidoo.mateview.data.TimeRange
import adidoo.mateview.data.UnitSettings
import adidoo.mateview.data.net.Table

/** A period for the lists of drives and charges; [durationMs] is null for "everything". */
enum class ListPeriod(val durationMs: Long?) {
    DAY(DAY_MS),
    WEEK(7 * DAY_MS),
    MONTH(30 * DAY_MS),
    QUARTER(90 * DAY_MS),
    YEAR(365 * DAY_MS),
    ALL(null);

    fun range(now: Long): TimeRange = TimeRange(durationMs?.let { now - it } ?: 0L, now)
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** A position on the map, in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** One row of the drive list, mirroring the "Drives" dashboard table. */
data class DriveSummary(
    val id: Int,
    val startMs: Long,
    /** Null while the drive is still going on, or if TeslaMate never closed it. */
    val endMs: Long?,
    val startAddress: String?,
    val endAddress: String?,
    val durationMin: Double?,
    val distance: Double?,
    val startSoc: Double?,
    val endSoc: Double?,
    val outsideTemp: Double?,
    val speedMax: Double?,
    val powerMax: Double?,
    /** Net energy, from the range lost (kWh). */
    val energy: Double?,
    /** Wh per km (or mile). */
    val consumption: Double?,
)

data class DriveList(
    val units: UnitSettings,
    val drives: List<DriveSummary>,
    val fetchedAtMs: Long,
    val fromCache: Boolean,
)

/** Everything the drive details page shows, mirroring the "Drive Details" dashboard. */
data class DriveDetail(
    val units: UnitSettings,
    val summary: DriveSummary,
    val startOdometer: Double?,
    val endOdometer: Double?,
    val ascent: Double?,
    val descent: Double?,
    val speedAvg: Double?,
    val energyRecovered: Double?,
    val track: List<GeoPoint>,
    val speed: List<Point>,
    val power: List<Point>,
    val soc: List<Point>,
    val range: List<Point>,
    val elevation: List<Point>,
    val outsideTemp: List<Point>,
    val insideTemp: List<Point>,
    /** Seconds spent in each 10 km/h (or mph) speed band. */
    val speedHistogram: List<Pair<Double, Double>>,
    val fetchedAtMs: Long,
    val fromCache: Boolean,
)

class DrivesRepository(private val data: DataAccess) {

    suspend fun list(carId: Int, period: ListPeriod): DriveList {
        val units = data.units()
        val result = data.batch(
            cacheKey = "drives|$carId|$period",
            queries = mapOf("list" to "drives/list.sql"),
            variables = units.variables(carId),
            range = period.range(System.currentTimeMillis()),
        )
        val table = result["list"]
        return DriveList(units, (0 until table.rowCount).mapNotNull { table.drive(it) }, result.fetchedAtMs, result.fromCache)
    }

    suspend fun detail(carId: Int, driveId: Int): DriveDetail? {
        val units = data.units()
        val result = data.batch(
            cacheKey = "drive|$carId|$driveId",
            queries = mapOf(
                "detail" to "drives/detail.sql",
                "positions" to "drives/positions.sql",
                "histogram" to "drives/speed_histogram.sql",
            ),
            variables = units.variables(carId) + ("drive_id" to driveId.toString()),
            range = null,
        )
        val detail = result["detail"]
        val summary = detail.drive(0) ?: return null
        val positions = result["positions"]
        val histogram = result["histogram"]
        return DriveDetail(
            units = units,
            summary = summary,
            startOdometer = detail.firstDouble("start_km"),
            endOdometer = detail.firstDouble("end_km"),
            ascent = detail.firstDouble("ascent"),
            descent = detail.firstDouble("descent"),
            speedAvg = detail.firstDouble("speed_avg"),
            energyRecovered = detail.firstDouble("energy_recovered"),
            track = (0 until positions.rowCount).mapNotNull { row ->
                val latitude = positions.doubleOrNull(row, "latitude") ?: return@mapNotNull null
                val longitude = positions.doubleOrNull(row, "longitude") ?: return@mapNotNull null
                GeoPoint(latitude, longitude)
            },
            speed = positions.points("time", "speed"),
            power = positions.points("time", "power"),
            soc = positions.points("time", "battery_level"),
            range = positions.points("time", "range"),
            elevation = positions.points("time", "elevation"),
            outsideTemp = positions.points("time", "outside_temp"),
            insideTemp = positions.points("time", "inside_temp"),
            speedHistogram = (0 until histogram.rowCount).mapNotNull { row ->
                val speed = histogram.doubleOrNull(row, "speed") ?: return@mapNotNull null
                val seconds = histogram.doubleOrNull(row, "seconds") ?: return@mapNotNull null
                speed to seconds
            },
            fetchedAtMs = result.fetchedAtMs,
            fromCache = result.fromCache,
        )
    }
}

private fun Table.drive(row: Int): DriveSummary? {
    if (row >= rowCount) return null
    val id = longOrNull(row, "id")?.toInt() ?: return null
    val start = longOrNull(row, "start_date") ?: return null
    return DriveSummary(
        id = id,
        startMs = start,
        endMs = longOrNull(row, "end_date"),
        startAddress = stringOrNull(row, "start_address")?.takeIf { it.isNotBlank() },
        endAddress = stringOrNull(row, "end_address")?.takeIf { it.isNotBlank() },
        durationMin = doubleOrNull(row, "duration_min"),
        distance = doubleOrNull(row, "distance"),
        startSoc = doubleOrNull(row, "start_battery_level"),
        endSoc = doubleOrNull(row, "end_battery_level"),
        outsideTemp = doubleOrNull(row, "outside_temp"),
        speedMax = doubleOrNull(row, "speed_max"),
        powerMax = doubleOrNull(row, "power_max"),
        energy = doubleOrNull(row, "energy"),
        consumption = doubleOrNull(row, "consumption"),
    )
}
