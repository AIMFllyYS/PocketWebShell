package com.webshell.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Address presentation must never rewrite the URL held by a tab/session. */
class BrowserUrlPresentationTest {
    @Test
    fun newTabUsesEmptyAddressPresentation() {
        assertEquals("", "about:blank".stripScheme())
    }

    @Test
    fun addressPresentationOnlyRemovesTheWebScheme() {
        assertEquals("example.com/path?q=value#section", "https://example.com/path?q=value#section".stripScheme())
        assertEquals("example.com:8080/", "http://example.com:8080/".stripScheme())
    }

    @Test
    fun localAndOtherSchemesAreNotMisrepresentedAsWebHosts() {
        assertEquals("local://sample/index.html", "local://sample/index.html".stripScheme())
        assertEquals("about:version", "about:version".stripScheme())
    }

    @Test
    fun displayedHostCanStillNavigateThroughExistingNormalizer() {
        assertEquals("https://example.com/path", normalizeUrl("example.com/path"))
        assertEquals("", normalizeUrl("   "))
    }

    @Test
    fun httpAndHttpsShareDisplayKey() {
        assertEquals(
            "https://example.com/path".displayKey(),
            "http://example.com/path".displayKey(),
        )
    }

    @Test
    fun wwwAndBareHostShareDisplayKey() {
        assertEquals(
            "https://www.example.com/path".displayKey(),
            "https://example.com/path".displayKey(),
        )
    }

    @Test
    fun trailingSlashSharesDisplayKey() {
        assertEquals(
            "https://example.com/path/".displayKey(),
            "https://example.com/path".displayKey(),
        )
    }

    @Test
    fun differentPathsKeepDistinctDisplayKeys() {
        assertEquals("example.com/a", "https://example.com/a".displayKey())
        assertEquals("example.com/b", "https://example.com/b".displayKey())
    }

    @Test
    fun localAndAboutAreNotMergedWithWebHosts() {
        assertEquals("local://sample/index.html", "local://sample/index.html".displayKey())
        assertEquals("about:version", "about:version".displayKey())
        assertEquals("about:blank", "about:blank".displayKey())
        assertEquals("example.com/index.html", "https://example.com/index.html".displayKey())
    }

    @Test
    fun hostStripsSchemeAndWww() {
        assertEquals("example.com", "https://www.example.com/path".host())
        assertEquals("example.com", "http://example.com/".host())
        assertEquals("local://sample/index.html", "local://sample/index.html".host())
        assertEquals("about:version", "about:version".host())
    }

    @Test
    fun incomingAssetLoaderUrlsAreNotOrdinaryWebPages() {
        assertTrue(
            isIncomingAssetUrl("https://appassets.androidplatform.net/local/tmp-abc/index.html"),
        )
        assertFalse(
            isIncomingAssetUrl("https://appassets.androidplatform.net/local/saved-app/index.html"),
        )
        assertFalse(isIncomingAssetUrl("https://example.com/"))
    }
}
