// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

data class Datasource(val uid: String, val name: String)

/** The part of the Grafana HTTP API the app needs. */
class GrafanaClient(private val transport: Transport) {

    /** Grafana's version, from the unauthenticated health endpoint. */
    suspend fun version(): String {
        val response = transport.execute(ApiRequest("GET", "/api/health"))
        val json = response.takeIf { it.isSuccessful }?.body?.parseObject()
        return json?.text("version")
            ?: throw TransportException(Stage.GRAFANA, "No Grafana at this address (HTTP ${response.code}).",
                problem = Problem.NO_GRAFANA, detail = response.code.toString())
    }

    /** The PostgreSQL data source to query: the one named "TeslaMate" when there are several. */
    suspend fun findDatasource(): Datasource {
        val response = transport.execute(ApiRequest("GET", "/api/frontend/settings"))
        val sources = response.takeIf { it.isSuccessful }?.body?.parseObject()?.get("datasources") as? JsonObject
            ?: throw TransportException(
                Stage.DATASOURCE,
                "Grafana refused to list its data sources (HTTP ${response.code}). Check the token or allow anonymous viewers.",
                problem = Problem.DATASOURCES_REFUSED, detail = response.code.toString(),
            )
        val candidates = sources.mapNotNull { (name, value) ->
            val source = value as? JsonObject ?: return@mapNotNull null
            val uid = source.text("uid") ?: return@mapNotNull null
            if (source.text("type")?.contains("postgres") == true) Datasource(uid, name) else null
        }
        return candidates.firstOrNull { it.name.equals("TeslaMate", ignoreCase = true) }
            ?: candidates.firstOrNull()
            ?: throw TransportException(Stage.DATASOURCE, "Grafana has no PostgreSQL data source.", problem = Problem.NO_POSTGRES)
    }

    /**
     * Runs several queries (name to SQL) in one request and returns the raw answer,
     * to be read with [FrameParser.parse] using each name as `refId`. A failing
     * query does not prevent the others from being answered.
     */
    suspend fun queryBatch(datasource: Datasource, queries: Map<String, String>, fromMs: Long?, toMs: Long?): String {
        val body = buildJsonObject {
            put("queries", buildJsonArray {
                for ((name, sql) in queries) {
                    add(buildJsonObject {
                        put("refId", name)
                        putJsonObject("datasource") { put("uid", datasource.uid) }
                        put("rawSql", sql)
                        put("format", "table")
                    })
                }
            })
            if (fromMs != null) put("from", fromMs.toString())
            if (toMs != null) put("to", toMs.toString())
        }
        val response = transport.execute(ApiRequest("POST", "/api/ds/query", body.toString()))
        // Grafana answers 400 when one query fails, but still returns every result.
        if (response.body.parseObject()?.containsKey("results") != true) {
            throw TransportException(Stage.DATABASE, "Grafana refused the queries (HTTP ${response.code}).",
                problem = Problem.QUERIES_REFUSED, detail = response.code.toString())
        }
        return response.body
    }

    /**
     * Runs [sql] on [datasource]. [fromMs] and [toMs] feed Grafana's time macros
     * (`$__timeFilter`, `$__timeFrom`...), which the server expands.
     */
    suspend fun query(datasource: Datasource, sql: String, fromMs: Long? = null, toMs: Long? = null): Table {
        val body = buildJsonObject {
            put("queries", buildJsonArray {
                add(buildJsonObject {
                    put("refId", "A")
                    putJsonObject("datasource") { put("uid", datasource.uid) }
                    put("rawSql", sql)
                    put("format", "table")
                })
            })
            if (fromMs != null) put("from", fromMs.toString())
            if (toMs != null) put("to", toMs.toString())
        }
        val response = transport.execute(ApiRequest("POST", "/api/ds/query", body.toString()))
        return FrameParser.parse(response.body)
    }
}

private fun String.parseObject(): JsonObject? = try {
    Json.parseToJsonElement(this) as? JsonObject
} catch (e: IllegalArgumentException) {
    null
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
