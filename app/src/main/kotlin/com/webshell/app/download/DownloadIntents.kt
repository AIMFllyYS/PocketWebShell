package com.webshell.app.download

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File

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

    /**
     * DocumentsUI tree browse URI. FLAG_GRANT on these URIs is a no-op: this
     * app is not the ExternalStorageProvider, so a SecurityException is normal
     * and [launch] must continue to DownloadManager / file VIEW.
     */
    fun folderTreeUriString(): String {
        val id = encodeDocumentId(FOLDER_DOCUMENT_ID)
        return "content://$DOCUMENTS_AUTHORITY/tree/$id/document/$id"
    }

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
        add(DownloadIntentSpec(Intent.ACTION_VIEW, folderTreeUriString(), MIME_TYPE_DIR))
        add(DownloadIntentSpec(DownloadManager.ACTION_VIEW_DOWNLOADS, null, null))
        fileUriString?.let { add(DownloadIntentSpec(Intent.ACTION_VIEW, it, "*/*")) }
        fileUriString?.let { add(DownloadIntentSpec(Intent.ACTION_SEND, it, "application/octet-stream")) }
    }

    fun folderDocumentUri(): Uri = Uri.parse(folderDocumentUriString())

    fun folderTreeUri(): Uri = Uri.parse(folderTreeUriString())

    fun folderViewIntent(): Intent = documentsFolderIntent(folderDocumentUri())

    fun folderTreeViewIntent(): Intent = documentsFolderIntent(folderTreeUri())

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
        add(folderTreeViewIntent())
        add(systemDownloadsIntent())
        (documentUri ?: fileUri)?.let { add(fileViewIntent(it)) }
        (documentUri ?: fileUri)?.let { add(shareIntent(it, shareTitle)) }
    }

    fun folderOpenIntents(): List<Intent> = listOf(
        folderViewIntent(),
        folderTreeViewIntent(),
        systemDownloadsIntent(),
    )

    fun launch(context: Context, intent: Intent): Boolean = start(context, intent)

    fun launchAll(context: Context, intents: List<Intent>): Boolean = intents.any { start(context, it) }

    /**
     * Try every candidate. Do not gate on resolveActivity: a Documents UI match
     * plus SecurityException used to abort the chain before ACTION_VIEW_DOWNLOADS.
     */
    fun launch(context: Context, documentUri: Uri?, fileUri: Uri?, shareTitle: String): Boolean {
        val shareableFile = fileUri?.let { shareableUri(context, it) }
        val shareableDocument = documentUri?.let { shareableUri(context, it) }
        val viewUri = shareableFile ?: shareableDocument
        return candidates(shareableDocument, viewUri, shareTitle).any { start(context, it) }
    }

    fun shareableUri(context: Context, uri: Uri): Uri {
        if (!uri.scheme.equals("file", ignoreCase = true)) return uri
        val path = uri.path ?: return uri
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path),
            )
        }.getOrDefault(uri)
    }

    private fun documentsFolderIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, MIME_TYPE_DIR)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
}
