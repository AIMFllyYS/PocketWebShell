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
 * 存储统计仓库：扫描磁盘并把字节按互斥桶归类（见 [OverviewBuckets]）。
 * 结果带约 30s 内存缓存；[invalidate] 或 forceRefresh 强制重扫。
 * 所有磁盘遍历都在 [Dispatchers.IO]，单个目录不可读只影响对应站点的 measurable。
 */
@Singleton
class StorageStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private data class CachedScan(val appIds: List<String>, val overview: StorageOverview)

    private val lock = Any()
    private var cached: CachedScan? = null

    suspend fun scan(appIds: List<String>, forceRefresh: Boolean = false): StorageOverview =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            synchronized(lock) {
                val c = cached
                if (!forceRefresh && c != null && c.appIds == appIds &&
                    now - c.overview.scannedAt < CACHE_TTL_MS
                ) {
                    return@withContext c.overview
                }
            }
            val overview = doScan(appIds, now)
            synchronized(lock) { cached = CachedScan(appIds, overview) }
            overview
        }

    fun invalidate() = synchronized(lock) { cached = null }

    private fun doScan(appIds: List<String>, scannedAt: Long): StorageOverview {
        val dataDir = context.dataDir
        val webviewRoot = File(dataDir, "app_webview")
        val profilesRoot = File(webviewRoot, "profiles")

        // 系统口径（与系统设置一致）的权威总量；查询本应用无需权限，失败仅降级为 null。
        val systemStats = runCatching {
            context.getSystemService(StorageStatsManager::class.java)
                .queryStatsForPackage(StorageManager.UUID_DEFAULT, context.packageName, Process.myUserHandle())
        }.onFailure { e ->
            AppLog.warn(TAG, "StorageStatsManager 查询失败：${e.javaClass.simpleName} ${e.message}")
        }.getOrNull()
        val systemTotalBytes = systemStats?.dataBytes
        val systemCacheBytes = systemStats?.cacheBytes

        val multiProfile = runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        }.getOrDefault(false)

        // profiles 目录：不存在（从未创建/不支持多 Profile）→ 各站点按 0 字节可测；
        // 存在但不可列读 → 全部站点不可测。
        val profileNames: Set<String>? = if (!profilesRoot.exists()) {
            emptySet()
        } else {
            runCatching { profilesRoot.list()?.toSet() }.getOrNull()
        }

        val siteProfiles = LinkedHashMap<String, List<FileEntry>?>()
        if (profileNames == null) {
            for (id in appIds) siteProfiles[id] = null
            AppLog.warn(TAG, "profiles 目录不可读，全部站点标记为不可测")
        } else {
            for (id in appIds) {
                val dir = File(profilesRoot, id)
                if (dir.exists()) {
                    val files = walkFiles(dir)
                    if (files == null) AppLog.warn(TAG, "站点 Profile 目录不可读: $id")
                    siteProfiles[id] = files
                }
                // 目录不存在 → 不入表，按可测 0/0 处理（数据物理上在默认共享 Profile）
            }
        }

        val orphans = mutableListOf<List<FileEntry>>()
        profileNames?.forEach { name ->
            if (name !in appIds && File(profilesRoot, name).isDirectory) {
                val files = walkFiles(File(profilesRoot, name))
                if (files == null) AppLog.warn(TAG, "孤儿 Profile 目录不可读: $name")
                else orphans += files
            }
        }

        // 默认共享 Profile：缓存子目录 → 可清理；其余 → 应用数据。
        val defaultFiles = walkFiles(File(webviewRoot, "Default"))
        if (defaultFiles == null) AppLog.warn(TAG, "默认 Profile 目录不可读，按 0 字节计")
        val defaultCache = mutableListOf<FileEntry>()
        val defaultRest = mutableListOf<FileEntry>()
        defaultFiles?.forEach { e ->
            if (StorageClassifier.classifyProfileEntry(e.relativePath) == StorageCategory.CLEARABLE_CACHE) {
                defaultCache += e
            } else {
                defaultRest += e
            }
        }
        // 旧版 WebView 直接把缓存写在 app_webview 根部。
        for (legacy in CACHE_DIR_NAMES) {
            val files = walkFiles(File(webviewRoot, legacy))
            if (files != null) defaultCache += files
        }
        // app_webview 根部的其他条目（如 Safe Browsing、Variations）→ 应用数据。
        val knownTop = setOf("profiles", "Default") + CACHE_DIR_NAMES
        val rootChildren = if (webviewRoot.exists()) {
            runCatching { webviewRoot.listFiles() }.getOrNull()
        } else {
            emptyArray()
        }
        if (webviewRoot.exists() && rootChildren == null) {
            AppLog.warn(TAG, "app_webview 根目录不可读，其杂项按 0 字节计")
        }
        rootChildren.orEmpty().filter { it.name !in knownTop }.forEach { child ->
            if (child.isDirectory) {
                val files = walkFiles(child)
                if (files == null) AppLog.warn(TAG, "app_webview 子目录不可读: ${child.name}")
                else defaultRest += files
            } else {
                defaultRest += FileEntry(child.name, child.length())
            }
        }

        // 应用自身目录：databases + filesDir（含 icons/localapps/wallpaper/datastore）。
        val dbFiles = walkFiles(File(dataDir, "databases"))
        if (dbFiles == null) AppLog.warn(TAG, "应用目录不可读: databases")
        val filesDirFiles = walkFiles(context.filesDir)
        if (filesDirFiles == null) AppLog.warn(TAG, "应用目录不可读: ${context.filesDir.name}")
        val appDirs = dbFiles.orEmpty() + filesDirFiles.orEmpty()

        // cacheDir：image_cache → 可清理；其余（logs 等）→ 应用数据。
        val cacheFiles = walkFiles(context.cacheDir)
        if (cacheFiles == null) AppLog.warn(TAG, "cacheDir 不可读，按 0 字节计")
        val imageCache = mutableListOf<FileEntry>()
        val cacheDirRest = mutableListOf<FileEntry>()
        cacheFiles?.forEach { e ->
            if (e.relativePath.substringBefore('/') == IMAGE_CACHE_DIR) imageCache += e else cacheDirRest += e
        }

        val overview = computeOverview(
            buckets = OverviewBuckets(
                siteProfiles = siteProfiles,
                orphanProfiles = orphans,
                defaultCache = defaultCache,
                defaultRest = defaultRest,
                appDirs = appDirs,
                imageCache = imageCache,
                cacheDirRest = cacheDirRest,
            ),
            appIds = appIds,
            multiProfile = multiProfile,
            scannedAt = scannedAt,
            systemTotalBytes = systemTotalBytes,
            systemCacheBytes = systemCacheBytes,
        )

        // 诊断：逐桶记录各探测根目录的字节数/不可读，真机统计异常时可在应用内日志定位。
        val profilesDesc = when {
            !profilesRoot.exists() -> "不存在"
            profileNames == null -> "不可读"
            profileNames.isEmpty() -> "空"
            else -> profileNames.sorted().joinToString(",")
        }
        AppLog.log(
            TAG,
            "存储扫描明细: profiles根=[$profilesDesc] Default=${describe(defaultFiles)} " +
                "webview根子项=${rootChildren?.size?.toString() ?: "不可读"} " +
                "databases=${describe(dbFiles)} filesDir=${describe(filesDirFiles)} " +
                "cacheDir=${describe(cacheFiles)} 系统总量=${systemTotalBytes ?: -1}B " +
                "系统缓存=${systemCacheBytes ?: -1}B",
        )
        val walkedTotal = overview.clearableBytes + overview.siteDataBytes + overview.appBytes
        if (systemTotalBytes != null && systemTotalBytes > 0 && walkedTotal == 0L) {
            AppLog.warn(
                TAG,
                "磁盘遍历结果为 0，但系统报告应用数据 ${systemTotalBytes}B，本设备路径假设可能不匹配",
            )
        }
        AppLog.log(
            TAG,
            "存储扫描完成: 站点=${appIds.size} 可清理=${overview.clearableBytes}B " +
                "站点数据=${overview.siteDataBytes}B 应用=${overview.appBytes}B " +
                "不可测=${overview.unmeasurableSiteIds.size} multiProfile=$multiProfile",
        )
        return overview
    }

    private fun describe(files: List<FileEntry>?): String =
        files?.let { "${it.totalBytes()}B" } ?: "不可读"

    internal companion object {
        const val TAG = "storage"
        const val CACHE_TTL_MS = 30_000L
        const val IMAGE_CACHE_DIR = "image_cache"
        val CACHE_DIR_NAMES = listOf("Cache", "Code Cache", "GPUCache")
    }
}
