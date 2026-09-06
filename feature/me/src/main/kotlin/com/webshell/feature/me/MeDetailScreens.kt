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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import com.webshell.core.data.HomeSettings
import com.webshell.core.data.SCROLL_MODE_PAGER
import com.webshell.core.data.SCROLL_MODE_VERTICAL
import com.webshell.core.data.THEME_MODE_DARK
import com.webshell.core.data.THEME_MODE_LIGHT
import com.webshell.core.data.THEME_MODE_PHOTO
import com.webshell.core.data.THEME_MODE_SYSTEM
import com.webshell.core.data.TRANSITION_FADE
import com.webshell.core.data.TRANSITION_NONE
import com.webshell.core.data.TRANSITION_SCALE
import com.webshell.core.data.TRANSITION_SLIDE
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
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
        SectionCard(title = "桌面滑动") {
            ThemeModeRow(
                title = "左右翻页",
                subtitle = "一屏一页横向切换（默认）",
                selected = settings.homeScrollMode == SCROLL_MODE_PAGER,
                onClick = { viewModel.setHomeScrollMode(SCROLL_MODE_PAGER) },
            )
            ThemeModeRow(
                title = "上下滚动",
                subtitle = "所有页摊平成一条纵向列表连续滚动",
                selected = settings.homeScrollMode == SCROLL_MODE_VERTICAL,
                showDivider = false,
                onClick = { viewModel.setHomeScrollMode(SCROLL_MODE_VERTICAL) },
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionCard(title = "桌面整理") {
            ToggleRow(
                title = "自动整理桌面",
                subtitle = if (settings.autoArrangeHome) {
                    "图标自动压实排列"
                } else {
                    "自由摆放：图标拖到哪个网格位就停在哪个网格位"
                },
                checked = settings.autoArrangeHome,
                onCheckedChange = viewModel::setAutoArrangeHome,
            )
            AppListDivider(hasLeadingIcon = false)
            ToggleRow(
                title = "显示全部应用入口",
                subtitle = "主屏右下角浮动图标，点按打开全部应用抽屉",
                checked = settings.allAppsEntryVisible,
                onCheckedChange = viewModel::setAllAppsEntryVisible,
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
            AppListDivider(hasLeadingIcon = false)
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
        SectionCard(title = "引擎信息") {
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
                showDivider = false,
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
        SectionCard(title = "页面切换动效") {
            ThemeModeRow(
                title = "滑入",
                subtitle = "新页面从右侧滑入（默认）",
                selected = settings.transitionStyle == TRANSITION_SLIDE,
                onClick = { viewModel.setTransitionStyle(TRANSITION_SLIDE) },
            )
            ThemeModeRow(
                title = "淡入",
                subtitle = "新页面平滑淡入",
                selected = settings.transitionStyle == TRANSITION_FADE,
                onClick = { viewModel.setTransitionStyle(TRANSITION_FADE) },
            )
            ThemeModeRow(
                title = "缩放",
                subtitle = "新页面从轻缩放大进入",
                selected = settings.transitionStyle == TRANSITION_SCALE,
                onClick = { viewModel.setTransitionStyle(TRANSITION_SCALE) },
            )
            ThemeModeRow(
                title = "无动画",
                subtitle = "直接切换，最省电",
                selected = settings.transitionStyle == TRANSITION_NONE,
                showDivider = false,
                onClick = { viewModel.setTransitionStyle(TRANSITION_NONE) },
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
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    AppListRow(
        title = title,
        subtitle = subtitle,
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        trailing = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
    )
    if (showDivider) AppListDivider(hasLeadingIcon = false)
}
