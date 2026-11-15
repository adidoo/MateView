// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameParserTest {

    @Test
    fun `reads columns and typed values`() {
        val body = """
            {"results":{"A":{"status":200,"frames":[{"schema":{"refId":"A","fields":[
              {"name":"id","type":"number"},{"name":"name","type":"string"},
              {"name":"efficiency","type":"number"},{"name":"lfp","type":"boolean"}]},
              "data":{"values":[[1,2],["Titine",null],[0.145,0.2],[false,true]]}}]}}}
        """.trimIndent()

        val table = FrameParser.parse(body)

        assertEquals(listOf("id", "name", "efficiency", "lfp"), table.columns)
        assertEquals(2, table.rowCount)
        assertEquals(1L, table.long(0, "id"))
        assertEquals("Titine", table.string(0, "name"))
        assertNull(table.string(1, "name"))
        assertEquals(0.145, table.double(0, "efficiency")!!, 1e-9)
        assertEquals(true, table.boolean(1, "lfp"))
    }

    @Test
    fun `a query without rows gives an empty table`() {
        val body = """{"results":{"A":{"status":200,"frames":[{"schema":{"fields":[{"name":"id"}]},"data":{"values":[[]]}}]}}}"""
        assertEquals(0, FrameParser.parse(body).rowCount)
        assertEquals(0, FrameParser.parse("""{"results":{"A":{"status":200}}}""").rowCount)
    }

    @Test
    fun `a database error is reported with its message`() {
        val body = """{"results":{"A":{"error":"db query error: ERROR: permission denied for schema private","status":500}}}"""
        val error = assertThrows(GrafanaQueryException::class.java) { FrameParser.parse(body) }
        assertTrue(error.message!!.contains("permission denied"))
    }

    @Test
    fun `a page that is not JSON is rejected`() {
        assertThrows(GrafanaQueryException::class.java) { FrameParser.parse("<html>login</html>") }
    }
}
