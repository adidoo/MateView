// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data

import adidoo.mateview.data.net.Datasource
import adidoo.mateview.data.net.DirectTransport
import adidoo.mateview.data.net.GrafanaClient
import adidoo.mateview.data.net.GrafanaQueryException
import adidoo.mateview.data.net.HaIngressTransport
import adidoo.mateview.data.net.Problem
import adidoo.mateview.data.net.Stage
import adidoo.mateview.data.net.Transport
import adidoo.mateview.data.net.TransportException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

/** Connection settings with the address parsed and the token read from the vault. */
sealed interface ResolvedConnection {
    data class HomeAssistant(val url: HttpUrl, val token: String, val panel: String) : ResolvedConnection
    data class Direct(val url: HttpUrl, val token: String?) : ResolvedConnection

    val stages: List<Stage>
        get() = when (this) {
            is HomeAssistant -> Stage.entries
            is Direct -> listOf(Stage.GRAFANA, Stage.DATASOURCE, Stage.DATABASE)
        }
}

data class Car(val id: Int, val name: String, val model: String?)

/** What a successful connection test found. */
data class ConnectionReport(
    val grafanaVersion: String,
    val datasource: Datasource,
    val cars: List<Car>,
    val schemaVersion: Long?,
    val databaseUser: String?,
    /** True when the database account could modify TeslaMate's data: the user should be warned. */
    val canWrite: Boolean,
)

sealed interface StepStatus {
    data object Pending : StepStatus
    data object Running : StepStatus
    data class Done(val detail: String? = null) : StepStatus
    /** [problem] and [detail] let the interface explain the failure in the user's language. */
    data class Failed(val message: String, val problem: Problem? = null, val detail: String = "") : StepStatus
}

/** Walks through a connection stage by stage, so a failure names the stage that broke. */
class ConnectionTester(private val client: OkHttpClient) {

    fun transportFor(connection: ResolvedConnection): Transport = when (connection) {
        is ResolvedConnection.HomeAssistant ->
            HaIngressTransport(connection.url, connection.token, connection.panel, client)
        is ResolvedConnection.Direct -> DirectTransport(connection.url, connection.token, client)
    }

    /** Returns the report, or null when a stage failed (reported through [onProgress]). */
    suspend fun run(
        connection: ResolvedConnection,
        onProgress: (Stage, StepStatus) -> Unit,
    ): ConnectionReport? {
        val stages = connection.stages
        var current = stages.first()
        fun start(stage: Stage) {
            current = stage
            onProgress(stage, StepStatus.Running)
        }

        try {
            val transport = transportFor(connection)
            if (transport is HaIngressTransport) {
                start(Stage.REACH_HA)
                transport.handshake { done ->
                    onProgress(done, StepStatus.Done())
                    stages.getOrNull(stages.indexOf(done) + 1)
                        ?.takeIf { it.ordinal <= Stage.OPEN_SESSION.ordinal }
                        ?.let(::start)
                }
            }
            val grafana = GrafanaClient(transport)

            start(Stage.GRAFANA)
            val version = grafana.version()
            onProgress(Stage.GRAFANA, StepStatus.Done("Grafana $version"))

            start(Stage.DATASOURCE)
            val datasource = grafana.findDatasource()
            onProgress(Stage.DATASOURCE, StepStatus.Done(datasource.name))

            start(Stage.DATABASE)
            val carsTable = grafana.query(datasource, CARS_SQL)
            val cars = (0 until carsTable.rowCount).mapNotNull { row ->
                val id = carsTable.long(row, "id")?.toInt() ?: return@mapNotNull null
                Car(id, carsTable.string(row, "name") ?: "#$id", carsTable.string(row, "model"))
            }
            if (cars.isEmpty()) throw GrafanaQueryException("The database contains no car yet.")
            val meta = grafana.query(datasource, META_SQL)
            val report = ConnectionReport(
                grafanaVersion = version,
                datasource = datasource,
                cars = cars,
                schemaVersion = meta.long(0, "schema_version"),
                databaseUser = meta.string(0, "db_user"),
                canWrite = meta.boolean(0, "can_write") ?: false,
            )
            onProgress(Stage.DATABASE, StepStatus.Done())
            return report
        } catch (e: TransportException) {
            onProgress(e.stage.takeIf { it in stages } ?: current, StepStatus.Failed(e.message.orEmpty(), e.problem, e.detail))
        } catch (e: GrafanaQueryException) {
            onProgress(current, StepStatus.Failed(e.message.orEmpty()))
        } catch (e: IOException) {
            onProgress(current, StepStatus.Failed(e.message ?: e.javaClass.simpleName))
        }
        return null
    }

    companion object {
        /** Latest database migration of the TeslaMate release the bundled queries were written for (v4.4.0). */
        const val SUPPORTED_SCHEMA_VERSION = 20261014120000L

        private const val CARS_SQL = """
            SELECT id, COALESCE(name, CONCAT('VIN ', vin)) AS name, model
            FROM cars
            ORDER BY display_priority ASC, name ASC, vin ASC"""

        private const val META_SQL = """
            SELECT
              (SELECT max(version) FROM schema_migrations) AS schema_version,
              current_user::text AS db_user,
              has_table_privilege(current_user, 'cars', 'UPDATE') AS can_write"""
    }
}
