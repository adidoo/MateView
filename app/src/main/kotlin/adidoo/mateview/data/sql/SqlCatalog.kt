// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.sql

import java.util.concurrent.ConcurrentHashMap

/**
 * The SQL queries shipped with the app, under `assets/sql/`.
 *
 * Queries use two kinds of placeholders:
 *  - Grafana time macros (`$__timeFilter(col)`, `$__timeFrom()`...), left as
 *    they are and expanded by the Grafana server;
 *  - dashboard variables (`$car_id`, `${preferred_range}`...), replaced here.
 */
class SqlCatalog(private val readAsset: (String) -> String) {
    private val loaded = ConcurrentHashMap<String, String>()

    /** Loads `sql/<path>` and substitutes [variables]. */
    fun bind(path: String, variables: Map<String, String>): String =
        substitute(loaded.getOrPut(path) { readAsset("sql/$path") }, variables)

    companion object {
        private val SAFE_VALUE = Regex("[A-Za-z0-9_.-]*")

        /**
         * Replaces `${name}` and `$name` for each variable. Values end up inside
         * SQL, so only plain identifiers and numbers are accepted.
         */
        fun substitute(sql: String, variables: Map<String, String>): String {
            var result = sql
            // Longest names first, so `$car` can never eat the start of `$car_id`.
            for ((name, value) in variables.entries.sortedByDescending { it.key.length }) {
                require(SAFE_VALUE.matches(value)) { "Unsafe value for \$$name" }
                result = result
                    .replace("\${$name}", value)
                    .replace(Regex("\\$" + Regex.escape(name) + "(?![A-Za-z0-9_])"), Regex.escapeReplacement(value))
            }
            return result
        }
    }
}
