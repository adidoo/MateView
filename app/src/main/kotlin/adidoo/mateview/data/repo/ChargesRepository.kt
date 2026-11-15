// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.repo

import adidoo.mateview.data.DataAccess
import adidoo.mateview.data.UnitSettings
import adidoo.mateview.data.net.Table

/** One row of the charge list, mirroring the "Charges" dashboard table. */
data class ChargeSummary(
    val id: Int,
    val startMs: Long,
    val endMs: Long?,
    val address: String?,
    /** True for a DC (fast) charge, false for AC. */
    val isDc: Boolean,
    val durationMin: Double?,
    val cost: Double?,
    val energyAdded: Double?,
    val energyUsed: Double?,
    val startSoc: Double?,
    val endSoc: Double?,
    val outsideTemp: Double?,
    val rangeAdded: Double?,
    /** Average power over the whole charge (kW). */
    val avgPower: Double?,
)

data class ChargeList(
    val units: UnitSettings,
    val charges: List<ChargeSummary>,
    val fetchedAtMs: Long,
    val fromCache: Boolean,
)

/** Everything the charge details page shows, mirroring the "Charge Details" dashboard. */
data class ChargeDetail(
    val units: UnitSettings,
    val summary: ChargeSummary,
    val startRange: Double?,
    val endRange: Double?,
    /** Energy added over energy used, in %. */
    val efficiency: Double?,
    val powerAvg: Double?,
    val powerMax: Double?,
    val location: GeoPoint?,
    val power: List<Point>,
    val soc: List<Point>,
    val range: List<Point>,
    /** AC only: empty for a DC charge. */
    val voltage: List<Point>,
    val current: List<Point>,
    val outsideTemp: List<Point>,
    /** Charging curve: power (kW) against battery level (%). */
    val curve: List<Pair<Double, Double>>,
    val fetchedAtMs: Long,
    val fromCache: Boolean,
)

class ChargesRepository(private val data: DataAccess) {

    suspend fun list(carId: Int, period: ListPeriod): ChargeList {
        val units = data.units()
        val result = data.batch(
            cacheKey = "charges|$carId|$period",
            queries = mapOf("list" to "charges/list.sql"),
            variables = units.variables(carId),
            range = period.range(System.currentTimeMillis()),
        )
        val table = result["list"]
        return ChargeList(units, (0 until table.rowCount).mapNotNull { table.charge(it) }, result.fetchedAtMs, result.fromCache)
    }

    suspend fun detail(carId: Int, chargeId: Int): ChargeDetail? {
        val units = data.units()
        val result = data.batch(
            cacheKey = "charge|$carId|$chargeId",
            queries = mapOf("detail" to "charges/detail.sql", "samples" to "charges/samples.sql"),
            variables = units.variables(carId) + ("charge_id" to chargeId.toString()),
            range = null,
        )
        val detail = result["detail"]
        val summary = detail.charge(0) ?: return null
        val samples = result["samples"]
        val latitude = detail.firstDouble("latitude")
        val longitude = detail.firstDouble("longitude")
        return ChargeDetail(
            units = units,
            summary = summary,
            startRange = detail.firstDouble("start_range"),
            endRange = detail.firstDouble("end_range"),
            efficiency = detail.firstDouble("efficiency"),
            powerAvg = detail.firstDouble("power_avg"),
            powerMax = detail.firstDouble("power_max"),
            location = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null,
            power = samples.points("time", "charger_power"),
            soc = samples.points("time", "battery_level"),
            range = samples.points("time", "range"),
            voltage = samples.points("time", "charger_voltage"),
            current = samples.points("time", "charger_current"),
            outsideTemp = samples.points("time", "outside_temp"),
            curve = (0 until samples.rowCount).mapNotNull { row ->
                val soc = samples.doubleOrNull(row, "battery_level") ?: return@mapNotNull null
                val power = samples.doubleOrNull(row, "charger_power")?.takeIf { it > 0 } ?: return@mapNotNull null
                soc to power
            },
            fetchedAtMs = result.fetchedAtMs,
            fromCache = result.fromCache,
        )
    }
}

private fun Table.charge(row: Int): ChargeSummary? {
    if (row >= rowCount) return null
    val id = longOrNull(row, "id")?.toInt() ?: return null
    val start = longOrNull(row, "start_date") ?: return null
    return ChargeSummary(
        id = id,
        startMs = start,
        endMs = longOrNull(row, "end_date"),
        address = stringOrNull(row, "address")?.takeIf { it.isNotBlank() },
        isDc = stringOrNull(row, "charge_type") == "DC",
        durationMin = doubleOrNull(row, "duration_min"),
        cost = doubleOrNull(row, "cost"),
        energyAdded = doubleOrNull(row, "charge_energy_added"),
        energyUsed = doubleOrNull(row, "charge_energy_used"),
        startSoc = doubleOrNull(row, "start_battery_level"),
        endSoc = doubleOrNull(row, "end_battery_level"),
        outsideTemp = doubleOrNull(row, "outside_temp"),
        rangeAdded = doubleOrNull(row, "range_added"),
        avgPower = doubleOrNull(row, "avg_power"),
    )
}
