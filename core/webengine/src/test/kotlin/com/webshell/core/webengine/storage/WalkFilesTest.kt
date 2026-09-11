package com.webshell.core.webengine.storage

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkFilesTest {

    @Test
    fun `missing root is empty not unmeasurable`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "no-such-walk-${System.nanoTime()}")
        assertEquals(emptyList<FileEntry>(), walkFiles(dir))
    }

    @Test
    fun `lists nested http cache files`() {
        val root = createTempDirectory("walk-http").toFile()
        try {
            val cache = File(root, "WebView/Default/HTTP Cache")
            assertTrue(cache.mkdirs())
            File(cache, "index").writeText("ab")
            File(root, "other.log").writeText("c")
            val files = walkFiles(root)
            requireNotNull(files)
            val paths = files.map { it.relativePath }.toSet()
            assertTrue(paths.contains("WebView/Default/HTTP Cache/index"))
            assertTrue(paths.contains("other.log"))
            assertEquals(2, files.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `file used as root is unmeasurable`() {
        val file = File.createTempFile("walk-file", ".tmp")
        try {
            assertNull(walkFiles(file))
        } finally {
            file.delete()
        }
    }
}
