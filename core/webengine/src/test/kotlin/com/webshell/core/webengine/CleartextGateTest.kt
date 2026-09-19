package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextGateTest {
    @Test fun `only the http scheme is treated as cleartext`() {
        assertTrue(CleartextGate.isCleartextHttp("http://hust.m.humanyun.com/"))
        assertTrue(CleartextGate.isCleartextHttp("HTTP://example.com:8080/a"))
        assertFalse(CleartextGate.isCleartextHttp("https://hust.humanyun.com/"))
        assertFalse(CleartextGate.isCleartextHttp("https://example.com/http://looks-like"))
        assertFalse(CleartextGate.isCleartextHttp("about:blank"))
        assertFalse(CleartextGate.isCleartextHttp(""))
    }

    @Test fun `host is taken from the URI and lowercased`() {
        assertEquals("hust.m.humanyun.com", CleartextGate.hostOf("http://hust.m.humanyun.com/login"))
        assertEquals("example.com", CleartextGate.hostOf("http://EXAMPLE.com:8080/a"))
        assertEquals(null, CleartextGate.hostOf("not a url"))
    }

    @Test fun `consent is per session and per host`() {
        val session = "browser-cleartext-a"
        val other = "browser-cleartext-b"
        CleartextGate.forgetSession(session)
        CleartextGate.forgetSession(other)
        val url = "http://hust.m.humanyun.com/"
        assertTrue(CleartextGate.requiresPrompt(session, url))
        assertFalse(CleartextGate.requiresPrompt(session, "https://hust.humanyun.com/"))
        CleartextGate.allow(session, "hust.m.humanyun.com")
        assertFalse(CleartextGate.requiresPrompt(session, url))
        assertFalse(CleartextGate.requiresPrompt(session, "http://hust.m.humanyun.com/frontend/login/login"))
        assertTrue(CleartextGate.requiresPrompt(session, "http://other.example/"))
        assertTrue(CleartextGate.requiresPrompt(other, url))
        CleartextGate.forgetSession(session)
        assertTrue(CleartextGate.requiresPrompt(session, url))
        CleartextGate.forgetSession(other)
    }
}
