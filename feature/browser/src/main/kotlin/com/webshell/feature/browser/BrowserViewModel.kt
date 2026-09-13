package com.webshell.feature.browser

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.BrowserOpenTabEntity
import com.webshell.core.data.BrowserOpenTabsRepository
import com.webshell.core.data.BrowserSavedPagesRepository
import com.webshell.core.data.IncomingSourceKey
import com.webshell.core.data.SettingsRepository
import com.webshell.core.data.metadata.SiteMetadataFetcher
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.ShellListener
import com.webshell.core.webengine.NewWindowRequest
import com.webshell.core.webengine.UrlRoute
import com.webshell.core.webengine.UrlRouter
import com.webshell.core.webengine.WebViewPool
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 多标签浏览器状态中枢：
 * - 标签列表 / 激活标签：切 tab 只换 activeTabId，池保证会话（JS/网络/返回栈）不丢；
 * - 收藏 / 历史：Room 持久化；
 * - 桌面模式：按会话记忆，切换即对池中会话生效并 reload。
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val savedPages: BrowserSavedPagesRepository,
    private val metadataFetcher: SiteMetadataFetcher,
    private val settingsRepository: SettingsRepository,
    private val openTabs: BrowserOpenTabsRepository,
    private val incomingDocuments: IncomingDocumentAccess,
) : ViewModel() {

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    /** Sessions whose renderer has been created or attached; conservative (prefer true). */
    private val _mountedRendererSessions = MutableStateFlow<Set<String>>(emptySet())

    /**
     * True when the active tab has a real page or a mounted renderer.
     * Dock selection and start-page routing share this value; it does not
     * emit when only progress/title on [_tabs] changes.
     */
    val showsWebView: StateFlow<Boolean> = combine(
        _tabs,
        _activeTabId,
        _mountedRendererSessions,
    ) { tabs, activeTabId, mounted ->
        val active = tabs.firstOrNull { it.tabId == activeTabId }
        when (active?.kind) {
            BrowserTabKind.INCOMING_MARKDOWN -> false
            BrowserTabKind.INCOMING_HTML -> true
            else -> {
                val url = active?.url.orEmpty()
                val hasPage = url.isNotBlank() && url != "about:blank"
                val sessionId = active?.sessionId ?: activeTabId?.let { "browser-$it" }
                hasPage || (sessionId != null && sessionId in mounted)
            }
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _findState = MutableStateFlow(FindState())
    val findState: StateFlow<FindState> = _findState.asStateFlow()

    /** 已收藏 URL 集合（工具栏星标点亮依据） */
    private val _bookmarkedUrls = MutableStateFlow<Set<String>>(emptySet())
    val bookmarkedUrls: StateFlow<Set<String>> = _bookmarkedUrls.asStateFlow()

    /** 收藏列表（收藏夹面板） */
    val bookmarks: StateFlow<List<BrowserSavedPage>> = savedPages.observeBookmarks()
        .map { entries ->
            entries.map {
                BrowserSavedPage(
                    it.id,
                    it.title,
                    it.url,
                    it.iconUrl ?: metadataFetcher.displayFallbackIconUrl(it.url),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 历史列表（最近 100 条，同 URL 合并为最新一条） */
    val history: StateFlow<List<BrowserSavedPage>> = savedPages.observeHistory()
        .map { entries ->
            entries.map {
                BrowserSavedPage(it.id, it.title, it.url, metadataFetcher.displayFallbackIconUrl(it.url))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** sessionId → 桌面模式（会话级记忆） */
    private val _desktopModes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val desktopModes: StateFlow<Map<String, Boolean>> = _desktopModes.asStateFlow()
    /**
     * null until DataStore emits. BrowserScreen must keep a session-baked
     * [ShellConfig.forceEnableZoom] until this is non-null.
     */
    val forceEnableZoomEnabled: StateFlow<Boolean?> = settingsRepository.settings
        .map { it.forceEnableZoomEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * sessionId → 持久会话监听者（随会话存活，不随 Compose 组合摘除）。
     * 所有状态写回类回调（标题/URL/进度/返回栈）按自己的 tabId 写入 per-tab 状态，
     * 不依赖"当前激活 tab"——后台 tab 的回调不再丢失、不再串台。
     */
    private val sessionListeners = mutableMapOf<String, ShellListener>()
    private val thumbnailRevisions = mutableMapOf<String, Long>()
    private var browserVisible = false
    private var persistReady = false
    private val _tabsHydrated = MutableStateFlow(false)
    val tabsHydrated: StateFlow<Boolean> = _tabsHydrated.asStateFlow()
    private var persistJob: Job? = null
    private val _askAddToHomeTabId = MutableStateFlow<String?>(null)
    val askAddToHomeTabId: StateFlow<String?> = _askAddToHomeTabId.asStateFlow()
    private val _incomingHomeNotice = MutableStateFlow<Int?>(null)
    val incomingHomeNotice: StateFlow<Int?> = _incomingHomeNotice.asStateFlow()
    private val evictionListener: (String) -> Unit = { sessionId ->
        // Eviction removes only the renderer instance. UI tab state and the
        // pool's recovery bundle remain so selecting the tab recreates it.
        if (sessionId.startsWith("browser-")) AppLog.log("browser", "后台会话暂时淘汰，保留标签状态")
    }

    init {
        viewModelScope.launch {
            savedPages.observeBookmarks().collect { list ->
                _bookmarkedUrls.value = list.map { it.url }.toSet()
            }
        }
        // 池满淘汰时仅移除 renderer，实例快照和对应 tab UI 状态继续保留。
        WebViewPool.onSessionEvicted = evictionListener
        viewModelScope.launch { hydrateOpenTabs() }
    }

    override fun onCleared() {
        if (WebViewPool.onSessionEvicted === evictionListener) WebViewPool.onSessionEvicted = null
        sessionListeners.forEach { (sessionId, listener) ->
            WebViewPool.get(sessionId)?.let { if (it.sessionListener === listener) it.sessionListener = null }
        }
        sessionListeners.clear()
        setVisible(false)
        super.onCleared()
    }

    /** 取（或建）指定会话的持久监听者；由 BrowserScreen 挂到 ShellWebView.sessionListener */
    fun listenerFor(sessionId: String): ShellListener =
        sessionListeners.getOrPut(sessionId) {
            val tabId = tabIdForSession(sessionId)
            object : ShellListener {
                override fun onNewWindow(request: NewWindowRequest) {
                    // onCreateWindow allocates the real pooled target before
                    // Chromium receives its WebViewTransport. Adopt it from the
                    // persistent session listener as well as the visible UI
                    // listener, so a popup opened by a background tab cannot
                    // become a naked/orphan renderer.
                    if (request.targetSessionId.startsWith("browser-")) {
                        createTabForSession(
                            request.targetSessionId,
                            request.initialUrl ?: "about:blank",
                            activate = request.isUserGesture &&
                                request.sourceSessionId == activeSessionId(),
                            restoreStartUrlIfBlank = false,
                        )
                    }
                }

                override fun onPageStarted(url: String) {
                    updateTabMeta(tabId, url = url)
                    updateTabNav(tabId, progress = 0, loading = true)
                    updateTabError(tabId, null)
                }

                override fun onFirstPaint(url: String) {
                    // Capture only after the first composited frame; capturing when
                    // opening the tab switcher can otherwise produce a blank/loading card.
                    captureThumbnail(tabId, url)
                }

                override fun onProgress(progress: Int) {
                    updateTabNav(tabId, progress = progress, loading = progress < 100)
                }

                override fun onPageError(url: String, errorCode: Int, description: String, insecureHttp: Boolean) {
                    updateTabError(tabId, if (errorCode == -99) BrowserLoadError.RENDERER_RECOVERING
                    else if (insecureHttp) BrowserLoadError.INSECURE_HTTP else BrowserLoadError.NETWORK)
                }

                override fun onRenderProcessRecovered() {
                    updateTabError(tabId, null)
                }

                override fun onRenderProcessRecoveryFailed() {
                    updateTabError(tabId, BrowserLoadError.NETWORK)
                }

                override fun onTitleReceived(title: String) {
                    updateTabMeta(tabId, title = title)
                }

                override fun onPageFinished(url: String) {
                    updateTabNav(tabId, progress = 100, loading = false)
                    captureThumbnail(tabId, url)
                    val tab = _tabs.value.firstOrNull { it.tabId == tabId }
                    if (tab?.kind == BrowserTabKind.INCOMING_HTML &&
                        !url.equals("about:blank", ignoreCase = true)
                    ) {
                        offerAddToHome(tabId)
                    }
                    if (tab?.kind == BrowserTabKind.WEB) {
                        recordHistory(url, tab.title)
                    }
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
        return createTabForSession("browser-$tabId", startUrl, activate, restoreStartUrlIfBlank = true)
    }

    /** Adopt the session created by WebView.onCreateWindow so it is never a naked probe. */
    fun createTabForSession(
        sessionId: String,
        startUrl: String,
        activate: Boolean,
        restoreStartUrlIfBlank: Boolean = true,
    ): String {
        val existing = _tabs.value.firstOrNull { it.sessionId == sessionId }
        val pooled = WebViewPool.get(sessionId)
        if (pooled != null) markRendererMounted(sessionId)
        val liveUrl = pooled?.currentUrl()?.takeUnless { it.isNullOrBlank() || it == "about:blank" }
        val safeStartUrl = UrlRouter.classify((liveUrl ?: startUrl).orEmpty()).let { decision ->
            if (decision.route == UrlRoute.WEB || decision.route == UrlRoute.ABOUT_BLANK) decision.normalized
            else "about:blank"
        }
        if (existing != null) {
            pooled?.sessionListener = listenerFor(sessionId)
            applyBrowserTabConfig(sessionId)
            if (liveUrl != null) updateTabMeta(existing.tabId, url = liveUrl)
            if (activate) setActive(existing.tabId)
            return existing.tabId
        }
        val tabId = uniqueTabId(sessionId)
        _tabs.value = _tabs.value + BrowserTab(
            tabId = tabId,
            title = if (safeStartUrl == "about:blank") "" else safeStartUrl,
            url = safeStartUrl,
            sessionId = sessionId,
            restoreStartUrlIfBlank = restoreStartUrlIfBlank,
        )
        // Bind before Chromium's first onPageStarted whenever the owner is
        // notified ahead of sendToTarget(); otherwise the tab stays blank.
        pooled?.sessionListener = listenerFor(sessionId)
        applyBrowserTabConfig(sessionId)
        AppLog.log("browser", "新建标签 $tabId（共 ${_tabs.value.size} 个）")
        if (activate) setActive(tabId)
        schedulePersist()
        return tabId
    }

    fun incomingReuseTokens(): Set<String> = _tabs.value.mapNotNull { tab ->
        val key = tab.sourceKey ?: IncomingSourceKey.fromFilesystemPath(tab.displayPath) ?: return@mapNotNull null
        when (tab.kind) {
            BrowserTabKind.INCOMING_HTML -> IncomingSourceKey.reuseToken(html = true, key)
            BrowserTabKind.INCOMING_MARKDOWN -> IncomingSourceKey.reuseToken(html = false, key)
            BrowserTabKind.WEB -> null
        }
    }.toSet()

    fun activateIncomingBySourceKey(sourceKey: String, html: Boolean): Boolean {
        val kind = if (html) BrowserTabKind.INCOMING_HTML else BrowserTabKind.INCOMING_MARKDOWN
        val existing = _tabs.value.firstOrNull { tab ->
            tab.kind == kind && (tab.sourceKey ?: IncomingSourceKey.fromFilesystemPath(tab.displayPath)) == sourceKey
        } ?: return false
        setActive(existing.tabId)
        schedulePersist()
        return true
    }

    fun openIncomingHtml(
        startUrl: String,
        title: String,
        displayPath: String,
        localAppId: String,
        sourceKey: String? = null,
    ): String {
        val existing = _tabs.value.firstOrNull { tab ->
            tab.kind == BrowserTabKind.INCOMING_HTML && (
                tab.localAppId == localAppId ||
                    (sourceKey != null && (tab.sourceKey ?: IncomingSourceKey.fromFilesystemPath(tab.displayPath)) == sourceKey)
                )
        }
        if (existing != null) {
            setActive(existing.tabId)
            schedulePersist()
            return existing.tabId
        }
        val tabId = UUID.randomUUID().toString().take(8)
        val sessionId = "browser-$tabId"
        _tabs.value = _tabs.value + BrowserTab(
            tabId = tabId,
            title = title,
            url = startUrl,
            sessionId = sessionId,
            restoreStartUrlIfBlank = true,
            kind = BrowserTabKind.INCOMING_HTML,
            displayPath = displayPath,
            localAppId = localAppId,
            sourceKey = sourceKey,
        )
        WebViewPool.get(sessionId)?.sessionListener = listenerFor(sessionId)
        applyBrowserTabConfig(sessionId)
        setActive(tabId)
        schedulePersist()
        return tabId
    }

    fun openIncomingMarkdown(
        title: String,
        displayPath: String,
        content: String,
        localAppId: String,
        sourceKey: String? = null,
    ): String {
        val existing = _tabs.value.firstOrNull { tab ->
            tab.kind == BrowserTabKind.INCOMING_MARKDOWN && (
                tab.localAppId == localAppId ||
                    (sourceKey != null && (tab.sourceKey ?: IncomingSourceKey.fromFilesystemPath(tab.displayPath)) == sourceKey)
                )
        }
        if (existing != null) {
            setActive(existing.tabId)
            schedulePersist()
            return existing.tabId
        }
        val tabId = UUID.randomUUID().toString().take(8)
        _tabs.value = _tabs.value + BrowserTab(
            tabId = tabId,
            title = title,
            url = "about:blank",
            sessionId = "browser-$tabId",
            restoreStartUrlIfBlank = false,
            kind = BrowserTabKind.INCOMING_MARKDOWN,
            displayPath = displayPath,
            markdownContent = content,
            localAppId = localAppId,
            sourceKey = sourceKey,
        )
        setActive(tabId)
        offerAddToHome(tabId)
        schedulePersist()
        return tabId
    }

    fun offerAddToHome(tabId: String) {
        val tab = _tabs.value.firstOrNull { it.tabId == tabId } ?: return
        if (tab.kind == BrowserTabKind.WEB) return
        if (_askAddToHomeTabId.value == null) _askAddToHomeTabId.value = tabId
    }

    fun dismissAddToHome() {
        _askAddToHomeTabId.value = null
    }

    fun addIncomingToHome(tabId: String? = _askAddToHomeTabId.value ?: _activeTabId.value) {
        val resolvedId = tabId ?: return
        val tab = _tabs.value.firstOrNull { it.tabId == resolvedId } ?: return
        if (tab.kind == BrowserTabKind.WEB) return
        viewModelScope.launch {
            val ok = incomingDocuments.persistShortcut(tab)
            if (_askAddToHomeTabId.value == resolvedId) _askAddToHomeTabId.value = null
            _incomingHomeNotice.value = if (ok) {
                R.string.browser_added_home
            } else {
                R.string.browser_add_home_failed
            }
        }
    }

    fun consumeIncomingHomeNotice() {
        _incomingHomeNotice.value = null
    }

    private fun captureThumbnail(tabId: String, expectedUrl: String) {
        val revision = (thumbnailRevisions[tabId] ?: 0L) + 1L
        thumbnailRevisions[tabId] = revision
        viewModelScope.launch {
            repeat(4) { attempt ->
                val shell = WebViewPool.get(sessionIdOf(tabId))
                val bitmap = runCatching { shell?.captureThumbnail() }.getOrNull()
                if (bitmap != null && thumbnailRevisions[tabId] == revision && shell?.currentUrl() == expectedUrl) {
                    updateTabThumbnail(tabId, bitmap)
                    return@launch
                }

                if (attempt < 3) kotlinx.coroutines.delay(80L * (attempt + 1))
            }
        }
    }

    fun activateTab(tabId: String) {
        if (_tabs.value.any { it.tabId == tabId }) {
            AppLog.log("browser", "切换标签 $tabId")
            setActive(tabId)
        }
    }

    /** 激活 tab 切换的唯一出口：同步池的激活保护（激活会话不被淘汰） */
    private fun setActive(tabId: String?) {
        val previous = _activeTabId.value
        if (previous != tabId) {
            previous?.let {
                val previousSession = sessionIdOf(it)
                WebViewPool.suspendSession(previousSession)
                WebViewPool.unprotect(previousSession, WebViewPool.ProtectionReason.ACTIVE)
            }
            _findState.value = FindState()
        }
        _activeTabId.value = tabId
        if (browserVisible) {
            tabId?.let {
                val sessionId = sessionIdOf(it)
                WebViewPool.protect(sessionId, WebViewPool.ProtectionReason.ACTIVE)
                WebViewPool.activeSessionId = sessionId
            }
        }
        schedulePersist()
    }

    fun setVisible(visible: Boolean) {
        browserVisible = visible
        val sid = activeSessionId() ?: return
        if (visible) {
            WebViewPool.activeSessionId = sid
            WebViewPool.protect(sid, WebViewPool.ProtectionReason.ACTIVE)
        } else if (WebViewPool.activeSessionId == sid) {
            WebViewPool.activeSessionId = null
            WebViewPool.unprotect(sid, WebViewPool.ProtectionReason.ACTIVE)
        }
    }

    fun markRendererMounted(sessionId: String) {
        if (sessionId.isBlank()) return
        val current = _mountedRendererSessions.value
        if (sessionId in current) return
        _mountedRendererSessions.value = current + sessionId
    }

    /** Invoked after the lifecycle host has installed the persistent per-session listener. */
    fun onHostReady(sessionId: String) {
        markRendererMounted(sessionId)
        val shell = WebViewPool.get(sessionId) ?: return
        val tab = _tabs.value.firstOrNull { it.sessionId == sessionId } ?: return
        val currentUrl = shell.currentUrl()
        if ((currentUrl == null || currentUrl == "about:blank") &&
            tab.restoreStartUrlIfBlank &&
            tab.url.isNotBlank() &&
            tab.url != "about:blank"
        ) {
            shell.loadWithStateRestore(tab.url)
        } else if (currentUrl != null && currentUrl != "about:blank") {
            updateTabMeta(tab.tabId, url = currentUrl)
        }
        updateTabNav(tab.tabId, canGoBack = shell.canGoBack(), canGoForward = shell.canGoForward())
    }

    fun onHostDisposed(sessionId: String) {
        // Prefer keeping the mark while the pool still holds a renderer so
        // returning to Browse does not flash live glass over a live WebView.
        if (WebViewPool.get(sessionId) == null) {
            _mountedRendererSessions.value = _mountedRendererSessions.value - sessionId
        }
    }

    fun captureActiveThumbnail() {
        val tabId = _activeTabId.value ?: return
        val expectedUrl = _tabs.value.firstOrNull { it.tabId == tabId }?.url
        val sessionId = sessionIdOf(tabId)
        thumbnailRevisions[tabId] = (thumbnailRevisions[tabId] ?: 0L) + 1L
        runCatching { WebViewPool.get(sessionId)?.captureThumbnail() }
            .getOrNull()?.takeIf { WebViewPool.get(sessionId)?.currentUrl() == expectedUrl }
            ?.let { updateTabThumbnail(tabId, it) }
    }

    fun openUrl(url: String) {
        if (url.isBlank()) return
        val shell = activeSessionId()?.let { WebViewPool.get(it) }
        if (shell != null) shell.loadWithStateRestore(url) else createTab(url, activate = true)
    }

    fun goBack() { activeSessionId()?.let { WebViewPool.get(it)?.goBack() } }
    fun goForward() { activeSessionId()?.let { WebViewPool.get(it)?.goForward() } }
    fun refreshOrStop(loading: Boolean) {
        activeSessionId()?.let { WebViewPool.get(it) }?.let { if (loading) it.stopLoading() else it.reload() }
    }

    /** 切走前由 Screen 先截缩略图，再调这里更新卡片 */
    fun updateTabThumbnail(tabId: String, thumbnail: Bitmap?) {
        if (thumbnail == null) return
        _tabs.value = _tabs.value.map {
            if (it.tabId == tabId) it.copy(thumbnail = thumbnail) else it
        }
    }

    fun updateTabMeta(tabId: String, title: String? = null, url: String? = null) {
        var changed = false
        _tabs.value = _tabs.value.map { tab ->
            if (tab.tabId == tabId) {
                val nextTitle = title?.takeIf { it.isNotBlank() } ?: tab.title
                val nextUrl = url ?: tab.url
                if (nextTitle != tab.title || nextUrl != tab.url) {
                    changed = true
                    tab.copy(title = nextTitle, url = nextUrl)
                } else {
                    tab
                }
            } else {
                tab
            }
        }
        if (changed) schedulePersist()
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

    private fun updateTabError(tabId: String, error: BrowserLoadError?) {
        _tabs.value = _tabs.value.map { if (it.tabId == tabId) it.copy(loadError = error) else it }
    }

    /** 关闭标签：彻底销毁池中会话（不留快照），并清理会话监听者与桌面模式记忆 */
    fun closeTab(tabId: String) {
        val tab = _tabs.value.firstOrNull { it.tabId == tabId }
        val sessionId = sessionIdOf(tabId)
        WebViewPool.get(sessionId)?.sessionListener = null
        sessionListeners.remove(sessionId)
        WebViewPool.destroyAndForget(sessionId)
        _mountedRendererSessions.value = _mountedRendererSessions.value - sessionId
        tab?.localAppId?.let(incomingDocuments::deleteTemporary)
        if (_askAddToHomeTabId.value == tabId) _askAddToHomeTabId.value = null
        removeTabState(tabId)
        thumbnailRevisions.remove(tabId)
        AppLog.log("browser", "关闭标签 $tabId（剩 ${_tabs.value.size} 个）")
        schedulePersist()
    }

    fun closeAllTabs() {
        val count = _tabs.value.size
        _tabs.value.forEach { tab ->
            WebViewPool.get(tab.sessionId)?.sessionListener = null
            sessionListeners.remove(tab.sessionId)
            WebViewPool.destroyAndForget(tab.sessionId)
            tab.localAppId?.let(incomingDocuments::deleteTemporary)
        }
        _mountedRendererSessions.value = emptySet()
        _desktopModes.value = emptyMap()
        thumbnailRevisions.clear()
        _tabs.value = emptyList()
        _askAddToHomeTabId.value = null
        setActive(null)
        _findState.value = FindState()
        AppLog.log("browser", "关闭全部标签（$count 个）")
        schedulePersist()
    }

    /** Remove a tab's UI state after an explicit user close. */
    private fun removeTabState(tabId: String) {
        val current = _tabs.value
        val index = current.indexOfFirst { it.tabId == tabId }
        if (index < 0) return
        val remaining = current.filterNot { it.tabId == tabId }
        _tabs.value = remaining
        sessionListeners.remove(current[index].sessionId)
        _desktopModes.value = _desktopModes.value - current[index].sessionId
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

    fun onFindResult(sessionId: String, active: Int, total: Int) {
        if (sessionId != activeSessionId() || !_findState.value.visible) return
        _findState.value = _findState.value.copy(active = active, total = total)
    }

    fun findNext(forward: Boolean) {
        activeSessionId()?.let { WebViewPool.get(it) }?.findNext(forward)
    }

    // ---------------------------------------------------------------- bookmarks

    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch {
            savedPages.toggleBookmark(url, title, metadataFetcher.displayFallbackIconUrl(url))
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { savedPages.removeBookmark(url) }
    }

    // ---------------------------------------------------------------- history

    fun recordHistory(url: String, title: String) {
        if (isIncomingAssetUrl(url)) return
        viewModelScope.launch { savedPages.recordVisit(url, title) }
    }

    fun clearHistory() {
        viewModelScope.launch { savedPages.clearHistory() }
    }

    // ---------------------------------------------------------------- desktop mode

    fun setDesktopMode(sessionId: String, enabled: Boolean) {
        _desktopModes.value = _desktopModes.value + (sessionId to enabled)
        WebViewPool.get(sessionId)?.setDesktopMode(enabled)
        schedulePersist()
    }

    private fun activeSessionId(): String? = _activeTabId.value?.let(::sessionIdOf)

    private fun sessionIdOf(tabId: String): String =
        _tabs.value.firstOrNull { it.tabId == tabId }?.sessionId ?: "browser-$tabId"

    private fun tabIdForSession(sessionId: String): String =
        _tabs.value.firstOrNull { it.sessionId == sessionId }?.tabId
            ?: sessionId.removePrefix("browser-").ifBlank { sessionId }

    private fun uniqueTabId(sessionId: String): String {
        val derived = sessionId.removePrefix("browser-")
        if (derived.isNotBlank() && derived != sessionId && _tabs.value.none { it.tabId == derived }) {
            return derived
        }
        var candidate: String
        do {
            candidate = UUID.randomUUID().toString().take(8)
        } while (_tabs.value.any { it.tabId == candidate })
        return candidate
    }

    private fun applyBrowserTabConfig(sessionId: String) {
        val shell = WebViewPool.get(sessionId) ?: return
        if (shell.config.desktopMode && _desktopModes.value[sessionId] == null) {
            _desktopModes.value = _desktopModes.value + (sessionId to true)
        }
        val tab = _tabs.value.firstOrNull { it.sessionId == sessionId }
        shell.reconfigure(
            shell.config.copy(
                externalLinkPolicy = ShellConfig.ExternalLinkPolicy.OPEN_IN_SAME,
                localAppId = tab?.localAppId.takeIf { tab?.kind == BrowserTabKind.INCOMING_HTML }
                    ?: shell.config.localAppId,
            ),
        )
    }

    private suspend fun hydrateOpenTabs() {
        val saved = runCatching { openTabs.load() }.getOrDefault(emptyList())
        val activeId = settingsRepository.settings.first().browserActiveTabId
        if (saved.isNotEmpty()) {
            val restored = saved.map { row ->
                val kind = parseBrowserTabKind(row.kind)
                val markdown = row.localAppId
                    ?.takeIf { kind == BrowserTabKind.INCOMING_MARKDOWN }
                    ?.let(incomingDocuments::readMarkdownForDisplay)
                row.toBrowserTab(kind, markdown)
            }
            _tabs.value = restored
            restored.forEach { tab ->
                if (saved.firstOrNull { it.tabId == tab.tabId }?.desktopMode == true) {
                    _desktopModes.value = _desktopModes.value + (tab.sessionId to true)
                }
            }
            val active = restored.firstOrNull { it.tabId == activeId }?.tabId
                ?: restored.first().tabId
            setActive(active)
        }
        persistReady = true
        _tabsHydrated.value = true
    }

    private fun schedulePersist() {
        if (!persistReady) return
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(250)
            persistNow()
        }
    }

    private suspend fun persistNow() {
        val now = System.currentTimeMillis()
        val rows = _tabs.value.mapIndexed { index, tab ->
            tab.toOpenTabEntity(
                position = index,
                desktopMode = _desktopModes.value[tab.sessionId] == true,
                updatedAt = now,
            )
        }
        runCatching { openTabs.replaceAll(rows) }
        runCatching { settingsRepository.setBrowserActiveTabId(_activeTabId.value) }
    }
}

internal fun parseBrowserTabKind(raw: String): BrowserTabKind = when (raw) {
    "incoming_html" -> BrowserTabKind.INCOMING_HTML
    "incoming_markdown" -> BrowserTabKind.INCOMING_MARKDOWN
    else -> BrowserTabKind.WEB
}

internal fun BrowserTabKind.storageName(): String = when (this) {
    BrowserTabKind.WEB -> "web"
    BrowserTabKind.INCOMING_HTML -> "incoming_html"
    BrowserTabKind.INCOMING_MARKDOWN -> "incoming_markdown"
}

internal fun BrowserOpenTabEntity.toBrowserTab(
    kind: BrowserTabKind,
    markdown: String?,
): BrowserTab = BrowserTab(
    tabId = tabId,
    title = title,
    url = url,
    sessionId = sessionId,
    restoreStartUrlIfBlank = restoreStartUrlIfBlank,
    kind = kind,
    displayPath = displayPath,
    markdownContent = markdown,
    localAppId = localAppId,
    sourceKey = sourceKey,
)

internal fun BrowserTab.toOpenTabEntity(
    position: Int,
    desktopMode: Boolean,
    updatedAt: Long,
): BrowserOpenTabEntity = BrowserOpenTabEntity(
    tabId = tabId,
    sessionId = sessionId,
    url = url,
    title = title,
    position = position,
    desktopMode = desktopMode,
    restoreStartUrlIfBlank = restoreStartUrlIfBlank,
    kind = kind.storageName(),
    displayPath = displayPath,
    localAppId = localAppId,
    sourceKey = sourceKey,
    updatedAt = updatedAt,
)
