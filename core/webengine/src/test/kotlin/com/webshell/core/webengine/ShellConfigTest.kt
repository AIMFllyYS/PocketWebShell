package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShellConfigTest {
    @Test fun `merge preserves stable session and profile identity`() {
        val base = ShellConfig(sessionId = "s", profileId = null, startUrl = "https://a.example")
        val updated = base.mergedWith(ShellConfig(sessionId = "other", profileId = "private", desktopMode = true))
        assertEquals("s", updated.sessionId)
        assertEquals(null, updated.profileId)
        assertEquals("https://a.example", updated.startUrl)
        assertEquals(true, updated.desktopMode)
        assertEquals(false, base.pullToRefresh)
    }

    @Test fun `merge keeps local resource capability`() {
        val base = ShellConfig(sessionId = "app-a", localAppId = "app-a")
        val updated = base.mergedWith(ShellConfig(localAppId = "app-b", textZoomPercent = 110))
        assertEquals("app-a", updated.localAppId)
        assertEquals(110, updated.textZoomPercent)
    }

    /**
     * ShellWebViewHost's SideEffect calls `shell.reconfigure(configFactory())`
     * on every recomposition, including ones driven purely by a
     * progress/title callback. reconfigure() only does real work
     * (WebSettings/CookieManager churn, a possible reload) when
     * `old.mergedWith(newConfig) != old`; this pins that the merge result of
     * an unrelated re-submission of the *same* settings is bit-for-bit equal
     * to the original, so the short-circuit actually fires.
     */
    @Test fun `re-submitting identical settings merges to an unchanged config`() {
        val base = ShellConfig(
            sessionId = "s", profileId = null, startUrl = "https://a.example",
            desktopMode = true, algorithmicDark = true, textZoomPercent = 125,
            pullToRefresh = true, forceEnableZoom = true, autoplayMedia = false, thirdPartyCookies = false,
        )
        val resubmitted = base.mergedWith(base.copy())
        assertEquals(base, resubmitted)
    }

    @Test fun `an actual settings change still produces a different merged config`() {
        val base = ShellConfig(sessionId = "s", pullToRefresh = false)
        val updated = base.mergedWith(base.copy(pullToRefresh = true))
        assertNotEquals(base, updated)
        assertEquals(true, updated.pullToRefresh)
    }

    @Test fun `newWindowPolicy participates in merge equality`() {
        val base = ShellConfig(sessionId = "s")
        val updated = base.mergedWith(base.copy(newWindowPolicy = ShellConfig.NewWindowPolicy.REPLACE_IN_SHELL))
        assertEquals(ShellConfig.NewWindowPolicy.REPLACE_IN_SHELL, updated.newWindowPolicy)
        assertEquals(base, base.mergedWith(base.copy()))
    }

    @Test fun `forceEnableZoom participates in merge equality`() {
        val base = ShellConfig(sessionId = "s", forceEnableZoom = false)
        val updated = base.mergedWith(base.copy(forceEnableZoom = true))
        assertNotEquals(base, updated)
        assertEquals(true, updated.forceEnableZoom)
        assertEquals(base, base.mergedWith(base.copy()))
    }
}
