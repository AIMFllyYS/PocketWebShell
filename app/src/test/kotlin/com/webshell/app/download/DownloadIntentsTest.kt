package com.webshell.app.download

import android.app.DownloadManager
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FPocketWebShell/document/primary%3ADownload%2FPocketWebShell",
            DownloadIntents.folderTreeUriString(),
        )
        assertEquals("vnd.android.document/directory", DownloadIntents.MIME_TYPE_DIR)
    }

    @Test
    fun candidateOrderIsFolderThenTreeThenDownloadsThenFileThenShare() {
        val file = "content://media/external/downloads/42"
        val specs = DownloadIntents.specs(file)
        assertEquals(5, specs.size)
        assertEquals(Intent.ACTION_VIEW, specs[0].action)
        assertEquals(DownloadIntents.folderDocumentUriString(), specs[0].data)
        assertEquals(DownloadIntents.MIME_TYPE_DIR, specs[0].mimeType)
        assertEquals(Intent.ACTION_VIEW, specs[1].action)
        assertEquals(DownloadIntents.folderTreeUriString(), specs[1].data)
        assertEquals(DownloadIntents.MIME_TYPE_DIR, specs[1].mimeType)
        assertEquals(DownloadManager.ACTION_VIEW_DOWNLOADS, specs[2].action)
        assertNull(specs[2].data)
        assertEquals(Intent.ACTION_VIEW, specs[3].action)
        assertEquals(file, specs[3].data)
        assertEquals(Intent.ACTION_SEND, specs[4].action)
        assertEquals(file, specs[4].data)
        assertEquals("application/octet-stream", specs[4].mimeType)
    }

    @Test
    fun folderOpenChainWithoutFileEndsAtSystemDownloads() {
        val specs = DownloadIntents.specs(null)
        assertEquals(3, specs.size)
        assertEquals(DownloadIntents.folderDocumentUriString(), specs[0].data)
        assertEquals(DownloadIntents.folderTreeUriString(), specs[1].data)
        assertEquals(DownloadManager.ACTION_VIEW_DOWNLOADS, specs[2].action)
    }
}

class DownloadStorageAccessTest {
    @Test
    fun legacyReadOnlyOnApi29To32() {
        assertEquals(android.Manifest.permission.READ_EXTERNAL_STORAGE, DownloadStorageAccess.runtimeReadPermission(29))
        assertEquals(android.Manifest.permission.READ_EXTERNAL_STORAGE, DownloadStorageAccess.runtimeReadPermission(32))
        assertNull(DownloadStorageAccess.runtimeReadPermission(33))
        assertNull(DownloadStorageAccess.runtimeReadPermission(36))
    }

    @Test
    fun folderAndFileNeedLegacyReadShareDoesNot() {
        assertTrue(DownloadStorageAccess.needsLegacyRead(null))
        assertTrue(DownloadStorageAccess.needsLegacyRead(DownloadOpenTarget.Folder))
        assertTrue(DownloadStorageAccess.needsLegacyRead(DownloadOpenTarget.File))
        assertFalse(DownloadStorageAccess.needsLegacyRead(DownloadOpenTarget.SystemDownloads))
        assertFalse(DownloadStorageAccess.needsLegacyRead(DownloadOpenTarget.Share))
    }
}
