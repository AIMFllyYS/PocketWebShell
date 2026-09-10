package com.webshell.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLoadProgressTest {
    @Test
    fun finishedLoadReportsComplete() {
        assertEquals(1f, PageLoadProgress.display(40, loading = false, trickle = 0.2f), 0f)
    }

    @Test
    fun loadingNeverExceedsCeilingAndKeepsAVisibleFloor() {
        assertEquals(PageLoadProgress.Floor, PageLoadProgress.display(0, loading = true, trickle = 0f), 0f)
        assertTrue(PageLoadProgress.display(100, loading = true, trickle = 0f) <= PageLoadProgress.Ceiling)
        assertEquals(PageLoadProgress.TrickleCeiling, PageLoadProgress.display(10, loading = true, trickle = 0.9f), 0.001f)
    }

    @Test
    fun rawProgressCanOutrunTheTrickle() {
        val value = PageLoadProgress.display(80, loading = true, trickle = 0.2f)
        assertTrue(value > 0.2f)
        assertTrue(value < 1f)
    }
}
