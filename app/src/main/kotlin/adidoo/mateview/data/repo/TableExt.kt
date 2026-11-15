// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.repo

import adidoo.mateview.data.net.Table

/** Readers that tolerate a missing column: a query that failed gives an empty table. */
internal fun Table.has(column: String) = rowCount > 0 && column in columns
internal fun Table.firstDouble(column: String): Double? = if (has(column)) double(0, column) else null
internal fun Table.firstLong(column: String): Long? = if (has(column)) long(0, column) else null
internal fun Table.firstString(column: String): String? = if (has(column)) string(0, column) else null
internal fun Table.firstBoolean(column: String): Boolean? = if (has(column)) boolean(0, column) else null

internal fun Table.doubleOrNull(row: Int, column: String): Double? = if (column in columns) double(row, column) else null
internal fun Table.longOrNull(row: Int, column: String): Long? = if (column in columns) long(row, column) else null
internal fun Table.stringOrNull(row: Int, column: String): String? = if (column in columns) string(row, column) else null

/** The rows of [valueColumn] that have a value, as points in time. */
internal fun Table.points(timeColumn: String, valueColumn: String): List<Point> {
    if (timeColumn !in columns || valueColumn !in columns) return emptyList()
    return (0 until rowCount).mapNotNull { row ->
        val time = long(row, timeColumn) ?: return@mapNotNull null
        val value = double(row, valueColumn) ?: return@mapNotNull null
        Point(time, value)
    }
}
