package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWebHostBoundaryTest {
    @Test fun `local path accepts only an app relative safe path`() {
        assertTrue(LocalWebHost.isSafeLocalPath("app-a", "index.html"))
        assertTrue(LocalWebHost.isSafeLocalPath("app-a", "assets/icon.svg"))
        assertFalse(LocalWebHost.isSafeLocalPath("app-a", "../app-b/index.html"))
        assertFalse(LocalWebHost.isSafeLocalPath("app-a", "assets/../../app-b/index.html"))
        assertFalse(LocalWebHost.isSafeLocalPath("app/a", "index.html"))
    }

    @Test fun `https local resource requires the owning session app id`() {
        val own = "https://${LocalWebHost.HOST}/local/app-a/index.html"
        val foreign = "https://${LocalWebHost.HOST}/local/app-b/index.html"
        assertTrue(LocalWebHost.isAllowedLocalUrl(own, "app-a"))
        assertFalse(LocalWebHost.isAllowedLocalUrl(own, "app-b"))
        assertFalse(LocalWebHost.isAllowedLocalUrl(foreign, "app-a"))
        assertFalse(LocalWebHost.isAllowedLocalUrl(own, null))
        assertFalse(LocalWebHost.isAllowedLocalUrl("https://${LocalWebHost.HOST}/unknown", null))
    }

    @Test fun `encoded traversal and encoded separators cannot cross the boundary`() {
        assertFalse(
            LocalWebHost.isAllowedLocalUrl(
                "https://${LocalWebHost.HOST}/local/app-a/%2e%2e/app-b/index.html",
                "app-a",
            ),
        )
        assertFalse(
            LocalWebHost.isAllowedLocalUrl(
                "https://${LocalWebHost.HOST}/local/app-a/assets%2fsecret.html",
                "app-a",
            ),
        )
    }

    @Test fun `packaged assets are not tied to an imported app`() {
        assertTrue(
            LocalWebHost.isAllowedLocalUrl(
                "https://${LocalWebHost.HOST}/assets/icons/xuanlan.svg",
                null,
            ),
        )
    }

    @Test fun `session capability alone does not authorize a subresource once the document left the local host`() {
        val own = "https://${LocalWebHost.HOST}/local/app-a/index.html"
        val subresource = "https://${LocalWebHost.HOST}/local/app-a/style.css"
        // Main-frame navigation within the same imported app is unaffected by
        // the current document (there may not even be one yet, e.g. the
        // initial load from about:blank).
        assertTrue(LocalWebHost.isAllowedLocalUrl(own, "app-a", isMainFrame = true, documentUrl = null))
        // A subresource fetched while the top document is still the same
        // imported app's own page remains allowed.
        assertTrue(LocalWebHost.isAllowedLocalUrl(subresource, "app-a", isMainFrame = false, documentUrl = own))
        // The session still carries localAppId="app-a" (cookies/profile are
        // shared, on purpose), but the main document has since navigated to a
        // remote page. That remote page's iframe/script must not be able to
        // read this app's local files just by knowing the URL shape.
        assertFalse(
            LocalWebHost.isAllowedLocalUrl(
                subresource, "app-a", isMainFrame = false, documentUrl = "https://example.org/",
            ),
        )
        assertFalse(LocalWebHost.isAllowedLocalUrl(subresource, "app-a", isMainFrame = false, documentUrl = null))
    }

    @Test fun `local URL conversion preserves encoded filenames and query`() {
        val persisted = LocalWebHost.buildLocalAppUrl("app-a", "hello world.html") + "?v=1#top"
        assertEquals(
            "https://${LocalWebHost.HOST}/local/app-a/hello%20world.html?v=1#top",
            LocalWebHost.toHttpsUrl(persisted),
        )
    }
}
