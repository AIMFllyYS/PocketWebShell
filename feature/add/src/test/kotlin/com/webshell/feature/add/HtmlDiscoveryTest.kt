package com.webshell.feature.add

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlDiscoveryTest {
    @Test
    fun walkFindsHtmlInSeedFoldersAndSkipsNoise() {
        val volume = createTempDirectory("html-discovery").toFile()
        val download = File(volume, "Download").apply { mkdirs() }
        File(download, "page.html").writeText("<html></html>")
        File(File(download, "notes").apply { mkdirs() }, "note.htm").writeText("<html></html>")
        File(File(download, "cache").apply { mkdirs() }, "old.html").writeText("<html></html>")
        File(File(volume, "Pictures").apply { mkdirs() }, "photo.html").writeText("<html></html>")
        val data = File(volume, "Android/data/com.other.app/files").apply { mkdirs() }
        File(data, "hidden.html").writeText("<html></html>")

        val found = HtmlDiscovery.walk(listOf(download), volume)
        val names = found.map { it.name }.toSet()
        assertEquals(setOf("page.html", "note.htm"), names)
        assertTrue(found.none { it.name == "old.html" || it.name == "photo.html" || it.name == "hidden.html" })
        assertEquals("Download", found.first { it.name == "page.html" }.location)
        assertEquals("Download/notes", found.first { it.name == "note.htm" }.location)
    }

    @Test
    fun walkFindsHtmlAfterManyNonHtmlSiblings() {
        val volume = createTempDirectory("html-late").toFile()
        val download = File(volume, "Download").apply { mkdirs() }
        repeat(300) { index ->
            File(download, "noise-$index.apk").writeBytes(ByteArray(1))
        }
        File(download, "late.html").writeText("<html></html>")
        val found = HtmlDiscovery.walk(listOf(download), volume)
        assertTrue(found.any { it.name == "late.html" })
    }

    @Test
    fun ownedRootsMayReadAppPrivateFiles() {
        val volume = createTempDirectory("html-owned").toFile()
        val owned = File(volume, "Android/data/com.webshell.app/files/Download").apply { mkdirs() }
        File(owned, "mine.html").writeText("<html></html>")
        File(File(volume, "Android/data/com.other.app/files").apply { mkdirs() }, "theirs.html")
            .writeText("<html></html>")

        val found = HtmlDiscovery.walk(
            seeds = listOf(owned, File(volume, "Android/data/com.other.app/files")),
            volume = volume,
            ownedRoots = listOf(owned),
        )
        assertEquals(listOf("mine.html"), found.map { it.name })
    }

    @Test
    fun mergeDedupsAndSortsByRecency() {
        val dir = createTempDirectory("html-merge").toFile()
        val older = File(dir, "older.html").apply { writeText("a"); setLastModified(1_000L) }
        val newer = File(dir, "newer.html").apply { writeText("b"); setLastModified(5_000L) }
        val duplicate = DiscoveredHtml(older, older.name, 1, 1_000L, "Download")
        val first = DiscoveredHtml(older, older.name, 1, 1_000L, "Download")
        val second = DiscoveredHtml(newer, newer.name, 1, 5_000L, "Download")
        val merged = HtmlDiscovery.merge(listOf(duplicate, second, first))
        assertEquals(listOf("newer.html", "older.html"), merged.map { it.name })
    }

    @Test
    fun queryMatchesNameAndRelativeLocation() {
        assertTrue(HtmlDiscovery.matchesQuery("note html", "notes.html", "Download"))
        assertTrue(HtmlDiscovery.matchesQuery("weixin", "page.html", "Download/WeiXin"))
        assertTrue(HtmlDiscovery.matchesQuery("download", "page.html", "Download/WeiXin"))
        assertTrue(HtmlDiscovery.matchesQuery("微信", "page.html", "Download/微信"))
        assertFalse(HtmlDiscovery.matchesQuery("telegram", "page.html", "Download/WeiXin"))
        assertFalse(HtmlDiscovery.matchesQuery("emulated", "page.html", "Download/WeiXin"))
        assertFalse(HtmlDiscovery.matchesQuery("storage", "page.html", "Download/WeiXin"))
        assertTrue(HtmlDiscovery.matchesQuery("  ", "any.html", "Download"))
        assertTrue(HtmlDiscovery.matchesListedFile("weixin", "page.html", "", "Download/WeiXin"))
        assertFalse(HtmlDiscovery.matchesListedFile("weixin", "page.html", "", "Download/QQ"))
    }

    @Test
    fun seedPathsStayInPublicPlaces() {
        assertFalse(HtmlDiscovery.seedRelativePaths.any { it.isEmpty() || it == "/" || it == "." })
        assertTrue(HtmlDiscovery.seedRelativePaths.all { !it.contains("Android/data") && !it.contains("Android/obb") })
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("tencent/MicroMsg/Download"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("Tencent/MicroMsg/Download"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("tencent/MicroMsg/WeiXin"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("Tencent/MicroMsg/WeiXin"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("tencent/QQfile_recv"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("Tencent/QQfile_recv"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("tencent/QQfile_share"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("Tencent/QQfile_share"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("tencent/QQ_Download"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("Tencent/QQ_Download"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("tencent/TIMfile_recv"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("Tencent/TIMfile_recv"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("Download/Tencent"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("Download/WeChat"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("WeiXin"))
        assertTrue(HtmlDiscovery.seedRelativePaths.contains("Quark/Download"))
        assertTrue(HtmlDiscovery.seedRelativePaths.any { it.startsWith("Download") })
        assertTrue(HtmlDiscovery.shouldSkipDirectory("cache"))
        assertTrue(HtmlDiscovery.shouldSkipDirectory(".hidden"))
        assertFalse(HtmlDiscovery.shouldSkipDirectory("Download"))
        assertFalse(HtmlDiscovery.shouldSkipDirectory("data"))
        assertTrue(HtmlDiscovery.isProtectedTree(File("/storage/emulated/0/Android/data/com.foo/files")))
        assertFalse(HtmlDiscovery.isProtectedTree(File("/storage/emulated/0/Android/media/com.tencent.mm")))
    }

    @Test
    fun nonHtmlAndHiddenNamesAreRejected() {
        val volume = createTempDirectory("html-reject").toFile()
        val download = File(volume, "Download").apply { mkdirs() }
        File(download, "ok.html").writeText("<html></html>")
        File(download, "notes.md").writeText("# no")
        File(download, ".secret.html").writeText("<html></html>")
        assertTrue(HtmlDiscovery.acceptFile(File(download, "ok.html"), volume))
        assertFalse(HtmlDiscovery.acceptFile(File(download, "notes.md"), volume))
        assertFalse(HtmlDiscovery.acceptFile(File(download, ".secret.html"), volume))
    }
}
