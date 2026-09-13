package com.webshell.app.shell

import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.WebEngineDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SiteShellRecoveryTest {
    private fun ready() = SiteShellState.Ready(
        config = ShellConfig(
            sessionId = "saved-app",
            startUrl = "https://appassets.androidplatform.net/local/saved-app/index.html",
            localAppId = "saved-app",
        ),
        request = "local://saved-app/index.html" to "saved-app",
        pageUrl = "https://appassets.androidplatform.net/local/saved-app/index.html",
        loading = true,
    )

    @Test
    fun `renderer gone code surfaces a retryable failed state`() {
        val failed = ready().applyPageError(WebEngineDefaults.ERROR_RENDERER_GONE)
        assertEquals(false, failed.loading)
        assertEquals(SiteShellLoadError.RENDERER_GONE, failed.loadError)
        assertEquals(SiteShellLoadError.RENDERER_GONE, failed.applyRendererRecoveryFailed().loadError)
        assertNull(failed.applyRendererRecovered().loadError)
    }

    @Test
    fun `about blank after a crash keeps the visible failed state`() {
        val failed = ready().applyPageError(WebEngineDefaults.ERROR_RENDERER_GONE)
        val blank = failed.applyPageStarted("about:blank")
        assertEquals(SiteShellLoadError.RENDERER_GONE, blank.loadError)
        assertNotNull(blank.loadError)
        val retried = blank.applyPageStarted(
            "https://appassets.androidplatform.net/local/saved-app/index.html",
        )
        assertNull(retried.loadError)
        assertEquals(true, retried.loading)
    }

    @Test
    fun `first paint does not close the loading state`() {
        val painted = ready().applyFirstPaint("https://example.com/")
        assertEquals(true, painted.loading)
        assertEquals(90, painted.progress)
        val blank = ready().applyFirstPaint("about:blank")
        assertEquals(true, blank.loading)
        assertEquals(0, blank.progress)
    }

    @Test
    fun `ordinary page errors do not become renderer-gone`() {
        val network = ready().applyPageError(-2)
        assertEquals(false, network.loading)
        assertNull(network.loadError)
    }
}
