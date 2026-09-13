package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererRecoveryGateTest {
    @Test fun `allows one automatic retry then stops`() {
        val gate = RendererRecoveryGate()
        assertTrue(gate.tryBeginRecovery())
        assertFalse(gate.tryBeginRecovery())
    }

    @Test fun `stable page and explicit navigation reset the budget`() {
        val gate = RendererRecoveryGate()
        assertTrue(gate.tryBeginRecovery())
        gate.markStable()
        assertTrue(gate.tryBeginRecovery())
        assertFalse(gate.tryBeginRecovery())
        gate.resetForExplicitNavigation()
        assertTrue(gate.tryBeginRecovery())
    }

    @Test fun `local app crash does not auto-reload the same document`() {
        val html = "https://appassets.androidplatform.net/local/app-a/index.html"
        assertFalse(RendererRecoveryPolicy.shouldAutoReloadDocument("app-a"))
        assertEquals("about:blank", RendererRecoveryPolicy.currentUrlAfterAutomaticRecovery("app-a", html))
        assertEquals(
            html,
            RendererRecoveryPolicy.retryLoadUrl(
                pendingRecoveryUrl = html,
                currentUrl = "about:blank",
                startUrl = html,
            ),
        )
    }

    @Test fun `temporary incoming local html also does not auto-reload`() {
        assertFalse(RendererRecoveryPolicy.shouldAutoReloadDocument("tmp-incoming"))
        assertEquals(
            "about:blank",
            RendererRecoveryPolicy.currentUrlAfterAutomaticRecovery(
                "tmp-incoming",
                "https://appassets.androidplatform.net/local/tmp-incoming/index.html",
            ),
        )
    }

    @Test fun `remote crash may auto-reload once`() {
        val url = "https://example.org/read"
        assertTrue(RendererRecoveryPolicy.shouldAutoReloadDocument(null))
        assertEquals(url, RendererRecoveryPolicy.currentUrlAfterAutomaticRecovery(null, url))
    }
}
