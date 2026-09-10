package com.webshell.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.app.catalog.PlaybookScreen
import com.webshell.app.shell.ShellScreen
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.LocalIsDarkTheme
import com.webshell.core.designsystem.theme.LocalOverlayClearance
import androidx.compose.runtime.CompositionLocalProvider
import com.webshell.feature.add.AddScreen
import com.webshell.feature.browser.BrowserScreen
import com.webshell.feature.browser.BrowserViewModel
import com.webshell.feature.browser.rememberBrowserChromeController
import com.webshell.feature.browser.BrowserChromeEvent
import com.webshell.feature.home.HomeScreen
import com.webshell.feature.me.MeScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** App shell owns insets, the full-bleed backdrop and the only live glass surface. */
@Composable
fun MainScaffold(
    launchUrl: String? = null,
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
    // Keep tab drafts/scroll anchors alive while a website temporarily owns the whole screen.
    val stateHolder = rememberSaveableStateHolder()
    val homeVisible = selectedTab == MainTab.HOME && openedUrl == null && !playbookOpen
    SystemBarAppearance(lightIcons = homeVisible || LocalIsDarkTheme.current)

    if (playbookOpen) {
        PlaybookScreen(onBack = { playbookOpen = false })
        return
    }
    openedUrl?.let { url ->
        val leave = { openedUrl = null; openedAppId = null }
        BackHandler { leave() }
        ShellScreen(
            initialUrl = url,
            appId = openedAppId,
            onLeave = leave,
            onAdoptWindow = { adoptedSessionId, adoptedUrl ->
                leave()
                selectedTab = MainTab.BROWSE
                browserViewModel.createTabForSession(adoptedSessionId, adoptedUrl ?: "about:blank", activate = true)
            },
        )
        return
    }
    BackHandler(enabled = selectedTab != MainTab.HOME) { selectedTab = MainTab.HOME }

    var hideLauncherDock by rememberSaveable { mutableStateOf(false) }
    val hazeState = remember { HazeState() }
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val navBottom = safeInsets.calculateBottomPadding()
    // A real page manages its own bottom inset (parentHandlesInsets + the
    // floating Dock auto-collapses over it) — but the start page/empty-tabs
    // prompt is ordinary scrolling content with no such mechanism, so it must
    // reserve the same clearance every other tab reserves, or the
    // permanently-revealed BrowserDockHost covers its bottom edge.
    val browserTabs by browserViewModel.tabs.collectAsStateWithLifecycle()
    val browserActiveTabId by browserViewModel.activeTabId.collectAsStateWithLifecycle()
    val browserHasPage = browserTabs.firstOrNull { it.tabId == browserActiveTabId }
        ?.url?.let { it.isNotBlank() && it != "about:blank" } == true
    val overlayClearance = when {
        selectedTab == MainTab.HOME -> 0.dp
        selectedTab == MainTab.BROWSE -> if (browserHasPage) 0.dp else navBottom + measuredDockHeight(MainTab.BROWSE) + 20.dp
        hideLauncherDock -> navBottom + 12.dp
        else -> navBottom + measuredDockHeight(selectedTab) + 20.dp
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().hazeSource(state = hazeState)) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(AppMotion.NormalMs),
                label = "main-tab",
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                // Background and viewport follow this transition branch, not the target tab.
                // The outgoing desktop therefore keeps both its wallpaper and fixed grid bounds.
                val bottomClearance = when (tab) {
                    MainTab.HOME -> HomeDockHeight + 20.dp
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
                                        onLaunch = { appId, _ -> viewModel.launchApp(appId) { url, id -> openedAppId = id; openedUrl = url } },
                                        onAddRequested = { selectedTab = MainTab.ADD },
                                    )
                                    MainTab.ADD -> AddScreen(onCreated = { selectedTab = MainTab.HOME })
                                    MainTab.BROWSE -> BrowserScreen(
                                        viewModel = browserViewModel,
                                        chrome = browserChrome,
                                        isVisible = selectedTab == MainTab.BROWSE,
                                        autoCollapse = browserPreferences.autoCollapse,
                                        pullToRefresh = browserPreferences.pullToRefresh,
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
        if (selectedTab == MainTab.BROWSE) BrowserDockHost(
            chrome = browserChrome, preferences = browserPreferences,
            onSelect = { selectedTab = it; if (it != MainTab.ME) hideLauncherDock = false; if (it == MainTab.BROWSE) browserChrome.dispatch(BrowserChromeEvent.Reveal) },
            onAnchorChanged = viewModel::setBrowserOrbPosition,
        ) else if (!hideLauncherDock) LauncherDock(
            selectedTab = selectedTab,
            onSelect = { selectedTab = it; if (it != MainTab.ME) hideLauncherDock = false },
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
