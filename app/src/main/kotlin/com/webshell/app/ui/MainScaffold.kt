package com.webshell.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.app.catalog.PlaybookScreen
import com.webshell.app.download.DownloadViewModel
import com.webshell.app.download.GlobalDownloadHost
import com.webshell.app.shell.ShellScreen
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.designsystem.theme.LocalIsDarkTheme
import com.webshell.core.designsystem.theme.LocalOverlayClearance
import com.webshell.core.designsystem.components.glassSurface
import com.webshell.core.designsystem.components.staticGlassSurface
import androidx.compose.runtime.CompositionLocalProvider
import com.webshell.feature.add.AddScreen
import com.webshell.feature.browser.BrowserScreen
import com.webshell.feature.browser.BrowserViewModel
import com.webshell.feature.browser.rememberBrowserChromeController
import com.webshell.feature.browser.BrowserChromeEvent
import com.webshell.feature.home.HOME_EDIT_TOOLBAR_CLEARANCE_DP
import com.webshell.feature.home.HomeEditChromeState
import com.webshell.feature.home.HomeEditToolbar
import com.webshell.feature.home.HomeScreen
import com.webshell.feature.me.MeScreen
import com.webshell.app.incoming.IncomingMountResult
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.feature.viewer.IncomingOpenCandidate
import com.webshell.feature.viewer.SafeMarkdown
import com.webshell.feature.viewer.ViewerFailure
import com.webshell.feature.viewer.R as ViewerR
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** App shell owns insets, the full-bleed backdrop and the only live glass surface. */
@Composable
fun MainScaffold(
    launchUrl: String? = null,
    incoming: IncomingOpenCandidate? = null,
    onIncomingLeave: () -> Unit = {},
    suppressLiveGlass: Boolean = false,
    viewModel: MainScaffoldViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var openedUrl by rememberSaveable { mutableStateOf(launchUrl) }
    var openedAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var playbookOpen by rememberSaveable { mutableStateOf(false) }
    val browserChrome = rememberBrowserChromeController()
    val browserPreferences by viewModel.browserPreferences.collectAsStateWithLifecycle()
    // Hoisted above every tab branch (including the site-shell early return
    // below) so it exists for the whole app session: a saved-site/direct
    // shell can hand a popup/OAuth window off to the browser even if the
    // Browse tab itself has never been opened yet.
    val browserViewModel: BrowserViewModel = hiltViewModel()
    val downloadViewModel: DownloadViewModel = hiltViewModel()
    // Keep tab drafts/scroll anchors alive while a website temporarily owns the whole screen.
    val stateHolder = rememberSaveableStateHolder()
    var incomingError by remember { mutableStateOf<ViewerFailure?>(null) }
    val tabsHydrated by browserViewModel.tabsHydrated.collectAsStateWithLifecycle()
    val homeVisible = selectedTab == MainTab.HOME && openedUrl == null && !playbookOpen
    SystemBarAppearance(lightIcons = homeVisible || LocalIsDarkTheme.current)

    LaunchedEffect(incoming?.token, tabsHydrated) {
        if (!tabsHydrated) return@LaunchedEffect
        val candidate = incoming ?: return@LaunchedEffect
        selectedTab = MainTab.BROWSE
        openedUrl = null
        openedAppId = null
        playbookOpen = false
        browserChrome.dispatch(BrowserChromeEvent.Reveal)
        when (val mounted = viewModel.mountIncoming(candidate, browserViewModel.incomingReuseTokens())) {
            is IncomingMountResult.Html -> browserViewModel.openIncomingHtml(
                startUrl = mounted.startUrl,
                title = mounted.title,
                displayPath = mounted.displayPath,
                localAppId = mounted.localAppId,
                sourceKey = mounted.sourceKey,
            )
            is IncomingMountResult.Markdown -> browserViewModel.openIncomingMarkdown(
                title = mounted.title,
                displayPath = mounted.displayPath,
                content = mounted.content,
                localAppId = mounted.localAppId,
                sourceKey = mounted.sourceKey,
            )
            is IncomingMountResult.ReuseHome -> {
                selectedTab = MainTab.HOME
                openedAppId = mounted.appId
                openedUrl = mounted.url
            }
            is IncomingMountResult.ReuseTab -> {
                browserViewModel.activateIncomingBySourceKey(mounted.sourceKey, mounted.html)
            }
            is IncomingMountResult.Failed -> incomingError = mounted.reason
        }
        onIncomingLeave()
    }

    Box(Modifier.fillMaxSize()) {
    val siteUrl = openedUrl
    if (playbookOpen) {
        PlaybookScreen(onBack = { playbookOpen = false })
    } else if (siteUrl != null) {
        val leave = { openedUrl = null; openedAppId = null }
        BackHandler { leave() }
        ShellScreen(
            initialUrl = siteUrl,
            appId = openedAppId,
            onLeave = leave,
            onOpenDownloads = downloadViewModel::showHistory,
            onAdoptWindow = { adoptedSessionId, adoptedUrl ->
                leave()
                selectedTab = MainTab.BROWSE
                browserViewModel.createTabForSession(
                    adoptedSessionId,
                    adoptedUrl ?: "about:blank",
                    activate = true,
                    restoreStartUrlIfBlank = false,
                )
                browserChrome.dispatch(BrowserChromeEvent.Reveal)
            },
            onSwitchKeepAlive = { appId, url ->
                openedAppId = appId
                openedUrl = url
            },
        )
    } else {
    BackHandler(enabled = selectedTab != MainTab.HOME) { selectedTab = MainTab.HOME }

    var hideLauncherDock by rememberSaveable { mutableStateOf(false) }
    var homeEditChrome by remember { mutableStateOf<HomeEditChromeState?>(null) }
    val hazeState = remember { HazeState() }
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Start page / empty tabs use LauncherDock; content must reserve clearance inside the scroll.
    val browserShowsWebView by browserViewModel.showsWebView.collectAsStateWithLifecycle()
    val browserDockIsStatic = selectedTab == MainTab.BROWSE && browserShowsWebView
    val showHomeEditBar = selectedTab == MainTab.HOME && homeEditChrome != null
    val launcherDockDrawn = !browserDockIsStatic && !hideLauncherDock && !showHomeEditBar
    val liveGlass = !suppressLiveGlass && !browserDockIsStatic && (launcherDockDrawn || showHomeEditBar)
    val overlayClearance = when {
        selectedTab == MainTab.HOME -> 0.dp
        browserDockIsStatic -> 0.dp
        !launcherDockDrawn -> navBarBottom + 12.dp
        else -> navBarBottom + measuredDockHeight(selectedTab) + 20.dp
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().then(if (liveGlass) Modifier.hazeSource(state = hazeState) else Modifier)) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(AppMotion.NormalMs),
                label = "main-tab",
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                // Background and viewport follow this transition branch, not the target tab.
                // The outgoing desktop therefore keeps both its wallpaper and fixed grid bounds.
                val bottomClearance = when (tab) {
                    MainTab.HOME -> maxOf(HomeDockHeight + 20.dp, HOME_EDIT_TOOLBAR_CLEARANCE_DP.dp)
                    else -> 0.dp
                }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (tab == MainTab.HOME) LauncherBackdrop(Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().padding(
                            start = safeInsets.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                            end = safeInsets.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                            top = safeInsets.calculateTopPadding(),
                            bottom = if (tab == MainTab.HOME) {
                                safeInsets.calculateBottomPadding() + bottomClearance
                            } else {
                                0.dp
                            },
                        ).consumeWindowInsets(safeInsets),
                    ) {
                        CompositionLocalProvider(LocalOverlayClearance provides overlayClearance) {
                            stateHolder.SaveableStateProvider(tab.name) {
                                when (tab) {
                                    MainTab.HOME -> HomeScreen(
                                        wallpaperBacked = true,
                                        onLaunch = { appId, url ->
                                            openedAppId = appId
                                            openedUrl = url
                                        },
                                        onAddRequested = { selectedTab = MainTab.ADD },
                                        onEditChromeChange = { homeEditChrome = it },
                                    )
                                    MainTab.ADD -> AddScreen(onCreated = { selectedTab = MainTab.HOME })
                                    MainTab.BROWSE -> BrowserScreen(
                                        viewModel = browserViewModel,
                                        chrome = browserChrome,
                                        isVisible = selectedTab == MainTab.BROWSE,
                                        autoCollapse = browserPreferences.autoCollapse,
                                        pullToRefresh = browserPreferences.pullToRefresh,
                                        onOpenDownloads = downloadViewModel::showHistory,
                                        onLeaveToHome = { selectedTab = MainTab.HOME },
                                        documentContent = { tab ->
                                            SafeMarkdown(
                                                content = tab.markdownContent.orEmpty(),
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg)
                                                    .padding(bottom = LocalOverlayClearance.current),
                                            )
                                        },
                                    )
                                    MainTab.ME -> MeScreen(
                                        onKeepAliveServiceChanged = viewModel::setKeepAliveServiceEnabled,
                                        onOpenPlaybook = { playbookOpen = true },
                                        onHideLauncherDock = { hideLauncherDock = it },
                                        onStopSessions = viewModel::closeSessions,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (browserDockIsStatic) BrowserDockHost(
            chrome = browserChrome, preferences = browserPreferences,
            onSelect = { selectedTab = it; if (it != MainTab.ME) hideLauncherDock = false; if (it == MainTab.BROWSE) browserChrome.dispatch(BrowserChromeEvent.Reveal) },
            onAnchorChanged = viewModel::setBrowserOrbPosition,
        ) else if (showHomeEditBar) {
            val chrome = homeEditChrome
            if (chrome != null) {
                HomeEditToolbar(
                    state = chrome,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 500.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .then(
                            if (liveGlass) {
                                Modifier.glassSurface(hazeState, shape = RoundedCornerShape(34.dp))
                            } else {
                                Modifier.staticGlassSurface(shape = RoundedCornerShape(34.dp))
                            },
                        ),
                )
            }
        } else if (launcherDockDrawn) LauncherDock(
            selectedTab = selectedTab,
            onSelect = { selectedTab = it; if (it != MainTab.ME) hideLauncherDock = false },
            hazeState = hazeState,
            liveGlass = liveGlass,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }
    incomingError?.let { reason ->
        val hint = when (reason) {
            ViewerFailure.UNSUPPORTED -> stringResource(ViewerR.string.viewer_unsupported_hint)
            ViewerFailure.TOO_LARGE -> stringResource(ViewerR.string.viewer_too_large_hint)
            ViewerFailure.UNREADABLE -> stringResource(ViewerR.string.viewer_unreadable_hint)
        }
        AppConfirmDialog(
            title = stringResource(ViewerR.string.viewer_unavailable),
            text = hint,
            confirmText = stringResource(ViewerR.string.viewer_close),
            onConfirm = { incomingError = null },
            onDismiss = { incomingError = null },
        )
    }
    GlobalDownloadHost(viewModel = downloadViewModel)
    }
}

/** Real Android bars use the active surface contrast, not the device's theme. */
@Composable
private fun SystemBarAppearance(lightIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, lightIcons) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = !lightIcons
        controller?.isAppearanceLightNavigationBars = !lightIcons
        onDispose {
            previousStatus?.let { controller?.isAppearanceLightStatusBars = it }
            previousNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
}
