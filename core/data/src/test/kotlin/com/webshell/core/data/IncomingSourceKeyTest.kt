package com.webshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingSourceKeyTest {
    @Test
    fun primaryAliasesFoldToEmulatedZero() {
        val expected = "/storage/emulated/0/Download/a.md"
        assertEquals(expected, IncomingSourceKey.fromFilesystemPath("/sdcard/Download/a.md"))
        assertEquals(expected, IncomingSourceKey.fromFilesystemPath("/storage/self/primary/Download/a.md"))
        assertEquals(expected, IncomingSourceKey.fromFilesystemPath("/mnt/sdcard/Download/a.md"))
        assertEquals(expected, IncomingSourceKey.fromFilesystemPath("file:///sdcard/Download/a.md"))
        assertEquals(expected, IncomingSourceKey.fromFilesystemPath("raw:/storage/emulated/0/Download/a.md"))
        assertEquals(expected, IncomingSourceKey.fromFilesystemPath("/SDCARD/Download/a.md"))
    }

    @Test
    fun relativeCaseIsPreserved() {
        val upper = IncomingSourceKey.fromFilesystemPath("/storage/emulated/0/Download/A.md")
        val lower = IncomingSourceKey.fromFilesystemPath("/storage/emulated/0/Download/a.md")
        assertEquals("/storage/emulated/0/Download/A.md", upper)
        assertNotEquals(upper, lower)
    }

    @Test
    fun slashesAndDotsAreNormalizedOrRejected() {
        assertEquals(
            "/storage/emulated/0/Download/a.md",
            IncomingSourceKey.fromFilesystemPath("/storage/emulated/0//Download/a.md/"),
        )
        assertNull(IncomingSourceKey.fromFilesystemPath("/storage/emulated/0/Download/../secret.md"))
        assertNull(IncomingSourceKey.fromFilesystemPath("notes.md"))
        assertNull(IncomingSourceKey.fromFilesystemPath("document"))
        assertNull(IncomingSourceKey.fromFilesystemPath(""))
    }

    @Test
    fun externalVolumeIsNotFolded() {
        assertEquals(
            "/storage/XXXX-XXXX/Download/a.md",
            IncomingSourceKey.fromFilesystemPath("/storage/XXXX-XXXX/Download/a.md"),
        )
    }

    @Test
    fun contentUriKeySkipsFileProvider() {
        assertEquals(
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fa.md",
            IncomingSourceKey.fromContentUri(
                "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fa.md",
            ),
        )
        assertNull(IncomingSourceKey.fromContentUri("content://com.tencent.mm.external.fileprovider/foo"))
        assertNull(IncomingSourceKey.fromContentUri("notes.md"))
    }

    @Test
    fun addressLabelPrefersRealPathAndDecodesJunk() {
        assertEquals(
            "/storage/emulated/0/Download/notes.md",
            IncomingSourceKey.addressLabel(
                "raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fnotes.md",
                "notes.md",
                "about:blank",
            ),
        )
        assertEquals(
            "notes.md",
            IncomingSourceKey.addressLabel(null, "notes.md", "https://appassets.androidplatform.net/local/tmp-abc/index.html"),
        )
        assertEquals(
            "html:/storage/emulated/0/Download/a.html",
            IncomingSourceKey.reuseToken(html = true, "/storage/emulated/0/Download/a.html"),
        )
        assertEquals(true, IncomingSourceKey.localAppMatchesKind("local://app-1/document.md", html = false))
        assertEquals(false, IncomingSourceKey.localAppMatchesKind("local://app-1/index.html", html = false))
    }
}
