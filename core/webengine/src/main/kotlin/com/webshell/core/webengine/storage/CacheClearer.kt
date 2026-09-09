package com.webshell.core.webengine.storage

import android.content.Context
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
 * 缓存清理器：只删除各 Profile 的缓存子目录（Cache / Code Cache / GPUCache），
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
        liveSessions().filter { it.startsWith("browser-") || it.startsWith("direct-") }

    /**
     * 关闭会话：WebViewPool.destroy 保留返回栈快照（Profile 数据在磁盘），并注销保活。
     * 返回实际关闭的 id。
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
     * 删除 app_webview/profiles/<appId> 下的三个缓存子目录。
     * 调用方须先确认 [runningSessionsFor] 为空；仍有会话运行时直接失败。
     */
    suspend fun clearSiteCache(appId: String): ClearOutcome = withContext(Dispatchers.IO) {
        if (runningSessionsFor(appId).isNotEmpty()) {
            AppLog.warn(TAG, "跳过清理 $appId：会话正在运行")
            return@withContext ClearOutcome.Failed("会话正在运行")
        }
        clearSiteCacheInternal(appId)
    }

    /**
     * 清理全部站点的可清理缓存 + 共享可清理（默认 Profile 缓存目录 + Coil image_cache）。
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

        // 默认共享 Profile 的缓存目录（仅当没有浏览器/直接会话在使用时）。
        if (runningSharedSessions().isEmpty()) {
            val webviewRoot = File(context.dataDir, "app_webview")
            val sharedFreed = clearCacheDirsOf(File(webviewRoot, "Default"), "默认 Profile")
            val legacyFreed = clearCacheDirsOf(webviewRoot, "旧版根目录缓存")
            for (freed in listOf(sharedFreed, legacyFreed)) {
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
            AppLog.log(TAG, "清理 $appId：无 Profile 目录，无需清理")
            return ClearOutcome.Done(0L)
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

    private companion object {
        const val TAG = "storage"
        val CACHE_DIR_NAMES = listOf("Cache", "Code Cache", "GPUCache")
    }
}
