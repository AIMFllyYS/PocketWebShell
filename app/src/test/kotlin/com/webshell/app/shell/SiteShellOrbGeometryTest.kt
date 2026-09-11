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
        assertFalse(parked.parkedLeft)
    }

    @Test
    fun expandCentersAndParkRemembersSide() {
        val center = SiteShellOrbAnchor.expandedCenter()
        assertEquals(0.5f, center.x, 0f)
        assertEquals(0.5f, center.y, 0f)
        assertFalse(center.parked)
        val left = SiteShellOrbAnchor.parked(left = true, y = 0.35f)
        assertTrue(left.parkedLeft)
        assertEquals(0f, left.x, 0f)
        val right = SiteShellOrbAnchor.parked(left = false, y = 0.8f)
        assertTrue(right.parked)
        assertFalse(right.parkedLeft)
        assertEquals(1f, right.x, 0f)
        assertEquals(0.8f, right.y, 0f)
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
    fun parkedCapsuleIsAShortEdgeTab() {
        assertEquals(56f, SiteShellOrbMetrics.ORB_SIZE, 0f)
        assertEquals(18f, SiteShellOrbMetrics.PARKED_WIDTH, 0f)
        assertEquals(48f, SiteShellOrbMetrics.PARKED_HEIGHT, 0f)
        assertTrue(SiteShellOrbMetrics.PARKED_HEIGHT < SiteShellOrbMetrics.ORB_SIZE)
        assertEquals(0f, SiteShellOrbMetrics.PARKED_EDGE_INSET, 0f)
        val bounds = SiteShellOrbBounds(360f, 640f)
        assertEquals(SiteShellOrbMetrics.ORB_SIZE, bounds.orbSize, 0f)
        val leftSnap = bounds.parkSnapCenter(SiteShellOrbAnchor.parked(left = true, y = 0.4f))
        assertEquals(bounds.orbSize / 2f, leftSnap.x, 0.01f)
    }

    @Test
    fun tapStaysPutAndASlideDocksToThatEdge() {
        val bounds = SiteShellOrbBounds(360f, 640f)
        val mid = bounds.width / 2f
        assertEquals(
            SiteShellOrbRelease.TAP,
            SiteShellOrbGesture.classifyRelease(8f, -4f, mid, bounds),
        )
        assertEquals(
            SiteShellOrbRelease.PARK_LEFT,
            SiteShellOrbGesture.classifyRelease(-80f, 10f, mid, bounds),
        )
        assertEquals(
            SiteShellOrbRelease.PARK_RIGHT,
            SiteShellOrbGesture.classifyRelease(80f, -6f, mid, bounds),
        )
        assertEquals(
            SiteShellOrbRelease.PARK_LEFT,
            SiteShellOrbGesture.classifyRelease(4f, 30f, bounds.minX, bounds),
        )
        assertEquals(
            SiteShellOrbRelease.PARK_RIGHT,
            SiteShellOrbGesture.classifyRelease(4f, 30f, bounds.maxX, bounds),
        )
        assertEquals(
            SiteShellOrbRelease.PARK_LEFT,
            SiteShellOrbGesture.classifyRelease(20f, 8f, 40f, bounds),
        )
        assertEquals(
            SiteShellOrbRelease.PARK_RIGHT,
            SiteShellOrbGesture.classifyRelease(-20f, 8f, bounds.width - 40f, bounds),
        )
    }
}
