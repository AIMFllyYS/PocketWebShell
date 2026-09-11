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

    @Test fun `public relative path stays under Download PocketWebShell`() {
        assertEquals("Download/PocketWebShell/", DownloadPolicy.PUBLIC_RELATIVE_DIR)
        assertEquals("Download/PocketWebShell/a__b.txt", DownloadPolicy.publicRelativePath("a:/b.txt"))
        assertEquals("PocketWebShell/report.pdf", DownloadPolicy.publicSubPath("report.pdf"))
        val escaped = DownloadPolicy.publicRelativePath("../secret")
        assertTrue(escaped.startsWith("Download/PocketWebShell/"))
        assertFalse(escaped.contains("/../") || escaped.contains("\\"))
    }

    @Test fun `data and non-http schemes are rejected`() {
        assertFalse(DownloadPolicy.canEnqueue("data:text/plain;base64,SGVsbG8="))
        assertFalse(DownloadPolicy.canEnqueue("DATA:application/octet-stream,abc"))
        assertFalse(DownloadPolicy.canEnqueue("ftp://files.example.com/a.bin"))
        assertFalse(DownloadPolicy.canEnqueue("javascript:alert(1)"))
        assertFalse(DownloadPolicy.canEnqueue("https://example.com/form", method = "POST"))
        assertTrue(DownloadPolicy.canEnqueue("https://example.com/a.pdf"))
        assertTrue(DownloadPolicy.canEnqueue("http://example.com/a.pdf", method = "GET"))
        assertEquals("cdn.example.com", DownloadPolicy.logHost("https://cdn.example.com/file?token=secret"))
    }

    @Test fun `file extensions on http urls are treated as downloads`() {
        assertTrue(DownloadPolicy.looksLikeFileDownload("https://cdn.example.com/report.xlsx"))
        assertTrue(DownloadPolicy.looksLikeFileDownload("https://files.example.com/a.apk?sig=1"))
        assertFalse(DownloadPolicy.looksLikeFileDownload("https://example.com/path/index.html"))
        assertFalse(DownloadPolicy.looksLikeFileDownload("https://example.com/photos/img.png"))
        assertFalse(DownloadPolicy.looksLikeFileDownload("data:application/zip,xxxx"))
    }
}
