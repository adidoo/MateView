// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.repo

import adidoo.mateview.data.BatchResult
import adidoo.mateview.data.DataAccess
import adidoo.mateview.data.UnitSettings
import adidoo.mateview.data.net.Table
import java.util.TimeZone

/** The tables of one statistics dashboard over a period; screens read them by query name. */
class StatsData(
    val units: UnitSettings,
    val fromMs: Long,
    val toMs: Long,
    private val result: BatchResult,
) {
    val fetchedAtMs: Long get() = result.fetchedAtMs
    val fromCache: Boolean get() = result.fromCache
    operator fun get(name: String): Table = result[name]
}

/** How the "Statistics" dashboard groups drives and charges. The names are SQL `date_trunc` units. */
enum class Granularity { DAY, WEEK, MONTH, YEAR }

class StatsRepository(private val data: DataAccess) {

    /**
     * Runs the SQL files of [queries] (name to path under `assets/sql/stats/`, or a full path
     * when it contains a slash) for [carId] over [period].
     */
    suspend fun load(
        name: String,
        carId: Int,
        period: ListPeriod,
        queries: Map<String, String>,
        granularity: Granularity = Granularity.MONTH,
    ): StatsData {
        val units = data.units()
        val now = System.currentTimeMillis()
        val range = period.range(now)
        // About 300 points per chart, never finer than five minutes; "all" is sized for two years.
        val bucketS = ((period.durationMs ?: (730 * DAY_MS)) / 1000 / 300).coerceAtLeast(300)
        val result = data.batch(
            cacheKey = "$name|$carId|$period|$granularity",
            queries = queries.mapValues { (_, path) -> if ('/' in path) path else "stats/$path" },
            variables = units.variables(carId) + mapOf(
                "bucket_s" to bucketS.toString(),
                "period" to granularity.name.lowercase(),
                "tz_offset_s" to (TimeZone.getDefault().getOffset(now) / 1000).toString(),
            ),
            range = range,
        )
        return StatsData(units, range.fromMs, range.toMs, result)
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
