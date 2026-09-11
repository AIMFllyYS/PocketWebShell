package com.webshell.app.download

import android.app.DownloadManager
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadIntentsTest {
    @Test
    fun folderSpecTargetsPocketWebShellDirectory() {
        assertEquals("com.android.externalstorage.documents", DownloadIntents.DOCUMENTS_AUTHORITY)
        assertEquals("primary:Download/PocketWebShell", DownloadIntents.FOLDER_DOCUMENT_ID)
        assertEquals("primary%3ADownload%2FPocketWebShell", DownloadIntents.encodeDocumentId(DownloadIntents.FOLDER_DOCUMENT_ID))
        assertEquals(
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2FPocketWebShell",
            DownloadIntents.folderDocumentUriString(),
        )
        assertEquals("vnd.android.document/directory", DownloadIntents.MIME_TYPE_DIR)
    }

    @Test
    fun candidateOrderIsFolderThenDownloadsThenFileThenShare() {
        val file = "content://media/external/downloads/42"
        val specs = DownloadIntents.specs(file)
        assertEquals(4, specs.size)
        assertEquals(Intent.ACTION_VIEW, specs[0].action)
        assertEquals(DownloadIntents.folderDocumentUriString(), specs[0].data)
        assertEquals(DownloadIntents.MIME_TYPE_DIR, specs[0].mimeType)
        assertEquals(DownloadManager.ACTION_VIEW_DOWNLOADS, specs[1].action)
        assertNull(specs[1].data)
        assertEquals(Intent.ACTION_VIEW, specs[2].action)
        assertEquals(file, specs[2].data)
        assertEquals(Intent.ACTION_SEND, specs[3].action)
        assertEquals(file, specs[3].data)
        assertEquals("application/octet-stream", specs[3].mimeType)
    }
}
