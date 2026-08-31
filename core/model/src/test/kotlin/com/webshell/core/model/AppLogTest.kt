package com.webshell.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLogTest {

    @Before
    fun setUp() {
        AppLog.clear()
    }

    @Test
    fun `log appends entries in order`() {
        AppLog.log("home", "first")
        AppLog.log("home", "second")

        val entries = AppLog.entries()
        assertEquals(2, entries.size)
        assertEquals("first", entries[0].message)
        assertEquals("second", entries[1].message)
    }

    @Test
    fun `buffer evicts oldest beyond capacity`() {
        repeat(AppLog.CAPACITY + 50) { index ->
            AppLog.log("home", "msg-$index")
        }

        val entries = AppLog.entries()
        assertEquals(AppLog.CAPACITY, entries.size)
        assertEquals("msg-50", entries.first().message)
        assertEquals("msg-${AppLog.CAPACITY + 49}", entries.last().message)
    }

    @Test
    fun `exportText contains time tag and message`() {
        AppLog.log("theme", "switched to pure black")

        val text = AppLog.exportText()
        assertTrue(text.contains("[theme]"))
        assertTrue(text.contains("switched to pure black"))
        assertTrue(text.contains(AppLog.formatTime(AppLog.entries().first().timeMillis)))
    }

    @Test
    fun `clear empties the buffer`() {
        AppLog.log("home", "hello")
        AppLog.clear()

        assertTrue(AppLog.entries().isEmpty())
        assertTrue(AppLog.exportText().isEmpty())
    }
}
