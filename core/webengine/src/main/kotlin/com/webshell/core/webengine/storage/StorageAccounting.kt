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

internal fun Map<String, Long>.sumForSiteGroup(groupKey: String): Long =
    entries.filter { siteGroupKey(it.key) == groupKey }.sumOf { it.value }

internal fun localImportMetric(bytes: Long?): Metric<Long> = when (bytes) {
    null -> Metric.Unavailable
    0L -> Metric.Absent
    else -> Metric.Measured(bytes, exact = true)
}

internal fun attributableSum(
    cookie: Metric<CookieFacts>,
    indexedDb: Metric<Long>,
    quota: Metric<Long>,
    localImport: Metric<Long>,
): Long {
    var sum = 0L
    if (cookie is Metric.Measured) sum += cookie.value.serializedBytes
    if (indexedDb is Metric.Measured) sum += indexedDb.value
    if (quota is Metric.Measured) sum += quota.value
    if (localImport is Metric.Measured) sum += localImport.value
    return sum
}

/**
 * 按 siteKey 共享 Cookie / IndexedDB / 配额证据；本地导入仍按 appId。
 * [quotaByHost] 为 null 表示没问到 → Unavailable，空 Map → Absent。
 */
internal fun attributeSites(
    inputs: List<SiteScanInput>,
    indexedDbByHost: Map<String, Long>,
    cookiesBySiteKey: Map<String, Metric<CookieFacts>>,
    quotaByHost: Map<String, Long>?,
    localImportByAppId: Map<String, Long?>,
): List<SiteStorageStats> = inputs.map { input ->
    val localImport = if (localImportByAppId.containsKey(input.appId)) {
        localImportMetric(localImportByAppId[input.appId])
    } else {
        Metric.Absent
    }
    if (input.isLocal) {
        return@map SiteStorageStats(
            appId = input.appId,
            siteKey = input.siteKey,
            host = input.host,
            isLocal = true,
            cookie = Metric.Absent,
            indexedDbBytes = Metric.Absent,
            quotaUsageBytes = Metric.Absent,
            localImportBytes = localImport,
            signedIn = false,
            attributableBytes = attributableSum(Metric.Absent, Metric.Absent, Metric.Absent, localImport),
        )
    }
    val cookie = cookiesBySiteKey[input.siteKey] ?: Metric.Unavailable
    val idbSum = indexedDbByHost.sumForSiteGroup(input.siteKey)
    val indexedDb = if (idbSum == 0L) Metric.Absent else Metric.Measured(idbSum, exact = true)
    val quota = when {
        quotaByHost == null -> Metric.Unavailable
        else -> {
            val usage = quotaByHost.sumForSiteGroup(input.siteKey)
            if (usage == 0L) Metric.Absent else Metric.Measured(usage, exact = false)
        }
    }
    SiteStorageStats(
        appId = input.appId,
        siteKey = input.siteKey,
        host = input.host,
        isLocal = false,
        cookie = cookie,
        indexedDbBytes = indexedDb,
        quotaUsageBytes = quota,
        localImportBytes = localImport,
        signedIn = (cookie as? Metric.Measured)?.value?.count?.let { it > 0 } == true,
        attributableBytes = attributableSum(cookie, indexedDb, quota, localImport),
    )
}

internal fun attributedIndexedDbBytes(sites: List<SiteStorageStats>): Long =
    sites.distinctBy { it.siteKey }.sumOf { stats ->
        (stats.indexedDbBytes as? Metric.Measured)?.value ?: 0L
    }

internal fun unattributableSharedBytes(defaultSiteDataBytes: Long, sites: List<SiteStorageStats>): Long =
    (defaultSiteDataBytes - attributedIndexedDbBytes(sites)).coerceAtLeast(0L)

/**
 * 一次扫描的互斥分桶结果（由仓库层遍历磁盘装配，本类型纯 JVM 可测）：
 * 每个字节只落在其中一个桶里，汇总时不会重复计数。
 */
internal data class OverviewBuckets(
    /** 遗留 `Profile N` 目录（产品从不创建命名 Profile；残留计入应用数据）。 */
    val legacyProfiles: List<List<FileEntry>>,
    /** 默认共享 Profile 的缓存子目录 + app_webview 根部的旧版缓存目录。 */
    val defaultCache: List<FileEntry>,
    /** 默认共享 Profile 的非缓存部分（IndexedDB / Cache Storage / Cookie 等），计入网站数据。 */
    val defaultRest: List<FileEntry>,
    /** databases/ + filesDir/（含 icons、localapps、wallpaper、datastore）。 */
    val appDirs: List<FileEntry>,
    /** cacheDir/image_cache（Coil）以及 cache/WebView HTTP 缓存。 */
    val imageCache: List<FileEntry>,
    /** cacheDir 其余部分。 */
    val cacheDirRest: List<FileEntry>,
)

internal fun computeOverview(
    buckets: OverviewBuckets,
    sites: List<SiteStorageStats>,
    multiProfile: Boolean,
    scannedAt: Long,
    probeCapabilities: SiteProbeCapabilities,
    systemTotalBytes: Long? = null,
    systemCacheBytes: Long? = null,
): StorageOverview {
    val walkedClearable = buckets.defaultCache.totalBytes() + buckets.imageCache.totalBytes()
    val walkedSiteData = buckets.defaultRest.totalBytes()
    val walkedApp = buckets.appDirs.totalBytes() +
        buckets.legacyProfiles.sumOf { it.totalBytes() } +
        buckets.cacheDirRest.totalBytes()
    val walkedTotal = walkedClearable + walkedSiteData + walkedApp
    val fallback = walkedTotal == 0L && systemTotalBytes != null && systemTotalBytes > 0L
    val clearable = if (fallback) systemCacheBytes ?: 0L else walkedClearable
    val sharedSite = if (fallback) {
        (systemTotalBytes - (systemCacheBytes ?: 0L)).coerceAtLeast(0L)
    } else {
        walkedSiteData
    }
    val siteData = if (fallback) sharedSite else walkedSiteData
    val appBytes = if (fallback) 0L else walkedApp
    return StorageOverview(
        clearableBytes = clearable,
        siteDataBytes = siteData,
        appBytes = appBytes,
        sharedSiteDataBytes = sharedSite,
        unattributableSharedBytes = unattributableSharedBytes(sharedSite, sites),
        probeCapabilities = probeCapabilities,
        sites = sites,
        unmeasurableSiteIds = sites.filter { it.allMetricsUnavailable() }.map { it.appId },
        multiProfile = multiProfile,
        scannedAt = scannedAt,
        systemTotalBytes = systemTotalBytes,
        systemCacheBytes = systemCacheBytes,
    )
}
