// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.sql

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SqlCatalogTest {

    @Test
    fun `both placeholder forms are replaced`() {
        val sql = "SELECT start_\${preferred_range}_range_km FROM drives WHERE car_id = \$car_id"
        assertEquals(
            "SELECT start_rated_range_km FROM drives WHERE car_id = 3",
            SqlCatalog.substitute(sql, mapOf("car_id" to "3", "preferred_range" to "rated")),
        )
    }

    @Test
    fun `Grafana macros and longer names are left alone`() {
        val sql = "WHERE \$__timeFilter(date) AND id = \$car AND car_id = \$car_id AND x = \$car_identity"
        assertEquals(
            "WHERE \$__timeFilter(date) AND id = 7 AND car_id = 1 AND x = \$car_identity",
            SqlCatalog.substitute(sql, mapOf("car" to "7", "car_id" to "1")),
        )
    }

    @Test
    fun `a value that could alter the query is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            SqlCatalog.substitute("WHERE car_id = \$car_id", mapOf("car_id" to "1; DROP TABLE cars"))
        }
    }

    @Test
    fun `files are read once and bound on each call`() {
        var reads = 0
        val catalog = SqlCatalog { path -> reads++; "-- \$path\nSELECT \$car_id".replace("\$path", path) }
        assertEquals("-- sql/a.sql\nSELECT 1", catalog.bind("a.sql", mapOf("car_id" to "1")))
        assertEquals("-- sql/a.sql\nSELECT 2", catalog.bind("a.sql", mapOf("car_id" to "2")))
        assertEquals(1, reads)
    }
}
