// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NiceTopTest {

    @Test
    fun `the axis top is three round steps above the largest value`() {
        assertEquals(30.0, niceTop(29.6), 1e-9)
        assertEquals(45.0, niceTop(31.0), 1e-9)
        assertEquals(12.0, niceTop(11.0), 1e-9)
        assertEquals(6.0, niceTop(5.2), 1e-9)
        assertEquals(0.6, niceTop(0.5), 1e-9)
        assertEquals(300.0, niceTop(250.0), 1e-9)
    }

    @Test
    fun `an empty chart still gets an axis`() {
        assertEquals(3.0, niceTop(0.0), 1e-9)
    }
}
