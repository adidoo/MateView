// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressesTest {

    private fun url(input: String) = (Addresses.parse(input) as Addresses.Result.Ok).url.toString()

    @Test
    fun `a bare public name gets https`() {
        assertEquals("https://ha.example.org/", url("ha.example.org"))
        assertEquals("https://ha.example.org/", url(" https://ha.example.org/ "))
    }

    @Test
    fun `a local address may use http`() {
        assertEquals("http://192.168.1.20:3000/", url("192.168.1.20:3000"))
        assertEquals("http://homeassistant.local:8123/", url("homeassistant.local:8123"))
        assertEquals("http://grafana:3000/", url("http://grafana:3000"))
    }

    @Test
    fun `clear text is refused outside the local network`() {
        assertEquals(Addresses.Result.InsecureRemote, Addresses.parse("http://ha.example.org"))
        assertEquals(Addresses.Result.InsecureRemote, Addresses.parse("http://8.8.8.8:3000"))
    }

    @Test
    fun `empty and malformed inputs are told apart`() {
        assertEquals(Addresses.Result.Empty, Addresses.parse("  "))
        assertEquals(Addresses.Result.Invalid, Addresses.parse("https://exa mple.org"))
    }

    @Test
    fun `a sub-path is kept when appending an API path`() {
        val base = (Addresses.parse("https://example.org/grafana/") as Addresses.Result.Ok).url
        assertEquals("https://example.org/grafana/api/health", base.resolveUnder("/api/health").toString())
        assertTrue(Addresses.isLocalHost("10.0.0.4"))
    }
}
