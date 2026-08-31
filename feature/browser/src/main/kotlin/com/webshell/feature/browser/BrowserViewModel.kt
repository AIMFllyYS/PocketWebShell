package com.webshell.feature.browser

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.BookmarkDao
import com.webshell.core.data.BookmarkEntity
import com.webshell.core.data.HistoryDao
import com.webshell.core.data.HistoryEntity
import com.webshell.core.webengine.ShellListener
import com.webshell.core.webengine.WebViewPool
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 浏览器标签页的 UI 状态（缩略图仅内存持有，进程恢复后重绘） */
data class BrowserTab(
    val tabId: String,
    val title: String,
    val url: String,
    val thumbnail: Bitmap? = null,
    /** 加载进度 0..100（per-tab，切 tab 不串台） */
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

/** 页面内查找状态 */
data class FindState(
    val visible: Boolean = false,
    val query: String = "",
    val active: Int = 0,
    val total: Int = 0,
)

/**
 * 多标签浏览器状态中枢：
 * - 标签列表 / 激活标签：切 tab 只换 activeTabId，池保证会话（JS/网络/返回栈）不丢；
 * - 收藏 / 历史：Room 持久化；
 * - 桌面模式：按会话记忆，切换即对池中会话生效并 reload。
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val historyDao: HistoryDao,
    private val bookmarkDao: BookmarkDao,
) : ViewModel() {

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val _findState = MutableStateFlow(FindState())
    val findState: StateFlow<FindState> = _findState.asStateFlow()

    /** 已收藏 URL 集合（工具栏星标点亮依据） */
    private val _bookmarkedUrls = MutableStateFlow<Set<String>>(emptySet())
    val bookmarkedUrls: StateFlow<Set<String>> = _bookmarkedUrls.asStateFlow()

    /** 收藏列表（收藏夹面板） */
    val bookmarks: StateFlow<List<BookmarkEntity>> = bookmarkDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 历史列表（最近 100 条，同 URL 合并为最新一条） */
    val history: StateFlow<List<HistoryEntity>> = historyDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** sessionId → 桌面模式（会话级记忆） */
    private val _desktopModes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val desktopModes: StateFlow<Map<String, Boolean>> = _desktopModes.asStateFlow()

    /**
     * sessionId → 持久会话监听者（随会话存活，不随 Compose 组合摘除）。
     * 所有状态写回类回调（标题/URL/进度/返回栈）按自己的 tabId 写入 per-tab 状态，
     * 不依赖"当前激活 tab"——后台 tab 的回调不再丢失、不再串台。
     */
    private val sessionListeners = mutableMapOf<String, ShellListener>()

    init {
        viewModelScope.launch {
            bookmarkDao.observeAll().collect { list ->
                _bookmarkedUrls.value = list.map { it.url }.toSet()
            }
        }
        // 池满淘汰时同步移除对应 tab（实例已被池销毁并留存快照，这里不重复 destroy）
        WebViewPool.onSessionEvicted = { sessionId ->
            if (sessionId.startsWith("browser-")) {
                removeTabState(sessionId.removePrefix("browser-"))
            }
        }
    }

    override fun onCleared() {
        WebViewPool.onSessionEvicted = null
        super.onCleared()
    }

    /** 取（或建）指定会话的持久监听者；由 BrowserScreen 挂到 ShellWebView.sessionListener */
    fun listenerFor(sessionId: String): ShellListener =
        sessionListeners.getOrPut(sessionId) {
            val tabId = sessionId.removePrefix("browser-")
            object : ShellListener {
                override fun onPageStarted(url: String) {
                    updateTabMeta(tabId, url = url)
                    updateTabNav(tabId, progress = 0, loading = true)
                }

                override fun onProgress(progress: Int) {
                    updateTabNav(tabId, progress = progress, loading = progress < 100)
                }

                override fun onTitleReceived(title: String) {
                    updateTabMeta(tabId, title = title)
                }

                override fun onPageFinished(url: String) {
                    updateTabNav(tabId, progress = 100, loading = false)
                    // 入历史（同 URL 合并为最新一条），标题取该 tab 自己的最新值
                    val title = _tabs.value.firstOrNull { it.tabId == tabId }?.title.orEmpty()
                    recordHistory(url, title)
                }

                override fun onCanGoBackChanged(canGoBack: Boolean) {
                    updateTabNav(tabId, canGoBack = canGoBack)
                }

                override fun onCanGoForwardChanged(canGoForward: Boolean) {
                    updateTabNav(tabId, canGoForward = canGoForward)
                }
            }
        }

    /** 打开新标签；activate=false 时留在后台（供 target=_blank 备用） */
    fun createTab(startUrl: String, activate: Boolean): String {
        val tabId = UUID.randomUUID().toString().take(8)
        _tabs.value = _tabs.value + BrowserTab(
            tabId = tabId,
            title = if (startUrl == "about:blank") "" else startUrl,
            url = startUrl,
        )
        if (activate) setActive(tabId)
        return tabId
    }

    fun activateTab(tabId: String) {
        if (_tabs.value.any { it.tabId == tabId }) {
            setActive(tabId)
        }
    }

    /** 激活 tab 切换的唯一出口：同步池的激活保护（激活会话不被淘汰） */
    private fun setActive(tabId: String?) {
        _activeTabId.value = tabId
        WebViewPool.activeSessionId = tabId?.let { "browser-$it" }
    }

    /** 切走前由 Screen 先截缩略图，再调这里更新卡片 */
    fun updateTabThumbnail(tabId: String, thumbnail: Bitmap?) {
        if (thumbnail == null) return
        _tabs.value = _tabs.value.map {
            if (it.tabId == tabId) it.copy(thumbnail = thumbnail) else it
        }
    }

    fun updateTabMeta(tabId: String, title: String? = null, url: String? = null) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.tabId == tabId) {
                tab.copy(
                    title = title?.takeIf { it.isNotBlank() } ?: tab.title,
                    url = url ?: tab.url,
                )
            } else {
                tab
            }
        }
    }

    /** 更新指定 tab 的导航状态（进度/加载中/返回前进），参数为 null 表示不变 */
    fun updateTabNav(
        tabId: String,
        progress: Int? = null,
        loading: Boolean? = null,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null,
    ) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.tabId == tabId) {
                tab.copy(
                    progress = progress ?: tab.progress,
                    loading = loading ?: tab.loading,
                    canGoBack = canGoBack ?: tab.canGoBack,
                    canGoForward = canGoForward ?: tab.canGoForward,
                )
            } else {
                tab
            }
        }
    }

    /** 关闭标签：彻底销毁池中会话（不留快照），并清理会话监听者与桌面模式记忆 */
    fun closeTab(tabId: String) {
        val sessionId = "browser-$tabId"
        WebViewPool.get(sessionId)?.sessionListener = null
        sessionListeners.remove(sessionId)
        WebViewPool.destroyAndForget(sessionId)
        removeTabState(tabId)
    }

    fun closeAllTabs() {
        _tabs.value.forEach { tab ->
            val sessionId = "browser-${tab.tabId}"
            WebViewPool.get(sessionId)?.sessionListener = null
            sessionListeners.remove(sessionId)
            WebViewPool.destroyAndForget(sessionId)
        }
        _desktopModes.value = emptyMap()
        _tabs.value = emptyList()
        setActive(null)
        _findState.value = FindState()
    }

    /**
     * 移除 tab 的 UI 状态（不动池实例）：closeTab 在 destroy 后调用，
     * 池淘汰回调在实例已销毁后调用；若关的是激活 tab 则向前继/后续补位。
     */
    private fun removeTabState(tabId: String) {
        val current = _tabs.value
        val index = current.indexOfFirst { it.tabId == tabId }
        if (index < 0) return
        val remaining = current.filterNot { it.tabId == tabId }
        _tabs.value = remaining
        sessionListeners.remove("browser-$tabId")
        _desktopModes.value = _desktopModes.value - "browser-$tabId"
        if (_activeTabId.value == tabId) {
            setActive(remaining.getOrNull(index.coerceAtMost(remaining.size - 1))?.tabId)
        }
        if (remaining.isEmpty()) {
            _findState.value = FindState()
        }
    }

    // ---------------------------------------------------------------- find in page

    fun showFindBar() {
        _findState.value = _findState.value.copy(visible = true)
    }

    fun hideFindBar() {
        _findState.value = _findState.value.copy(visible = false)
        activeSessionId()?.let { WebViewPool.get(it) }?.clearFindMatches()
    }

    fun updateFindQuery(query: String) {
        _findState.value = _findState.value.copy(query = query, active = 0)
        val shell = activeSessionId()?.let { WebViewPool.get(it) }
        if (query.isBlank()) {
            shell?.clearFindMatches()
        } else {
            shell?.findInPage(query)
        }
    }

    fun onFindResult(active: Int, total: Int) {
        _findState.value = _findState.value.copy(active = active, total = total)
    }

    fun findNext(forward: Boolean) {
        activeSessionId()?.let { WebViewPool.get(it) }?.findNext(forward)
    }

    // ---------------------------------------------------------------- bookmarks

    fun toggleBookmark(url: String, title: String) {
        if (url.isBlank() || url == "about:blank") return
        viewModelScope.launch {
            if (bookmarkDao.getByUrl(url) != null) {
                bookmarkDao.deleteByUrl(url)
            } else {
                bookmarkDao.upsert(
                    BookmarkEntity(
                        url = url,
                        title = title.ifBlank { url },
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { bookmarkDao.deleteByUrl(url) }
    }

    // ---------------------------------------------------------------- history

    fun recordHistory(url: String, title: String) {
        if (url.isBlank() || url == "about:blank" || url.startsWith("data:")) return
        viewModelScope.launch {
            historyDao.deleteByUrl(url)
            historyDao.insert(
                HistoryEntity(
                    url = url,
                    title = title.ifBlank { url },
                    visitedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch { historyDao.clearAll() }
    }

    // ---------------------------------------------------------------- desktop mode

    fun setDesktopMode(sessionId: String, enabled: Boolean) {
        _desktopModes.value = _desktopModes.value + (sessionId to enabled)
        WebViewPool.get(sessionId)?.let { shell ->
            shell.setDesktopMode(enabled)
            shell.reload()
        }
    }

    private fun activeSessionId(): String? = _activeTabId.value?.let { "browser-$it" }
}
