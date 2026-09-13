package com.webshell.feature.me

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.LogDao
import com.webshell.core.data.LogRepository
import com.webshell.core.data.WebAppDao
import com.webshell.core.data.WebAppEntity
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.storage.CacheClearer
import com.webshell.core.webengine.storage.ClearOutcome
import com.webshell.core.webengine.storage.EraseSiteOutcome
import com.webshell.core.webengine.storage.Metric
import com.webshell.core.webengine.storage.SiteDataEraser
import com.webshell.core.webengine.storage.SiteProbeCapabilities
import com.webshell.core.webengine.storage.SiteScanInput
import com.webshell.core.webengine.storage.StorageOverview
import com.webshell.core.webengine.storage.StorageStatsRepository
import com.webshell.core.webengine.storage.hostMatchesDeletedDomain
import com.webshell.core.webengine.storage.hostOfUrl
import com.webshell.core.webengine.storage.siteKeyOf
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StorageSortMode { SIZE, NAME }

data class StorageUiState(
    val scanning: Boolean = true,
    val overview: StorageOverview? = null,
    val scanFailed: Boolean = false,
    val query: String = "",
    val sortMode: StorageSortMode = StorageSortMode.SIZE,
    val clearingAll: Boolean = false,
    val erasingSite: Boolean = false,
    /** 上次全量清理未完成的站点数；>0 时概览区展示重试提示。 */
    val clearAllFailedSites: Int = 0,
    val logBytes: Long = 0L,
    val probeCapabilities: SiteProbeCapabilities? = null,
)

/** 一次性 Toast；arg 供带占位符的字符串使用（如已释放大小）。 */
data class StorageToast(@StringRes val resId: Int, val arg: String? = null)

/** 全量清理确认弹窗的文案数据：将关闭的会话数与预计释放字节。 */
data class ClearAllPreview(
    val affectedSites: Int,
    val runningSessions: Int,
    val estimatedBytes: Long,
)

data class EraseSitePreview(
    val siteDomain: String,
    val affectedAppIds: List<String>,
    val affectedTitles: List<String>,
    val cookieCount: Int?,
    val willSignOut: Boolean,
)

/**
 * 存储管理页状态中枢：磁盘统计来自 [StorageStatsRepository]（带 ~30s 缓存），
 * 站点清单来自 Room；站点集合或 URL 变化时自动重扫。
 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storageStats: StorageStatsRepository,
    private val clearer: CacheClearer,
    private val siteDataEraser: SiteDataEraser,
    private val webAppDao: WebAppDao,
    private val logRepository: LogRepository,
    private val logDao: LogDao,
) : ViewModel() {

    val apps: StateFlow<List<WebAppEntity>> = webAppDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    private val _toasts = MutableSharedFlow<StorageToast>(extraBufferCapacity = 1)
    val toasts: SharedFlow<StorageToast> = _toasts.asSharedFlow()

    private var scanJob: Job? = null
    private var scannedSites: List<SiteScanInput>? = null

    init {
        viewModelScope.launch {
            apps.collect { list ->
                val sites = list.map { it.toScanInput() }
                if (sites != scannedSites) scan(forceRefresh = false)
            }
        }
    }

    fun scan(forceRefresh: Boolean) {
        val sites = apps.value.map { it.toScanInput() }
        scannedSites = sites
        scanJob?.cancel()
        _state.value = _state.value.copy(scanning = true, scanFailed = false)
        scanJob = viewModelScope.launch {
            try {
                val overview = storageStats.scan(sites, forceRefresh)
                val logBytes = runCatching { logDao.approxBytes() }.getOrDefault(0L)
                _state.value = _state.value.copy(
                    scanning = false,
                    overview = overview,
                    logBytes = logBytes,
                    probeCapabilities = overview.probeCapabilities,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLog.error(TAG, "存储统计失败：${e.message}")
                _state.value = _state.value.copy(scanning = false, scanFailed = true)
                _toasts.tryEmit(StorageToast(R.string.me_storage_scan_failed))
            }
        }
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun setSortMode(mode: StorageSortMode) {
        _state.value = _state.value.copy(sortMode = mode)
    }

    fun runningSessionsFor(appId: String): List<String> = clearer.runningSessionsFor(appId)

    fun canEraseSite(): Boolean = _state.value.effectiveCapabilities().deleteForSite

    fun eraseSitePreview(appId: String): EraseSitePreview? {
        val app = apps.value.firstOrNull { it.id == appId } ?: return null
        if (app.isLocal) return null
        val host = hostOfUrl(app.url) ?: return null
        val siteKey = siteKeyOf(app.id, host, isLocal = false)
        val siblings = apps.value.filter { other ->
            !other.isLocal && hostOfUrl(other.url)?.let { siteKeyOf(other.id, it, false) } == siteKey
        }
        val stats = _state.value.overview?.sites?.firstOrNull { it.appId == appId }
        val cookieCount = (stats?.cookie as? Metric.Measured)?.value?.count
        return EraseSitePreview(
            siteDomain = host,
            affectedAppIds = siblings.map { it.id },
            affectedTitles = siblings.map { it.title },
            cookieCount = cookieCount,
            willSignOut = stats?.signedIn == true || (cookieCount ?: 0) > 0,
        )
    }

    /** 预计释放用 overview 的显式 clearableBytes，不再用站点行之和做启发式。 */
    fun clearAllPreview(): ClearAllPreview {
        val overview = _state.value.overview
        val sharedRunning = clearer.runningSharedSessions()
        return ClearAllPreview(
            affectedSites = if ((overview?.clearableBytes ?: 0L) > 0L) 1 else 0,
            runningSessions = sharedRunning.size,
            estimatedBytes = (overview?.clearableBytes ?: 0L) + _state.value.logBytes,
        )
    }

    /**
     * 删除共享 Cookie / WebStorage 并清空会话快照。会退出所有网站登录。
     * 调用前必须先经确认弹窗；此处会先彻底销毁全部活动会话。
     */
    fun clearWebsiteData() {
        val s = _state.value
        if (s.clearingAll || s.erasingSite) return
        _state.value = s.copy(clearingAll = true, clearAllFailedSites = 0)
        viewModelScope.launch {
            try {
                clearer.destroySessions(clearer.runningSharedSessions())
                when (val outcome = clearer.clearAllWebViewData()) {
                    is ClearOutcome.Done -> {
                        clearer.clearAllClearable()
                        storageStats.invalidate()
                        scan(forceRefresh = true)
                        _toasts.tryEmit(StorageToast(R.string.me_storage_data_cleared))
                    }
                    is ClearOutcome.Failed -> {
                        _toasts.tryEmit(StorageToast(R.string.me_storage_data_clear_failed, outcome.reason))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLog.error(TAG, "清除网站数据失败")
                _toasts.tryEmit(StorageToast(R.string.me_storage_data_clear_failed, "无法清除 WebView 数据"))
            } finally {
                _state.value = _state.value.copy(clearingAll = false)
            }
        }
    }

    /** 全量清理：关闭共享会话 → 清 Default/根缓存 + HTTP 缓存 + 图片缓存 → 重扫。 */
    fun clearAll() {
        val s = _state.value
        if (s.clearingAll || s.erasingSite) return
        if ((s.overview?.clearableBytes ?: 0L) + s.logBytes <= 0L) return
        _state.value = s.copy(clearingAll = true, clearAllFailedSites = 0)
        viewModelScope.launch {
            try {
                val running = clearer.runningSharedSessions()
                clearer.clearLiveWebViewCaches()
                clearer.closeSessions(running)
                val result = clearer.clearAllClearable()
                AppLog.clear()
                runCatching { logRepository.clear() }
                storageStats.invalidate()
                scan(forceRefresh = true)
                val freed = result.freedBytes
                _toasts.tryEmit(
                    if (freed != null) {
                        StorageToast(R.string.me_storage_cleared_size, formatStorageBytes(freed))
                    } else {
                        StorageToast(R.string.me_storage_cleared)
                    },
                )
                if (result.failedSites > 0) {
                    _state.value = _state.value.copy(clearAllFailedSites = result.failedSites)
                    _toasts.tryEmit(StorageToast(R.string.me_storage_clear_failed))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLog.error(TAG, "全量清理失败")
                _toasts.tryEmit(StorageToast(R.string.me_storage_clear_failed))
            } finally {
                _state.value = _state.value.copy(clearingAll = false)
            }
        }
    }

    fun eraseSite(appId: String) {
        val s = _state.value
        if (s.clearingAll || s.erasingSite) return
        val app = apps.value.firstOrNull { it.id == appId } ?: return
        if (app.isLocal) return
        _state.value = s.copy(erasingSite = true)
        viewModelScope.launch {
            try {
                when (val outcome = siteDataEraser.eraseSite(app.url)) {
                    EraseSiteOutcome.Unsupported -> {
                        _toasts.tryEmit(StorageToast(R.string.me_storage_erase_site_unsupported))
                    }
                    is EraseSiteOutcome.Done -> {
                        val domain = outcome.deletedDomain
                        AppLog.log(
                            TAG,
                            "按站清除完成: domain=$domain affected=${
                                apps.value.count { other ->
                                    !other.isLocal && hostOfUrl(other.url)?.let { host ->
                                        hostMatchesDeletedDomain(host, domain)
                                    } == true
                                }
                            }",
                        )
                        storageStats.invalidate()
                        scan(forceRefresh = true)
                        _toasts.tryEmit(StorageToast(R.string.me_storage_erase_site_done, domain))
                    }
                    is EraseSiteOutcome.Failed -> {
                        _toasts.tryEmit(StorageToast(R.string.me_storage_erase_site_failed, outcome.reason))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLog.error(TAG, "按站清除失败")
                _toasts.tryEmit(StorageToast(R.string.me_storage_erase_site_failed, e.javaClass.simpleName))
            } finally {
                _state.value = _state.value.copy(erasingSite = false)
            }
        }
    }

    private companion object {
        const val TAG = "storage"
    }
}

internal fun StorageUiState.effectiveCapabilities(): SiteProbeCapabilities =
    probeCapabilities ?: overview?.probeCapabilities ?: SiteProbeCapabilities(deleteForSite = false)

internal fun WebAppEntity.toScanInput(): SiteScanInput = SiteScanInput(
    appId = id,
    url = url,
    host = hostOfUrl(url).orEmpty(),
    isLocal = isLocal,
)

/** B 取整数，KB 起保留一位小数（12.4 MB 风格）。 */
internal fun formatStorageBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.1f GB", mb / 1024.0)
}
