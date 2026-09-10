package com.webshell.feature.me

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.LocalTransitionStyle

internal enum class MeSection { APPEARANCE, FONT, LAYOUT, BACKGROUND, ENGINE, STORAGE, DATA, UPDATE_LOG, DEVELOPER, SESSIONS }

/** Route/state collection only. Playbook is aggregated by app, not by this feature. */
@Composable
fun MeScreen(
    onKeepAliveServiceChanged: (Boolean) -> Unit = {},
    onOpenPlaybook: () -> Unit = {},
    onHideLauncherDock: (Boolean) -> Unit = {},
    /**
     * "结束会话" must actually stop the session's renderer and, if nothing
     * else needs it, the foreground service — not merely drop it from the
     * keep-alive list. [MeViewModel] has no access to the app-level session
     * controller (feature modules do not depend on `app`), so the real
     * teardown is bridged up to the composition root, mirroring
     * [onKeepAliveServiceChanged].
     */
    onStopSessions: (List<String>) -> Unit = {},
    viewModel: MeViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fontSaveState by viewModel.fontSaveState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var section by rememberSaveable { mutableStateOf<MeSection?>(null) }
    var pendingStopIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSessions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.refreshSessions()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun goBack() {
        if (section == MeSection.FONT) viewModel.resetFontSaveState()
        section = if (section == MeSection.FONT) MeSection.APPEARANCE else null
    }
    BackHandler(enabled = section != null && fontSaveState != FontSaveState.Saving) { goBack() }
    val hideDock = rememberUpdatedState(onHideLauncherDock)
    LaunchedEffect(section) { hideDock.value(section == MeSection.SESSIONS) }
    DisposableEffect(Unit) {
        onDispose { hideDock.value(false) }
    }
    LaunchedEffect(fontSaveState) {
        if (fontSaveState == FontSaveState.Saved) {
            section = MeSection.APPEARANCE
            viewModel.resetFontSaveState()
        }
    }
    val transitionStyle = LocalTransitionStyle.current
    AnimatedContent(
        targetState = section,
        transitionSpec = { AppMotion.detailEnterFor(transitionStyle) togetherWith AppMotion.detailExitFor(transitionStyle) },
        label = "me-section",
    ) { target ->
        when (target) {
            null -> MeHome(
                state = state,
                onRequestStop = { pendingStopIds = listOf(it) },
                onOpenSection = { section = it },
            )
            MeSection.APPEARANCE -> AppearanceSettingsPage(
                settings = settings,
                onThemeMode = viewModel::setThemeMode,
                onTransitionStyle = viewModel::setTransitionStyle,
                onPickWallpaper = viewModel::setPhotoWallpaper,
                onOpenFonts = { viewModel.resetFontSaveState(); section = MeSection.FONT },
                onBack = ::goBack,
            )
            MeSection.FONT -> FontSettingsPage(
                settings = settings,
                saving = fontSaveState == FontSaveState.Saving,
                failed = fontSaveState == FontSaveState.Failed,
                onApply = viewModel::setAppTypography,
                onBack = ::goBack,
            )
            MeSection.LAYOUT -> LayoutSettingsPage(settings, viewModel::onLayoutAction, ::goBack)
            MeSection.BACKGROUND -> BackgroundSettingsPage(
                state = state,
                keepAliveEnabled = settings.keepAliveServiceEnabled,
                onKeepAliveChanged = { viewModel.setKeepAliveServiceEnabled(it); onKeepAliveServiceChanged(it) },
                onBatteryState = viewModel::refreshBatteryState,
                onBack = ::goBack,
            )
            MeSection.ENGINE -> EngineInfoPage(
                capabilities = state.capabilities,
                autoCollapse = settings.browserAutoCollapse,
                pullToRefresh = settings.pullToRefreshEnabled,
                onAutoCollapse = viewModel::setBrowserAutoCollapse,
                onPullToRefresh = viewModel::setPullToRefreshEnabled,
                onBack = ::goBack,
            )
            MeSection.STORAGE -> StorageManagementPage(onBack = ::goBack)
            MeSection.DATA -> DataManagementPage(onBack = ::goBack)
            MeSection.UPDATE_LOG -> UpdateLogPage(onBack = ::goBack)
            MeSection.DEVELOPER -> DeveloperCenterPage(onBack = ::goBack, onOpenPlaybook = onOpenPlaybook)
            MeSection.SESSIONS -> SessionsPage(
                sessions = state.runningSessions,
                onStopSessions = { ids -> viewModel.stopSessions(ids); onStopSessions(ids) },
                onBack = ::goBack,
            )
        }
    }
    if (pendingStopIds.isNotEmpty()) {
        SessionStopConfirmDialog(
            count = pendingStopIds.size,
            onConfirm = {
                viewModel.stopSessions(pendingStopIds)
                onStopSessions(pendingStopIds)
                pendingStopIds = emptyList()
            },
            onDismiss = { pendingStopIds = emptyList() },
        )
    }
}
