package com.webshell.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BrowserTabSessionTest {
    @Test
    fun regularTabKeepsCanonicalSessionId() {
        val tab = BrowserTab("abc12345", "Example", "https://example.com")
        assertEquals("browser-abc12345", tab.sessionId)
    }

    @Test
    fun adoptedWindowKeepsPoolSessionId() {
        val tab = BrowserTab(
            tabId = "tab9",
            title = "",
            url = "about:blank",
            sessionId = "browser-4f2c91aa0b3d",
        )
        assertEquals("browser-4f2c91aa0b3d", tab.sessionId)
        assertNotEquals("browser-${tab.tabId}", tab.sessionId)
        assertEquals(true, tab.restoreStartUrlIfBlank)
        val adopted = tab.copy(restoreStartUrlIfBlank = false)
        assertEquals(false, adopted.restoreStartUrlIfBlank)
    }
}
