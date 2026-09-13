package com.webshell.feature.add

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import java.io.File

data class DiscoveredHtml(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val location: String,
)

/**
 * Fast HTML discovery for the in-app picker. Does not walk the whole volume.
 * MediaStore first, then a capped BFS of high-yield public folders. Never
 * enters Android/data or Android/obb. I/O belongs on a background thread.
 */
object HtmlDiscovery {
    const val MAX_FILES: Int = 200
    const val MAX_DISPLAY_RECENTS: Int = 30
    const val MAX_DIRS: Int = 80
    const val MAX_DEPTH: Int = 3
    const val MAX_MILLIS: Long = 1_600L
    const val MAX_ENQUEUED_SUBDIRS_PER_DIR: Int = 80
    const val MEDIASTORE_LIMIT: Int = 150

    private val skipDirectoryNames = setOf(
        "cache", "caches", "tmp", "temp", "thumbnail", "thumbnails", ".thumbnails",
        ".trash", "recyclebin", "lost.dir", "lost+found", "node_modules", "__macosx",
        "dcim", "movies", "music", "alarms", "ringtones", "notifications",
        "podcasts", "audiobooks", "pictures", "camera", "recordings",
    )

    /**
     * Relative to the primary volume. These are the places Android OEMs and
     * chat apps actually drop received HTML. The volume root itself is not a seed.
     */
    val seedRelativePaths: List<String> = listOf(
        "Download",
        "Downloads",
        "Documents",
        "Download/PocketWebShell",
        "Documents/PocketWebShell",
        "MIUI/Downloads",
        "tencent/MicroMsg/Download",
        "Tencent/MicroMsg/Download",
        "tencent/MicroMsg/WeiXin",
        "Tencent/MicroMsg/WeiXin",
        "tencent/QQfile_recv",
        "Tencent/QQfile_recv",
        "tencent/QQfile_share",
        "Tencent/QQfile_share",
        "tencent/QQ_Download",
        "Tencent/QQ_Download",
        "tencent/TIMfile_recv",
        "Tencent/TIMfile_recv",
        "Android/media/com.tencent.mm/MicroMsg/Download",
        "Android/media/com.tencent.mobileqq/Tencent/QQfile_recv",
        "Telegram",
        "Telegram/Telegram Documents",
        "Telegram/Telegram Files",
        "Download/Telegram",
        "Download/WeiXin",
        "Download/Weixin",
        "Download/WeChat",
        "Download/微信",
        "Download/tencent",
        "Download/Tencent",
        "Download/QQ",
        "WeiXin",
        "Download/Browser",
        "Download/Chrome",
        "browser/download",
        "QQBrowser/下载",
        "QQBrowser/Download",
        "UCDownloads",
        "Quark/Download",
        "Huawei/Download",
        "Huawei/Browser/Download",
        "OPPO/Download",
        "ColorOS/Browser/Download",
        "vivo/Download",
        "DingTalk",
        "Download/EmailAttachments",
    )

    fun shouldSkipDirectory(name: String): Boolean {
        if (name.startsWith('.')) return true
        return name.lowercase() in skipDirectoryNames
    }

    fun matchesListedFile(query: String, name: String, location: String, directoryLocation: String): Boolean =
        matchesQuery(query, name, location.ifBlank { directoryLocation })

    /** [location] is the relative parent (e.g. Download/WeiXin), never an absolute path. */
    fun matchesQuery(query: String, name: String, location: String): Boolean {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val haystack = buildString {
            append(name.lowercase())
            append(' ')
            append(location.replace('\\', '/').lowercase())
        }
        return tokens.all { it in haystack }
    }

    fun locationLabel(file: File, volume: File?): String {
        val parent = file.parentFile ?: return ""
        val volumePath = volume?.let { runCatching { it.canonicalFile }.getOrNull() }
        val parentPath = runCatching { parent.canonicalFile }.getOrNull() ?: parent
        if (volumePath != null && HtmlImportRoots.isUnder(parentPath, volumePath)) {
            val rel = parentPath.path.removePrefix(volumePath.path).trimStart('/', '\\')
            return rel.replace('\\', '/').ifBlank { volumePath.name }
        }
        return parent.name
    }

    fun merge(files: List<DiscoveredHtml>, limit: Int = MAX_FILES): List<DiscoveredHtml> =
        files.distinctBy { runCatching { it.file.canonicalPath }.getOrDefault(it.file.absolutePath) }
            .sortedWith(compareByDescending<DiscoveredHtml> { it.lastModified }.thenBy { it.name.lowercase() })
            .take(limit)

    fun walk(
        seeds: List<File>,
        volume: File?,
        ownedRoots: List<File> = emptyList(),
        maxFiles: Int = MAX_FILES,
        maxDirs: Int = MAX_DIRS,
        maxDepth: Int = MAX_DEPTH,
        maxMillis: Long = MAX_MILLIS,
        maxEnqueuedSubdirsPerDir: Int = MAX_ENQUEUED_SUBDIRS_PER_DIR,
        now: () -> Long = { System.currentTimeMillis() },
    ): List<DiscoveredHtml> {
        val started = now()
        val found = ArrayList<DiscoveredHtml>(maxFiles)
        val queue = ArrayDeque<Pair<File, Int>>()
        val visited = HashSet<String>()
        seeds.forEach { seed ->
            val canonical = runCatching { seed.canonicalFile }.getOrNull() ?: return@forEach
            if (!canonical.isDirectory) return@forEach
            if (!isAllowedLocation(canonical, volume, ownedRoots)) return@forEach
            if (visited.add(canonical.path)) queue.addLast(canonical to 0)
        }
        var dirsVisited = 0
        while (queue.isNotEmpty() &&
            found.size < maxFiles &&
            dirsVisited < maxDirs &&
            now() - started <= maxMillis
        ) {
            val (dir, depth) = queue.removeFirst()
            dirsVisited++
            val children = dir.listFiles() ?: continue
            val recurse = depth < maxDepth
            var enqueued = 0
            for (child in children) {
                if (found.size >= maxFiles || now() - started > maxMillis) break
                if (child.name.startsWith('.')) continue
                if (child.isDirectory) {
                    if (!recurse || enqueued >= maxEnqueuedSubdirsPerDir || shouldSkipDirectory(child.name)) continue
                    val canonical = runCatching { child.canonicalFile }.getOrNull() ?: continue
                    if (!isAllowedLocation(canonical, volume, ownedRoots)) continue
                    if (visited.add(canonical.path)) {
                        queue.addLast(canonical to depth + 1)
                        enqueued++
                    }
                } else if (acceptFile(child, volume, ownedRoots)) {
                    found.add(toDiscovered(child, volume))
                }
            }
        }
        return merge(found, maxFiles)
    }

    fun discover(
        context: Context,
        volume: File?,
        extraSeeds: List<File>,
        ownedRoots: List<File>,
        publicListingAllowed: Boolean,
    ): List<DiscoveredHtml> {
        val indexed = if (publicListingAllowed) queryIndexedHtml(context, volume, ownedRoots) else emptyList()
        val seeds = buildList {
            if (publicListingAllowed && volume != null) {
                seedRelativePaths.forEach { relative ->
                    add(File(volume, relative.replace('/', File.separatorChar)))
                }
            }
            if (publicListingAllowed) addAll(extraSeeds)
            addAll(ownedRoots)
        }
        val walked = walk(seeds, volume, ownedRoots)
        return merge(indexed + walked)
    }

    internal fun acceptFile(file: File, volume: File?, ownedRoots: List<File> = emptyList()): Boolean {
        if (!file.isFile || !HtmlDirectoryLister.isHtmlName(file.name)) return false
        if (file.name.startsWith('.')) return false
        if (file.length() > LocalAppImporter.MAX_HTML_BYTES) return false
        if (!isAllowedLocation(file, volume, ownedRoots)) return false
        return file.canRead()
    }

    internal fun isAllowedLocation(file: File, volume: File?, ownedRoots: List<File>): Boolean {
        if (ownedRoots.any { HtmlImportRoots.isUnder(file, it) }) return true
        if (isProtectedTree(file)) return false
        if (volume == null) return true
        return HtmlImportRoots.isUnder(file, volume)
    }

    internal fun isProtectedTree(file: File): Boolean {
        val parts = file.path.split(File.separatorChar, '/', '\\')
        val androidIdx = parts.indexOfFirst { it.equals("Android", ignoreCase = true) }
        if (androidIdx < 0 || androidIdx >= parts.lastIndex) return false
        val next = parts[androidIdx + 1]
        return next.equals("data", ignoreCase = true) || next.equals("obb", ignoreCase = true)
    }

    private fun toDiscovered(file: File, volume: File?): DiscoveredHtml = DiscoveredHtml(
        file = file,
        name = file.name,
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
        location = locationLabel(file, volume),
    )

    private fun queryIndexedHtml(
        context: Context,
        volume: File?,
        ownedRoots: List<File>,
    ): List<DiscoveredHtml> = runCatching {
        val resolver = context.contentResolver
        val collections = buildList {
            add(MediaStore.Files.getContentUri("external"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { add(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)) }
            }
        }
        val found = ArrayList<DiscoveredHtml>()
        collections.forEach { uri ->
            if (found.size >= MAX_FILES) return@forEach
            @Suppress("DEPRECATION")
            val projection = arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.MIME_TYPE,
            )
            val selection = "(${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.MediaColumns.MIME_TYPE}=? OR " +
                "${MediaStore.MediaColumns.MIME_TYPE}=?)"
            val args = arrayOf("%.html", "%.htm", "text/html", "application/xhtml+xml")
            queryIndexedCollection(resolver, uri, projection, selection, args)
                ?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val modifiedIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val relativeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    @Suppress("DEPRECATION")
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    var taken = 0
                    while (cursor.moveToNext() && found.size < MAX_FILES && taken < MEDIASTORE_LIMIT) {
                        taken++
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                        val data = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                        val relative = if (relativeIdx >= 0) cursor.getString(relativeIdx) else null
                        val file = resolveIndexedFile(volume, data, relative, name) ?: continue
                        if (!acceptFile(file, volume, ownedRoots)) continue
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else file.length()
                        val modifiedRaw = if (modifiedIdx >= 0) cursor.getLong(modifiedIdx) else 0L
                        val modified = when {
                            modifiedRaw <= 0L -> file.lastModified()
                            modifiedRaw < 1_000_000_000_000L -> modifiedRaw * 1000L
                            else -> modifiedRaw
                        }
                        found.add(
                            DiscoveredHtml(
                                file = file,
                                name = name ?: file.name,
                                sizeBytes = size,
                                lastModified = modified,
                                location = locationLabel(file, volume),
                            ),
                        )
                    }
                }
        }
        found
    }.getOrDefault(emptyList())

    private fun queryIndexedCollection(
        resolver: ContentResolver,
        uri: Uri,
        projection: Array<String>,
        selection: String,
        args: Array<String>,
    ): Cursor? {
        val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val limited = runCatching {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
                putInt(ContentResolver.QUERY_ARG_LIMIT, MEDIASTORE_LIMIT)
            }
            resolver.query(uri, projection, queryArgs, null)
        }.getOrNull()
        if (limited != null) return limited
        return resolver.query(uri, projection, selection, args, sort)
    }

    private fun resolveIndexedFile(
        volume: File?,
        data: String?,
        relative: String?,
        name: String?,
    ): File? {
        if (!data.isNullOrBlank()) {
            val fromData = File(data)
            if (fromData.isFile) return fromData
        }
        if (volume != null && !relative.isNullOrBlank() && !name.isNullOrBlank()) {
            val fromRelative = File(File(volume, relative), name)
            if (fromRelative.isFile) return fromRelative
        }
        return null
    }
}
