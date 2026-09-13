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
    fun `existingPath keeps remote icon when no local file`() {
        val dir = kotlin.io.path.createTempDirectory("local-html-remote").toFile()
        try {
            java.io.File(dir, "index.html").writeText(
                """<html><head><link rel="icon" href="https://cdn.example/favicon.png"></head></html>""",
            )
            assertEquals(
                "https://cdn.example/favicon.png",
                LocalHtmlIcon.existingPath(dir, "index.html"),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `existingPath prefers local file over remote`() {
        val dir = kotlin.io.path.createTempDirectory("local-html-local-first").toFile()
        try {
            java.io.File(dir, "mark.png").writeBytes(byteArrayOf(1, 2, 3))
            java.io.File(dir, "index.html").writeText(
                """
                <link rel="icon" href="mark.png">
                <link rel="apple-touch-icon" href="https://cdn.example/app.png">
                """.trimIndent(),
            )
            assertEquals(
                java.io.File(dir, "mark.png").absolutePath,
                LocalHtmlIcon.existingPath(dir, "index.html"),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `existingPath writes a small png data uri`() {
        val dir = kotlin.io.path.createTempDirectory("local-html-data").toFile()
        try {
            val png = java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))
            java.io.File(dir, "index.html").writeText(
                """<link rel="icon" href="data:image/png;base64,$png">""",
            )
            val path = LocalHtmlIcon.existingPath(dir, "index.html")
            assertEquals(java.io.File(dir, "extracted-favicon.png").absolutePath, path)
            assertEquals(4, java.io.File(path!!).length().toInt())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `reads shortcut icon relative path`() {
        val html = """<link rel="shortcut icon" href="./mark.png?v=2">"""
        assertEquals("mark.png", LocalHtmlIcon.relativePath(html))
    }

    @Test
    fun `existingPath reads only the html head of a large file`() {
        val dir = kotlin.io.path.createTempDirectory("local-html-icon").toFile()
        try {
            java.io.File(dir, "favicon.png").writeBytes(byteArrayOf(1, 2, 3))
            val html = buildString {
                append("""<html><head><link rel="icon" href="favicon.png"></head><body>""")
                append("x".repeat(400_000))
                append("</body></html>")
            }
            java.io.File(dir, "index.html").writeText(html)
            assertEquals(
                java.io.File(dir, "favicon.png").absolutePath,
                LocalHtmlIcon.existingPath(dir, "index.html"),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `existingPath does not scan past the head budget`() {
        val dir = kotlin.io.path.createTempDirectory("local-html-icon-late").toFile()
        try {
            java.io.File(dir, "icons").mkdirs()
            java.io.File(dir, "icons/late.png").writeBytes(byteArrayOf(1))
            val html = "x".repeat(LocalHtmlIcon.HEAD_BYTES + 8) +
                """<link rel="icon" href="icons/late.png">"""
            java.io.File(dir, "index.html").writeText(html)
            assertNull(LocalHtmlIcon.existingPath(dir, "index.html"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
