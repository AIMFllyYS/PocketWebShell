package com.webshell.app.download

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** One step in the Files / Downloads / share fallback chain. */
data class DownloadIntentSpec(
    val action: String,
    val data: String?,
    val mimeType: String?,
)

/** Builds the Files / Downloads / share fallback chain. Does not start an Activity by itself. */
object DownloadIntents {
    const val DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"
    const val FOLDER_DOCUMENT_ID = "primary:Download/PocketWebShell"
    const val MIME_TYPE_DIR = DocumentsContract.Document.MIME_TYPE_DIR

    fun folderDocumentUriString(): String =
        "content://$DOCUMENTS_AUTHORITY/document/${encodeDocumentId(FOLDER_DOCUMENT_ID)}"

    fun encodeDocumentId(documentId: String): String =
        buildString(documentId.length + 8) {
            documentId.forEach { ch ->
                when (ch) {
                    ':' -> append("%3A")
                    '/' -> append("%2F")
                    else -> append(ch)
                }
            }
        }

    fun specs(fileUriString: String?): List<DownloadIntentSpec> = buildList {
        add(DownloadIntentSpec(Intent.ACTION_VIEW, folderDocumentUriString(), MIME_TYPE_DIR))
        add(DownloadIntentSpec(DownloadManager.ACTION_VIEW_DOWNLOADS, null, null))
        fileUriString?.let { add(DownloadIntentSpec(Intent.ACTION_VIEW, it, "*/*")) }
        fileUriString?.let { add(DownloadIntentSpec(Intent.ACTION_SEND, it, "application/octet-stream")) }
    }

    fun folderDocumentUri(): Uri = Uri.parse(folderDocumentUriString())

    fun folderViewIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(folderDocumentUri(), MIME_TYPE_DIR)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    fun systemDownloadsIntent(): Intent =
        Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun fileViewIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "*/*")
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
    }

    fun shareIntent(uri: Uri, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun candidates(documentUri: Uri?, fileUri: Uri?, shareTitle: String = ""): List<Intent> = buildList {
        add(folderViewIntent())
        add(systemDownloadsIntent())
        (documentUri ?: fileUri)?.let { add(fileViewIntent(it)) }
        (documentUri ?: fileUri)?.let { add(shareIntent(it, shareTitle)) }
    }

    fun launch(context: Context, documentUri: Uri?, fileUri: Uri?, shareTitle: String): Boolean {
        val pm = context.packageManager
        val folder = folderViewIntent()
        if (folder.resolveActivity(pm) != null) return start(context, folder)
        val downloads = systemDownloadsIntent()
        if (downloads.resolveActivity(pm) != null) return start(context, downloads)
        val file = (documentUri ?: fileUri)?.let { fileViewIntent(it) }
        if (file != null && file.resolveActivity(pm) != null) return start(context, file)
        val shareUri = documentUri ?: fileUri ?: return false
        val probe = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (probe.resolveActivity(pm) == null) return false
        return start(context, shareIntent(shareUri, shareTitle))
    }

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
}
