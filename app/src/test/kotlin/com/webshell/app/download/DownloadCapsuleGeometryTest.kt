package com.webshell.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCapsuleGeometryTest {
    @Test
    fun defaultYAvoidsTopSiteShellAndBottomDock() {
        assertEquals(0.62f, DownloadCapsuleGeometry.DEFAULT_Y, 0f)
        assertEquals(0.62f, DownloadCapsuleGeometry.defaultY(), 0f)
        assertTrue(DownloadCapsuleGeometry.DEFAULT_Y > 0f)
        assertTrue(DownloadCapsuleGeometry.DEFAULT_Y < 1f)
        assertTrue(DownloadCapsuleGeometry.clearsDefaultSiteShellAndDock(DownloadCapsuleGeometry.DEFAULT_Y))
        assertFalse(DownloadCapsuleGeometry.clearsDefaultSiteShellAndDock(0f))
        assertFalse(DownloadCapsuleGeometry.clearsDefaultSiteShellAndDock(1f))
    }

    @Test
    fun unsetYRestoresToDefaultMidRight() {
        assertEquals(0.62f, DownloadCapsuleGeometry.normalizeY(-1f), 0f)
        assertEquals(0.62f, DownloadCapsuleGeometry.normalizeY(Float.NaN), 0f)
        assertEquals(0f, DownloadCapsuleGeometry.normalizeY(0f), 0f)
        assertEquals(1f, DownloadCapsuleGeometry.normalizeY(1f), 0f)
    }

    @Test
    fun shortRightFlickParksVerticalMoveDoesNot() {
        assertTrue(DownloadCapsuleGeometry.parksToRight(12f, 4f))
        assertFalse(DownloadCapsuleGeometry.parksToRight(8f, 2f))
        assertFalse(DownloadCapsuleGeometry.parksToRight(4f, 40f))
        assertTrue(DownloadCapsuleGeometry.isRightFlick(11f, 4f))
        assertFalse(DownloadCapsuleGeometry.isRightFlick(8f, 2f))
        assertEquals(
            DownloadCapsuleRelease.TAP,
            DownloadCapsuleGeometry.classifyRelease(4f, 2f),
        )
        assertEquals(
            DownloadCapsuleRelease.PARK,
            DownloadCapsuleGeometry.classifyRelease(11f, 3f),
        )
        assertEquals(
            DownloadCapsuleRelease.MOVE,
            DownloadCapsuleGeometry.classifyRelease(4f, 40f),
        )
        assertEquals(
            DownloadCapsuleRelease.MOVE,
            DownloadCapsuleGeometry.classifyRelease(-8f, 24f),
        )
    }
}
