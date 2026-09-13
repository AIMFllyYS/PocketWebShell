package com.webshell.feature.add

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlDirectoryListerTest {
    @Test
    fun listsOnlyHtmlAndHtmIgnoringOtherTypes() {
        val dir = createTempDirectory("html-lister-types").toFile()
        File(dir, "page.HTML").writeText("<html></html>")
        File(dir, "note.htm").writeText("<html></html>")
        File(dir, "readme.md").writeText("no")
        File(dir, "app.apk").writeText("no")
        File(dir, "style.css").writeText("no")
        File(dir, "folder").mkdir()

        val outcome = HtmlDirectoryLister.list(dir) as HtmlDirectoryLister.Outcome.Success
        val names = outcome.entries.map { it.name }
        assertEquals(listOf("folder", "note.htm", "page.HTML"), names.sorted())
        assertTrue(outcome.entries.any { it is HtmlDirectoryLister.HtmlEntry.Directory && it.name == "folder" })
        assertTrue(outcome.entries.filterIsInstance<HtmlDirectoryLister.HtmlEntry.HtmlFile>().all {
            HtmlDirectoryLister.isHtmlName(it.name)
        })
    }

    @Test
    fun missingDirectoryIsEmptyAndAFileIsDenied() {
        val missing = File(createTempDirectory("html-lister-missing").toFile(), "gone")
        val missingOutcome = HtmlDirectoryLister.list(missing) as HtmlDirectoryLister.Outcome.Success
        assertTrue(missingOutcome.entries.isEmpty())
        val file = File(createTempDirectory("html-lister-file").toFile(), "not-a-dir.html").apply {
            writeText("<html></html>")
        }
        assertEquals(HtmlDirectoryLister.Outcome.AccessDenied, HtmlDirectoryLister.list(file))
    }

    @Test
    fun hiddenDotFilesDoNotAppear() {
        val dir = createTempDirectory("html-lister-hidden").toFile()
        File(dir, ".secret.html").writeText("<html></html>")
        File(dir, ".hidden").mkdir()
        File(dir, "visible.html").writeText("<html></html>")

        val outcome = HtmlDirectoryLister.list(dir) as HtmlDirectoryLister.Outcome.Success
        assertEquals(listOf("visible.html"), outcome.entries.map { it.name })
    }

    @Test
    fun directoriesComeFirstAndFilesSortByLastModifiedDescending() {
        val dir = createTempDirectory("html-lister-sort").toFile()
        val older = File(dir, "older.html").apply { writeText("a") }
        val newer = File(dir, "newer.html").apply { writeText("b") }
        older.setLastModified(1_000L)
        newer.setLastModified(5_000L)
        File(dir, "zeta").mkdir()
        File(dir, "alpha").mkdir()

        val outcome = HtmlDirectoryLister.list(dir) as HtmlDirectoryLister.Outcome.Success
        assertEquals(
            listOf("alpha", "zeta", "newer.html", "older.html"),
            outcome.entries.map { it.name },
        )
    }

    @Test
    fun manyDirectoriesDoNotHideHtmlFiles() {
        val dir = createTempDirectory("html-lister-cap").toFile()
        repeat(450) { index -> File(dir, "folder-$index").mkdir() }
        File(dir, "late.html").writeText("<html></html>")
        val outcome = HtmlDirectoryLister.list(dir) as HtmlDirectoryLister.Outcome.Success
        assertTrue(outcome.entries.any { it.name == "late.html" })
    }

    @Test
    fun canonicalParentEscapeIsDenied() {
        val root = createTempDirectory("html-lister-root").toFile()
        val inside = File(root, "inside").apply { mkdirs() }
        File(root, "escape.html").writeText("<html></html>")
        val escaped = File(inside, "..")
        assertEquals(
            HtmlDirectoryLister.Outcome.AccessDenied,
            HtmlDirectoryLister.list(escaped, mustStayUnder = inside),
        )
    }
}
