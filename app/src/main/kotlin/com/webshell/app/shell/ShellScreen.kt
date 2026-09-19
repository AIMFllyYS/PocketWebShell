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
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.feature.browser.R as BrowserR
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.app.R
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.designsystem.components.PageLoadIndicator
import com.webshell.core.webengine.CleartextGate
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
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pullToRefresh by viewModel.pullToRefreshEnabled.collectAsStateWithLifecycle()
    val forceEnableZoom by viewModel.forceEnableZoomEnabled.collectAsStateWithLifecycle()
    val orb by viewModel.siteShellOrb.collectAsStateWithLifecycle()
    val bookmarkedUrls by viewModel.bookmarkedUrls.collectAsStateWithLifecycle()
    val askAddToHome by viewModel.askAddToHome.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
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
    val latestSwitch = rememberUpdatedState(onSwitchKeepAlive)
    LaunchedEffect(initialUrl, appId) { viewModel.open(initialUrl, appId) }
    LaunchedEffect(markdown?.request, markdown?.temporary) {
        if (markdown?.temporary == true) {
            kotlinx.coroutines.delay(400)
            viewModel.offerAddToHome()
        }
    }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2500); message = null }
    }
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) { kotlinx.coroutines.delay(2500); viewModel.consumeStatusMessage() }
    }
    BackHandler(enabled = requests.fullScreenView != null) { requests.exitFullScreen() }
    BackHandler(enabled = requests.fullScreenView == null) {
        if (markdown != null || !viewModel.goBack()) onLeave()
    }

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
            is SiteShellState.Markdown -> SafeMarkdown(
                content = markdown?.content.orEmpty(),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
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
                if (CleartextGate.isCleartextHttp(ready?.pageUrl.orEmpty())) {
                    Text(
                        text = stringResource(BrowserR.string.browser_cleartext_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                PageLoadIndicator(loading = ready?.loading == true, rawProgress = ready?.progress ?: 0, sessionKey = sessionId)
            }
        }
        val showOrb = orb.enabled && !requests.busy &&
            (state is SiteShellState.Ready || state is SiteShellState.Markdown)
        if (showOrb) {
            val documentMode = markdown != null
            SiteShellOrb(
                posX = orb.x,
                posY = orb.y,
                parked = orb.parked,
                canGoBack = ready?.canGoBack == true,
                canGoForward = ready?.canGoForward == true,
                loading = ready?.loading == true,
                desktopMode = ready?.config?.desktopMode == true,
                desktopCapable = ready?.config?.localAppId == null,
                pageUrl = ready?.pageUrl.orEmpty(),
                bookmarked = ready?.pageUrl.orEmpty() in bookmarkedUrls,
                documentMode = documentMode,
                canAddToHome = markdown?.temporary == true,
                onPlacement = viewModel::setOrbPlacement,
                onRefresh = { if (documentMode) viewModel.retryOpen() else viewModel.reload() },
                onStop = viewModel::stopLoading,
                onBack = { viewModel.goBack() },
                onForward = { viewModel.goForward() },
                onDesktopMode = viewModel::setDesktopMode,
                onBookmark = { viewModel.toggleBookmark() },
                onOpenDownloads = onOpenDownloads,
                onHideOrb = viewModel::hideOrb,
                onLeave = onLeave,
                onAddToHome = {
                    viewModel.addIncomingToHome { id, url -> latestSwitch.value(id, url) }
                },
                currentSessionId = sessionId,
                onSwitchKeepAlive = onSwitchKeepAlive,
            )
        }
        val toast = message ?: statusMessage?.let { stringResource(it) }
        toast?.let {
            WebSessionStatusMessage(
                it,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
    }
    WebSessionDialogs(
        requests,
        onRetry = viewModel::reload,
        onLeave = onLeave,
        onCleartextContinued = { message = context.getString(BrowserR.string.browser_cleartext_continued) },
        onCleartextExit = {
            val url = ready?.pageUrl.orEmpty()
            if (url.isBlank() || url.equals("about:blank", ignoreCase = true)) onLeave()
        },
    )
    if (requests.fullScreenView != null) com.webshell.feature.browser.WebSessionFullScreen(requests)
    if (askAddToHome && markdown?.temporary == true) {
        AppConfirmDialog(
            title = stringResource(BrowserR.string.browser_add_home_title),
            text = stringResource(
                BrowserR.string.browser_add_home_message,
                markdown.title.ifBlank { stringResource(R.string.app_name) },
            ),
            confirmText = stringResource(BrowserR.string.browser_add_home_confirm),
            dismissText = stringResource(BrowserR.string.browser_add_home_dismiss),
            onConfirm = {
                viewModel.addIncomingToHome { id, url -> latestSwitch.value(id, url) }
            },
            onDismiss = viewModel::dismissAddToHome,
        )
    }
}
