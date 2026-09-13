package com.webshell.core.webengine.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import androidx.webkit.WebViewFeature
import com.webshell.core.model.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 存储统计仓库：扫描磁盘并把字节按互斥桶归类（见 [OverviewBuckets]），
 * 再与 [SiteStorageProbe] 的按站证据合并。结果带约 30s 内存缓存。
 */
@Singleton
class StorageStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val probe: SiteStorageProbe,
) {

    private data class CachedScan(val sites: List<SiteScanInput>, val overview: StorageOverview)

    private val lock = Any()
    private var cached: CachedScan? = null

    suspend fun scan(sites: List<SiteScanInput>, forceRefresh: Boolean = false): StorageOverview {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val c = cached
            if (!forceRefresh && c != null && c.sites == sites &&
                now - c.overview.scannedAt < CACHE_TTL_MS
            ) {
                return c.overview
            }
        }
        val overview = doScan(sites, now)
        synchronized(lock) { cached = CachedScan(sites, overview) }
        return overview
    }

    fun invalidate() = synchronized(lock) { cached = null }

    private suspend fun doScan(sites: List<SiteScanInput>, scannedAt: Long): StorageOverview {
        val walked = withContext(Dispatchers.IO) { walkDisk(sites) }
        val capabilities = probe.capabilities()
        val cookiesBySiteKey = LinkedHashMap<String, Metric<CookieFacts>>()
        for (input in sites) {
            if (input.siteKey in cookiesBySiteKey) continue
            cookiesBySiteKey[input.siteKey] = if (input.isLocal) {
                Metric.Absent
            } else {
                probe.cookieEvidence(input.url)
            }
        }
        val quotaByHost = probe.quotaUsageByHost()
        val attributed = attributeSites(
            inputs = sites,
            indexedDbByHost = walked.indexedDbByHost,
            cookiesBySiteKey = cookiesBySiteKey,
            quotaByHost = quotaByHost,
            localImportByAppId = walked.localImportByAppId,
        )
        val overview = computeOverview(
            buckets = walked.buckets,
            sites = attributed,
            multiProfile = walked.multiProfile,
            scannedAt = scannedAt,
            probeCapabilities = capabilities,
            systemTotalBytes = walked.systemTotalBytes,
            systemCacheBytes = walked.systemCacheBytes,
        )
        logScan(walked, capabilities, quotaByHost, attributed, overview)
        return overview
    }

    private data class WalkedDisk(
        val buckets: OverviewBuckets,
        val indexedDbByHost: Map<String, Long>,
        val localImportByAppId: Map<String, Long?>,
        val multiProfile: Boolean,
        val systemTotalBytes: Long?,
        val systemCacheBytes: Long?,
        val unknownDefaultTops: List<String>,
        val volumeUuidFallback: Boolean,
    )

    private fun walkDisk(sites: List<SiteScanInput>): WalkedDisk {
        val dataDir = context.dataDir
        val webviewRoot = File(dataDir, "app_webview")
        val defaultDir = File(webviewRoot, "Default")

        val uuidResult = runCatching {
            context.getSystemService(StorageManager::class.java).getUuidForPath(context.dataDir)
        }
        val volumeUuidFallback = uuidResult.isFailure
        val volumeUuid = uuidResult.getOrElse { e ->
            AppLog.warn(TAG, "getUuidForPath 失败，回退 UUID_DEFAULT：${e.javaClass.simpleName}")
            StorageManager.UUID_DEFAULT
        }
        val systemStats = runCatching {
            context.getSystemService(StorageStatsManager::class.java)
                .queryStatsForPackage(volumeUuid, context.packageName, Process.myUserHandle())
        }.onFailure { e ->
            AppLog.warn(TAG, "StorageStatsManager 查询失败：${e.javaClass.simpleName} ${e.message}")
        }.getOrNull()
        val systemCacheBytes = systemStats?.cacheBytes
        val systemTotalBytes = systemStats?.let { stats ->
            (stats.dataBytes + stats.cacheBytes).takeIf { it > 0L }
        }

        val multiProfile = runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        }.getOrDefault(false)

        val defaultFiles = walkFiles(defaultDir)
        if (defaultFiles == null) AppLog.warn(TAG, "默认 Profile 目录不可读，按 0 字节计")
        val defaultCache = mutableListOf<FileEntry>()
        val defaultRest = mutableListOf<FileEntry>()
        val unknownDefaultTops = LinkedHashSet<String>()
        defaultFiles?.forEach { e ->
            if (StorageClassifier.isUnknownProfileTopLevel(e.relativePath)) {
                unknownDefaultTops += e.relativePath.substringBefore('/')
            }
            if (StorageClassifier.classifyProfileEntry(e.relativePath) == StorageCategory.CLEARABLE_CACHE) {
                defaultCache += e
            } else {
                defaultRest += e
            }
        }
        if (unknownDefaultTops.isNotEmpty()) {
            AppLog.warn(TAG, "Default 未知顶层目录: ${unknownDefaultTops.sorted().joinToString()}")
        }

        for (legacy in StorageClassifier.CACHE_DIR_NAMES) {
            val files = walkFiles(File(webviewRoot, legacy))
            if (files != null) defaultCache += files
        }

        val legacyProfiles = mutableListOf<List<FileEntry>>()
        val rootChildren = if (webviewRoot.exists()) {
            runCatching { webviewRoot.listFiles() }.getOrNull()
        } else {
            emptyArray()
        }
        if (webviewRoot.exists() && rootChildren == null) {
            AppLog.warn(TAG, "app_webview 根目录不可读，其杂项按 0 字节计")
        }
        val knownRoot = setOf("Default") + StorageClassifier.CACHE_DIR_NAMES
        rootChildren.orEmpty().forEach { child ->
            when {
                child.name in knownRoot -> Unit
                StorageClassifier.isLegacyProfileDirName(child.name) && child.isDirectory -> {
                    val files = walkFiles(child)
                    if (files == null) AppLog.warn(TAG, "遗留 Profile 目录不可读: ${child.name}")
                    else legacyProfiles += files
                }
                child.isDirectory -> {
                    val files = walkFiles(child)
                    if (files == null) AppLog.warn(TAG, "app_webview 子目录不可读: ${child.name}")
                    else defaultRest += files
                    if (StorageClassifier.isUnknownProfileTopLevel(child.name)) {
                        AppLog.warn(TAG, "app_webview 未知顶层目录: ${child.name}")
                    }
                }
                else -> {
                    defaultRest += FileEntry(child.name, child.length())
                    if (StorageClassifier.isUnknownProfileTopLevel(child.name)) {
                        AppLog.warn(TAG, "app_webview 未知顶层文件: ${child.name}")
                    }
                }
            }
        }

        val dbFiles = walkFiles(File(dataDir, "databases"))
        if (dbFiles == null) AppLog.warn(TAG, "应用目录不可读: databases")
        val filesDirFiles = walkFiles(context.filesDir)
        if (filesDirFiles == null) AppLog.warn(TAG, "应用目录不可读: ${context.filesDir.name}")
        val appDirs = dbFiles.orEmpty() + filesDirFiles.orEmpty()

        val cacheFiles = walkFiles(context.cacheDir)
        if (cacheFiles == null) AppLog.warn(TAG, "cacheDir 不可读，按 0 字节计")
        val imageCache = mutableListOf<FileEntry>()
        val cacheDirRest = mutableListOf<FileEntry>()
        cacheFiles?.forEach { e ->
            val top = e.relativePath.substringBefore('/')
            when {
                top == IMAGE_CACHE_DIR || top in StorageClassifier.WEBVIEW_CACHE_DIR_NAMES -> imageCache += e
                else -> cacheDirRest += e
            }
        }

        val localImportByAppId = sites.associate { input ->
            val dir = File(context.filesDir, "localapps/${input.appId}")
            input.appId to walkFiles(dir)?.totalBytes()
        }

        return WalkedDisk(
            buckets = OverviewBuckets(
                legacyProfiles = legacyProfiles,
                defaultCache = defaultCache,
                defaultRest = defaultRest,
                appDirs = appDirs,
                imageCache = imageCache,
                cacheDirRest = cacheDirRest,
            ),
            indexedDbByHost = indexedDbBytesByHost(defaultDir),
            localImportByAppId = localImportByAppId,
            multiProfile = multiProfile,
            systemTotalBytes = systemTotalBytes,
            systemCacheBytes = systemCacheBytes,
            unknownDefaultTops = unknownDefaultTops.sorted(),
            volumeUuidFallback = volumeUuidFallback,
        )
    }

    private fun logScan(
        walked: WalkedDisk,
        capabilities: SiteProbeCapabilities,
        quotaByHost: Map<String, Long>?,
        sites: List<SiteStorageStats>,
        overview: StorageOverview,
    ) {
        AppLog.log(
            TAG,
            "存储扫描明细: Default未知顶层=[${walked.unknownDefaultTops.joinToString()}] " +
                "probe deleteForSite=${capabilities.deleteForSite} " +
                "cookieInfo=${capabilities.cookieInfo} httpCacheQuota=${capabilities.httpCacheQuota} " +
                "IndexedDB hosts=${walked.indexedDbByHost.size} " +
                "quota=${quotaByHost?.size?.toString() ?: "timeout"} " +
                "uuidFallback=${walked.volumeUuidFallback} " +
                "系统总量=${overview.systemTotalBytes ?: -1}B 系统缓存=${overview.systemCacheBytes ?: -1}B",
        )
        for (stats in sites.distinctBy { it.siteKey }) {
            AppLog.log(
                TAG,
                "站点证据: host=${stats.host.ifBlank { stats.siteKey }} " +
                    "cookie=${stats.cookie.stateLabel()}" +
                    cookieCountLabel(stats.cookie) +
                    " idb=${stats.indexedDbBytes.stateLabel()}${bytesLabel(stats.indexedDbBytes)} " +
                    "quota=${stats.quotaUsageBytes.stateLabel()}${bytesLabel(stats.quotaUsageBytes)} " +
                    "local=${stats.localImportBytes.stateLabel()}${bytesLabel(stats.localImportBytes)} " +
                    "attributable=${stats.attributableBytes}B",
            )
        }
        val walkedTotal = overview.clearableBytes + overview.siteDataBytes + overview.appBytes
        if (overview.systemTotalBytes != null && overview.systemTotalBytes > 0 && walkedTotal == 0L) {
            AppLog.warn(
                TAG,
                "磁盘遍历结果为 0，但系统报告应用数据 ${overview.systemTotalBytes}B，本设备路径假设可能不匹配",
            )
        }
        AppLog.log(
            TAG,
            "存储扫描完成: 站点=${sites.size} 可清理=${overview.clearableBytes}B " +
                "站点数据=${overview.siteDataBytes}B 应用=${overview.appBytes}B " +
                "不可归属共享=${overview.unattributableSharedBytes}B " +
                "不可测=${overview.unmeasurableSiteIds.size} multiProfile=${overview.multiProfile}",
        )
    }

    private fun cookieCountLabel(metric: Metric<CookieFacts>): String =
        when (metric) {
            is Metric.Measured -> " count=${metric.value.count} bytes=${metric.value.serializedBytes}"
            else -> ""
        }

    private fun bytesLabel(metric: Metric<Long>): String =
        when (metric) {
            is Metric.Measured -> " ${metric.value}B"
            else -> ""
        }

    internal companion object {
        const val TAG = "storage"
        const val CACHE_TTL_MS = 30_000L
        const val IMAGE_CACHE_DIR = "image_cache"
    }
}
