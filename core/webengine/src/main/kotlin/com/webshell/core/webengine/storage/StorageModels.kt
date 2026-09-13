package com.webshell.core.webengine.storage

import java.net.URI

/** 存储归类：可清理缓存 / 站点数据 / 应用自身数据。 */
enum class StorageCategory { CLEARABLE_CACHE, SITE_DATA, APP_DATA }

/**
 * 纯分类器：输入相对某个 WebView Profile 目录的路径（'/' 分隔，如 "Cache/index.txt"），
 * 按顶层段判断归属。Profile 内只有缓存与站点数据两类；APP_DATA 用于概览层面的应用目录。
 *
 * 缓存目录名、站点数据目录名与 HTTP 缓存目录名都只在这里维护一份。
 */
object StorageClassifier {

    val CACHE_DIR_NAMES = listOf(
        "Cache",
        "Code Cache",
        "GPUCache",
        "GrShaderCache",
        "ShaderCache",
        "DawnGraphiteCache",
        "DawnWebGPUCache",
        "GraphiteDawnCache",
    )

    val SITE_DATA_DIR_NAMES = listOf(
        "IndexedDB", "Local Storage", "Session Storage", "Shared Storage",
        "Service Worker", "Storage", "Network", "Cookies",
        "databases", "blob_storage", "File System", "Platform Notifications",
        "VideoDecodeStats", "Site Characteristics Database", "shared_proto_db",
    )

    val WEBVIEW_CACHE_DIR_NAMES = setOf(
        "WebView",
        "webview",
        "org.chromium.android_webview",
        "webviewCacheChromium",
    )

    private val LEGACY_PROFILE_DIR = Regex("^Profile \\d+$")
    private val CACHE_TOP_LEVEL = CACHE_DIR_NAMES.toSet()
    private val SITE_DATA_TOP_LEVEL = SITE_DATA_DIR_NAMES.toSet()

    fun classifyProfileEntry(relativePath: String): StorageCategory {
        val top = relativePath.substringBefore('/')
        return if (top in CACHE_TOP_LEVEL) StorageCategory.CLEARABLE_CACHE else StorageCategory.SITE_DATA
    }

    /** 顶层段既不在缓存清单也不在已知站点数据清单。未知项仍归入 SITE_DATA。 */
    fun isUnknownProfileTopLevel(relativePath: String): Boolean {
        val top = relativePath.substringBefore('/')
        return top.isNotEmpty() && top !in CACHE_TOP_LEVEL && top !in SITE_DATA_TOP_LEVEL
    }

    fun isLegacyProfileDirName(name: String): Boolean = LEGACY_PROFILE_DIR.matches(name)
}

/** 单项指标三态：实测有值 / 实测为 0 / 没测到或原理不可测。Unavailable 不得渲染成 0。 */
sealed interface Metric<out T> {
    data class Measured<T>(val value: T, val exact: Boolean) : Metric<T>
    data object Absent : Metric<Nothing>
    data object Unavailable : Metric<Nothing>
}

data class CookieFacts(
    val count: Int,
    /** Cookie 头序列化长度之和，近似值。 */
    val serializedBytes: Long,
)

data class SiteProbeCapabilities(
    val deleteForSite: Boolean,
    val cookieInfo: Boolean = false,
    val httpCacheQuota: Boolean = false,
)

/** 一次扫描的站点身份。缓存键必须包含 url/host/isLocal，避免只改 URL 不重扫。 */
data class SiteScanInput(
    val appId: String,
    val url: String,
    val host: String,
    val isLocal: Boolean,
) {
    val siteKey: String get() = siteKeyOf(appId, host, isLocal)
}

data class SiteStorageStats(
    val appId: String,
    val siteKey: String,
    val host: String,
    val isLocal: Boolean,
    val cookie: Metric<CookieFacts>,
    val indexedDbBytes: Metric<Long>,
    val quotaUsageBytes: Metric<Long>,
    val localImportBytes: Metric<Long>,
    val signedIn: Boolean,
    /**
     * 可归属项之和（Cookie 序列化字节 + IndexedDB + 配额型存储 + 本地导入）。
     * 这是下界，不含 HTTP 缓存与共享 LevelDB 分摊。
     */
    val attributableBytes: Long,
) {
    fun allMetricsUnavailable(): Boolean =
        cookie is Metric.Unavailable &&
            indexedDbBytes is Metric.Unavailable &&
            quotaUsageBytes is Metric.Unavailable &&
            localImportBytes is Metric.Unavailable
}

data class StorageOverview(
    /** 默认 Profile 缓存目录 + 根部遗留缓存 + Coil image_cache + cache/WebView HTTP 缓存。 */
    val clearableBytes: Long,
    /** 共享 Default Profile 的非缓存部分。 */
    val siteDataBytes: Long,
    /** databases、filesDir、遗留 `Profile N`、cacheDir 其余部分。 */
    val appBytes: Long,
    val sites: List<SiteStorageStats>,
    /** 与 [siteDataBytes] 相同口径的 Default 站点数据总量（未扣可归属项）。 */
    val sharedSiteDataBytes: Long = 0,
    /**
     * Default 站点数据总量 − Σ(可归属磁盘项，即按站 IndexedDB)。
     * 始终 ≥ 0。UI 单独展示，不得塞进每一行。
     */
    val unattributableSharedBytes: Long = 0,
    val probeCapabilities: SiteProbeCapabilities = SiteProbeCapabilities(deleteForSite = false),
    val unmeasurableSiteIds: List<String>,
    /** WebView capability only; the product deliberately uses the shared Default profile today. */
    val multiProfile: Boolean,
    val scannedAt: Long,
    /**
     * 系统口径（StorageStatsManager）的 dataBytes + cacheBytes；
     * 查询失败或总量为 0 时为 null，UI 回退到遍历汇总值。系统值不按类别拆分，仅供头条总量。
     */
    val systemTotalBytes: Long? = null,
    /** 系统口径的 cacheBytes；遍历成功时不覆盖「可清理缓存」分段。 */
    val systemCacheBytes: Long? = null,
)

fun hostOfUrl(url: String): String? =
    runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }?.lowercase()

fun siteGroupKey(host: String): String = host.lowercase().removePrefix("www.")

fun siteKeyOf(appId: String, host: String, isLocal: Boolean): String =
    if (isLocal) "local:$appId" else siteGroupKey(host)

fun hostMatchesDeletedDomain(host: String, domain: String): Boolean {
    val h = host.lowercase()
    val d = domain.lowercase().trim().removePrefix(".")
    if (h.isBlank() || d.isBlank()) return false
    return h == d || h.endsWith(".$d")
}

internal fun Metric<*>.stateLabel(): String = when (this) {
    is Metric.Measured -> if (exact) "measured" else "approx"
    Metric.Absent -> "absent"
    Metric.Unavailable -> "unavailable"
}
