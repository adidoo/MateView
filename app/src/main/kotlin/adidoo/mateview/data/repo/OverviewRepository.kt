// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.repo

import adidoo.mateview.data.DataAccess
import adidoo.mateview.data.TimeRange
import adidoo.mateview.data.UnitSettings
import adidoo.mateview.data.net.Table

/** A value at a point in time (epoch milliseconds). */
data class Point(val timeMs: Long, val value: Double)

enum class CarState { DRIVING, CHARGING, UPDATING, ONLINE, ASLEEP, OFFLINE, UNKNOWN }

/** The car entered [state] at [timeMs]. */
data class StateChange(val timeMs: Long, val state: CarState)

/** Everything the home screen shows, mirroring TeslaMate's "Overview" dashboard. */
data class Overview(
    val units: UnitSettings,
    val range: TimeRange,
    val fetchedAtMs: Long,
    val fromCache: Boolean,
    // Current state
    val state: CarState,
    val stateSinceMs: Long?,
    val place: String?,
    // Battery and charging
    val batteryLevel: Double?,
    val rangeDistance: Double?,
    val batteryDateMs: Long?,
    val lfpBattery: Boolean,
    val chargerPowerKw: Double?,
    val chargerVoltage: Double?,
    // Car
    val odometer: Double?,
    val firmware: String?,
    // Over the selected period
    val distance: Double?,
    val consumptionNet: Double?,
    val consumptionGross: Double?,
    // Last hour only
    val driverTemp: Double?,
    val insideTemp: Double?,
    val outsideTemp: Double?,
    // Charts
    val chargeLevel: List<Point>,
    val chargingPower: List<Point>,
    val energyAdded: List<Point>,
    val states: List<StateChange>,
    /** Width of the buckets the chart series were averaged into. */
    val bucketMs: Long,
)

class OverviewRepository(private val data: DataAccess) {

    suspend fun load(carId: Int, periodMs: Long): Overview {
        val units = data.units()
        val now = System.currentTimeMillis()
        val range = TimeRange(now - periodMs, now)
        val bucketSeconds = bucketSeconds(periodMs)
        val result = data.batch(
            cacheKey = "overview|$carId|$periodMs",
            queries = QUERIES.associateWith { "overview/$it.sql" },
            variables = mapOf(
                "car_id" to carId.toString(),
                "length_unit" to units.length,
                "temp_unit" to units.temperature,
                "preferred_range" to units.preferredRange,
                "bucket_s" to bucketSeconds.toString(),
            ),
            range = range,
        )

        val battery = result["battery"]
        val charging = result["charging"]
        val summary = result["summary"]
        val drives = result["drives"]
        val temperatures = result["temperatures"]
        val details = result["charging_details"]

        val isCharging = charging.firstBoolean("charging") == true
        val drivingSince = summary.firstLong("driving_since")
        val updatingSince = summary.firstLong("updating_since")
        val state = when {
            drivingSince != null -> CarState.DRIVING
            isCharging -> CarState.CHARGING
            updatingSince != null -> CarState.UPDATING
            else -> when (summary.firstString("state")) {
                "online" -> CarState.ONLINE
                "asleep" -> CarState.ASLEEP
                "offline" -> CarState.OFFLINE
                else -> CarState.UNKNOWN
            }
        }

        return Overview(
            units = units,
            // When shown from the cache, the charts cover the period that was fetched then.
            range = if (result.fromCache) TimeRange(result.fetchedAtMs - periodMs, result.fetchedAtMs) else range,
            fetchedAtMs = result.fetchedAtMs,
            fromCache = result.fromCache,
            state = state,
            stateSinceMs = when (state) {
                CarState.DRIVING -> drivingSince
                CarState.UPDATING -> updatingSince
                CarState.CHARGING -> null
                else -> summary.firstLong("state_since")
            },
            place = summary.firstString("place"),
            batteryLevel = battery.firstDouble("battery_level"),
            rangeDistance = battery.firstDouble("range"),
            batteryDateMs = battery.firstLong("date"),
            lfpBattery = summary.firstBoolean("lfp_battery") == true,
            chargerPowerKw = charging.firstDouble("charger_power").takeIf { isCharging },
            chargerVoltage = charging.firstDouble("charger_voltage").takeIf { isCharging },
            odometer = summary.firstDouble("odometer"),
            firmware = summary.firstString("firmware"),
            distance = drives.firstDouble("distance"),
            consumptionNet = drives.firstDouble("consumption_net"),
            consumptionGross = result["consumption_gross"].firstDouble("consumption_gross"),
            driverTemp = temperatures.firstDouble("driver_temp"),
            insideTemp = temperatures.firstDouble("inside_temp"),
            outsideTemp = temperatures.firstDouble("outside_temp"),
            chargeLevel = result["charge_level"].points("time", "soc"),
            chargingPower = details.points("time", "charger_power"),
            energyAdded = details.points("time", "charge_energy_added"),
            states = result["states"].let { table ->
                // Code 0 marks the end of a drive, charge or update: the car then goes back to
                // whatever connectivity state it was last reported in.
                var resting = CarState.ONLINE
                (0 until table.rowCount).mapNotNull { row ->
                    val time = table.long(row, "time") ?: return@mapNotNull null
                    val code = table.long(row, "state")
                    val state = if (code == 0L) resting else stateOf(code)
                    if (state == CarState.ONLINE || state == CarState.ASLEEP || state == CarState.OFFLINE) resting = state
                    StateChange(time, state)
                }
            },
            bucketMs = bucketSeconds * 1000L,
        )
    }

    private fun stateOf(code: Long?): CarState = when (code) {
        1L -> CarState.DRIVING
        2L -> CarState.CHARGING
        3L -> CarState.OFFLINE
        4L -> CarState.ASLEEP
        5L -> CarState.ONLINE
        6L -> CarState.UPDATING
        else -> CarState.UNKNOWN
    }

    companion object {
        private val QUERIES = listOf(
            "battery", "charging", "summary", "drives", "consumption_gross",
            "temperatures", "charge_level", "charging_details", "states",
        )

        /** Aims at about 300 points per chart, never finer than one minute. */
        fun bucketSeconds(periodMs: Long): Long = (periodMs / 1000 / 300).coerceAtLeast(60)
    }
}

private fun Table.has(column: String) = rowCount > 0 && column in columns
private fun Table.firstDouble(column: String): Double? = if (has(column)) double(0, column) else null
private fun Table.firstLong(column: String): Long? = if (has(column)) long(0, column) else null
private fun Table.firstString(column: String): String? = if (has(column)) string(0, column) else null
private fun Table.firstBoolean(column: String): Boolean? = if (has(column)) boolean(0, column) else null

private fun Table.points(timeColumn: String, valueColumn: String): List<Point> {
    if (timeColumn !in columns || valueColumn !in columns) return emptyList()
    return (0 until rowCount).mapNotNull { row ->
        val time = long(row, timeColumn) ?: return@mapNotNull null
        val value = double(row, valueColumn) ?: return@mapNotNull null
        Point(time, value)
    }
}
