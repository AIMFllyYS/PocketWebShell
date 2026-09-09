package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRouterTest {
    @Test fun `web schemes are accepted and credentials are blocked`() {
        assertEquals(UrlRoute.WEB, UrlRouter.classify("https://example.com/a").route)
        assertEquals(UrlRoute.BLOCKED, UrlRouter.classify("https://user:pass@example.com").route)
    }

    @Test fun `external and privileged schemes are classified`() {
        assertEquals(UrlRoute.EXTERNAL_INTENT, UrlRouter.classify("tel:+123").route)
        assertEquals(UrlRoute.EXTERNAL_INTENT, UrlRouter.classify("intent://scan/#Intent;scheme=zxing;end").route)
        assertEquals(UrlRoute.BLOCKED, UrlRouter.classify("file:///data/data/app").route)
        assertEquals(UrlRoute.JAVASCRIPT, UrlRouter.classify("javascript:alert(1)").route)
    }

    @Test fun `address bar adds https only for host-like input`() {
        assertEquals("https://example.com", UrlRouter.normalizeAddressBar("example.com").normalized)
        assertTrue(UrlRouter.normalizeAddressBar("file:///tmp/a").normalized.isEmpty())
        assertEquals(UrlRoute.ABOUT_BLANK, UrlRouter.normalizeAddressBar("about:blank").route)
    }
}
