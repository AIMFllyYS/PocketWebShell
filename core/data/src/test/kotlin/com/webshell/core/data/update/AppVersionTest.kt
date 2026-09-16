package com.webshell.core.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun parseStripsVPrefixAndRejectsLeadingZeros() {
        assertEquals(AppVersion(0, 1, 52), AppVersion.parse("0.1.52"))
        assertEquals(AppVersion(0, 1, 52), AppVersion.parse("v0.1.52"))
        assertEquals(AppVersion(0, 1, 10), AppVersion.parse("V0.1.10"))
        assertNull(AppVersion.parse("0.01.1"))
        assertNull(AppVersion.parse("0.1"))
        assertNull(AppVersion.parse("1.0.0-rc.1"))
        assertNull(AppVersion.parse(""))
    }

    @Test
    fun compareUsesNumericSegmentsWithoutCarry() {
        assertTrue(AppVersion.compareNames("0.1.10", "0.1.9")!! > 0)
        assertTrue(AppVersion.compareNames("0.1.1001", "0.1.1000")!! > 0)
        assertEquals(0, AppVersion.compareNames("v0.1.52", "0.1.52"))
        assertTrue(AppVersion.compareNames("0.1.52", "0.1.53")!! < 0)
        assertNull(AppVersion.compareNames("latest", "0.1.52"))
    }
}
