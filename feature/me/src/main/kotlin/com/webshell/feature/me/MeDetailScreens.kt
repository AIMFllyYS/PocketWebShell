package com.webshell.feature.me

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.webshell.core.data.HomeSettings
import com.webshell.core.webengine.WebViewCapabilities

/** 二级页：主页布局（网格行列 + 图标样式）。 */
@Composable
internal fun LayoutSettingsPage(
    settings: HomeSettings,
    viewModel: MeViewModel,
    onBack: () -> Unit,
) {
    DetailPage(title = "主页布局", onBack = onBack) {
        SectionCard(title = "网格") {
            NumberSetting(
                icon = Icons.Filled.GridView,
                title = "每行图标数",
                value = settings.gridColumns,
                range = 3..6,
                onValue = viewModel::setColumns,
            )
            NumberSetting(
                icon = Icons.Filled.GridView,
                title = "每页行数",
                value = settings.gridRows,
                range = 4..7,
                onValue = viewModel::setRows,
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionCard(title = "图标") {
            NumberSetting(
                icon = Icons.Filled.Apps,
                title = "图标大小",
                value = settings.iconSizeDp,
                range = 44..72,
                suffix = " dp",
                onValue = viewModel::setIconSize,
            )
            NumberSetting(
                icon = Icons.Filled.RoundedCorner,
                title = "图标圆角",
                value = settings.iconCornerRadiusPercent,
                range = 0..50,
                suffix = "%",
                onValue = viewModel::setIconCorner,
            )
            ToggleRow(
                title = "显示应用名称",
                checked = settings.showLabels,
                onCheckedChange = viewModel::setShowLabels,
            )
            ToggleRow(
                title = "显示页面指示器",
                checked = settings.showPageIndicator,
                onCheckedChange = viewModel::setShowPageIndicator,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

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
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshRuntimeState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshRuntimeState()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DetailPage(title = "后台与通知", onBack = onBack) {
        SectionCard(title = "系统权限") {
            SettingRow(
                icon = Icons.Filled.BatterySaver,
                title = if (state.batteryWhitelisted) "电池优化：已豁免" else "电池优化：受系统限制",
                subtitle = if (state.batteryWhitelisted) {
                    "系统限制更少，但厂商策略、内存压力仍可能暂停网页"
                } else {
                    "点按打开系统授权，可提高后台会话存活率"
                },
                onClick = {
                    val directRequest = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}"))
                    val listSettings = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    runCatching {
                        context.startActivity(if (state.batteryWhitelisted) listSettings else directRequest)
                    }.onFailure { context.startActivity(listSettings) }
                },
            ) {}
            SettingRow(
                icon = Icons.Filled.Notifications,
                title = if (notificationsGranted) "通知：已允许" else "通知：未允许",
                subtitle = "后台保活状态与停止入口会显示在通知中",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !notificationsGranted
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                },
            ) {}
        }
        Spacer(Modifier.height(16.dp))
        SectionCard(title = "保活") {
            ToggleRow(
                title = "增强保活服务",
                subtitle = "仅对明确开启保活的网页应用生效；可随时关闭",
                checked = keepAliveEnabled,
                onCheckedChange = onKeepAliveChanged,
            )
            Text(
                text = state.oemHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 二级页：WebView 引擎版本与能力。 */
@Composable
internal fun EngineInfoPage(
    capabilities: WebViewCapabilities.Snapshot,
    onBack: () -> Unit,
) {
    DetailPage(title = "WebView 引擎", onBack = onBack) {
        SectionCard(title = "引擎信息") {
            Text(
                "版本 ${capabilities.webViewVersion.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "独立存储 Profile：${capabilityLabel(capabilities.multiProfile)} · " +
                    "文档启动注入：${capabilityLabel(capabilities.documentStartJs)} · " +
                    "算法深色：${capabilityLabel(capabilities.algorithmicDarkening)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun capabilityLabel(supported: Boolean): String = if (supported) "支持" else "降级"
