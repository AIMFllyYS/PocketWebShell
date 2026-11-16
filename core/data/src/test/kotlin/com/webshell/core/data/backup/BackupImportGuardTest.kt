package com.webshell.core.data.backup

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupImportGuardTest {

    @Test
    fun `normal relative file resolves inside the root`() {
        val parent = createTempDirectory("pws-root").toFile()
        val root = File(parent, "tree")
        assertFalse(root.exists())
        assertTrue(BackupImportGuard.isSafeZipEntryName("assets/index.html"))
        assertTrue(BackupImportGuard.isSafeZipEntryName("notes..txt"))
        val resolved = BackupImportGuard.resolveTree(
            root,
            listOf("assets/index.html", "notes..txt"),
        )
        val rootCanonical = root.canonicalFile
        assertEquals(File(rootCanonical, "assets/index.html").canonicalFile, resolved[0])
        assertEquals(File(rootCanonical, "notes..txt").canonicalFile, resolved[1])
        resolved.forEach { destination ->
            assertTrue(destination.toPath().startsWith(rootCanonical.toPath()))
            assertFalse(destination.exists())
        }
        assertFalse(root.exists())
        assertEquals(
            listOf("assets/index.html"),
            BackupImportGuard.relativeNames("localapps/app/", listOf("localapps/app/assets/index.html")),
        )
    }

    @Test
    fun `parent segments are rejected`() {
        assertRejected("../secret")
        assertRejected("foo/../../secret")
        assertRejected("..")
        assertFalse(BackupImportGuard.isSafeZipEntryName("../secret"))
    }

    @Test
    fun `absolute paths are rejected`() {
        assertRejected("/etc/passwd")
        assertRejected("C:/Windows/system.ini")
        assertFalse(BackupImportGuard.isSafeZipEntryName("/etc/passwd"))
        assertFalse(BackupImportGuard.isSafeZipEntryName("localapps/app//etc/passwd"))
    }

    @Test
    fun `percent encoded traversal is rejected`() {
        assertRejected("%2e%2e")
        assertRejected("%2E%2E/secret")
        assertRejected("%2e%2e%2f%2e%2e%2fsecret")
        assertRejected("%252e%252e/secret")
        assertFalse(BackupImportGuard.isSafeZipEntryName("%2e%2e"))
    }

    @Test
    fun `backslash is rejected`() {
        assertRejected("dir\\file.txt")
        assertFalse(BackupImportGuard.isSafeZipEntryName("dir\\file.txt"))
    }

    @Test
    fun `dot empty and nul segments are rejected`() {
        assertRejected("foo/./bar")
        assertRejected("./x")
        assertRejected("foo//bar")
        assertRejected("foo/bar/")
        assertRejected("foo/\u0000bar")
    }

    @Test
    fun `name that canonicalizes outside the root writes nothing`() {
        val root = createTempDirectory("pws-zip-root").toFile()
        val outside = createTempDirectory("pws-zip-outside").toFile()
        val link = File(root, "alias")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        val escaped = File(root, "alias/secret.txt").canonicalFile
        assertEquals(File(outside, "secret.txt").canonicalFile, escaped)
        assertFalse(escaped.toPath().startsWith(root.canonicalFile.toPath()))
        assertTrue(BackupImportGuard.isSafeZipEntryName("alias/secret.txt"))

        val error = assertThrows(IOException::class.java) {
            BackupImportGuard.resolveTree(root, listOf("alias/secret.txt"))
        }
        assertEquals(BackupImportGuard.UNSAFE_PATH_MESSAGE, error.message)
        assertFalse(File(outside, "secret.txt").exists())
        assertFalse(File(root, "alias/secret.txt").exists())
    }

    @Test
    fun `bad prefixed name throws instead of an empty tree`() {
        val error = assertThrows(IOException::class.java) {
            BackupImportGuard.relativeNames(
                "localapps/app/",
                listOf("localapps/app/index.html", "localapps/app/../../evil.txt"),
            )
        }
        assertEquals(BackupImportGuard.UNSAFE_PATH_MESSAGE, error.message)
        assertFalse(error.message!!.contains("evil"))

        val hiddenInPrefix = assertThrows(IOException::class.java) {
            BackupImportGuard.relativeNames(
                "localapps/../../",
                listOf("localapps/../../index.html"),
            )
        }
        assertEquals(BackupImportGuard.UNSAFE_PATH_MESSAGE, hiddenInPrefix.message)
    }

    @Test
    fun `malicious zip entry is rejected before the tree is written`() {
        val scratch = createTempDirectory("pws-evil-zip").toFile()
        val archive = File(scratch, "evil.pws")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            put("localapps/app/index.html", "<html></html>")
            put("localapps/app/../../evil.txt", "pwn")
        }
        val target = File(scratch, "tree")
        ZipFile(archive).use { zip ->
            val matched = zip.entries().toList()
                .filter { !it.isDirectory && !it.name.endsWith("/") && it.name.startsWith("localapps/app/") }
                .map { it.name }
            assertTrue(matched.contains("localapps/app/../../evil.txt"))
            assertThrows(IOException::class.java) {
                BackupImportGuard.resolveTree(
                    target,
                    BackupImportGuard.relativeNames("localapps/app/", matched),
                )
            }
        }
        assertFalse(target.exists())
        assertFalse(File(scratch, "evil.txt").exists())
    }

    @Test
    fun `import copy cap is 512 MiB`() {
        assertEquals(512L * 1024L * 1024L, BackupImportGuard.MAX_IMPORT_BYTES)
    }

    @Test
    fun `copyBounded keeps a file within the cap`() {
        val dest = File(createTempDirectory("pws-cap-ok").toFile(), "import.zip")
        val payload = "PK\u0003\u0004ok".toByteArray()
        val copied = BackupImportGuard.copyBounded(
            ByteArrayInputStream(payload),
            dest,
            maxBytes = payload.size.toLong(),
        )
        assertEquals(payload.size.toLong(), copied)
        assertTrue(dest.isFile)
        assertTrue(dest.readBytes().contentEquals(payload))
    }

    @Test
    fun `copyBounded stops once the cap is exceeded and deletes the file`() {
        val dest = File(createTempDirectory("pws-cap-over").toFile(), "import.zip")
        val source = LimitedChunkStream(chunkSize = 16, totalChunks = 20)
        val error = assertThrows(IOException::class.java) {
            BackupImportGuard.copyBounded(source, dest, maxBytes = 32)
        }
        assertEquals(BackupImportGuard.OVERSIZE_MESSAGE, error.message)
        assertFalse(error.message!!.contains(dest.name))
        assertFalse(error.message!!.contains('/'))
        assertFalse(error.message!!.contains("content:"))
        assertFalse(dest.exists())
        // 16 + 16 刚好到上限并写入，再读一块发现超出后停止，不把剩余块读完。
        assertEquals(3, source.chunksRead)
    }

    private fun assertRejected(name: String) {
        val root = createTempDirectory("pws-reject").toFile()
        val error = assertThrows(IOException::class.java) {
            BackupImportGuard.resolveTree(root, listOf(name))
        }
        assertEquals(BackupImportGuard.UNSAFE_PATH_MESSAGE, error.message)
        assertFalse(error.message!!.contains(name))
        assertFalse(root.listFiles()?.any { it.isFile } ?: false)
    }

    private class LimitedChunkStream(
        private val chunkSize: Int,
        private val totalChunks: Int,
    ) : InputStream() {
        var chunksRead: Int = 0
            private set

        override fun read(): Int = error("single-byte read")

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (chunksRead >= totalChunks) return -1
            val count = minOf(chunkSize, len)
            chunksRead += 1
            b.fill(1.toByte(), off, off + count)
            return count
        }
    }
}
