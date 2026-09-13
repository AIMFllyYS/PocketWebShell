package com.webshell.feature.add

import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAppImporterTest {
    @Test
    fun importFilesCopiesHtmlIntoLocalapps() {
        val filesDir = createTempDirectory("importer-files").toFile()
        val source = File(createTempDirectory("importer-src").toFile(), "Note.html").apply {
            writeText("<html><title>Note</title></html>")
        }

        val result = LocalAppImporter.importFiles("app-test", listOf(source), filesDir)

        assertTrue(result.isSuccess)
        val copied = File(filesDir, "localapps/app-test/Note.html")
        assertTrue(copied.isFile)
        assertEquals("<html><title>Note</title></html>", copied.readText())
        assertEquals("local://app-test/Note.html", result.getOrThrow().entryUrl)
    }

    @Test
    fun importFilesRejectsApkAndExtensionlessFiles() {
        val filesDir = createTempDirectory("importer-reject").toFile()
        val apk = File(createTempDirectory("importer-apk").toFile(), "evil.apk").apply { writeText("xx") }
        val bare = File(createTempDirectory("importer-bare").toFile(), "readme").apply { writeText("xx") }

        assertTrue(LocalAppImporter.importFiles("app-apk", listOf(apk), filesDir).isFailure)
        assertTrue(LocalAppImporter.importFiles("app-bare", listOf(bare), filesDir).isFailure)
        assertFalse(File(filesDir, "localapps/app-apk/evil.apk").exists())
        assertFalse(File(filesDir, "localapps/app-bare/readme").exists())
    }

    @Test
    fun importFilesFailsWhenFileExceedsLimit() {
        val filesDir = createTempDirectory("importer-limit").toFile()
        val huge = File(createTempDirectory("importer-huge").toFile(), "huge.html")
        RandomAccessFile(huge, "rw").use { it.setLength(LocalAppImporter.MAX_HTML_BYTES + 1) }

        val result = LocalAppImporter.importFiles("app-huge", listOf(huge), filesDir)

        assertTrue(result.isFailure)
        assertFalse(File(filesDir, "localapps/app-huge/huge.html").exists())
    }

    @Test
    fun sanitizeFileNameStripsSeparatorsAndKeepsHtml() {
        assertEquals("my_file.htm", LocalAppImporter.sanitizeFileName("dir/my:file.htm"))
        assertEquals("index.html", LocalAppImporter.sanitizeFileName("   "))
    }

    @Test
    fun copyBoundedToFileRejectsOverflowAndKeepsParentClean() {
        val dir = createTempDirectory("importer-stream").toFile()
        val target = File(dir, "page.html")
        try {
            LocalAppImporter.copyBoundedToFile(ByteArrayInputStream(ByteArray(32) { 1 }), target, maxBytes = 8)
            error("expected overflow")
        } catch (_: IllegalStateException) {
            assertFalse(target.exists())
        }
    }
}
