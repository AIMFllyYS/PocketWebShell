package com.webshell.core.webengine.storage

import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import com.webshell.core.model.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

sealed interface EraseSiteOutcome {
    data object Unsupported : EraseSiteOutcome
    data class Done(val deletedDomain: String, val cookiesRemaining: Int?) : EraseSiteOutcome
    data class Failed(val reason: String) : EraseSiteOutcome
}

fun interface WebViewFeatureGate {
    fun isSupported(name: String): Boolean
}

interface SiteSessionGateway {
    fun runningSharedSessions(): List<String>
    fun destroySessions(sessionIds: List<String>): List<String>
}

interface SiteBrowsingDataGateway {
    suspend fun deleteBrowsingDataForSite(siteOrUrl: String): String
    fun flushCookies()
}

fun interface RemainingCookieProbe {
    suspend fun count(siteOrUrl: String): Int?
}

/**
 * 按站清除：feature 不支持时不执行任何删除；支持时先 destroyAndForget 活动会话，
 * 再在主线程调用 [WebStorageCompat.deleteBrowsingDataForSite]。
 */
@Singleton
class SiteDataEraser @Inject constructor(
    clearer: CacheClearer,
    probe: SiteStorageProbe,
    storageStats: StorageStatsRepository,
) {
    private val sessions = SiteSessionGateway(
        running = { clearer.runningSharedSessions() },
        destroy = { clearer.destroySessions(it) },
    )
    private val features = WebViewFeatureGate { name ->
        runCatching { WebViewFeature.isFeatureSupported(name) }.getOrDefault(false)
    }
    private val browsingData = AndroidSiteBrowsingDataGateway()
    private val remainingCookies = RemainingCookieProbe { url ->
        when (val metric = probe.cookieEvidence(url)) {
            is Metric.Measured -> metric.value.count
            Metric.Absent -> 0
            Metric.Unavailable -> null
        }
    }
    private val invalidateStats: () -> Unit = { storageStats.invalidate() }

    suspend fun eraseSite(siteOrUrl: String): EraseSiteOutcome = eraseSiteInternal(
        siteOrUrl = siteOrUrl,
        features = features,
        sessions = sessions,
        browsingData = browsingData,
        remainingCookies = remainingCookies,
        invalidate = invalidateStats,
    )
}

internal fun SiteSessionGateway(
    running: () -> List<String>,
    destroy: (List<String>) -> List<String>,
): SiteSessionGateway = object : SiteSessionGateway {
    override fun runningSharedSessions(): List<String> = running()
    override fun destroySessions(sessionIds: List<String>): List<String> = destroy(sessionIds)
}

internal suspend fun eraseSiteInternal(
    siteOrUrl: String,
    features: WebViewFeatureGate,
    sessions: SiteSessionGateway,
    browsingData: SiteBrowsingDataGateway,
    remainingCookies: RemainingCookieProbe,
    invalidate: () -> Unit,
): EraseSiteOutcome {
    if (!features.isSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
        return EraseSiteOutcome.Unsupported
    }
    if (siteOrUrl.startsWith("local:", ignoreCase = true)) {
        return EraseSiteOutcome.Failed("local")
    }
    val host = hostOfUrl(siteOrUrl) ?: siteOrUrl
    return try {
        val running = sessions.runningSharedSessions()
        if (running.isNotEmpty()) {
            sessions.destroySessions(running)
        }
        val domain = browsingData.deleteBrowsingDataForSite(siteOrUrl)
        browsingData.flushCookies()
        val remaining = remainingCookies.count(siteOrUrl)
        invalidate()
        AppLog.log(TAG, "已按站清除: domain=$domain cookiesRemaining=${remaining ?: -1}")
        EraseSiteOutcome.Done(deletedDomain = domain, cookiesRemaining = remaining)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        AppLog.warn(TAG, "按站清除失败: $host ${e.javaClass.simpleName}")
        EraseSiteOutcome.Failed(e.javaClass.simpleName)
    }
}

internal class AndroidSiteBrowsingDataGateway : SiteBrowsingDataGateway {
    override suspend fun deleteBrowsingDataForSite(siteOrUrl: String): String =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { cont ->
                val domainBox = arrayOf<String?>(null)
                domainBox[0] = WebStorageCompat.deleteBrowsingDataForSite(
                    WebStorage.getInstance(),
                    siteOrUrl,
                ) {
                    if (cont.isActive) cont.resume(domainBox[0] ?: siteOrUrl)
                }
            }
        }

    override fun flushCookies() {
        runCatching { CookieManager.getInstance().flush() }
    }
}

private const val TAG = "storage"
