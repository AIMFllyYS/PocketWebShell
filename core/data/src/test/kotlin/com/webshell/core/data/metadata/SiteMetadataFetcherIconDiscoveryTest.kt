package com.webshell.core.data.metadata

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteMetadataFetcherIconDiscoveryTest {

    private val fetcher = SiteMetadataFetcher(okhttp3.OkHttpClient())

    @Test
    fun `prefers apple touch png over tiny ico`() {
        val html = """
            <link rel="icon" href="/favicon.ico" sizes="16x16">
            <link rel="apple-touch-icon" href="/apple-touch-icon.png" sizes="180x180">
        """.trimIndent()
        val urls = fetcher.rankDocumentIcons("https://example.com/", Jsoup.parse(html, "https://example.com/"))
        assertEquals("https://example.com/apple-touch-icon.png", urls.first())
    }

    @Test
    fun `ignores data uri icons and still keeps favicon fallback`() {
        val html = """<link rel="icon" href="data:image/png;base64,aaa">"""
        val urls = fetcher.rankDocumentIcons("https://news.example/", Jsoup.parse(html, "https://news.example/"))
        assertTrue(urls.contains("https://news.example/favicon.ico"))
    }

    @Test
    fun `known hosts use official backup icons`() {
        assertEquals(
            "https://github.com/fluidicon.png",
            fetcher.displayFallbackIconUrl("https://github.com/AIMFllyYS/PocketWebShell"),
        )
        assertEquals(
            "https://www.wikipedia.org/static/apple-touch/wikipedia.png",
            fetcher.displayFallbackIconUrl("https://en.wikipedia.org/wiki/Android"),
        )
        assertEquals(
            "https://unknown-site.example/favicon.ico",
            fetcher.displayFallbackIconUrl("https://unknown-site.example/path"),
        )
        assertEquals(
            "https://unknown-site.example/favicon.ico",
            fetcher.originFaviconUrl("https://unknown-site.example/path"),
        )
    }

    @Test
    fun `sniffs html even when the host labels it as plain text`() {
        val html = "<!doctype html><html><head><link rel=\"icon\" href=\"/app.png\"></head></html>"
        assertTrue(fetcher.looksLikeHtml(html.toByteArray()))
        assertFalse(fetcher.looksLikeHtml("""{"ok":true}""".toByteArray()))
    }
}
