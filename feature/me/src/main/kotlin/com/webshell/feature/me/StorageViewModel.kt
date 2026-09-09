package com.webshell.feature.me

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.WebAppDao
import com.webshell.core.data.WebAppEntity
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.storage.CacheClearer
import com.webshell.core.webengine.storage.ClearOutcome
import com.webshell.core.webengine.storage.StorageOverview
import com.webshell.core.webengine.storage.StorageStatsRepository
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

/** 单站点清理失败：详情页据此展示原因与重试提示。 */
data class SiteClearFailure(val appId: String, val reason: String)

data class StorageUiState(
    val scanning: Boolean = true,
    val overview: StorageOverview? = null,
    val scanFailed: Boolean = false,
    val query: String = "",
    val sortMode: StorageSortMode = StorageSortMode.SIZE,
    /** 正在清理缓存的站点 id；非空时禁止并发起其他清理。 */
    val clearingSiteId: String? = null,
    val clearingAll: Boolean = false,
    val siteClearFailure: SiteClearFailure? = null,
    /** 上次全量清理未完成的站点数；>0 时概览区展示重试提示。 */
    val clearAllFailedSites: Int = 0,
)

/** 一次性 Toast；arg 供带占位符的字符串使用（如已释放大小）。 */
data class StorageToast(@StringRes val resId: Int, val arg: String? = null)

/** 全量清理确认弹窗的文案数据：受影响站点数、将关闭的会话数与预计释放字节。 */
data class ClearAllPreview(
    val affectedSites: Int,
    val runningSessions: Int,
    val estimatedBytes: Long,
)

/**
 * 存储管理页状态中枢：磁盘统计来自 [StorageStatsRepository]（带 ~30s 缓存），
 * 站点清单来自 Room；站点集合变化时自动重扫。所有磁盘操作都在仓库层 IO 线程。
 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storageStats: StorageStatsRepository,
    private val clearer: CacheClearer,
    private val webAppDao: WebAppDao,
) : ViewModel() {

    val apps: StateFlow<List<WebAppEntity>> = webAppDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    private val _toasts = MutableSharedFlow<StorageToast>(extraBufferCapacity = 1)
    val toasts: SharedFlow<StorageToast> = _toasts.asSharedFlow()

    private var scanJob: Job? = null
    private var scannedIds: List<String>? = null

    init {
        viewModelScope.launch {
            apps.collect { list ->
                val ids = list.map { it.id }
                if (ids != scannedIds) scan(forceRefresh = false)
            }
        }
    }

    fun scan(forceRefresh: Boolean) {
        val ids = apps.value.map { it.id }
        scannedIds = ids
        scanJob?.cancel()
        _state.value = _state.value.copy(scanning = true, scanFailed = false)
        scanJob = viewModelScope.launch {
            try {
                val overview = storageStats.scan(ids, forceRefresh)
                _state.value = _state.value.copy(scanning = false, overview = overview)
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

    /** 受影响站点 = 可测且缓存非空；会话数含共享 Profile 的浏览器/直接会话。 */
    fun clearAllPreview(): ClearAllPreview {
        val overview = _state.value.overview
        val affected = overview?.sites.orEmpty().filter { it.measurable && it.clearableBytes > 0 }
        val running = affected.flatMap { clearer.runningSessionsFor(it.appId) } + clearer.runningSharedSessions()
        return ClearAllPreview(
            affectedSites = affected.size,
            runningSessions = running.distinct().size,
            estimatedBytes = overview?.clearableBytes ?: 0L,
        )
    }

    /** 清理单站点缓存；调用前如仍有会话运行，调用方须已获用户确认（此处先关会话再清盘）。 */
    fun clearSite(appId: String) {
        val s = _state.value
        if (s.clearingSiteId != null || s.clearingAll) return
        _state.value = s.copy(clearingSiteId = appId, siteClearFailure = null)
        viewModelScope.launch {
            try {
                clearer.closeSessions(clearer.runningSessionsFor(appId))
                when (val outcome = clearer.clearSiteCache(appId)) {
                    is ClearOutcome.Done -> {
                        storageStats.invalidate()
                        scan(forceRefresh = true)
                        val freed = outcome.freedBytes
                        _toasts.tryEmit(
                            if (freed != null) {
                                StorageToast(R.string.me_storage_cleared_size, formatStorageBytes(freed))
                            } else {
                                StorageToast(R.string.me_storage_cleared)
                            },
                        )
                    }
                    is ClearOutcome.Failed -> {
                        _state.value = _state.value.copy(siteClearFailure = SiteClearFailure(appId, outcome.reason))
                        _toasts.tryEmit(StorageToast(R.string.me_storage_clear_failed))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLog.error(TAG, "清理站点缓存失败：${e.message}")
                _state.value = _state.value.copy(
                    siteClearFailure = SiteClearFailure(appId, e.message ?: "unknown"),
                )
                _toasts.tryEmit(StorageToast(R.string.me_storage_clear_failed))
            } finally {
                _state.value = _state.value.copy(clearingSiteId = null)
            }
        }
    }

    /** 全量清理：关闭受影响站点与共享会话 → 清各站点缓存 + 共享缓存 + 图片缓存 → 重扫。 */
    fun clearAll() {
        val s = _state.value
        if (s.clearingAll || s.clearingSiteId != null) return
        val affected = s.overview?.sites.orEmpty().filter { it.measurable && it.clearableBytes > 0 }
        if (affected.isEmpty()) return
        _state.value = s.copy(clearingAll = true, clearAllFailedSites = 0)
        viewModelScope.launch {
            try {
                val running = affected.flatMap { clearer.runningSessionsFor(it.appId) } +
                    clearer.runningSharedSessions()
                clearer.closeSessions(running.distinct())
                val result = clearer.clearAllClearable(affected.map { it.appId })
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
                AppLog.error(TAG, "全量清理失败：${e.message}")
                _toasts.tryEmit(StorageToast(R.string.me_storage_clear_failed))
            } finally {
                _state.value = _state.value.copy(clearingAll = false)
            }
        }
    }

    private companion object {
        const val TAG = "storage"
    }
}

/** B 取整数，KB 起保留一位小数（12.4 MB 风格）。 */
internal fun formatStorageBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.1f GB", mb / 1024.0)
}
