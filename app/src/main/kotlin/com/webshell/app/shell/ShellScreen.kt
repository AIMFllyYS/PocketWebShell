package com.webshell.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.app.R
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.designsystem.components.PageLoadIndicator
import com.webshell.core.webengine.compose.ShellWebViewHost
import com.webshell.core.webengine.resolveForceEnableZoom
import com.webshell.feature.browser.WebSessionDialogs
import com.webshell.feature.browser.WebSessionEmptyState
import com.webshell.feature.browser.WebSessionStatusMessage
import com.webshell.feature.browser.rememberWebSessionRequests
import com.webshell.feature.viewer.SafeMarkdown

/** Saved-site immersive launch. The configured app.id session is displayed, not a second random one. */
@Composable
fun ShellScreen(
    initialUrl: String,
    appId: String? = null,
    onLeave: () -> Unit,
    /**
     * A popup/OAuth window opened from this shell is a real, separate pooled
     * session (never this shell's own WebView). This shell has no tab
     * switcher of its own, so the adopted session hands off to the browser:
     * the caller leaves the shell, switches to the Browse tab, and turns
     * [sessionId] into a real tab there. Cookies stay shared (default
     * profile) either way — this is only about where the window is shown.
     */
    onAdoptWindow: (sessionId: String, initialUrl: String?) -> Unit = { _, _ -> },
    onOpenDownloads: () -> Unit = {},
    onSwitchKeepAlive: (sessionId: String, url: String) -> Unit = { _, _ -> },
    viewModel: SiteShellViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pullToRefresh by viewModel.pullToRefreshEnabled.collectAsStateWithLifecycle()
    val forceEnableZoom by viewModel.forceEnableZoomEnabled.collectAsStateWithLifecycle()
    val orb by viewModel.siteShellOrb.collectAsStateWithLifecycle()
    val bookmarkedUrls by viewModel.bookmarkedUrls.collectAsStateWithLifecycle()
    val ready = state as? SiteShellState.Ready
    val markdown = state as? SiteShellState.Markdown
    val config = ready?.takeIf { it.request == (initialUrl to appId) }?.config
    val sessionId = config?.sessionId
    var message by remember { mutableStateOf<String?>(null) }
    val requests = rememberWebSessionRequests(
        sessionId = sessionId, visible = markdown == null,
        onNewWindow = { request ->
            if (request.targetSessionId != request.sourceSessionId) {
                onAdoptWindow(request.targetSessionId, request.initialUrl)
            }
        },
        onMessage = { message = it },
    )
    val sessionListener = remember(config?.sessionId) {
        config?.sessionId?.let(viewModel::listenerFor)
    }
    val latestAdopt = rememberUpdatedState(onAdoptWindow)
    DisposableEffect(viewModel) {
        viewModel.adoptWindow = { sid, url -> latestAdopt.value(sid, url) }
        onDispose {
            viewModel.adoptWindow = null
            viewModel.cancelPendingOpen()
        }
    }
    LaunchedEffect(initialUrl, appId) { viewModel.open(initialUrl, appId) }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2500); message = null }
    }
    BackHandler(enabled = requests.fullScreenView != null) { requests.exitFullScreen() }
    BackHandler(enabled = requests.fullScreenView == null) { if (!viewModel.goBack()) onLeave() }

    Box(Modifier.fillMaxSize()) {
        when (state) {
            SiteShellState.Loading -> WebSessionEmptyState(
                title = stringResource(R.string.site_shell_opening),
                description = stringResource(R.string.site_shell_opening_hint),
                actionLabel = stringResource(R.string.site_shell_opening),
                onAction = {}, loading = true, modifier = Modifier.fillMaxSize(),
            )
            SiteShellState.Unavailable -> Column(Modifier.fillMaxSize()) {
                AppNavigationBar(stringResource(R.string.site_shell_unavailable), onBack = onLeave)
                WebSessionEmptyState(
                    title = stringResource(R.string.site_shell_unavailable),
                    description = stringResource(R.string.site_shell_unavailable_hint),
                    actionLabel = stringResource(R.string.site_shell_retry), onAction = viewModel::retryOpen,
                    modifier = Modifier.weight(1f),
                )
            }
            is SiteShellState.Markdown -> Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                AppNavigationBar(markdown?.title.orEmpty(), onBack = onLeave)
                SafeMarkdown(
                    content = markdown?.content.orEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            is SiteShellState.Ready -> if (config != null && sessionId != null) {
                ShellWebViewHost(
                    sessionId = sessionId,
                    // Until the live setting has been read at least once,
                    // keep whatever pullToRefresh the session was already
                    // opened with (see pullToRefreshEnabled's kdoc) — never
                    // overwrite it with a synthetic "not yet loaded" default.
                    configFactory = {
                        config.copy(
                            pullToRefresh = pullToRefresh ?: config.pullToRefresh,
                            forceEnableZoom = forceEnableZoom?.let {
                                resolveForceEnableZoom(
                                    desktopMode = config.desktopMode,
                                    localApp = config.localAppId != null,
                                    userEnabled = it,
                                )
                            } ?: config.forceEnableZoom,
                        )
                    },
                    listener = requests.listener,
                    sessionListener = sessionListener,
                    onReady = { viewModel.onReady(config) },
                )
            }
        }
        if (ready?.loadError == SiteShellLoadError.RENDERER_GONE) {
            WebSessionEmptyState(
                title = stringResource(R.string.site_shell_renderer_gone),
                description = stringResource(R.string.site_shell_renderer_gone_hint),
                actionLabel = stringResource(R.string.site_shell_retry),
                onAction = viewModel::reload,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
        if (state is SiteShellState.Ready) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                PageLoadIndicator(loading = ready?.loading == true, rawProgress = ready?.progress ?: 0, sessionKey = sessionId)
            }
            if (orb.enabled && !requests.busy) {
                SiteShellOrb(
                    posX = orb.x,
                    posY = orb.y,
                    parked = orb.parked,
                    canGoBack = ready?.canGoBack == true,
                    canGoForward = ready?.canGoForward == true,
                    loading = ready?.loading == true,
                    desktopMode = ready?.config?.desktopMode == true,
                    pageUrl = ready?.pageUrl.orEmpty(),
                    bookmarked = ready?.pageUrl.orEmpty() in bookmarkedUrls,
                    onPlacement = viewModel::setOrbPlacement,
                    onRefresh = viewModel::reload,
                    onStop = viewModel::stopLoading,
                    onBack = { viewModel.goBack() },
                    onForward = { viewModel.goForward() },
                    onDesktopMode = viewModel::setDesktopMode,
                    onBookmark = { viewModel.toggleBookmark() },
                    onOpenDownloads = onOpenDownloads,
                    onHideOrb = viewModel::hideOrb,
                    onLeave = onLeave,
                    currentSessionId = sessionId,
                    onSwitchKeepAlive = onSwitchKeepAlive,
                )
            }
        }
        message?.let {
            WebSessionStatusMessage(
                it,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
    }
    WebSessionDialogs(requests, onRetry = viewModel::reload, onLeave = onLeave)
    if (requests.fullScreenView != null) com.webshell.feature.browser.WebSessionFullScreen(requests)
}
