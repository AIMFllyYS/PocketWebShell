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
import com.webshell.app.shell.ShellScreen
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.LocalIsDarkTheme
import com.webshell.feature.add.AddScreen
import com.webshell.feature.browser.BrowserScreen
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
    // Keep tab drafts/scroll anchors alive while a website temporarily owns the whole screen.
    val stateHolder = rememberSaveableStateHolder()
    val homeVisible = selectedTab == MainTab.HOME && openedUrl == null
    SystemBarAppearance(lightIcons = homeVisible || LocalIsDarkTheme.current)

    // Must run before the immersive early-return: external launches need registration too.
    LaunchedEffect(launchUrl) {
        launchUrl?.let(viewModel::registerKeepAliveFor)
    }
    openedUrl?.let { url ->
        BackHandler { openedUrl = null }
        ShellScreen(initialUrl = url, immersive = true)
        return
    }
    BackHandler(enabled = selectedTab != MainTab.HOME) { selectedTab = MainTab.HOME }

    val hazeState = remember { HazeState() }
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()

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
                val bottomClearance = if (tab == MainTab.HOME) {
                    HomeDockHeight + 20.dp
                } else {
                    TabBarHeight + 20.dp
                }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (tab == MainTab.HOME) LauncherBackdrop(Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().padding(
                            start = safeInsets.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                            end = safeInsets.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                            top = safeInsets.calculateTopPadding(),
                            bottom = safeInsets.calculateBottomPadding() + bottomClearance,
                        ),
                    ) {
                        stateHolder.SaveableStateProvider(tab.name) {
                            when (tab) {
                                MainTab.HOME -> HomeScreen(
                                    wallpaperBacked = true,
                                    onLaunch = { appId, _ -> viewModel.launchApp(appId) { openedUrl = it } },
                                    onAddRequested = { selectedTab = MainTab.ADD },
                                )
                                MainTab.ADD -> AddScreen(onCreated = { selectedTab = MainTab.HOME })
                                MainTab.BROWSE -> BrowserScreen()
                                MainTab.ME -> MeScreen(
                                    onKeepAliveServiceChanged = viewModel::setKeepAliveServiceEnabled,
                                )
                            }
                        }
                    }
                }
            }
        }
        LauncherDock(
            selectedTab = selectedTab,
            onSelect = { selectedTab = it },
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
