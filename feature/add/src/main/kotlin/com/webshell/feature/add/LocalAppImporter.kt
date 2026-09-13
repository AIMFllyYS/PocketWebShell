package com.webshell.feature.add

import android.content.Context
import android.net.Uri
import com.webshell.core.data.metadata.LocalHtmlIcon
import com.webshell.core.webengine.LocalWebHost
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/**
 * 本地 HTML 导入：把用户选中的文件扁平拷贝到
 * filesDir/localapps/<appId>/，首个（或唯一）html 文件视为入口 index。
 * 持久化 URL 形如 local://<appId>/index.html（见 [LocalWebHost.buildLocalAppUrl]）。
 */
class LocalAppImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class ImportedApp(val entryUrl: String, val iconPath: String?)

    /**
     * @param uris 文档选择器返回的 content:// URI（按选择顺序；首个 html 为入口）
     * @return 入口 URL（local://<appId>/<entryFile>）以及目录里解析到的标签页图标路径
     */
    suspend fun import(appId: String, uris: List<Uri>): Result<ImportedApp> = withContext(Dispatchers.IO) {
        runCatching {
            require(uris.isNotEmpty()) { "未选择任何文件" }
            val dir = LocalWebHost.localAppDir(context, appId).apply { mkdirs() }
            val htmlName = tryCopyAll(uris, dir)
            ImportedApp(
                entryUrl = LocalWebHost.buildLocalAppUrl(appId, htmlName),
                iconPath = LocalHtmlIcon.existingPath(dir, htmlName),
            )
        }
    }

    suspend fun importFiles(appId: String, files: List<File>): Result<ImportedApp> = withContext(Dispatchers.IO) {
        importFiles(appId, files, context.filesDir)
    }

    /** 扁平拷贝；只收 .html/.htm，入口取第一个 HTML。 */
    private fun tryCopyAll(uris: List<Uri>, dir: File): String {
        val destCanonical = dir.canonicalFile
        try {
            var entry: String? = null
            for (uri in uris) {
                val name = sanitizeFileName(queryName(uri))
                if (!HtmlDirectoryLister.isHtmlName(name)) continue
                val target = File(destCanonical, name)
                val stream = context.contentResolver.openInputStream(uri)
                    ?: error("无法读取所选文件：${uri.lastPathSegment}")
                stream.use { copyBoundedToFile(it, target) }
                if (entry == null) entry = name
            }
            return entry ?: error("没有可拷贝的文件")
        } catch (error: Throwable) {
            destCanonical.deleteRecursively()
            throw error
        }
    }

    private fun queryName(uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "index.html"

    companion object {
        const val MAX_HTML_BYTES = 20L * 1024 * 1024

        fun importFiles(appId: String, files: List<File>, filesDir: File): Result<ImportedApp> = runCatching {
            require(files.isNotEmpty()) { "未选择任何文件" }
            val dir = File(File(filesDir, LocalWebHost.LOCAL_APPS_DIR), appId)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("import directory unavailable")
            val htmlName = copyHtmlFiles(files, dir)
            ImportedApp(
                entryUrl = LocalWebHost.buildLocalAppUrl(appId, htmlName),
                iconPath = LocalHtmlIcon.existingPath(dir, htmlName),
            )
        }

        internal fun copyHtmlFiles(files: List<File>, destDir: File): String {
            val destCanonical = destDir.canonicalFile
            if (!destCanonical.isDirectory && !destCanonical.mkdirs()) {
                throw IOException("import directory unavailable")
            }
            var firstCopied: String? = null
            var entry: String? = null
            for (file in files) {
                val name = sanitizeFileName(file.name)
                if (!HtmlDirectoryLister.isHtmlName(name)) error("unsupported type")
                if (file.length() > MAX_HTML_BYTES) error("too large")
                val source = file.canonicalFile
                if (!source.isFile) error("not a file")
                val target = File(destCanonical, name)
                source.inputStream().use { copyBoundedToFile(it, target) }
                if (firstCopied == null) firstCopied = name
                if (entry == null) entry = name
            }
            return entry ?: firstCopied ?: error("没有可拷贝的文件")
        }

        internal fun copyBoundedToFile(
            input: InputStream,
            target: File,
            maxBytes: Long = MAX_HTML_BYTES,
        ) {
            val parent = target.parentFile?.canonicalFile ?: throw IOException("import directory unavailable")
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("import directory unavailable")
            val dest = target.canonicalFile
            if (dest.parentFile?.canonicalFile != parent) error("path escape")
            var copied = 0L
            var overflow = false
            dest.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > maxBytes) {
                        overflow = true
                        break
                    }
                    output.write(buffer, 0, count)
                }
            }
            if (overflow || copied <= 0L) {
                dest.delete()
                error(if (overflow) "too large" else "empty")
            }
        }

        internal fun sanitizeFileName(name: String): String {
            val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[/\\\\:*?\"<>|]"), "_")
                .trim()
                .ifBlank { "index.html" }
            return cleaned
        }
    }
}
