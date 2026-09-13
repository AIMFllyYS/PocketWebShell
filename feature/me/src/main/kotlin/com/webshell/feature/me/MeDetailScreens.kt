package com.webshell.feature.me

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppSettingsSection
import com.webshell.core.designsystem.components.AppToggleRow
import com.webshell.core.webengine.WebViewCapabilities

/** 二级页：后台与通知（系统权限入口 + 增强保活）。 */
@Composable
internal fun BackgroundSettingsPage(
    state: MeUiState,
    keepAliveEnabled: Boolean,
    onKeepAliveChanged: (Boolean) -> Unit,
    onBatteryState: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }
    var settingsUnavailable by remember { mutableStateOf(false) }
    var filesAccessGranted by remember { mutableStateOf(hasHtmlFileAccess(context)) }

    fun openSystemSettings(intent: Intent, fallback: Intent? = null) {
        settingsUnavailable = runCatching { context.startActivity(intent) }.recoverCatching {
            if (fallback == null) throw it
            context.startActivity(fallback)
        }.isFailure
    }

    fun openFilesAccessSettings() {
        val pkg = Uri.parse("package:${context.packageName}")
        val chain = listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).setData(pkg),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(pkg),
        )
        settingsUnavailable = true
        for (intent in chain) {
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                settingsUnavailable = false
                return
            }
        }
    }

    fun refreshRuntimeState() {
        val powerManager = context.getSystemService(PowerManager::class.java)
        onBatteryState(
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true,
        )
        notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        filesAccessGranted = hasHtmlFileAccess(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshRuntimeState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshRuntimeState()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DetailPage(title = stringResource(R.string.me_background), onBack = onBack) {
        BackgroundSettingsContent(
            batteryWhitelisted = state.batteryWhitelisted,
            notificationsGranted = notificationsGranted,
            filesAccessGranted = filesAccessGranted,
            keepAliveEnabled = keepAliveEnabled,
            oemHint = state.oemHint,
            onKeepAliveChanged = onKeepAliveChanged,
            onBatteryClick = {
                val directRequest = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
                val listSettings = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                openSystemSettings(if (state.batteryWhitelisted) listSettings else directRequest, listSettings)
            },
            onNotificationClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else openSystemSettings(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            onFilesAccessClick = { openFilesAccessSettings() },
        )
        if (settingsUnavailable) Text(stringResource(R.string.me_system_settings_unavailable),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
internal fun BackgroundSettingsContent(
    batteryWhitelisted: Boolean,
    notificationsGranted: Boolean,
    filesAccessGranted: Boolean,
    keepAliveEnabled: Boolean,
    oemHint: String,
    onKeepAliveChanged: (Boolean) -> Unit,
    onBatteryClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onFilesAccessClick: () -> Unit,
) {
    AppSettingsSection(stringResource(R.string.me_system_permissions)) {
        AppListRow(
            title = stringResource(if (batteryWhitelisted) R.string.me_battery_allowed else R.string.me_battery_restricted),
            subtitle = stringResource(if (batteryWhitelisted) R.string.me_battery_allowed_hint else R.string.me_battery_restricted_hint),
            leadingIcon = Icons.Rounded.BatterySaver,
            leadingIconBackground = Color(0xFF34C759),
            onClick = onBatteryClick,
            trailing = { SettingsChevron() },
        )
        AppListDivider()
        AppListRow(
            title = stringResource(if (notificationsGranted) R.string.me_notifications_allowed else R.string.me_notifications_denied),
            subtitle = stringResource(R.string.me_notifications_hint),
            leadingIcon = Icons.Rounded.Notifications,
            leadingIconBackground = Color(0xFFFF3B30),
            onClick = onNotificationClick,
            trailing = { SettingsChevron() },
        )
        AppListDivider()
        AppListRow(
            title = stringResource(if (filesAccessGranted) R.string.me_files_access_allowed else R.string.me_files_access_denied),
            subtitle = stringResource(R.string.me_files_access_hint),
            leadingIcon = Icons.Rounded.Folder,
            leadingIconBackground = Color(0xFF007AFF),
            onClick = onFilesAccessClick,
            trailing = { SettingsChevron() },
        )
    }
    Spacer(Modifier.height(16.dp))
    AppSettingsSection(stringResource(R.string.me_keep_alive)) {
        AppToggleRow(
            title = stringResource(R.string.me_keep_alive_service),
            subtitle = stringResource(R.string.me_keep_alive_service_hint),
            checked = keepAliveEnabled,
            onCheckedChange = onKeepAliveChanged,
        )
        Text(oemHint, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
    }
    Spacer(Modifier.height(24.dp))
}

/** 二级页：悬浮球与浏览手势。 */
@Composable
internal fun FeatureSettingsPage(
    autoCollapse: Boolean,
    pullToRefresh: Boolean,
    forceEnableZoom: Boolean,
    siteShellOrb: Boolean,
    downloadCapsule: Boolean,
    newWindowAdopt: Boolean,
    onAutoCollapse: (Boolean) -> Unit,
    onPullToRefresh: (Boolean) -> Unit,
    onForceEnableZoom: (Boolean) -> Unit,
    onSiteShellOrb: (Boolean) -> Unit,
    onDownloadCapsule: (Boolean) -> Unit,
    onNewWindowAdopt: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    DetailPage(stringResource(R.string.me_features), onBack) {
        FeatureSettingsContent(
            autoCollapse,
            pullToRefresh,
            forceEnableZoom,
            siteShellOrb,
            downloadCapsule,
            newWindowAdopt,
            onAutoCollapse,
            onPullToRefresh,
            onForceEnableZoom,
            onSiteShellOrb,
            onDownloadCapsule,
            onNewWindowAdopt,
        )
    }
}

@Composable
internal fun FeatureSettingsContent(
    autoCollapse: Boolean,
    pullToRefresh: Boolean,
    forceEnableZoom: Boolean,
    siteShellOrb: Boolean,
    downloadCapsule: Boolean,
    newWindowAdopt: Boolean,
    onAutoCollapse: (Boolean) -> Unit,
    onPullToRefresh: (Boolean) -> Unit,
    onForceEnableZoom: (Boolean) -> Unit,
    onSiteShellOrb: (Boolean) -> Unit,
    onDownloadCapsule: (Boolean) -> Unit,
    onNewWindowAdopt: (Boolean) -> Unit,
) {
    AppSettingsSection(stringResource(R.string.me_browsing_experience)) {
        AppToggleRow(
            title = stringResource(R.string.me_site_shell_orb),
            subtitle = stringResource(R.string.me_site_shell_orb_hint),
            checked = siteShellOrb,
            onCheckedChange = onSiteShellOrb,
        )
        AppListDivider(hasLeadingIcon = false)
        AppToggleRow(
            title = stringResource(R.string.me_download_orb),
            subtitle = stringResource(R.string.me_download_orb_hint),
            checked = downloadCapsule,
            onCheckedChange = onDownloadCapsule,
        )
        AppListDivider(hasLeadingIcon = false)
        AppToggleRow(
            title = stringResource(R.string.me_auto_collapse),
            subtitle = stringResource(R.string.me_auto_collapse_hint),
            checked = autoCollapse,
            onCheckedChange = onAutoCollapse,
        )
        AppListDivider(hasLeadingIcon = false)
        AppToggleRow(
            title = stringResource(R.string.me_pull_to_refresh),
            subtitle = stringResource(R.string.me_pull_to_refresh_hint),
            checked = pullToRefresh,
            onCheckedChange = onPullToRefresh,
        )
        AppListDivider(hasLeadingIcon = false)
        AppToggleRow(
            title = stringResource(R.string.me_force_enable_zoom),
            subtitle = stringResource(R.string.me_force_enable_zoom_hint),
            checked = forceEnableZoom,
            onCheckedChange = onForceEnableZoom,
        )
        AppListDivider(hasLeadingIcon = false)
        AppToggleRow(
            title = stringResource(R.string.me_new_window_adopt),
            subtitle = stringResource(R.string.me_new_window_adopt_hint),
            checked = newWindowAdopt,
            onCheckedChange = onNewWindowAdopt,
        )
    }
    Spacer(Modifier.height(24.dp))
}

/** 二级页：WebView 引擎版本与能力。 */
@Composable
internal fun EngineInfoPage(
    capabilities: WebViewCapabilities.Snapshot,
    onBack: () -> Unit,
) {
    DetailPage(stringResource(R.string.me_engine), onBack) {
        EngineInfoContent(capabilities)
    }
}

@Composable
internal fun EngineInfoContent(
    capabilities: WebViewCapabilities.Snapshot,
) {
        AppSettingsSection(title = stringResource(R.string.me_engine_info)) {
            AppListRow(
                title = stringResource(R.string.me_engine_version),
                subtitle = capabilities.webViewVersion ?: stringResource(R.string.me_unknown),
            )
            AppListDivider(hasLeadingIcon = false)
            EngineCapabilityRow(stringResource(R.string.me_engine_profiles), capabilities.multiProfile)
            AppListDivider(hasLeadingIcon = false)
            EngineCapabilityRow(stringResource(R.string.me_engine_document_start), capabilities.documentStartJs)
            AppListDivider(hasLeadingIcon = false)
            EngineCapabilityRow(
                stringResource(R.string.me_engine_darkening),
                capabilities.algorithmicDarkening,
            )
        }
        Spacer(Modifier.height(24.dp))
}

private fun hasHtmlFileAccess(context: android.content.Context): Boolean {
    val sdk = Build.VERSION.SDK_INT
    if (sdk >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) return true
    if (sdk == 29) {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }
    return false
}

@Composable
private fun EngineCapabilityRow(title: String, supported: Boolean) {
    AppListRow(
        title = title,
        trailing = {
            Text(
                stringResource(if (supported) R.string.me_supported else R.string.me_fallback),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
