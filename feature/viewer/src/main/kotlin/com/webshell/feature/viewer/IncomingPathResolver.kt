package com.webshell.feature.viewer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.webshell.core.data.IncomingSourceKey
import java.io.File

data class IncomingResolvedPath(
    val displayPath: String,
    val sourceKey: String?,
)

/**
 * Display-only path for incoming chrome. Never used as a WebView URL.
 * Prefer a real filesystem path when MediaStore / ExternalStorage / file:// allow it;
 * [File.isFile] is not required — Android 30+ often cannot stat shared HTML/MD.
 */
object IncomingPathResolver {
    fun resolve(context: Context, uri: Uri, displayName: String?): String =
        resolveInfo(context, uri, displayName).displayPath

    fun resolveInfo(context: Context, uri: Uri, displayName: String?): IncomingResolvedPath {
        val resolved = when (uri.scheme?.lowercase()) {
            "file" -> filePath(uri)
            "content" -> fromMediaStore(context, uri)
                ?: fromDownloadsProvider(context, uri)
                ?: fromExternalStorageDocument(uri)
            else -> null
        }
        if (resolved != null) {
            val key = IncomingSourceKey.fromFilesystemPath(resolved)
            return IncomingResolvedPath(
                displayPath = key ?: resolved,
                sourceKey = key,
            )
        }
        val decodedName = displayName
            ?.let(IncomingSourceKey::decodeRepeated)
            ?.takeIf { it.isNotBlank() }
        IncomingSourceKey.fromFilesystemPath(decodedName)?.let { key ->
            return IncomingResolvedPath(displayPath = key, sourceKey = key)
        }
        val contentKey = IncomingSourceKey.fromContentUri(uri.toString())
        return IncomingResolvedPath(
            displayPath = decodedName ?: "document",
            sourceKey = contentKey,
        )
    }

    private fun filePath(uri: Uri): String? {
        val raw = uri.path ?: return null
        return IncomingSourceKey.fromFilesystemPath(raw)
    }

    private fun fromMediaStore(context: Context, uri: Uri): String? {
        val authority = uri.authority.orEmpty()
        if (!authority.contains("media", ignoreCase = true) &&
            !authority.contains("downloads", ignoreCase = true)
        ) {
            return null
        }
        return queryDataColumn(context, uri)
    }

    private fun fromDownloadsProvider(context: Context, uri: Uri): String? {
        if (uri.authority != "com.android.providers.downloads.documents") return null
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        if (id.startsWith("raw:", ignoreCase = true)) {
            return IncomingSourceKey.fromFilesystemPath(id)
        }
        val contentUri = runCatching {
            ContentResolver.SCHEME_CONTENT + "://downloads/public_downloads/" + id.toLong()
        }.getOrNull()?.let(Uri::parse) ?: return null
        return queryDataColumn(context, contentUri)
    }

    private fun fromExternalStorageDocument(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val split = docId.split(':', limit = 2)
        if (split.size < 2) return null
        val type = split[0]
        val relative = IncomingSourceKey.decodeRepeated(split[1]).replace('\\', '/')
        if (relative.contains("..")) return null
        val root = when {
            type.equals("primary", ignoreCase = true) || type.equals("home", ignoreCase = true) -> {
                @Suppress("DEPRECATION")
                Environment.getExternalStorageDirectory()
            }
            else -> return null
        }
        return IncomingSourceKey.fromFilesystemPath(File(root, relative).absolutePath)
    }

    private fun queryDataColumn(context: Context, uri: Uri): String? = runCatching {
        @Suppress("DEPRECATION")
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            @Suppress("DEPRECATION")
            val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (idx >= 0 && cursor.moveToFirst()) {
                IncomingSourceKey.fromFilesystemPath(cursor.getString(idx))
            } else {
                null
            }
        }
    }.getOrNull()
}
