package com.webshell.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAppUrlsTest {
    @Test
    fun markdownEntriesMatchMdAndMarkdown() {
        assertTrue(LocalAppUrls.isMarkdown("local://app-1/document.md"))
        assertTrue(LocalAppUrls.isMarkdown("local://app-1/Notes.MARKDOWN"))
        assertFalse(LocalAppUrls.isMarkdown("local://app-1/index.html"))
        assertTrue(LocalAppUrls.isMarkdown("https://example.com/readme.md"))
    }

    @Test
    fun htmlEntriesMatchHtmlAndHtm() {
        assertTrue(LocalAppUrls.isHtml("local://app-1/index.html"))
        assertTrue(LocalAppUrls.isHtml("local://app-1/page.HTM"))
        assertFalse(LocalAppUrls.isHtml("local://app-1/document.md"))
    }

    @Test
    fun entryNameStripsQuery() {
        assertEquals("document.md", LocalAppUrls.entryName("local://app-1/document.md?x=1"))
    }
}
