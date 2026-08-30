package com.webshell.feature.me

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import com.webshell.core.data.HomeSettings
import com.webshell.core.data.THEME_MODE_DARK
import com.webshell.core.data.THEME_MODE_LIGHT
import com.webshell.core.data.THEME_MODE_PHOTO
import com.webshell.core.data.THEME_MODE_SYSTEM
import com.webshell.core.webengine.WebViewCapabilities
import java.io.File

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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
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
        SectionCard(title = "引擎信息", edgeToEdge = false) {
            Text(
                "版本 ${capabilities.webViewVersion.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "独立存储 Profile：${capabilityLabel(capabilities.multiProfile)} · " +
                    "文档启动注入：${capabilityLabel(capabilities.documentStartJs)} · " +
                    "算法深色：${capabilityLabel(capabilities.algorithmicDarkening)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun capabilityLabel(supported: Boolean): String = if (supported) "支持" else "降级"

/** 二级页：外观与主题（跟随系统/纯白/纯黑/照片壁纸）。 */
@Composable
internal fun AppearanceSettingsPage(
    settings: HomeSettings,
    viewModel: MeViewModel,
    onBack: () -> Unit,
) {
    val pickWallpaper = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.setPhotoWallpaper(uri) }

    DetailPage(title = "外观与主题", onBack = onBack) {
        SectionCard(title = "主题模式") {
            ThemeModeRow(
                title = "跟随系统",
                subtitle = "随系统深浅色自动切换",
                selected = settings.themeMode == THEME_MODE_SYSTEM,
                onClick = { viewModel.setThemeMode(THEME_MODE_SYSTEM) },
            )
            ThemeModeRow(
                title = "纯白",
                subtitle = "苹果式纯白基底，始终浅色",
                selected = settings.themeMode == THEME_MODE_LIGHT,
                onClick = { viewModel.setThemeMode(THEME_MODE_LIGHT) },
            )
            ThemeModeRow(
                title = "纯黑",
                subtitle = "苹果式纯黑基底，始终深色",
                selected = settings.themeMode == THEME_MODE_DARK,
                onClick = { viewModel.setThemeMode(THEME_MODE_DARK) },
            )
            ThemeModeRow(
                title = "照片壁纸",
                subtitle = "上传照片作为主页壁纸，并从照片提取主题色",
                selected = settings.themeMode == THEME_MODE_PHOTO,
                onClick = {
                    if (settings.photoWallpaperPath.isNullOrBlank()) {
                        pickWallpaper.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    } else {
                        viewModel.setThemeMode(THEME_MODE_PHOTO)
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionCard(title = "壁纸") {
            if (!settings.photoWallpaperPath.isNullOrBlank()) {
                AsyncImage(
                    model = File(settings.photoWallpaperPath!!),
                    contentDescription = "当前壁纸预览",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            SettingRow(
                icon = Icons.Filled.Wallpaper,
                title = if (settings.photoWallpaperPath.isNullOrBlank()) "选择照片" else "更换照片",
                subtitle = "从相册挑选一张作为主页壁纸",
                onClick = {
                    pickWallpaper.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {}
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ThemeModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
