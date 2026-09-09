package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {
    @Test fun `same-origin referer is kept and filename is sanitized`() {
        val headers = DownloadPolicy.requestHeaders(
            "https://example.com/file", "ua", "https://example.com/page", "sid=redacted",
        )
        assertEquals("https://example.com/page", headers["Referer"])
        assertEquals("sid=redacted", headers["Cookie"])
        assertEquals("a__b.txt", DownloadPolicy.safeFileName("a:/b.txt"))
    }

    @Test fun `cross-origin referer is dropped`() {
        val headers = DownloadPolicy.requestHeaders(
            "https://cdn.example.com/file", "ua", "https://account.example.com/page", null,
        )
        assertFalse(headers.containsKey("Referer"))
        assertTrue(headers["User-Agent"] == "ua")
    }
}
