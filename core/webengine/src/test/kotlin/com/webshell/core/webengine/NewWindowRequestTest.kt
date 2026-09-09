package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Test

class NewWindowRequestTest {
    @Test fun `request keeps source and target identity`() {
        val request = NewWindowRequest("browser-a", "https://example.com", true, false, null, "browser-b")
        assertEquals("browser-a", request.sourceSessionId)
        assertEquals("browser-b", request.targetSessionId)
    }
}
