package com.webshell.core.webengine.storage

/** 存储归类：可清理缓存 / 站点数据 / 应用自身数据。 */
enum class StorageCategory { CLEARABLE_CACHE, SITE_DATA, APP_DATA }

/**
 * 纯分类器：输入相对某个 WebView Profile 目录的路径（'/' 分隔，如 "Cache/index.txt"），
 * 按顶层段判断归属。Profile 内只有缓存与站点数据两类；APP_DATA 用于概览层面的应用目录。
 */
object StorageClassifier {

    private val CACHE_TOP_LEVEL = setOf("Cache", "Code Cache", "GPUCache")

    fun classifyProfileEntry(relativePath: String): StorageCategory {
        val top = relativePath.substringBefore('/')
        return if (top in CACHE_TOP_LEVEL) StorageCategory.CLEARABLE_CACHE else StorageCategory.SITE_DATA
    }
}

data class SiteStorageStats(
    val appId: String,
    /** false → UI 展示「暂无法统计」。 */
    val measurable: Boolean,
    /** 仅 measurable 时有效；measurable 但无缓存时为 0。 */
    val clearableBytes: Long,
    /** Profile 总量减去可清理部分。 */
    val siteDataBytes: Long,
) {
    val totalBytes: Long get() = clearableBytes + siteDataBytes
}

data class StorageOverview(
    /** Σ 站点可清理 + 共享可清理（默认 Profile 缓存目录 + Coil image_cache）。 */
    val clearableBytes: Long,
    /** Σ 各站点站点数据。 */
    val siteDataBytes: Long,
    /** databases、filesDir（icons/localapps/wallpaper）、datastore、默认 Profile 非缓存部分、孤儿 Profile、cacheDir 其余部分。 */
    val appBytes: Long,
    val sites: List<SiteStorageStats>,
    val unmeasurableSiteIds: List<String>,
    /** WebView capability only; the product deliberately uses the shared Default profile today. */
    val multiProfile: Boolean,
    val scannedAt: Long,
    /**
     * 系统口径（StorageStatsManager，与系统设置一致）的应用数据总量（dataBytes）；
     * 查询失败为 null，此时 UI 回退到遍历汇总值。系统值不按类别拆分，仅供总量展示。
     */
    val systemTotalBytes: Long? = null,
    /** 系统口径的 cacheBytes；WebView 缓存常被系统低估，不用于「可清理缓存」展示。 */
    val systemCacheBytes: Long? = null,
)
