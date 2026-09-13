package com.webshell.feature.add

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import java.io.File

enum class HtmlImportRootKind {
    Download,
    Documents,
    Storage,
    MiuiDownloads,
}

data class HtmlImportRoot(
    val kind: HtmlImportRootKind,
    val directory: File,
)

object HtmlImportRoots {
    const val APP_DOWNLOAD_FOLDER = "PocketWebShell"

    fun resolve(context: Context, sdk: Int = Build.VERSION.SDK_INT): List<HtmlImportRoot> {
        val volume = primaryVolume(context, sdk) ?: return emptyList()
        val volumeCanonical = runCatching { volume.canonicalFile }.getOrNull() ?: return emptyList()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val miuiDownloads = File(volumeCanonical, "MIUI/Downloads")
        return buildList {
            addIfUnderVolume(downloads, HtmlImportRootKind.Download, volumeCanonical)
            addIfUnderVolume(documents, HtmlImportRootKind.Documents, volumeCanonical)
            add(HtmlImportRoot(HtmlImportRootKind.Storage, volumeCanonical))
            if (isPresentAndListable(miuiDownloads) && isUnder(miuiDownloads, volumeCanonical)) {
                addIfUnderVolume(miuiDownloads, HtmlImportRootKind.MiuiDownloads, volumeCanonical)
            }
        }
    }

    fun appOwnedFallback(context: Context, kind: HtmlImportRootKind): File? {
        val dir = when (kind) {
            HtmlImportRootKind.Download, HtmlImportRootKind.MiuiDownloads ->
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            HtmlImportRootKind.Documents ->
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            HtmlImportRootKind.Storage -> null
        } ?: return null
        return dir.takeIf { it.isDirectory }
    }

    fun appOwnedHtmlFiles(context: Context): List<File> {
        val fromDir = buildList {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { addAll(htmlFilesIn(it)) }
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { addAll(htmlFilesIn(it)) }
            publicAppDownloadDir()?.let { addAll(htmlFilesIn(it)) }
        }
        val fromStore = queryOwnMediaStoreHtml(context)
        return (fromDir + fromStore).distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
    }

    fun volumeRoot(context: Context, sdk: Int = Build.VERSION.SDK_INT): File? =
        primaryVolume(context, sdk)?.let { runCatching { it.canonicalFile }.getOrNull() }

    fun isUnder(child: File, parent: File): Boolean {
        val resolvedChild = runCatching { child.canonicalFile }.getOrNull() ?: return false
        val resolvedParent = runCatching { parent.canonicalFile }.getOrNull() ?: return false
        val prefix = resolvedParent.path + File.separator
        return resolvedChild.path == resolvedParent.path || resolvedChild.path.startsWith(prefix)
    }

    fun isListable(dir: File): Boolean {
        if (!dir.exists()) return true
        return dir.listFiles() != null
    }

    private fun MutableList<HtmlImportRoot>.addIfUnderVolume(
        dir: File,
        kind: HtmlImportRootKind,
        volume: File,
    ) {
        val canonical = runCatching { dir.canonicalFile }.getOrNull() ?: return
        if (isUnder(canonical, volume)) add(HtmlImportRoot(kind, canonical))
    }

    private fun primaryVolume(context: Context, sdk: Int): File? {
        if (sdk >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(StorageManager::class.java) ?: return null
            val primary = manager.storageVolumes.firstOrNull { it.isPrimary } ?: return null
            return primary.directory
        }
        @Suppress("DEPRECATION")
        return Environment.getExternalStorageDirectory()
    }

    private fun isPresentAndListable(dir: File): Boolean = dir.isDirectory && dir.listFiles() != null

    private fun publicAppDownloadDir(): File? {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folder = File(downloads, APP_DOWNLOAD_FOLDER)
        return folder.takeIf { it.isDirectory && it.listFiles() != null }
    }

    private fun htmlFilesIn(dir: File): List<File> {
        val children = dir.listFiles() ?: return emptyList()
        return children.filter { it.isFile && HtmlDirectoryLister.isHtmlName(it.name) && !it.name.startsWith('.') }
    }

    private fun queryOwnMediaStoreHtml(context: Context): List<File> {
        return runCatching {
            val uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
            @Suppress("DEPRECATION")
            val projection = arrayOf(MediaStore.Downloads.DATA, MediaStore.Downloads.DISPLAY_NAME)
            val selection = "${MediaStore.Downloads.OWNER_PACKAGE_NAME}=?"
            val args = arrayOf(context.packageName)
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                @Suppress("DEPRECATION")
                val dataIdx = cursor.getColumnIndex(MediaStore.Downloads.DATA)
                val nameIdx = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        val path = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                        val file = path?.let(::File) ?: continue
                        val label = name ?: file.name
                        if (file.isFile && HtmlDirectoryLister.isHtmlName(label) && !label.startsWith('.')) {
                            add(file)
                        }
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
