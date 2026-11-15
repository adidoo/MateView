// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data

import adidoo.mateview.data.net.Addresses
import adidoo.mateview.data.net.Datasource
import adidoo.mateview.data.net.FrameParser
import adidoo.mateview.data.net.GrafanaClient
import adidoo.mateview.data.net.GrafanaQueryException
import adidoo.mateview.data.net.Table
import adidoo.mateview.data.settings.ConnectionMode
import adidoo.mateview.data.settings.SettingsStore
import adidoo.mateview.data.sql.SqlCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** The connection is not set up, or its last test did not succeed. */
class NotConnectedException : IOException("No working connection is configured.")

/** Units and range type chosen in TeslaMate's own settings. */
data class UnitSettings(val length: String, val temperature: String, val preferredRange: String) {
    val isMetric: Boolean get() = length == "km"

    /** Unit of altitudes and elevation gains. */
    val altitude: String get() = if (isMetric) "m" else "ft"

    val speed: String get() = if (isMetric) "km/h" else "mph"

    /** The SQL variables every screen needs. */
    fun variables(carId: Int): Map<String, String> = mapOf(
        "car_id" to carId.toString(),
        "length_unit" to length,
        "alt_length_unit" to altitude,
        "temp_unit" to temperature,
        "preferred_range" to preferredRange,
    )

    companion object {
        val DEFAULT = UnitSettings("km", "C", "rated")

        /** Only known values are accepted: they are substituted into SQL. */
        fun of(length: String?, temperature: String?, preferredRange: String?) = UnitSettings(
            length = length.takeIf { it == "km" || it == "mi" } ?: DEFAULT.length,
            temperature = temperature.takeIf { it == "C" || it == "F" } ?: DEFAULT.temperature,
            preferredRange = preferredRange.takeIf { it == "rated" || it == "ideal" } ?: DEFAULT.preferredRange,
        )
    }
}

/** A closed time range, in epoch milliseconds. */
data class TimeRange(val fromMs: Long, val toMs: Long)

/** The tables of one screen, fetched in a single request. */
class BatchResult(
    private val tables: Map<String, Table>,
    val errors: Map<String, String>,
    /** When the data left the server. */
    val fetchedAtMs: Long,
    /** True when the server could not be reached and a previous answer is shown. */
    val fromCache: Boolean,
) {
    /** The table of query [name], empty when that query failed. */
    operator fun get(name: String): Table = tables[name] ?: Table.EMPTY
}

/** Keeps the last answer of each screen on disk, so it can still be shown offline. */
class QueryCache(private val directory: File) {

    fun put(key: String, body: String) {
        directory.mkdirs()
        file(key).writeText(body)
    }

    /** The cached answer and when it was saved, or null. */
    fun get(key: String): Pair<String, Long>? {
        val file = file(key).takeIf { it.isFile } ?: return null
        return file.readText() to file.lastModified()
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun file(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return File(directory, digest.joinToString("") { "%02x".format(it) }.take(40) + ".json")
    }
}

/** Runs the bundled SQL queries through the configured connection. */
class DataAccess(
    private val settings: SettingsStore,
    private val tester: ConnectionTester,
    private val catalog: SqlCatalog,
    private val cache: QueryCache,
) {
    private class Session(val client: GrafanaClient, val datasource: Datasource)

    private val mutex = Mutex()
    private var session: Session? = null
    private var units: UnitSettings? = null

    /** Forgets the open session; call it whenever the connection settings change. */
    suspend fun invalidate() = mutex.withLock {
        session = null
        units = null
    }

    suspend fun units(): UnitSettings {
        units?.let { return it }
        val table = batch("units", mapOf("units" to "units.sql"), emptyMap(), null)["units"]
        return UnitSettings.of(
            table.takeIf { it.rowCount > 0 }?.string(0, "unit_of_length"),
            table.takeIf { it.rowCount > 0 }?.string(0, "unit_of_temperature"),
            table.takeIf { it.rowCount > 0 }?.string(0, "preferred_range"),
        ).also { units = it }
    }

    /**
     * Runs the SQL files of [queries] (name to asset path) in one request.
     * [variables] replace the `$name` placeholders; [range] feeds the time macros.
     * Falls back to the last cached answer for [cacheKey] when the server cannot be reached.
     */
    suspend fun batch(
        cacheKey: String,
        queries: Map<String, String>,
        variables: Map<String, String>,
        range: TimeRange?,
    ): BatchResult {
        val sql = queries.mapValues { (_, path) -> catalog.bind(path, variables) }
        val fetchedAt = System.currentTimeMillis()
        return try {
            val current = session()
            val body = current.client.queryBatch(current.datasource, sql, range?.fromMs, range?.toMs)
            withContext(Dispatchers.IO) { cache.put(cacheKey, body) }
            parse(body, queries.keys, fetchedAt, fromCache = false)
        } catch (e: IOException) {
            val cached = withContext(Dispatchers.IO) { cache.get(cacheKey) } ?: throw e
            parse(cached.first, queries.keys, cached.second, fromCache = true)
        }
    }

    private fun parse(body: String, names: Set<String>, fetchedAt: Long, fromCache: Boolean): BatchResult {
        val tables = mutableMapOf<String, Table>()
        val errors = mutableMapOf<String, String>()
        for (name in names) {
            try {
                tables[name] = FrameParser.parse(body, name)
            } catch (e: GrafanaQueryException) {
                errors[name] = e.message.orEmpty()
            }
        }
        // Nothing usable at all: report it as a failure rather than an empty screen.
        if (tables.isEmpty() && errors.isNotEmpty()) throw IOException(errors.values.first())
        return BatchResult(tables, errors, fetchedAt, fromCache)
    }

    private suspend fun session(): Session = mutex.withLock {
        session?.let { return it }
        val saved = settings.connection.first()
        if (!saved.verified) throw NotConnectedException()
        val isHa = saved.mode == ConnectionMode.HOME_ASSISTANT
        val url = (Addresses.parse(if (isHa) saved.haUrl else saved.grafanaUrl) as? Addresses.Result.Ok)?.url
            ?: throw NotConnectedException()
        val connection = if (isHa) {
            ResolvedConnection.HomeAssistant(url, settings.haToken() ?: throw NotConnectedException(), saved.haPanel)
        } else {
            ResolvedConnection.Direct(url, settings.grafanaToken())
        }
        val client = GrafanaClient(tester.transportFor(connection))
        Session(client, client.findDatasource()).also { session = it }
    }
}
