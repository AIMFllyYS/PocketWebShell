package com.webshell.feature.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingFilePolicyTest {
    @Test
    fun resolvesHtmlAndMarkdownByExtension() {
        assertEquals(ViewerDocumentKind.HTML, IncomingFilePolicy.resolveKind("note.HTML", null))
        assertEquals(ViewerDocumentKind.HTML, IncomingFilePolicy.resolveKind("page.htm", "application/octet-stream"))
        assertEquals(ViewerDocumentKind.MARKDOWN, IncomingFilePolicy.resolveKind("readme.md", "text/plain"))
        assertEquals(ViewerDocumentKind.MARKDOWN, IncomingFilePolicy.resolveKind("SPEC.markdown", null))
    }

    @Test
    fun resolvesByMimeWhenNameHasNoExtension() {
        assertEquals(ViewerDocumentKind.HTML, IncomingFilePolicy.resolveKind("WeixinFile", "text/html; charset=utf-8"))
        assertEquals(ViewerDocumentKind.MARKDOWN, IncomingFilePolicy.resolveKind("blob", "text/markdown"))
        assertNull(IncomingFilePolicy.resolveKind("blob", "application/octet-stream"))
        assertNull(IncomingFilePolicy.resolveKind("notes.txt", "text/plain"))
    }

    @Test
    fun octetStreamWithoutNameSniffsHtmlOnly() {
        val html = "<!DOCTYPE html><html>".toByteArray()
        val md = "# Title\n\nHello".toByteArray()
        assertEquals(ViewerDocumentKind.HTML, IncomingFilePolicy.resolveKind(null, "application/octet-stream", html))
        assertNull(IncomingFilePolicy.resolveKind(null, "application/octet-stream", md))
    }

    @Test
    fun rejectsDeniedExtensionsAndBinaries() {
        assertNull(IncomingFilePolicy.resolveKind("setup.apk", "text/html"))
        assertNull(IncomingFilePolicy.resolveKind("payload.html", null, byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertNull(IncomingFilePolicy.resolveKind("tool.exe", "application/octet-stream"))
        assertTrue(IncomingFilePolicy.looksLikeDeniedBinary(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte())))
        assertFalse(IncomingFilePolicy.looksLikeHtml("not html".toByteArray()))
    }

    @Test
    fun sanitizesNamesAndKeepsSessionPrefix() {
        assertEquals("x.html", IncomingFilePolicy.sanitizeFileName("../x.html", ViewerDocumentKind.HTML))
        assertEquals("safe_name.md", IncomingFilePolicy.sanitizeFileName("safe:name.md", ViewerDocumentKind.MARKDOWN))
        assertEquals("index.html", IncomingFilePolicy.sanitizeFileName("evil.apk", ViewerDocumentKind.HTML))
        val session = IncomingFilePolicy.newSessionId()
        assertTrue(IncomingFilePolicy.isTemporarySessionId(session))
        assertFalse(IncomingFilePolicy.isTemporarySessionId("app-a"))
        assertFalse(IncomingFilePolicy.isTemporarySessionId("tmp-../x"))
    }

    @Test
    fun originalTitleUsesRealFilenameAndSkipsProviderJunk() {
        assertEquals("notes", IncomingFilePolicy.originalDocumentTitle("notes.md", null))
        assertEquals(
            "readme",
            IncomingFilePolicy.originalDocumentTitle(null, "/storage/emulated/0/Download/readme.md"),
        )
        assertEquals("报告", IncomingFilePolicy.originalDocumentTitle("报告.markdown", null))
        assertEquals("", IncomingFilePolicy.originalDocumentTitle("WeixinFile", "document"))
        assertEquals(
            "",
            IncomingFilePolicy.originalDocumentTitle(
                null,
                "content://com.tencent.mm.external.fileprovider/foo",
            ),
        )
        assertEquals("", IncomingFilePolicy.originalDocumentTitle("blob", null))
        assertEquals(
            "",
            IncomingFilePolicy.originalDocumentTitle("a1b2c3d4-e5f6-7890-abcd-ef1234567890.md", null),
        )
    }
}
