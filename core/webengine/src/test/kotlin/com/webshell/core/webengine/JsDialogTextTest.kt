package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsDialogTextTest {
    @Test fun `empty and null become empty`() {
        assertEquals("", JsDialogText.sanitize(null))
        assertEquals("", JsDialogText.sanitize(""))
    }

    @Test fun `control characters are stripped except newline and tab`() {
        assertEquals("a b\nc\td", JsDialogText.sanitize("a\u0000b\nc\td"))
    }

    @Test fun `long messages are truncated`() {
        val raw = "x".repeat(JsDialogText.MAX_MESSAGE_CHARS + 80)
        val sanitized = JsDialogText.sanitize(raw)
        assertEquals(JsDialogText.MAX_MESSAGE_CHARS, sanitized.length)
        assertTrue(sanitized.all { it == 'x' })
    }
}
