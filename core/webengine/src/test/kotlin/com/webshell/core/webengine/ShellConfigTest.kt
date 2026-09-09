package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellConfigTest {
    @Test fun `merge preserves stable session and profile identity`() {
        val base = ShellConfig(sessionId = "s", profileId = null, startUrl = "https://a.example")
        val updated = base.mergedWith(ShellConfig(sessionId = "other", profileId = "private", desktopMode = true))
        assertEquals("s", updated.sessionId)
        assertEquals(null, updated.profileId)
        assertEquals("https://a.example", updated.startUrl)
        assertEquals(true, updated.desktopMode)
    }
}
