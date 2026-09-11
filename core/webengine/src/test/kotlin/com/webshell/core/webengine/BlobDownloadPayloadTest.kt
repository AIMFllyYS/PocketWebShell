package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlobDownloadPayloadTest {
    @Test fun `parses the two-field OK wire format`() {
        val parsed = parseBlobEvaluation("\"OK:text/plain:YWJj\"")
        assertTrue(parsed is BlobDownloadParseResult.Success)
        val payload = (parsed as BlobDownloadParseResult.Success).payload
        assertEquals("text/plain", payload.mimeType)
        assertEquals("YWJj", payload.encoded)
    }

    @Test fun `preserves base64 and decodes escaped callback envelope`() {
        val parsed = parseBlobEvaluation("\"OK:application/json:eyJxIjoicmF3XCIifQ==\"")
        assertTrue(parsed is BlobDownloadParseResult.Success)
        assertEquals(
            "eyJxIjoicmF3XCIifQ==",
            (parsed as BlobDownloadParseResult.Success).payload.encoded,
        )
    }

    @Test fun `reports malformed or error results`() {
        assertEquals(
            BlobDownloadParseResult.Failure("blob-format"),
            parseBlobEvaluation("OK:text/plain"),
        )
        assertEquals(
            BlobDownloadParseResult.Failure("too-large"),
            parseBlobEvaluation("\"ERR:too-large\""),
        )
    }

    @Test fun `parses chunked blob control frames`() {
        assertEquals(12L to "application/zip", parseBlobMeta("\"META:12:application/zip\""))
        assertEquals("YWJj", parseBlobChunk("\"CHUNK:YWJj\""))
        assertEquals(null, parseBlobChunk("\"ERR:gone\""))
    }

    @Test fun `empty mime falls back to binary`() {
        val parsed = parseBlobEvaluation("OK::AA==") as BlobDownloadParseResult.Success
        assertEquals("application/octet-stream", parsed.payload.mimeType)
    }
}
