package com.webshell.feature.me

import android.net.Uri
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.data.update.AppUpdateChecker
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.LocalTransitionStyle

internal enum class MeSection {
    APPEARANCE, FONT, LAYOUT, BACKGROUND, FEATURES, ENGINE, STORAGE, DATA,
    LEGAL, PRIVACY, TERMS, LICENSE, CONTRIBUTE,
    UPDATE_LOG, DEVELOPER, SESSIONS,
}

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
    onOpenInBrowser: (String) -> Unit = {},
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
    val openInBrowser = rememberUpdatedState(onOpenInBrowser)
    fun openHttps(url: String) {
        acceptedHttpsUrl(url)?.let(openInBrowser.value)
    }
    fun goBack() {
        if (section == MeSection.FONT) viewModel.resetFontSaveState()
        section = when (section) {
            MeSection.FONT -> MeSection.APPEARANCE
            MeSection.PRIVACY, MeSection.TERMS, MeSection.LICENSE, MeSection.CONTRIBUTE -> MeSection.LEGAL
            else -> null
        }
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
                onCheckUpdate = viewModel::checkForUpdate,
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
            MeSection.FEATURES -> FeatureSettingsPage(
                autoCollapse = settings.browserAutoCollapse,
                pullToRefresh = settings.pullToRefreshEnabled,
                forceEnableZoom = settings.forceEnableZoomEnabled,
                siteShellOrb = settings.siteShellOrbEnabled,
                downloadCapsule = settings.downloadCapsuleEnabled,
                newWindowAdopt = settings.siteShellNewWindowPolicy != com.webshell.core.data.SITE_SHELL_NEW_WINDOW_REPLACE,
                onAutoCollapse = viewModel::setBrowserAutoCollapse,
                onPullToRefresh = viewModel::setPullToRefreshEnabled,
                onForceEnableZoom = viewModel::setForceEnableZoomEnabled,
                onSiteShellOrb = viewModel::setSiteShellOrbEnabled,
                onDownloadCapsule = viewModel::setDownloadCapsuleEnabled,
                onNewWindowAdopt = viewModel::setSiteShellNewWindowAdopt,
                onBack = ::goBack,
            )
            MeSection.ENGINE -> EngineInfoPage(
                capabilities = state.capabilities,
                onBack = ::goBack,
            )
            MeSection.STORAGE -> StorageManagementPage(onBack = ::goBack)
            MeSection.DATA -> DataManagementPage(onBack = ::goBack)
            MeSection.LEGAL -> LegalHubPage(
                onOpenSection = { section = it },
                onOpenHttps = ::openHttps,
                onBack = ::goBack,
            )
            MeSection.PRIVACY -> LegalMarkdownPage(
                title = stringResource(R.string.me_privacy),
                rawResId = R.raw.privacy,
                onOpenHttps = ::openHttps,
                onBack = ::goBack,
            )
            MeSection.TERMS -> LegalMarkdownPage(
                title = stringResource(R.string.me_terms),
                rawResId = R.raw.terms,
                onOpenHttps = ::openHttps,
                onBack = ::goBack,
            )
            MeSection.LICENSE -> LicenseNoticePage(onOpenHttps = ::openHttps, onBack = ::goBack)
            MeSection.CONTRIBUTE -> ContributePage(onOpenHttps = ::openHttps, onBack = ::goBack)
            MeSection.UPDATE_LOG -> UpdateLogPage(onBack = ::goBack)
            MeSection.DEVELOPER -> DeveloperCenterPage(
                onBack = ::goBack,
                onOpenPlaybook = onOpenPlaybook,
                onPreviewUpdate = viewModel::previewUpdatePrompt,
            )
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
    when (val prompt = state.updatePrompt) {
        is UpdatePrompt.UpToDate -> AppConfirmDialog(
            title = stringResource(R.string.me_update_latest_title),
            text = stringResource(R.string.me_update_latest_text, prompt.version),
            confirmText = stringResource(R.string.me_ok),
            onConfirm = viewModel::dismissUpdatePrompt,
            onDismiss = viewModel::dismissUpdatePrompt,
        )
        is UpdatePrompt.Available -> {
            val offer = prompt.offer
            UpdateAvailableDialog(
                offer = offer,
                onOpenHttps = ::openHttps,
                onConfirm = {
                    viewModel.dismissUpdatePrompt()
                    openHttps(offer.downloadUrl)
                },
                onDismiss = viewModel::dismissUpdatePrompt,
            )
        }
        UpdatePrompt.Failed -> AppConfirmDialog(
            title = stringResource(R.string.me_update_failed_title),
            text = stringResource(R.string.me_update_failed_text),
            confirmText = stringResource(R.string.me_update_open_website),
            dismissText = stringResource(R.string.me_cancel),
            onConfirm = {
                viewModel.dismissUpdatePrompt()
                openHttps(AppUpdateChecker.WEBSITE_URL)
            },
            onDismiss = viewModel::dismissUpdatePrompt,
        )
        null -> Unit
    }
}

/** Accepts https URLs with a host and no userInfo. */
internal fun acceptedHttpsUrl(url: String): String? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
    return url
}
