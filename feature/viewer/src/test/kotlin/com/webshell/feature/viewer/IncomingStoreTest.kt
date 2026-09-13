package com.webshell.feature.viewer

import java.io.ByteArrayInputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingStoreTest {
    @Test
    fun copyStopsAtSizeCapAndSweepOnlyRemovesTmp() {
        val root = createTempDirectory("incoming-store").toFile()
        val keep = File(File(root, "localapps"), "app-saved").apply { mkdirs() }
        File(keep, "index.html").writeText("<html></html>")
        val tmp = IncomingStore.sessionDir(root, "tmp-unit-a")
        tmp.mkdirs()
        File(tmp, "index.html").writeText("old")

        val dest = File(IncomingStore.sessionDir(root, "tmp-unit-b").apply { mkdirs() }, "index.html")
        IncomingStore.copyBounded(ByteArrayInputStream("<html>ok</html>".toByteArray()), dest)
        assertEquals("<html>ok</html>", dest.readText())

        try {
            IncomingStore.copyBounded(ByteArrayInputStream(ByteArray(32) { 1 }), dest, maxBytes = 8)
            error("expected too large")
        } catch (_: IncomingTooLargeException) {
            assertFalse(dest.exists())
        }

        IncomingStore.sweepOrphans(root, minAgeMs = 0L)
        assertFalse(File(File(root, "localapps"), "tmp-unit-a").exists())
        assertFalse(File(File(root, "localapps"), "tmp-unit-b").exists())
        assertTrue(File(keep, "index.html").isFile)
    }

    @Test
    fun sweepLeavesFreshTemporaryDirectories() {
        val root = createTempDirectory("incoming-fresh").toFile()
        val tmp = IncomingStore.sessionDir(root, "tmp-fresh")
        tmp.mkdirs()
        File(tmp, "index.html").writeText("<html></html>")
        IncomingStore.sweepOrphans(root, minAgeMs = IncomingStore.ORPHAN_MIN_AGE_MS, now = System.currentTimeMillis())
        assertTrue(tmp.isDirectory)
        IncomingStore.sweepOrphans(root, minAgeMs = 0L)
        assertFalse(tmp.exists())
    }

    @Test
    fun markdownDisplayStripsRemoteImagesAndKeepsText() {
        val raw = """
            # Title

            Hello <script>alert(1)</script>

            ![photo](https://evil.test/a.png)
            [safe](https://example.com)
        """.trimIndent()
        val shown = IncomingMarkdownPolicy.forDisplay(raw)
        assertTrue(shown.contains("Hello <script>alert(1)</script>"))
        assertTrue(shown.contains("![photo]()"))
        assertFalse(shown.contains("https://evil.test/a.png"))
        assertTrue(shown.contains("[safe](https://example.com)"))
    }

    @Test
    fun sessionMetaRoundTripsTitleAndSource() {
        val root = createTempDirectory("incoming-meta").toFile()
        val dir = IncomingStore.sessionDir(root, "tmp-meta").apply { mkdirs() }
        IncomingStore.writeMeta(
            dir,
            IncomingSessionMeta(
                title = "笔记 / notes",
                displayPath = "/storage/emulated/0/Download/notes.md",
                sourceKey = "/storage/emulated/0/Download/notes.md",
            ),
        )
        val meta = IncomingStore.readMeta(dir)
        assertEquals("笔记 / notes", meta?.title)
        assertEquals("/storage/emulated/0/Download/notes.md", meta?.displayPath)
        assertEquals("/storage/emulated/0/Download/notes.md", meta?.sourceKey)
    }

    @Test
    fun decodeTextDropsUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "# Hi".toByteArray()
        assertEquals("# Hi", IncomingStore.decodeText(bytes))
    }
}
