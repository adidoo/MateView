// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The result of one SQL query: named columns, values stored column by column as Grafana returns them. */
class Table(val columns: List<String>, private val values: List<List<JsonElement>>) {
    val rowCount: Int get() = values.firstOrNull()?.size ?: 0

    private fun cell(row: Int, column: String): JsonPrimitive? {
        val index = columns.indexOf(column)
        require(index >= 0) { "No column \"$column\" in $columns" }
        return values[index].getOrNull(row)?.takeUnless { it is JsonNull } as? JsonPrimitive
    }

    fun string(row: Int, column: String): String? = cell(row, column)?.contentOrNull
    fun double(row: Int, column: String): Double? = cell(row, column)?.doubleOrNull
    fun long(row: Int, column: String): Long? =
        cell(row, column)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    fun boolean(row: Int, column: String): Boolean? = cell(row, column)?.booleanOrNull

    companion object {
        val EMPTY = Table(emptyList(), emptyList())
    }
}

class GrafanaQueryException(message: String) : Exception(message)

/** Reads the body of a `POST /api/ds/query` answer. */
object FrameParser {

    /**
     * Returns the first frame of query [refId] as a [Table].
     * @throws GrafanaQueryException when Grafana or the database reports an error.
     */
    fun parse(body: String, refId: String = "A"): Table {
        val root = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: throw GrafanaQueryException("Grafana did not answer with JSON.")

        val result = (root["results"] as? JsonObject)?.get(refId) as? JsonObject
            ?: throw GrafanaQueryException(root.text("message") ?: "Grafana returned no result.")
        result.text("error")?.let { throw GrafanaQueryException(it) }

        val frame = (result["frames"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return Table.EMPTY
        val fields = (frame["schema"] as? JsonObject)?.get("fields") as? JsonArray ?: return Table.EMPTY
        val columns = fields.map { it.jsonObject.text("name").orEmpty() }
        val values = ((frame["data"] as? JsonObject)?.get("values") as? JsonArray)
            ?.map { it.jsonArray.toList() }
            ?: return Table(columns, columns.map { emptyList() })
        return Table(columns, values)
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
}
