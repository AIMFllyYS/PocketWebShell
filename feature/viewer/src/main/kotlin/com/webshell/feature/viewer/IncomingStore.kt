package com.webshell.feature.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.webshell.core.webengine.LocalWebHost
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import javax.inject.Inject

class IncomingTooLargeException : IOException("too large")

class IncomingUnsupportedException : IOException("unsupported")

/** Bounded copy of a one-shot content/file URI into filesDir/localapps/tmp-… */
class IncomingDocuments @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val filesDir: File get() = context.filesDir
    internal val appContext: Context get() = context

    fun tryPersistRead(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun resolveDisplayPath(uri: Uri, displayName: String?): String =
        IncomingPathResolver.resolve(context, uri, displayName)

    fun resolvePathInfo(uri: Uri, displayName: String?): IncomingResolvedPath =
        IncomingPathResolver.resolveInfo(context, uri, displayName)

    fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.let(com.webshell.core.data.IncomingSourceKey::decodeRepeated)

    fun openStream(uri: Uri): InputStream? = when (uri.scheme?.lowercase()) {
        "content" -> context.contentResolver.openInputStream(uri)
        "file" -> {
            val path = uri.path ?: return null
            val file = File(path).canonicalFile
            file.takeIf { it.isFile }?.inputStream()
        }
        else -> null
    }
}

object IncomingStore {
    const val ORPHAN_MIN_AGE_MS: Long = 5L * 60L * 1000L

    fun sessionDir(filesDir: File, sessionId: String): File {
        require(IncomingFilePolicy.isTemporarySessionId(sessionId)) { "invalid session" }
        return File(File(filesDir, LocalWebHost.LOCAL_APPS_DIR), sessionId)
    }

    fun sweepOrphans(
        filesDir: File,
        keep: Set<String> = emptySet(),
        minAgeMs: Long = ORPHAN_MIN_AGE_MS,
        now: Long = System.currentTimeMillis(),
    ) {
        val root = File(filesDir, LocalWebHost.LOCAL_APPS_DIR)
        val dirs = root.listFiles() ?: return
        dirs.forEach { child ->
            if (!child.isDirectory) return@forEach
            if (!IncomingFilePolicy.isTemporarySessionId(child.name)) return@forEach
            if (child.name in keep) return@forEach
            if (now - child.lastModified() < minAgeMs) return@forEach
            child.deleteRecursively()
        }
    }

    fun deleteSession(filesDir: File, sessionId: String) {
        if (!IncomingFilePolicy.isTemporarySessionId(sessionId)) return
        runCatching { sessionDir(filesDir, sessionId).deleteRecursively() }
    }

    fun copyBounded(input: InputStream, dest: File, maxBytes: Long = IncomingFilePolicy.MAX_BYTES): Long {
        val parent = dest.parentFile?.canonicalFile ?: throw IOException("import directory unavailable")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("import directory unavailable")
        val target = dest.canonicalFile
        if (target.parentFile?.canonicalFile != parent) throw IOException("path escape")
        if (target.exists() && !target.delete()) throw IOException("cannot replace")
        var copied = 0L
        var overflow = false
        input.use { source ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > maxBytes) {
                        overflow = true
                        break
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
        if (overflow || copied <= 0L) {
            target.delete()
            if (overflow) throw IncomingTooLargeException()
            throw IOException("empty")
        }
        return copied
    }

    fun readTextBounded(input: InputStream, maxBytes: Long = IncomingFilePolicy.MAX_BYTES): String {
        val bytes = input.use { source ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                copied += count
                if (copied > maxBytes) throw IncomingTooLargeException()
                out.write(buffer, 0, count)
            }
            if (copied <= 0L) throw IOException("empty")
            out.toByteArray()
        }
        return decodeText(bytes)
    }

    fun prependedStream(header: ByteArray, rest: InputStream): InputStream =
        if (header.isEmpty()) rest else SequenceInputStream(ByteArrayInputStream(header), rest)

    fun decodeText(bytes: ByteArray): String {
        val text = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, Charsets.UTF_16BE)
            else -> String(bytes, Charsets.UTF_8)
        }
        return text.trimStart('\uFEFF')
    }
}

object IncomingMarkdownPolicy {
    private val imageDestination = Regex("""!\[([^]]*)]\(\s*([^)]+)\)""")

    /** Neutralize image destinations so the Compose renderer cannot fetch remote URLs. */
    fun forDisplay(raw: String): String = imageDestination.replace(raw) { match ->
        "![${match.groupValues[1]}]()"
    }
}
