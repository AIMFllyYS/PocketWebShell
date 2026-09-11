package com.webshell.core.webengine.storage

import java.io.File

/** 一棵已遍历目录树里的单个文件：路径相对于树根、'/' 分隔。 */
internal data class FileEntry(val relativePath: String, val size: Long)

/**
 * 递归遍历 [root]，返回其下所有文件的 (相对路径, 大小) 列表。
 * - [root] 不存在 → 空列表（视为 0 字节，而非不可测）；
 * - [root] 自身不可读 → null（整体不可测）；
 * - 子目录不可读 → 跳过该子树，其余继续计入。
 */
internal fun walkFiles(root: File): List<FileEntry>? {
    if (!root.exists()) return emptyList()
    val children = runCatching { root.listFiles() }.getOrNull() ?: return null
    val out = ArrayList<FileEntry>()
    collectSkipping(root, children, out)
    return out
}

private fun collectSkipping(root: File, children: Array<File>, out: MutableList<FileEntry>) {
    for (child in children) {
        if (child.isDirectory) {
            val nested = runCatching { child.listFiles() }.getOrNull() ?: continue
            collectSkipping(root, nested, out)
        } else {
            val rel = runCatching { child.relativeTo(root).invariantSeparatorsPath }
                .getOrDefault(child.name)
            out += FileEntry(rel, child.length())
        }
    }
}

internal fun List<FileEntry>.totalBytes(): Long = sumOf { it.size }

/** 把某个 Profile 的文件列表按分类器拆成 (可清理, 站点数据)。 */
internal fun splitProfileBytes(entries: List<FileEntry>): Pair<Long, Long> {
    var clearable = 0L
    var siteData = 0L
    for (e in entries) {
        if (StorageClassifier.classifyProfileEntry(e.relativePath) == StorageCategory.CLEARABLE_CACHE) {
            clearable += e.size
        } else {
            siteData += e.size
        }
    }
    return clearable to siteData
}

internal fun computeSiteStats(appId: String, entries: List<FileEntry>?): SiteStorageStats {
    if (entries == null) {
        return SiteStorageStats(appId, measurable = false, clearableBytes = 0, siteDataBytes = 0)
    }
    val (clearable, siteData) = splitProfileBytes(entries)
    return SiteStorageStats(appId, measurable = true, clearableBytes = clearable, siteDataBytes = siteData)
}

/**
 * 一次扫描的互斥分桶结果（由仓库层遍历磁盘装配，本类型纯 JVM 可测）：
 * 每个字节只落在其中一个桶里，汇总时不会重复计数。
 */
internal data class OverviewBuckets(
    /** appId → 其 Profile 文件列表；值为 null 表示目录存在但不可读（不可测）。键缺失表示目录不存在（可测，0 字节）。 */
    val siteProfiles: Map<String, List<FileEntry>?>,
    /** 不属于任何现存站点的孤儿 Profile 目录（已删除站点的残留）。 */
    val orphanProfiles: List<List<FileEntry>>,
    /** 默认共享 Profile 的缓存子目录 + app_webview 根部的旧版缓存目录。 */
    val defaultCache: List<FileEntry>,
    /** 默认共享 Profile 的非缓存部分（IndexedDB / Cache Storage / Cookie 等），计入网站数据。 */
    val defaultRest: List<FileEntry>,
    /** databases/ + filesDir/（含 icons、localapps、wallpaper、datastore）。 */
    val appDirs: List<FileEntry>,
    /** cacheDir/image_cache（Coil）。 */
    val imageCache: List<FileEntry>,
    /** cacheDir 其余部分。 */
    val cacheDirRest: List<FileEntry>,
)

internal fun computeOverview(
    buckets: OverviewBuckets,
    appIds: List<String>,
    multiProfile: Boolean,
    scannedAt: Long,
    systemTotalBytes: Long? = null,
    systemCacheBytes: Long? = null,
): StorageOverview {
    val sites = appIds.map { id ->
        val entries = if (buckets.siteProfiles.containsKey(id)) buckets.siteProfiles[id] else emptyList()
        computeSiteStats(id, entries)
    }
    val walkedClearable = sites.sumOf { it.clearableBytes } +
        buckets.defaultCache.totalBytes() +
        buckets.imageCache.totalBytes()
    val walkedSiteData = sites.sumOf { it.siteDataBytes } + buckets.defaultRest.totalBytes()
    val walkedApp = buckets.appDirs.totalBytes() +
        buckets.orphanProfiles.sumOf { it.totalBytes() } +
        buckets.cacheDirRest.totalBytes()
    val walkedTotal = walkedClearable + walkedSiteData + walkedApp
    val fallback = walkedTotal == 0L && systemTotalBytes != null && systemTotalBytes > 0L
    val clearable = if (fallback) systemCacheBytes ?: 0L else walkedClearable
    val sharedSite = if (fallback) {
        (systemTotalBytes - (systemCacheBytes ?: 0L)).coerceAtLeast(0L)
    } else {
        buckets.defaultRest.totalBytes()
    }
    val siteData = if (fallback) sharedSite else walkedSiteData
    val appBytes = if (fallback) 0L else walkedApp
    return StorageOverview(
        clearableBytes = clearable,
        siteDataBytes = siteData,
        appBytes = appBytes,
        sharedSiteDataBytes = sharedSite,
        sites = sites,
        unmeasurableSiteIds = sites.filter { !it.measurable }.map { it.appId },
        multiProfile = multiProfile,
        scannedAt = scannedAt,
        systemTotalBytes = systemTotalBytes,
        systemCacheBytes = systemCacheBytes,
    )
}
