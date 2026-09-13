package com.webshell.core.webengine.storage

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteStorageProbeTest {

    @Test
    fun `parses example host leveldb directory`() {
        assertEquals("example.com", parseIndexedDbDirHost("https_example.com_0.indexeddb.leveldb"))
    }

    @Test
    fun `parses blob companion directory`() {
        assertEquals("example.com", parseIndexedDbDirHost("https_example.com_0.indexeddb.blob"))
    }

    @Test
    fun `strips non-default port`() {
        assertEquals("example.com", parseIndexedDbDirHost("https_example.com_8443_0"))
    }

    @Test
    fun `keeps underscores inside host`() {
        assertEquals("my_site_example", parseIndexedDbDirHost("https_my_site_example_0.indexeddb.leveldb"))
        assertEquals("my_site_example", parseIndexedDbDirHost("https_my_site_example_8443_0"))
    }

    @Test
    fun `parses localhost with port`() {
        assertEquals("localhost", parseIndexedDbDirHost("http_localhost_8080_0"))
    }

    @Test
    fun `malformed names do not throw and return null`() {
        assertNull(parseIndexedDbDirHost("garbage"))
        assertNull(parseIndexedDbDirHost("https_0"))
        assertNull(parseIndexedDbDirHost("ftp_example.com_0"))
        assertNull(parseIndexedDbDirHost("https_example.com_x"))
        assertNull(parseIndexedDbDirHost(""))
        assertNull(parseIndexedDbDirHost("_.indexeddb.leveldb"))
    }

    @Test
    fun `indexedDbBytesByHost aggregates files by host`() {
        val root = createTempDirectory("idb-probe").toFile()
        try {
            val profile = File(root, "Default")
            val idb = File(profile, "IndexedDB")
            val example = File(idb, "https_example.com_0.indexeddb.leveldb")
            val blob = File(idb, "https_example.com_0.indexeddb.blob")
            val local = File(idb, "http_localhost_8080_0.indexeddb.leveldb")
            val junk = File(idb, "not-a-real-dir")
            assertTrue(example.mkdirs())
            assertTrue(blob.mkdirs())
            assertTrue(local.mkdirs())
            assertTrue(junk.mkdirs())
            File(example, "000003.log").writeText("abcd")
            File(blob, "blob").writeText("xy")
            File(local, "LOG").writeText("z")
            File(junk, "x").writeText("nope")
            val bytes = indexedDbBytesByHost(profile)
            assertEquals(6L, bytes["example.com"])
            assertEquals(1L, bytes["localhost"])
            assertEquals(setOf("example.com", "localhost"), bytes.keys)
        } finally {
            root.deleteRecursively()
        }
    }
}
