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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 我的：所有控件都接入持久化设置或真实系统入口。 */
@Composable
fun MeScreen(
    onKeepAliveServiceChanged: (Boolean) -> Unit = {},
    viewModel: MeViewModel = hiltViewModel(),
) {
    val settingsState by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        viewModel.refreshBatteryState(
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true,
        )
        viewModel.refreshSessions()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineMedium)
        Text(
            "桌面、后台与引擎状态",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        SectionCard(title = "主页布局") {
            NumberSetting(
                icon = Icons.Filled.GridView,
                title = "每行图标数",
                value = settingsState.gridColumns,
                range = 3..6,
                onValue = viewModel::setColumns,
            )
            NumberSetting(
                icon = Icons.Filled.GridView,
                title = "每页行数",
                value = settingsState.gridRows,
                range = 4..7,
                onValue = viewModel::setRows,
            )
            NumberSetting(
                icon = Icons.Filled.Info,
                title = "图标大小",
                value = settingsState.iconSizeDp,
                range = 44..72,
                suffix = " dp",
                onValue = viewModel::setIconSize,
            )
            NumberSetting(
                icon = Icons.Filled.Info,
                title = "图标圆角",
                value = settingsState.iconCornerRadiusPercent,
                range = 0..50,
                suffix = "%",
                onValue = viewModel::setIconCorner,
            )
            ToggleRow(
                title = "显示应用名称",
                checked = settingsState.showLabels,
                onCheckedChange = viewModel::setShowLabels,
            )
            ToggleRow(
                title = "显示页面指示器",
                checked = settingsState.showPageIndicator,
                onCheckedChange = viewModel::setShowPageIndicator,
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "后台与通知") {
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
            ToggleRow(
                title = "增强保活服务",
                subtitle = "仅对明确开启保活的网页应用生效；可随时关闭",
                checked = settingsState.keepAliveServiceEnabled,
                onCheckedChange = {
                    viewModel.setKeepAliveServiceEnabled(it)
                    onKeepAliveServiceChanged(it)
                },
            )
            Text(
                text = state.oemHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "运行中的后台会话") {
            if (state.runningSessions.isEmpty()) {
                Text(
                    "暂无正在保活的会话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                state.runningSessions.forEach { session ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                session.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(onClick = { viewModel.stopSession(session.sessionId) }) {
                            Text("结束")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "WebView 引擎") {
            Text(
                "版本 ${state.capabilities.webViewVersion.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "独立存储 Profile：${capabilityLabel(state.capabilities.multiProfile)} · " +
                    "文档启动注入：${capabilityLabel(state.capabilities.documentStartJs)} · " +
                    "算法深色：${capabilityLabel(state.capabilities.algorithmicDarkening)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun capabilityLabel(supported: Boolean): String = if (supported) "支持" else "降级"

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun NumberSetting(
    icon: ImageVector,
    title: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    onValue: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        SettingRow(
            icon = icon,
            title = title,
            subtitle = "$value$suffix",
        ) {}
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.count() - 2).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        icon = Icons.Filled.Info,
        title = title,
        subtitle = subtitle,
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
