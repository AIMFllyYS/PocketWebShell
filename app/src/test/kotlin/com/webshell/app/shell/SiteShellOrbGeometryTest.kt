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
        val bounds = SiteShellOrbBounds(360f, 640f)
        val left = SiteShellOrbAnchor.parked(left = true, y = 0.35f)
        assertTrue(left.parkedLeft)
        assertEquals(0f, left.x, 0f)
        val right = SiteShellOrbAnchor.parked(left = false, y = 0.8f)
        assertTrue(right.parked)
        assertFalse(right.parkedLeft)
        assertEquals(1f, right.x, 0f)
        assertEquals(0.8f, right.y, 0f)

        val lastFree = bounds.anchorAt(120f, bounds.centerY(left))
        val restored = expandFromParked(left, lastFree, bounds)
        assertEquals(lastFree.x, restored.x, 0f)
        assertEquals(lastFree.y, restored.y, 0f)
        assertFalse(restored.parked)

        val expectedInner = bounds.anchorAt(
            SiteShellOrbMetrics.PARK_EDGE_BAND_DP + SiteShellOrbMetrics.UNPARK_SLACK_DP,
            bounds.centerY(left),
        )
        val noMemory = expandFromParked(left, lastFree = null, bounds)
        assertFalse(noMemory.parked)
        assertFalse(noMemory.x == 0.5f && noMemory.y == 0.5f)
        assertEquals(expectedInner.x, noMemory.x, 0.001f)
        assertEquals(expectedInner.y, noMemory.y, 0.001f)

        val opposite = bounds.anchorAt(280f, bounds.centerY(left))
        val fromOpposite = expandFromParked(left, opposite, bounds)
        assertFalse(fromOpposite.x == 0.5f && fromOpposite.y == 0.5f)
        assertEquals(expectedInner.x, fromOpposite.x, 0.001f)
        assertEquals(expectedInner.y, fromOpposite.y, 0.001f)
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
        assertEquals(48f, SiteShellOrbMetrics.ORB_SIZE, 0f)
        assertEquals(18f, SiteShellOrbMetrics.PARKED_WIDTH, 0f)
        assertEquals(48f, SiteShellOrbMetrics.PARKED_HEIGHT, 0f)
        assertTrue(SiteShellOrbMetrics.PARKED_WIDTH < SiteShellOrbMetrics.ORB_SIZE)
        assertTrue(SiteShellOrbMetrics.PARKED_HEIGHT <= SiteShellOrbMetrics.ORB_SIZE)
        assertEquals(0f, SiteShellOrbMetrics.PARKED_EDGE_INSET, 0f)
        assertEquals(36f, SiteShellOrbMetrics.MARK_DOT_DIAMETER, 0f)
        assertEquals(SiteShellOrbMetrics.ORB_SIZE * 0.75f, SiteShellOrbMetrics.MARK_DOT_DIAMETER, 0f)
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
            SiteShellOrbRelease.STAY,
            SiteShellOrbGesture.classifyRelease(20f, 8f, mid, bounds),
        )
        assertEquals(
            SiteShellOrbRelease.STAY,
            SiteShellOrbGesture.classifyRelease(4f, 40f, mid, bounds),
        )
        assertEquals(
            SiteShellOrbRelease.STAY,
            SiteShellOrbGesture.classifyRelease(28f, 70f, mid, bounds),
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
