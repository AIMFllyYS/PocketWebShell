package com.webshell.feature.browser

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.Intent
import com.webshell.core.webengine.UrlRoute
import com.webshell.core.webengine.UrlRouter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.theme.LocalOverlayClearance
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.compose.ShellWebViewHost

/**
 * Route/state collection only. Presentation, platform prompts, chrome transitions and native
 * ownership have separate boundaries. App owns the one glass Dock/orb/edge overlay.
 */
@Composable
fun BrowserScreen(
    onOpenUrl: (String) -> Unit = {},
    viewModel: BrowserViewModel = hiltViewModel(),
    chrome: BrowserChromeController = rememberBrowserChromeController(),
    isVisible: Boolean = true,
    autoCollapse: Boolean = true,
    pullToRefresh: Boolean = false,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val findState by viewModel.findState.collectAsStateWithLifecycle()
    val bookmarkedUrls by viewModel.bookmarkedUrls.collectAsStateWithLifecycle()
    val bookmarkPages by viewModel.bookmarks.collectAsStateWithLifecycle()
    val recentPages by viewModel.history.collectAsStateWithLifecycle()
    val desktopModes by viewModel.desktopModes.collectAsStateWithLifecycle()
    val activeTab = tabs.firstOrNull { it.tabId == activeTabId }
    val sessionId = activeTabId?.let { "browser-$it" }
    val currentUrl = activeTab?.url.orEmpty()
    val hasPage = currentUrl.isNotBlank() && currentUrl != "about:blank"
    val desktopOn = desktopModes[sessionId] == true
    var urlInput by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var editing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val requests = rememberWebSessionRequests(sessionId, isVisible,
        onNewWindow = { request ->
            viewModel.captureActiveThumbnail()
            viewModel.createTabForSession(request.targetSessionId, request.initialUrl ?: "about:blank", activate = true)
        },
        onMessage = { message = it })
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    SideEffect {
        viewModel.setVisible(isVisible)
        chrome.dispatch(BrowserChromeEvent.Environment(
            visible = isVisible, activeSessionId = sessionId.takeIf { hasPage },
            activeUrl = currentUrl.takeIf { hasPage },
            autoCollapse = autoCollapse,
            interactionBlocked = editing || imeVisible || findState.visible || requests.busy,
        ))
    }
    DisposableEffect(chrome, viewModel) {
        onDispose {
            viewModel.captureActiveThumbnail()
            viewModel.setVisible(false)
            val state = chrome.state
            chrome.dispatch(BrowserChromeEvent.Environment(false, state.activeSessionId, state.activeUrl, state.autoCollapse, false))
        }
    }
    LaunchedEffect(activeTabId) {
        focusManager.clearFocus()
        editing = false
        urlInput = TextFieldValue(currentUrl.takeUnless { it == "about:blank" }.orEmpty())
    }
    LaunchedEffect(currentUrl) {
        if (!editing) urlInput = TextFieldValue(currentUrl.takeUnless { it == "about:blank" }.orEmpty())
    }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2500); message = null }
    }
    LaunchedEffect(activeTab?.loadError) {
        when (activeTab?.loadError) {
            BrowserLoadError.INSECURE_HTTP -> message = context.getString(R.string.browser_http_failed)
            BrowserLoadError.RENDERER_RECOVERING -> message = context.getString(R.string.browser_renderer_recovering)
            BrowserLoadError.NETWORK -> message = context.getString(R.string.browser_page_loading_error)
            null -> Unit
        }
    }
    DisposableEffect(requests.fullScreenView) {
        val activity = context as? Activity
        val decor = activity?.window?.decorView
        val previous = decor?.systemUiVisibility ?: 0
        if (requests.fullScreenView != null) {
            decor?.systemUiVisibility = previous or 0x00000400 or 0x00000002 or 0x00001000
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else decor?.systemUiVisibility = previous
        onDispose { decor?.systemUiVisibility = previous }
    }

    fun dismissOverlay() = chrome.dispatch(BrowserChromeEvent.ShowOverlay(null))
    fun newTab() {
        viewModel.captureActiveThumbnail()
        dismissOverlay()
        chrome.dispatch(BrowserChromeEvent.Reveal)
        viewModel.createTab("about:blank", activate = true)
    }
    fun navigate(url: String) {
        val decision = UrlRouter.normalizeAddressBar(url)
        when (decision.route) {
            UrlRoute.WEB, UrlRoute.ABOUT_BLANK -> {
                dismissOverlay()
                onOpenUrl(decision.normalized)
                viewModel.openUrl(decision.normalized)
            }
            UrlRoute.EXTERNAL_INTENT -> runCatching {
                val intent = if (decision.normalized.startsWith("intent:", ignoreCase = true)) {
                    Intent.parseUri(decision.normalized, Intent.URI_INTENT_SCHEME)
                } else Intent(Intent.ACTION_VIEW, android.net.Uri.parse(decision.normalized))
                check(intent.component == null && intent.selector == null && intent.`package` == null)
                check(intent.resolveActivity(context.packageManager) != null)
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                dismissOverlay()
            }.onFailure { message = context.getString(R.string.browser_external_failed) }
            else -> if (url.isNotBlank()) message = context.getString(R.string.browser_invalid_url)
        }
    }
    fun closeActive() { activeTabId?.let(viewModel::closeTab) }
    fun menuAction(action: BrowserMenuAction) {
        dismissOverlay()
        when (action) {
            BrowserMenuAction.Back -> viewModel.goBack()
            BrowserMenuAction.Forward -> viewModel.goForward()
            BrowserMenuAction.RefreshOrStop -> viewModel.refreshOrStop(activeTab?.loading == true)
            BrowserMenuAction.Bookmark -> {
                viewModel.toggleBookmark(currentUrl, activeTab?.title.orEmpty())
                message = context.getString(if (currentUrl in bookmarkedUrls)
                    R.string.browser_bookmark_removed else R.string.browser_bookmark_added)
            }
            BrowserMenuAction.Find -> viewModel.showFindBar()
            BrowserMenuAction.NewTab -> newTab()
            BrowserMenuAction.Desktop -> sessionId?.let { viewModel.setDesktopMode(it, !desktopOn) }
            BrowserMenuAction.History -> chrome.dispatch(BrowserChromeEvent.ShowOverlay(BrowserOverlay.History))
            BrowserMenuAction.Bookmarks -> chrome.dispatch(BrowserChromeEvent.ShowOverlay(BrowserOverlay.Bookmarks))
            BrowserMenuAction.HideToolbar -> { focusManager.clearFocus(); chrome.dispatch(BrowserChromeEvent.HideToolbar) }
            BrowserMenuAction.Collapse -> chrome.dispatch(BrowserChromeEvent.Collapse)
            BrowserMenuAction.CloseAll -> chrome.dispatch(BrowserChromeEvent.ShowOverlay(BrowserOverlay.CloseAll))
        }
    }

    BackHandler(enabled = isVisible && requests.fullScreenView != null) { requests.exitFullScreen() }
    BackHandler(enabled = isVisible && requests.fullScreenView == null && (chrome.state.overlay != null || editing || findState.visible || activeTabId != null)) {
        when {
            chrome.state.overlay != null -> dismissOverlay()
            editing -> {
                focusManager.clearFocus()
                urlInput = TextFieldValue(currentUrl.takeUnless { it == "about:blank" }.orEmpty())
            }
            findState.visible -> viewModel.hideFindBar()
            activeTab?.canGoBack == true -> viewModel.goBack()
            else -> closeActive()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Deliberate top visibility changes layout once. Bottom morphs never measure this column.
        if (chrome.state.toolbarVisible) BrowserTopBar(
            urlInput = urlInput, editing = editing, onUrlInputChanged = { urlInput = it },
            onEditingChanged = { editing = it }, onGo = { navigate(urlInput.text) },
            loading = activeTab?.loading == true, tabCount = tabs.size, progress = activeTab?.progress ?: 0,
            sessionKey = sessionId,
            onTabSwitcher = {
                focusManager.clearFocus()
                viewModel.captureActiveThumbnail()
                chrome.dispatch(BrowserChromeEvent.ShowOverlay(BrowserOverlay.Tabs))
            },
            onMenu = { focusManager.clearFocus(); chrome.dispatch(BrowserChromeEvent.ShowOverlay(BrowserOverlay.Menu)) },
        )
        if (findState.visible) FindBar(findState.query, findState.active, findState.total,
            viewModel::updateFindQuery, { viewModel.findNext(false) }, { viewModel.findNext(true) }, viewModel::hideFindBar)
        Box(Modifier.weight(1f)) {
            if (activeTab != null && sessionId != null && hasPage) {
                ShellWebViewHost(
                    sessionId = sessionId,
                    configFactory = { ShellConfig(
                        sessionId = sessionId, startUrl = activeTab.url, desktopMode = desktopOn,
                        pullToRefresh = pullToRefresh,
                        externalLinkPolicy = ShellConfig.ExternalLinkPolicy.OPEN_IN_SAME,
                    ) },
                    listener = requests.listener,
                    isVisible = isVisible,
                    parentHandlesInsets = true,
                    sessionListener = viewModel.listenerFor(sessionId),
                    onFindResult = { active, total -> viewModel.onFindResult(sessionId, active, total) },
                    onReady = { viewModel.onHostReady(sessionId) },
                )
            } else if (activeTab != null) {
                // Ordinary scrolling content, unlike a real page: it has no
                // ShellWebViewHost/parentHandlesInsets mechanism of its own,
                // so it must reserve the same Dock clearance every other tab
                // reserves or the permanently-revealed BrowserDockHost covers
                // its bottom edge.
                BrowserStartPage(
                    bookmarkPages, recentPages, ::navigate,
                    Modifier.fillMaxSize().padding(bottom = LocalOverlayClearance.current),
                )
            } else EmptyTabsPrompt(Modifier.fillMaxSize().padding(bottom = LocalOverlayClearance.current), ::newTab)
            message?.let {
                WebSessionStatusMessage(it, Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 24.dp))
            }
        }
    }

    if (isVisible) {
        when (chrome.state.overlay) {
            BrowserOverlay.Menu -> BrowserMenuSheet(
                BrowserMenuState(hasPage, tabs.isNotEmpty(), activeTab?.canGoBack == true,
                    activeTab?.canGoForward == true, activeTab?.loading == true, currentUrl in bookmarkedUrls, desktopOn),
                ::menuAction, ::dismissOverlay,
            )
            BrowserOverlay.Tabs -> TabSwitcherSheet(tabs, activeTabId,
                onActivate = { viewModel.captureActiveThumbnail(); viewModel.activateTab(it); dismissOverlay() },
                onClose = viewModel::closeTab, onNewTab = ::newTab,
                onCloseAll = { chrome.dispatch(BrowserChromeEvent.ShowOverlay(BrowserOverlay.CloseAll)) },
                onDismiss = ::dismissOverlay)
            BrowserOverlay.History, BrowserOverlay.Bookmarks -> {
                val bookmarks = chrome.state.overlay == BrowserOverlay.Bookmarks
                val entries = if (bookmarks) bookmarkPages else recentPages
                SavedPagesSheet(entries, bookmarks, ::navigate, viewModel::removeBookmark,
                    { chrome.dispatch(BrowserChromeEvent.ShowOverlay(BrowserOverlay.ClearHistory)) }, ::dismissOverlay)
            }
            BrowserOverlay.CloseAll -> AppConfirmDialog(
                title = stringResource(R.string.browser_close_all_tabs), text = stringResource(R.string.browser_close_all_message),
                confirmText = stringResource(R.string.browser_close_all), dismissText = stringResource(R.string.browser_cancel),
                onConfirm = { viewModel.closeAllTabs(); dismissOverlay(); chrome.dispatch(BrowserChromeEvent.Reveal) },
                onDismiss = ::dismissOverlay, destructive = true)
            BrowserOverlay.ClearHistory -> AppConfirmDialog(
                title = stringResource(R.string.browser_clear_history), text = stringResource(R.string.browser_clear_history_message),
                confirmText = stringResource(R.string.browser_clear_history), dismissText = stringResource(R.string.browser_cancel),
                onConfirm = { viewModel.clearHistory(); dismissOverlay() },
                onDismiss = ::dismissOverlay, destructive = true)
            null -> Unit
        }
        WebSessionDialogs(requests, onRetry = { viewModel.refreshOrStop(false) }, onLeave = ::closeActive)
    }
    if (isVisible && requests.fullScreenView != null) WebSessionFullScreen(requests)
}
