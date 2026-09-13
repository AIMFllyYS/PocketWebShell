package com.webshell.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserOpenTabMappingTest {
    @Test
    fun incomingMarkdownRoundTripsWithoutThumbnail() {
        val tab = BrowserTab(
            tabId = "md1",
            title = "notes.md",
            url = "/storage/emulated/0/Download/notes.md",
            sessionId = "browser-md1",
            restoreStartUrlIfBlank = false,
            kind = BrowserTabKind.INCOMING_MARKDOWN,
            displayPath = "/storage/emulated/0/Download/notes.md",
            markdownContent = "# Hi",
            localAppId = "tmp-notes",
            sourceKey = "/storage/emulated/0/Download/notes.md",
        )
        val row = tab.toOpenTabEntity(position = 1, desktopMode = false, updatedAt = 10L)
        assertEquals("incoming_markdown", row.kind)
        assertEquals(tab.displayPath, row.displayPath)
        assertEquals(tab.sourceKey, row.sourceKey)
        assertEquals("tmp-notes", row.localAppId)
        val restored = row.toBrowserTab(parseBrowserTabKind(row.kind), markdown = "# Hi")
        assertEquals(tab.tabId, restored.tabId)
        assertEquals(tab.kind, restored.kind)
        assertEquals("# Hi", restored.markdownContent)
        assertEquals(tab.displayPath, restored.addressChrome())
        assertEquals(tab.sourceKey, restored.sourceKey)
        assertTrue(restored.canEditAddress().not())
        assertEquals(
            "/storage/emulated/0/Download/notes.md",
            BrowserTab(
                tabId = "md2",
                title = "notes.md",
                url = "about:blank",
                kind = BrowserTabKind.INCOMING_MARKDOWN,
                displayPath = "raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fnotes.md",
            ).addressChrome(),
        )
    }

    @Test
    fun webTabDoesNotTreatAssetTmpAsBookmarkable() {
        val tab = BrowserTab(
            tabId = "w1",
            title = "page",
            url = "https://example.com/",
            kind = BrowserTabKind.WEB,
        )
        assertTrue(tab.canBookmark())
        assertEquals("https://example.com/", tab.addressChrome())
        assertNull(parseBrowserTabKind("web").takeIf { it != BrowserTabKind.WEB })
        assertEquals(BrowserTabKind.INCOMING_HTML, parseBrowserTabKind("incoming_html"))
    }
}
