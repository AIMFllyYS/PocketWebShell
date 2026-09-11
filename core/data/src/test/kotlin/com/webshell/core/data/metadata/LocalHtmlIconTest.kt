package com.webshell.core.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalHtmlIconTest {

    @Test
    fun `reads apple touch ahead of generic icon`() {
        val html = """
            <link rel="icon" href="favicon.ico">
            <link rel="apple-touch-icon" href="icons/app.png">
        """.trimIndent()
        assertEquals("icons/app.png", LocalHtmlIcon.relativePath(html))
    }

    @Test
    fun `skips remote and data icons`() {
        val html = """
            <link rel="icon" href="https://cdn.example/favicon.ico">
            <link rel="icon" href="data:image/png;base64,xx">
        """.trimIndent()
        assertNull(LocalHtmlIcon.relativePath(html))
    }

    @Test
    fun `reads shortcut icon relative path`() {
        val html = """<link rel="shortcut icon" href="./mark.png?v=2">"""
        assertEquals("mark.png", LocalHtmlIcon.relativePath(html))
    }
}
