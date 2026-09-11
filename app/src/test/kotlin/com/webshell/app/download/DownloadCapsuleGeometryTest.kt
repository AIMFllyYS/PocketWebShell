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
}
