package com.webshell.core.webengine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewPoolProtectionTest {
    @Test fun `protection reasons compose and can be removed independently`() {
        val id = "test-protection"
        WebViewPool.unprotect(id, WebViewPool.ProtectionReason.ACTIVE)
        WebViewPool.protect(id, WebViewPool.ProtectionReason.KEEP_ALIVE)
        WebViewPool.protect(id, WebViewPool.ProtectionReason.FULLSCREEN)
        assertTrue(WebViewPool.isProtected(id))
        assertTrue(WebViewPool.protectionReasons(id).contains(WebViewPool.ProtectionReason.KEEP_ALIVE))
        WebViewPool.unprotect(id, WebViewPool.ProtectionReason.FULLSCREEN)
        assertTrue(WebViewPool.isProtected(id))
        WebViewPool.unprotect(id, WebViewPool.ProtectionReason.KEEP_ALIVE)
        assertFalse(WebViewPool.isProtected(id))
    }
}
