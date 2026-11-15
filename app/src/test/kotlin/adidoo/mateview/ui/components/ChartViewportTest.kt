// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.components

import adidoo.mateview.data.repo.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartViewportTest {
    private val hour = 3_600_000L

    private fun viewport() = ChartViewport(0L, 24 * hour, zoomable = true).apply {
        plotLeft = 100f
        plotWidth = 1000f
    }

    @Test
    fun `positions and times map onto each other`() {
        val viewport = viewport()
        assertEquals(12 * hour, viewport.timeAt(600f))
        assertEquals(600f, viewport.xOf(12 * hour), 0.01f)
        assertEquals(0L, viewport.timeAt(0f))
    }

    @Test
    fun `zooming keeps the time under the fingers in place`() {
        val viewport = viewport()
        viewport.transform(zoom = 4f, panX = 0f, centroidX = 350f)
        assertEquals(6 * hour, viewport.to - viewport.from)
        assertEquals(6 * hour, viewport.timeAt(350f))
        assertTrue(viewport.zoomed)
    }

    @Test
    fun `the view never leaves the full range`() {
        val viewport = viewport()
        viewport.transform(zoom = 2f, panX = 0f, centroidX = 600f)
        viewport.transform(zoom = 1f, panX = 5000f, centroidX = 600f)
        assertEquals(0L, viewport.from)
        viewport.transform(zoom = 0.1f, panX = 0f, centroidX = 600f)
        assertFalse(viewport.zoomed)
    }

    @Test
    fun `reset clears the zoom and the cursor`() {
        val viewport = viewport()
        viewport.transform(zoom = 3f, panX = 0f, centroidX = 600f)
        viewport.cursorTime = 5 * hour
        viewport.reset()
        assertFalse(viewport.zoomed)
        assertNull(viewport.cursorTime)
    }

    @Test
    fun `the nearest point is found on either side`() {
        val points = listOf(Point(0, 1.0), Point(10, 2.0), Point(20, 3.0))
        assertEquals(2.0, points.nearest(12)!!.value, 0.0)
        assertEquals(3.0, points.nearest(16)!!.value, 0.0)
        assertEquals(1.0, points.nearest(-5)!!.value, 0.0)
        assertEquals(3.0, points.nearest(99)!!.value, 0.0)
        assertNull(emptyList<Point>().nearest(3))
    }

    @Test
    fun `durations are written in the largest useful unit`() {
        assertEquals("45 min", formatSpan(45 * 60_000L, "j"))
        assertEquals("2 h 05", formatSpan(125 * 60_000L, "j"))
        assertEquals("3 j 4 h", formatSpan((3 * 24 + 4) * hour, "j"))
    }
}
