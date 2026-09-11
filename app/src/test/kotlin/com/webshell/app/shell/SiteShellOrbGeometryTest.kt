package com.webshell.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteShellOrbGeometryTest {
    @Test
    fun unsetCoordinatesRestoreToTopRight() {
        val unset = SiteShellOrbAnchor.restored(-1f, -1f, parked = false)
        assertEquals(1f, unset.x, 0f)
        assertEquals(0f, unset.y, 0f)
        assertFalse(unset.parked)
        val parked = SiteShellOrbAnchor.restored(Float.NaN, 0.4f, parked = true)
        assertEquals(1f, parked.x, 0f)
        assertEquals(0.4f, parked.y, 0f)
        assertTrue(parked.parked)
    }

    @Test
    fun centersStayInTheSafeRectangle() {
        for (width in listOf(320f, 411f, 800f)) for (height in listOf(230f, 500f, 850f)) {
            val bounds = SiteShellOrbBounds(width, height)
            for (x in listOf(-1f, 0f, 0.5f, 1f, Float.NaN)) {
                val anchor = SiteShellOrbAnchor.restored(x, x, parked = false)
                assertTrue(bounds.centerX(anchor) in bounds.minX..bounds.maxX)
                assertTrue(bounds.centerY(anchor) in bounds.minY..bounds.maxY)
            }
            val clamped = bounds.clampCenter(-40f, 10_000f)
            assertTrue(clamped.x in bounds.orbSize / 2..width - bounds.orbSize / 2)
            assertTrue(clamped.y in bounds.minY..bounds.maxY)
        }
    }

    @Test
    fun parkedHandleHitBoxMatchesTheVisibleSlice() {
        assertEquals(56f, SiteShellOrbMetrics.ORB_SIZE, 0f)
        assertEquals(SiteShellOrbMetrics.PARKED_HEIGHT, SiteShellOrbMetrics.ORB_SIZE, 0f)
        assertTrue(SiteShellOrbMetrics.PARKED_WIDTH < SiteShellOrbMetrics.ORB_SIZE)
        assertTrue(SiteShellOrbMetrics.PARKED_WIDTH >= 18f)
        val bounds = SiteShellOrbBounds(360f, 640f)
        assertEquals(SiteShellOrbMetrics.ORB_SIZE, bounds.orbSize, 0f)
    }

    @Test
    fun onlyTheRightEdgeIsAParkHotspot() {
        val bounds = SiteShellOrbBounds(320f, 600f)
        assertTrue(bounds.isRightEdgeDrop(bounds.maxX))
        assertTrue(bounds.isRightEdgeDrop(bounds.maxX - 35f))
        assertFalse(bounds.isRightEdgeDrop(bounds.minX))
        assertFalse(bounds.isRightEdgeDrop((bounds.minX + bounds.maxX) / 2f))
        val left = bounds.anchorAt(bounds.minX, 120f)
        assertEquals(0f, left.x, 0.001f)
        assertFalse(left.parked)
    }

    @Test
    fun tapAndSlowDragNeverRefreshOrParkOnTheLeft() {
        val bounds = SiteShellOrbBounds(360f, 640f)
        assertEquals(
            SiteShellOrbRelease.TAP,
            SiteShellOrbGesture.classifyRelease(8f, -4f, 80L, bounds.maxX, bounds),
        )
        assertEquals(
            SiteShellOrbRelease.REPOSITION,
            SiteShellOrbGesture.classifyRelease(-120f, 10f, 500L, bounds.minX, bounds),
        )
    }

    @Test
    fun fastHorizontalFlicksRefreshLeftAndParkRight() {
        val bounds = SiteShellOrbBounds(360f, 640f)
        val mid = (bounds.minX + bounds.maxX) / 2f
        assertEquals(
            SiteShellOrbRelease.REFRESH,
            SiteShellOrbGesture.classifyRelease(-90f, 8f, 120L, mid, bounds),
        )
        assertEquals(
            SiteShellOrbRelease.PARK_RIGHT,
            SiteShellOrbGesture.classifyRelease(90f, -6f, 120L, mid, bounds),
        )
        assertEquals(
            SiteShellOrbRelease.PARK_RIGHT,
            SiteShellOrbGesture.classifyRelease(20f, 4f, 400L, bounds.maxX, bounds),
        )
    }
}
