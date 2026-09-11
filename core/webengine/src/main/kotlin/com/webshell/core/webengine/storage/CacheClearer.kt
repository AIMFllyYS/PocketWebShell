package com.webshell.core.webengine.storage

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.WebViewFeature
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.KeepAliveRegistry
import com.webshell.core.webengine.WebViewPool
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ClearOutcome {
    /** freedBytes = 清理前后实测差值；测量不可靠时为 null（UI 此时只提示「缓存清理完成」不带大小）。 */
    data class Done(val freedBytes: Long?) : ClearOutcome

    data class Failed(val reason: String) : ClearOutcome
}

data class ClearAllResult(
    val clearedSites: Int,
    val failedSites: Int,
    /** 任一环节测量不可靠时为 null。 */
    val freedBytes: Long?,
    /** 清理失败（含因会话运行被跳过）的 appId 列表。 */
    val failures: List<String>,
)

/**
 * 缓存清理器：只删除共享 Default（以及旧版遗留 Profile）的缓存子目录
 * （Cache / Code Cache / GPUCache / Dawn / Shader）和 `cache/WebView` HTTP 缓存，
 * 绝不触碰 Cookies、Local Storage、IndexedDB、Service Worker 等站点数据。
 */
@Singleton
class CacheClearer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 该站点当前存活的会话 id（WebView 池 + 保活登记）。为空才可安全清理磁盘。 */
    fun runningSessionsFor(appId: String): List<String> = liveSessions().filter { it == appId }

    /** 浏览器标签 / 直接打开的会话（默认共享 Profile 的使用者）。 */
    fun runningSharedSessions(): List<String> =
        // All current sessions deliberately use the Default profile. There is
        // no safe site-level cache boundary to infer from a session id.
        liveSessions()

    /**
     * 关闭会话：WebViewPool.destroy 保留返回栈快照（Profile 数据在磁盘），并注销保活。
     * 返回实际关闭的 id。用于"清缓存"一类操作——缓存清完后无缝续用同一标签。
     *
     * 注意：若 [KeepAliveRegistry] 因此变空，停止前台服务通知是调用方（UI）的职责。
     */
    fun closeSessions(sessionIds: List<String>): List<String> {
        val closed = mutableListOf<String>()
        for (id in sessionIds) {
            val inPool = WebViewPool.get(id) != null
            val inKeepAlive = KeepAliveRegistry.isAlive(id)
            runCatching { WebViewPool.destroy(id) }
                .onFailure { AppLog.warn(TAG, "销毁会话失败: $id") }
            runCatching { KeepAliveRegistry.unregister(id) }
            if (inPool || inKeepAlive) closed += id
        }
        AppLog.log(TAG, "关闭会话 ${closed.size}/${sessionIds.size}")
        return closed
    }

    /**
     * "退出登录"专用：销毁会话且不留快照（并清除该会话已有的快照）。
     * [closeSessions] 保留返回栈快照是为了让"清缓存"后无缝续用；但一次显式的
     * "退出登录/清除全部网站数据"绝不能让某个标签的返回栈快照，在下次打开时把
     * 已经失效的登录态页面（甚至其 back-forward 缓存渲染结果）悄悄恢复出来。
     */
    fun destroySessions(sessionIds: List<String>): List<String> {
        val closed = mutableListOf<String>()
        for (id in sessionIds) {
            val inPool = WebViewPool.get(id) != null
            val inKeepAlive = KeepAliveRegistry.isAlive(id)
            runCatching { WebViewPool.destroyAndForget(id) }
                .onFailure { AppLog.warn(TAG, "销毁会话失败: $id") }
            runCatching { KeepAliveRegistry.unregister(id) }
            if (inPool || inKeepAlive) closed += id
        }
        AppLog.log(TAG, "退出登录关闭会话 ${closed.size}/${sessionIds.size}")
        return closed
    }

    /** 在仍存活的 WebView 上调用平台 clearCache；必须在主线程、销毁会话之前。 */
    fun clearLiveWebViewCaches() {
        val ids = liveSessions()
        for (id in ids) {
            runCatching { WebViewPool.get(id)?.webView?.clearCache(true) }
                .onFailure { AppLog.warn(TAG, "WebView.clearCache 失败: $id") }
        }
        if (ids.isNotEmpty()) {
            AppLog.log(TAG, "已对 ${ids.size} 个活动 WebView 调用 clearCache")
        }
    }

    /** Clear only re-creatable browser cache. Cookies and LocalStorage remain intact. */
    suspend fun clearBrowserCache(): ClearOutcome = withContext(Dispatchers.IO) {
        if (runningSharedSessions().isNotEmpty()) return@withContext ClearOutcome.Failed("浏览器仍有活动会话")
        val root = File(context.dataDir, "app_webview")
        val before = measureAllCacheBytes(root)
        val failure = CACHE_DIR_NAMES.filter { name ->
            val candidates = listOf(File(root, name), File(root, "Default/$name"))
            candidates.any { it.exists() && !runCatching { it.deleteRecursively() }.getOrDefault(false) }
        }
        if (failure.isNotEmpty()) return@withContext ClearOutcome.Failed(failure.joinToString())
        val httpFreed = clearWebViewHttpCache()
        val after = measureAllCacheBytes(root)
        val profileFreed = if (before != null && after != null) (before - after).coerceAtLeast(0) else null
        val freed = if (profileFreed != null && httpFreed != null) profileFreed + httpFreed else null
        ClearOutcome.Done(freed)
    }

    /**
     * Explicit destructive operation: removes shared cookies/site storage and
     * logs the user out of every site, everywhere — the product has exactly
     * one login state, so there is no such thing as a partial/per-site sign
     * out. Callers must close every live session (with [destroySessions], not
     * [closeSessions]) before calling this.
     */
    suspend fun clearAllWebViewData(): ClearOutcome = withContext(Dispatchers.IO) {
        if (liveSessions().isNotEmpty()) return@withContext ClearOutcome.Failed("请先关闭所有浏览器会话")
        val cleared = runCatching {
            withContext(Dispatchers.Main.immediate) {
                // These are the platform-sanctioned "sign out of every site"
                // APIs; they are the guaranteed part of this contract.
                WebStorage.getInstance().deleteAllData()
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                    CookieManager.getInstance().removeAllCookies { continuation.resume(Unit) { _, _, _ -> } }
                }
            }
        }.isSuccess
        if (!cleared) return@withContext ClearOutcome.Failed("无法清除 WebView 数据")
        // A cookie flush is a disk write; it does not need the platform's
        // main-thread requirement for CookieManager mutation calls above.
        runCatching { CookieManager.getInstance().flush() }
        deleteResidualSiteDataDirs()
        WebViewPool.clearSavedStates()
        ClearOutcome.Done(null)
    }

    /**
     * Defense in depth after the platform sign-out APIs above: explicitly
     * delete the on-disk site-data directories (IndexedDB, Service Worker
     * registrations, Cache Storage, Local/Session Storage, …) from the shared
     * Default profile and any legacy per-site remnant. Best-effort only —
     * failures are logged, not fatal; [WebStorage.deleteAllData] and
     * [CookieManager.removeAllCookies] above are the guaranteed contract.
     */
    private fun deleteResidualSiteDataDirs() {
        val webviewRoot = File(context.dataDir, "app_webview")
        val profileDirs = listOf(File(webviewRoot, "Default")) +
            File(webviewRoot, "profiles").listFiles()?.filter { it.isDirectory }.orEmpty()
        for (dir in profileDirs) {
            for (name in SITE_DATA_DIR_NAMES) {
                val target = File(dir, name)
                if (target.exists()) {
                    runCatching { target.deleteRecursively() }
                        .onFailure { AppLog.warn(TAG, "站点数据目录删除失败: ${dir.name}/$name") }
                }
            }
        }
    }

    /**
     * 清理全部站点的可清理缓存 + 共享可清理
     * （默认 Profile 缓存目录 + `cache/WebView` HTTP 缓存 + Coil image_cache）。
     * 仍有会话运行的站点会被跳过并计入失败。
     */
    suspend fun clearAllClearable(siteIds: List<String>): ClearAllResult = withContext(Dispatchers.IO) {
        var cleared = 0
        var failed = 0
        val failures = mutableListOf<String>()
        var freedSum = 0L
        var reliable = true

        for (id in siteIds) {
            val running = runningSessionsFor(id)
            if (running.isNotEmpty()) {
                failed++
                failures += id
                AppLog.warn(TAG, "跳过清理 $id：${running.size} 个会话正在运行")
                continue
            }
            if (!File(context.dataDir, "app_webview/profiles/$id").exists()) {
                // Current sessions all use Default; the shared cache is handled below.
                continue
            }
            when (val outcome = clearSiteCacheInternal(id)) {
                is ClearOutcome.Done -> {
                    cleared++
                    val freed = outcome.freedBytes
                    if (freed == null) reliable = false else freedSum += freed
                }
                is ClearOutcome.Failed -> {
                    failed++
                    failures += id
                    AppLog.warn(TAG, "清理 $id 失败: ${outcome.reason}")
                }
            }
        }

        // 默认共享 Profile 的缓存目录 + HTTP 缓存（仅当没有浏览器/直接会话在使用时）。
        if (runningSharedSessions().isEmpty()) {
            val webviewRoot = File(context.dataDir, "app_webview")
            val sharedFreed = clearCacheDirsOf(File(webviewRoot, "Default"), "默认 Profile")
            val legacyFreed = clearCacheDirsOf(webviewRoot, "旧版根目录缓存")
            val httpFreed = clearWebViewHttpCache()
            for (freed in listOf(sharedFreed, legacyFreed, httpFreed)) {
                if (freed == null) reliable = false else freedSum += freed
            }
        } else {
            AppLog.log(TAG, "默认 Profile 有共享会话运行，跳过其缓存清理")
        }

        // Coil 图片缓存始终可清（内存缓存由 Coil 自行管理）。
        val imageCacheFreed = deleteDirMeasured(File(context.cacheDir, "image_cache"), "图片缓存")
        if (imageCacheFreed == null) reliable = false else freedSum += imageCacheFreed

        val result = ClearAllResult(
            clearedSites = cleared,
            failedSites = failed,
            freedBytes = if (reliable) freedSum else null,
            failures = failures,
        )
        AppLog.log(
            TAG,
            "全量缓存清理完成: 成功=$cleared 失败=$failed 释放=${result.freedBytes ?: -1}B",
        )
        result
    }

    fun multiProfileSupported(): Boolean = runCatching {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }.getOrDefault(false)

    private fun liveSessions(): List<String> =
        (WebViewPool.liveSessions() + KeepAliveRegistry.entries.map { it.sessionId }).distinct()

    /** 调用方须确保该站点无运行会话。 */
    private fun clearSiteCacheInternal(appId: String): ClearOutcome {
        val profileDir = File(context.dataDir, "app_webview/profiles/$appId")
        if (!profileDir.exists()) {
            AppLog.log(TAG, "清理 $appId：当前为共享默认 Profile，拒绝伪装成单站点清理")
            return ClearOutcome.Failed("当前使用共享默认 Profile")
        }
        val before = measureCacheBytes(profileDir)
            ?: return ClearOutcome.Failed("无法读取缓存目录")
        val failures = mutableListOf<String>()
        for (name in CACHE_DIR_NAMES) {
            val dir = File(profileDir, name)
            if (dir.exists()) {
                runCatching { require(dir.deleteRecursively()) { "deleteRecursively 返回 false" } }
                    .onFailure { failures += name }
            }
        }
        if (failures.isNotEmpty()) {
            return ClearOutcome.Failed("缓存目录删除失败: ${failures.joinToString()}")
        }
        val after = measureCacheBytes(profileDir)
        val freed = if (after != null) (before - after).coerceAtLeast(0) else null
        AppLog.log(TAG, "清理站点缓存 $appId: 释放 ${freed ?: -1}B")
        return ClearOutcome.Done(freed)
    }

    /** 删除 [root] 下的三个缓存子目录（若存在），返回实测释放字节；测量不可靠返回 null。失败仅记日志。 */
    private fun clearCacheDirsOf(root: File, label: String): Long? {
        if (!root.exists()) return 0L
        val before = measureCacheBytes(root)
        for (name in CACHE_DIR_NAMES) {
            val dir = File(root, name)
            if (dir.exists()) {
                runCatching { require(dir.deleteRecursively()) { "deleteRecursively 返回 false" } }
                    .onFailure { AppLog.warn(TAG, "$label 缓存目录删除失败: $name") }
            }
        }
        val after = measureCacheBytes(root)
        val freed = if (before != null && after != null) (before - after).coerceAtLeast(0) else null
        AppLog.log(TAG, "清理$label: 释放 ${freed ?: -1}B")
        return freed
    }

    /** 删除整个目录并实测释放字节；目录不存在返回 0，测量不可靠返回 null。 */
    private fun deleteDirMeasured(dir: File, label: String): Long? {
        if (!dir.exists()) return 0L
        val before = walkFiles(dir)?.totalBytes()
        runCatching { require(dir.deleteRecursively()) { "deleteRecursively 返回 false" } }
            .onFailure { AppLog.warn(TAG, "$label 删除失败") }
        val after = walkFiles(dir)?.totalBytes()
        val freed = if (before != null && after != null) (before - after).coerceAtLeast(0) else null
        AppLog.log(TAG, "清理$label: 释放 ${freed ?: -1}B")
        return freed
    }

    /** 三个缓存子目录的总字节；任一存在但不可读 → null。 */
    private fun measureCacheBytes(profileDir: File): Long? {
        var total = 0L
        for (name in CACHE_DIR_NAMES) {
            val dir = File(profileDir, name)
            if (dir.exists()) {
                val files = walkFiles(dir) ?: return null
                total += files.totalBytes()
            }
        }
        return total
    }

    private fun measureAllCacheBytes(webviewRoot: File): Long? {
        val root = measureCacheBytes(webviewRoot) ?: return null
        val default = measureCacheBytes(File(webviewRoot, "Default")) ?: return null
        return root + default
    }

    /** 删除 cacheDir 下 Chromium HTTP 缓存整树（`WebView/Default/HTTP Cache` 等）。 */
    private fun clearWebViewHttpCache(): Long? {
        var freed = 0L
        var reliable = true
        for (name in StorageStatsRepository.WEBVIEW_CACHE_DIR_NAMES) {
            val result = deleteDirMeasured(File(context.cacheDir, name), "HTTP 缓存 $name")
            if (result == null) reliable = false else freed += result
        }
        return if (reliable) freed else null
    }

    private companion object {
        const val TAG = "storage"
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

        /**
         * 站点数据目录（非缓存）：这些才是"这个网站还记得你"的真正来源
         * （IndexedDB / Service Worker 注册 / Cache Storage / Local·Session Storage /
         * 旧版 WebSQL 等）。缓存目录由 [CACHE_DIR_NAMES] 单独处理——那些始终可安全删除，
         * 不属于"退出登录"的范畴。
         */
        val SITE_DATA_DIR_NAMES = listOf(
            "IndexedDB", "Local Storage", "Session Storage", "Shared Storage",
            "Service Worker", "Storage", "Network", "Cookies",
            "databases", "blob_storage", "File System", "Platform Notifications",
            "VideoDecodeStats", "Site Characteristics Database", "shared_proto_db",
        )
    }
}
