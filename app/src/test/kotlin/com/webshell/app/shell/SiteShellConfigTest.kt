package com.webshell.app.shell

import com.webshell.core.data.WebAppEntity
import com.webshell.core.webengine.ShellConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteShellConfigTest {
    private fun site(url: String = "https://example.org/read") = WebAppEntity(
        id = "saved-app", title = "Example", url = url, iconUrl = null,
        desktopMode = true, darkMode = true, keepAlive = true, isFavorite = false,
        homePage = 1, homeCellIndex = 3, folderId = null, createdAt = 0,
        textZoomPercent = 125,
    )

    @Test fun savedAppUsesSharedProfileAndStoredWebZoom() {
        val config = requireNotNull(configuredSiteShell(site()))
        assertEquals("saved-app", config.sessionId)
        assertNull(config.profileId)
        assertEquals(125, config.textZoomPercent)
        assertTrue(config.desktopMode)
        assertTrue(config.forceEnableZoom)
        assertTrue(config.algorithmicDark)
        assertFalse(config.pullToRefresh)
        assertEquals(ShellConfig.ExternalLinkPolicy.OPEN_IN_SAME, config.externalLinkPolicy)
        assertTrue(requireNotNull(configuredSiteShell(site(), pullToRefresh = true)).pullToRefresh)
    }

    @Test fun onlyTheExplicitExternalLinkSwitchChangesPolicy() {
        assertEquals(ShellConfig.ExternalLinkPolicy.OPEN_IN_BROWSER,
            configuredSiteShell(site().copy(externalLinksToBrowser = true))?.externalLinkPolicy)
        // The home-screen star is presentation only; it must not silently
        // route a favorited site's off-site links (e.g. OAuth) to the system
        // browser and break the shared, single-user login session.
        assertEquals(ShellConfig.ExternalLinkPolicy.OPEN_IN_SAME,
            configuredSiteShell(site().copy(isFavorite = true))?.externalLinkPolicy)
    }

    @Test fun importedLocalSiteMapsOnlyItsOwnSavedDirectory() {
        val app = site("local://saved-app/index.html").copy(isLocal = true)
        val config = requireNotNull(configuredSiteShell(app))
        assertEquals("https://appassets.androidplatform.net/local/saved-app/index.html", config.startUrl)
        assertEquals("saved-app", config.localAppId)
        assertEquals(125, config.textZoomPercent)
        assertFalse(config.forceEnableZoom)
        assertTrue(requireNotNull(configuredSiteShell(app, forceEnableZoomUser = true)).forceEnableZoom)
        assertNull(configuredSiteShell(app.copy(url = "local://other-app/index.html")))
        assertNull(configuredSiteShell(app.copy(url = "local://saved-app/%2e%2e/other/index.html")))
    }

    @Test fun directLaunchAcceptsWebUrlsButNotPrivilegedSchemesOrCredentials() {
        assertEquals("https://example.org/read?q=value", validatedExternalSiteUrl("https://example.org/read?q=value"))
        assertNotNull(validatedExternalSiteUrl("http://localhost:8080/test"))
        assertNotNull(validatedExternalSiteUrl("https://appassets.androidplatform.net/assets/local_demo.html"))
        listOf("javascript:alert(1)", "file:///data/private", "content://private", "intent://open",
            "local://saved-app/index.html", "https://user:pass@example.org", "https://example.org:99999",
            "https://appassets.androidplatform.net/local/saved-app/index.html", "https://example.org/with space",
        ).forEach { assertNull(it, validatedExternalSiteUrl(it)) }
    }

    @Test fun newWindowPolicyFollowsPerAppThenGlobal() {
        val adopt = requireNotNull(configuredSiteShell(site()))
        assertEquals(ShellConfig.NewWindowPolicy.ADOPT_IN_BROWSER, adopt.newWindowPolicy)
        val replaced = requireNotNull(
            configuredSiteShell(
                site().copy(siteShellNewWindowPolicy = com.webshell.core.data.SITE_SHELL_NEW_WINDOW_REPLACE),
            ),
        )
        assertEquals(ShellConfig.NewWindowPolicy.REPLACE_IN_SHELL, replaced.newWindowPolicy)
        val globalReplace = requireNotNull(
            configuredSiteShell(
                site(),
                globalNewWindowPolicy = com.webshell.core.data.SITE_SHELL_NEW_WINDOW_REPLACE,
            ),
        )
        assertEquals(ShellConfig.NewWindowPolicy.REPLACE_IN_SHELL, globalReplace.newWindowPolicy)
        val perAppAdopt = requireNotNull(
            configuredSiteShell(
                site().copy(siteShellNewWindowPolicy = com.webshell.core.data.SITE_SHELL_NEW_WINDOW_ADOPT),
                globalNewWindowPolicy = com.webshell.core.data.SITE_SHELL_NEW_WINDOW_REPLACE,
            ),
        )
        assertEquals(ShellConfig.NewWindowPolicy.ADOPT_IN_BROWSER, perAppAdopt.newWindowPolicy)
    }

    @Test fun defaultWebsiteZoomRemainsUnscaled() {
        val config = requireNotNull(configuredSiteShell(site().copy(textZoomPercent = 100, desktopMode = false)))
        assertEquals(100, config.textZoomPercent)
        assertFalse(config.desktopMode)
        assertFalse(config.forceEnableZoom)
        assertTrue(
            requireNotNull(configuredSiteShell(site().copy(desktopMode = false), forceEnableZoomUser = true))
                .forceEnableZoom,
        )
    }
}
