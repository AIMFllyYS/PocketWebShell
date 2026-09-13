package com.webshell.feature.viewer

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingIntentParserTest {
    @Test
    fun prefersViewDataThenSendStreamThenClip() {
        val view = IncomingIntentParser.parse(
            action = Intent.ACTION_VIEW,
            data = "content://com.tencent.mm.external.fileprovider/external/chat.html",
            type = "application/octet-stream",
            extraStream = "content://ignored/other.md",
        )
        assertEquals(
            "content://com.tencent.mm.external.fileprovider/external/chat.html",
            view?.uriString,
        )
        assertEquals("application/octet-stream", view?.mimeType)

        val send = IncomingIntentParser.parse(
            action = Intent.ACTION_SEND,
            data = null,
            type = "text/markdown",
            extraStream = "content://downloads/note.md",
        )
        assertEquals("content://downloads/note.md", send?.uriString)

        val multiple = IncomingIntentParser.parse(
            action = Intent.ACTION_SEND_MULTIPLE,
            data = null,
            type = "*/*",
            extraStreams = listOf("content://a/one.html", "content://a/two.html"),
        )
        assertEquals("content://a/one.html", multiple?.uriString)
        assertNull(multiple?.mimeType)
    }

    @Test
    fun rejectsHttpAndMainAndUnsupportedSchemes() {
        assertNull(
            IncomingIntentParser.parse(
                action = Intent.ACTION_VIEW,
                data = "https://example.com/page.html",
                type = "text/html",
            ),
        )
        assertNull(
            IncomingIntentParser.parse(
                action = Intent.ACTION_MAIN,
                data = "content://safe/file.html",
                type = "text/html",
            ),
        )
        assertNull(
            IncomingIntentParser.parse(
                action = Intent.ACTION_SEND,
                data = null,
                type = "text/plain",
                extraStream = "javascript:alert(1)",
            ),
        )
        assertTrue(IncomingIntentParser.isSupportedUri("file:///storage/emulated/0/Download/a.md"))
        assertFalse(IncomingIntentParser.isSupportedUri("local://app-a/index.html"))
    }
}
