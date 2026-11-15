// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.repo

import adidoo.mateview.data.DataAccess
import adidoo.mateview.data.UnitSettings
import kotlin.math.floor

/** Mirrors TeslaMate's "Battery Health" dashboard. Capacities are in kWh. */
data class BatteryHealth(
    val units: UnitSettings,
    val maxCapacity: Double?,
    val currentCapacity: Double?,
    val maxRange: Double?,
    val currentRange: Double?,
    val usableLevel: Double?,
    /** Wh per km (or mile) of rated range. */
    val ratedEfficiency: Double?,
    val charges: Long?,
    val energyAdded: Double?,
    val energyUsed: Double?,
    val logged: Double?,
    val mileage: Double?,
    val odometer: Double?,
    val acEnergy: Double,
    val dcEnergy: Double,
    /** Odometer to derived capacity, one point per half month. */
    val capacityByMileage: List<Pair<Double, Double>>,
    val fetchedAtMs: Long,
    val fromCache: Boolean,
) {
    /** Share of the original capacity that is gone, in %, never negative. */
    val degradation: Double?
        get() = if (maxCapacity == null || currentCapacity == null || maxCapacity <= 0) null
        else (100 - currentCapacity * 100 / maxCapacity).coerceAtLeast(0.0)
    val health: Double? get() = degradation?.let { 100 - it }
    val storedEnergy: Double? get() = if (usableLevel == null || currentCapacity == null) null else usableLevel * currentCapacity / 100
    val cycles: Double? get() = if (energyAdded == null || maxCapacity == null || maxCapacity <= 0) null else floor(energyAdded / maxCapacity)
    val chargingEfficiency: Double? get() = if (energyAdded == null || energyUsed == null || energyUsed <= 0) null else energyAdded * 100 / energyUsed
    val notLogged: Double? get() = if (mileage == null || logged == null) null else mileage - logged
}

/** Time series for the "Charge Level" and "Projected Range" dashboards. */
data class BatterySeries(
    val units: UnitSettings,
    val fromMs: Long,
    val toMs: Long,
    val bucketMs: Long,
    val level: List<Point>,
    val usableLevel: List<Point>,
    val projectedRange: List<Point>,
    val outsideTemp: List<Point>,
    val odometer: List<Point>,
    val fetchedAtMs: Long,
    val fromCache: Boolean,
)

/** A period spent parked between two drives or charges ("Vampire Drain"). */
data class IdlePeriod(
    val startMs: Long,
    val endMs: Long,
    val durationS: Double,
    /** Share of the period spent asleep or offline, 0..1. */
    val standby: Double?,
    val startSoc: Double?,
    val endSoc: Double?,
    /** The cold hid part of the battery: range figures are not meaningful. */
    val reducedRange: Boolean,
    val rangeLoss: Double?,
    val energy: Double?,
    /** Average drain in W. */
    val avgPower: Double?,
    val rangeLossPerHour: Double?,
)

data class VampireDrain(val units: UnitSettings, val periods: List<IdlePeriod>, val fetchedAtMs: Long, val fromCache: Boolean)

class BatteryRepository(private val data: DataAccess) {

    suspend fun health(carId: Int): BatteryHealth {
        val units = data.units()
        val result = data.batch(
            cacheKey = "battery-health|$carId",
            queries = mapOf(
                "health" to "battery/health.sql",
                "ac_dc" to "battery/ac_dc.sql",
                "capacity" to "battery/capacity_by_mileage.sql",
            ),
            variables = units.variables(carId),
            range = null,
        )
        val health = result["health"]
        val acDc = result["ac_dc"]
        val capacity = result["capacity"]
        fun energyOf(kind: String) = (0 until acDc.rowCount)
            .firstOrNull { acDc.stringOrNull(it, "kind") == kind }
            ?.let { acDc.doubleOrNull(it, "energy") } ?: 0.0
        return BatteryHealth(
            units = units,
            maxCapacity = health.firstDouble("max_capacity"),
            currentCapacity = health.firstDouble("current_capacity"),
            maxRange = health.firstDouble("max_range"),
            currentRange = health.firstDouble("current_range"),
            usableLevel = health.firstDouble("usable_battery_level"),
            ratedEfficiency = health.firstDouble("rated_efficiency"),
            charges = health.firstLong("charges"),
            energyAdded = health.firstDouble("energy_added"),
            energyUsed = health.firstDouble("energy_used"),
            logged = health.firstDouble("logged"),
            mileage = health.firstDouble("mileage"),
            odometer = health.firstDouble("odometer"),
            acEnergy = energyOf("AC"),
            dcEnergy = energyOf("DC"),
            capacityByMileage = (0 until capacity.rowCount).mapNotNull { row ->
                val odometer = capacity.doubleOrNull(row, "odometer") ?: return@mapNotNull null
                val value = capacity.doubleOrNull(row, "capacity") ?: return@mapNotNull null
                odometer to value
            },
            fetchedAtMs = result.fetchedAtMs,
            fromCache = result.fromCache,
        )
    }

    suspend fun chargeLevel(carId: Int, period: ListPeriod): BatterySeries =
        series(carId, period, "charge-level", mapOf("level" to "battery/charge_level.sql"))

    suspend fun projectedRange(carId: Int, period: ListPeriod): BatterySeries = series(
        carId, period, "projected-range",
        mapOf("projected" to "battery/projected_range.sql", "mileage" to "battery/mileage.sql"),
    )

    private suspend fun series(carId: Int, period: ListPeriod, name: String, queries: Map<String, String>): BatterySeries {
        val units = data.units()
        val range = period.range(System.currentTimeMillis())
        // About 300 points per chart, never finer than five minutes; "all" is sized for two years.
        val bucketS = ((period.durationMs ?: (730 * DAY_MS)) / 1000 / 300).coerceAtLeast(300)
        val result = data.batch(
            cacheKey = "$name|$carId|$period",
            queries = queries,
            variables = units.variables(carId) + ("bucket_s" to bucketS.toString()),
            range = range,
        )
        val level = result["level"]
        val projected = result["projected"]
        val levelPoints = level.points("time", "battery_level") + projected.points("time", "battery_level")
        val projectedPoints = projected.points("time", "projected_range")
        val first = (levelPoints + projectedPoints).minOfOrNull { it.timeMs }
        return BatterySeries(
            units = units,
            // "All" starts at the first data point rather than in 1970.
            fromMs = if (period.durationMs == null) first ?: (range.toMs - DAY_MS) else range.fromMs,
            toMs = range.toMs,
            bucketMs = bucketS * 1000,
            level = levelPoints,
            usableLevel = level.points("time", "usable_battery_level"),
            projectedRange = projectedPoints,
            outsideTemp = projected.points("time", "outside_temp"),
            odometer = result["mileage"].points("time", "odometer"),
            fetchedAtMs = result.fetchedAtMs,
            fromCache = result.fromCache,
        )
    }

    suspend fun vampireDrain(carId: Int, period: ListPeriod): VampireDrain {
        val units = data.units()
        val result = data.batch(
            cacheKey = "vampire|$carId|$period",
            queries = mapOf("drain" to "battery/vampire_drain.sql"),
            variables = units.variables(carId),
            range = period.range(System.currentTimeMillis()),
        )
        val table = result["drain"]
        val periods = (0 until table.rowCount).mapNotNull { row ->
            val start = table.longOrNull(row, "start_date") ?: return@mapNotNull null
            val end = table.longOrNull(row, "end_date") ?: return@mapNotNull null
            IdlePeriod(
                startMs = start,
                endMs = end,
                durationS = table.doubleOrNull(row, "duration") ?: ((end - start) / 1000.0),
                standby = table.doubleOrNull(row, "standby"),
                startSoc = table.doubleOrNull(row, "start_battery_level"),
                endSoc = table.doubleOrNull(row, "end_battery_level"),
                reducedRange = "has_reduced_range" in table.columns && table.boolean(row, "has_reduced_range") == true,
                rangeLoss = table.doubleOrNull(row, "range_loss"),
                energy = table.doubleOrNull(row, "energy"),
                avgPower = table.doubleOrNull(row, "avg_power"),
                rangeLossPerHour = table.doubleOrNull(row, "range_loss_per_hour"),
            )
        }
        return VampireDrain(units, periods, result.fetchedAtMs, result.fromCache)
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
