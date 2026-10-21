package com.webshell.core.webengine.storage

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.KeepAliveRegistry
import com.webshell.core.webengine.WebViewPool
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.resume

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
    /** 清理失败的 appId 列表。 */
    val failures: List<String>,
)

/**
 * Public WebView data operations. Resource-cache clearing preserves website data;
 * explicit website-data deletion is a separate operation with capability checks.
 * No Chromium-private directory deletion is used.
 */
@Singleton
class CacheClearer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 该站点当前存活的会话 id（WebView 池 + 保活登记）。为空才可安全清理磁盘。 */
    fun runningSessionsFor(appId: String): List<String> = liveSessions().filter { it == appId }

    /** 浏览器标签 / 直接打开的会话（默认共享 Profile 的使用者）。 */
    fun runningSharedSessions(): List<String> =
        liveSessions()

    /**
     * 关闭会话：WebViewPool.destroy 保留返回栈快照（Profile 数据在磁盘），并强制注销保活。
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
     * "退出登录/清除网站数据"绝不能让某个标签的返回栈快照，在下次打开时把
     * 已经失效的登录态页面悄悄恢复出来。
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
    suspend fun clearBrowserCache(): ClearOutcome = withContext(Dispatchers.Main.immediate) {
        if (runningSharedSessions().isNotEmpty()) return@withContext ClearOutcome.Failed("浏览器仍有活动会话")
        try {
            val probe = android.webkit.WebView(context)
            try { probe.clearCache(true) } finally { probe.destroy() }
            // The public API has no disk-deletion callback; do not invent a freed-byte count.
            ClearOutcome.Done(null)
        } catch (_: Exception) { ClearOutcome.Failed("无法清除 WebView 缓存") }
    }

    /**
     * 清除全部网站数据：feature 支持时优先 [WebStorageCompat.deleteBrowsingData]，
     * 否则回退 `deleteAllData` + `removeAllCookies`。调用前必须先 [destroySessions]。
     */
    suspend fun clearAllWebViewData(): ClearOutcome = withContext(Dispatchers.IO) {
        if (liveSessions().isNotEmpty()) return@withContext ClearOutcome.Failed("请先关闭所有浏览器会话")
        WebViewPool.clearSavedStates()
        var complete = true
        val cleared = runCatching {
            kotlinx.coroutines.withTimeout(10_000) { withContext(Dispatchers.Main.immediate) {
                val storage = WebStorage.getInstance()
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                    suspendCancellableCoroutine { continuation ->
                        WebStorageCompat.deleteBrowsingData(storage) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                } else {
                    complete = false
                    storage.deleteAllData()
                    suspendCancellableCoroutine { continuation ->
                        CookieManager.getInstance().removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
                    }
                }
            } }
        }.isSuccess
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (!cleared) return@withContext ClearOutcome.Failed("无法清除 WebView 数据")
        runCatching { CookieManager.getInstance().flush() }
        if (!complete) return@withContext ClearOutcome.Failed(context.getString(com.webshell.core.webengine.R.string.web_data_partial))
        WebViewPool.clearSavedStates()
        ClearOutcome.Done(null)
    }

    /** Native resource-cache clearing. ImageCacheRepository owns Coil's live caches. */
    suspend fun clearAllClearable(): ClearAllResult = when (val outcome = clearBrowserCache()) {
        is ClearOutcome.Done -> ClearAllResult(0, 0, null, emptyList())
        is ClearOutcome.Failed -> ClearAllResult(0, 1, null, listOf(outcome.reason))
    }

    fun multiProfileSupported(): Boolean = runCatching {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }.getOrDefault(false)

    private fun liveSessions(): List<String> =
        (WebViewPool.liveSessions() + KeepAliveRegistry.entries.map { it.sessionId }).distinct()

    private companion object {
        const val TAG = "storage"
    }
}
