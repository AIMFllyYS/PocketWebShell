package com.webshell.feature.me

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.designsystem.components.AppCard
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppSectionHeader
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.designsystem.theme.LocalTransitionStyle

/** “我的”一级菜单入口；点击进入对应二级页面。 */
private enum class MeSection {
    APPEARANCE,
    LAYOUT,
    BACKGROUND,
    ENGINE,
    UPDATE_LOG,
    DEVELOPER,
}

/** 我的：一级菜单 + 二级设置页；运行中的后台会话固定置顶。 */
@Composable
fun MeScreen(
    onKeepAliveServiceChanged: (Boolean) -> Unit = {},
    viewModel: MeViewModel = hiltViewModel(),
) {
    val settingsState by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var section by rememberSaveable { mutableStateOf<MeSection?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSessions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.refreshSessions()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = section != null) { section = null }

    // 二级页面进入/退出：按用户选择的动效风格（外观与主题可切换 slide/fade/scale/none）。
    val transitionStyle = LocalTransitionStyle.current
    AnimatedContent(
        targetState = section,
        transitionSpec = {
            AppMotion.detailEnterFor(transitionStyle) togetherWith
                AppMotion.detailExitFor(transitionStyle)
        },
        label = "me-section",
    ) { target ->
        when (target) {
            null -> MeHome(
                state = state,
                onStopSession = viewModel::stopSession,
                onOpenSection = { section = it },
            )
            MeSection.APPEARANCE -> AppearanceSettingsPage(
                settings = settingsState,
                viewModel = viewModel,
                onBack = { section = null },
            )
            MeSection.LAYOUT -> LayoutSettingsPage(
                settings = settingsState,
                viewModel = viewModel,
                onBack = { section = null },
            )
            MeSection.BACKGROUND -> BackgroundSettingsPage(
                state = state,
                keepAliveEnabled = settingsState.keepAliveServiceEnabled,
                onKeepAliveChanged = {
                    viewModel.setKeepAliveServiceEnabled(it)
                    onKeepAliveServiceChanged(it)
                },
                onBatteryState = viewModel::refreshBatteryState,
                onBack = { section = null },
            )
            MeSection.ENGINE -> EngineInfoPage(
                capabilities = state.capabilities,
                onBack = { section = null },
            )
            MeSection.UPDATE_LOG -> UpdateLogPage(onBack = { section = null })
            MeSection.DEVELOPER -> DeveloperCenterPage(onBack = { section = null })
        }
    }
}

@Composable
private fun MeHome(
    state: MeUiState,
    onStopSession: (String) -> Unit,
    onOpenSection: (MeSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineMedium)
        Text(
            "后台会话与设置中心",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AppSpacing.lg))

        AppSectionHeader("运行中的后台会话")
        AppCard {
            if (state.runningSessions.isEmpty()) {
                Text(
                    "暂无正在保活的会话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.runningSessions.forEachIndexed { index, session ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppSpacing.sm),
                    ) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(AppSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                session.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(AppSpacing.md))
                        OutlinedButton(onClick = { onStopSession(session.sessionId) }) {
                            Text("结束")
                        }
                    }
                    if (index < state.runningSessions.lastIndex) {
                        AppListDivider(hasLeadingIcon = false)
                    }
                }
            }
        }

        Spacer(Modifier.height(AppSpacing.lg))

        AppSectionHeader("设置")
        AppCard(contentPadding = PaddingValues(0.dp)) {
            MenuEntry(
                icon = Icons.Filled.Palette,
                title = "外观与主题",
                subtitle = "纯白/纯黑/跟随系统/照片壁纸",
                onClick = { onOpenSection(MeSection.APPEARANCE) },
            )
            AppListDivider()
            MenuEntry(
                icon = Icons.Filled.GridView,
                title = "主页布局",
                subtitle = "行列数、图标样式与页面指示",
                onClick = { onOpenSection(MeSection.LAYOUT) },
            )
            AppListDivider()
            MenuEntry(
                icon = Icons.Filled.BatterySaver,
                title = "后台与通知",
                subtitle = "电池优化、通知与增强保活",
                onClick = { onOpenSection(MeSection.BACKGROUND) },
            )
            AppListDivider()
            MenuEntry(
                icon = Icons.Filled.Public,
                title = "WebView 引擎",
                subtitle = "版本与能力支持",
                onClick = { onOpenSection(MeSection.ENGINE) },
            )
            AppListDivider()
            MenuEntry(
                icon = Icons.Filled.NewReleases,
                title = "项目更新日志",
                subtitle = "查看最近版本更新",
                onClick = { onOpenSection(MeSection.UPDATE_LOG) },
            )
            AppListDivider()
            MenuEntry(
                icon = Icons.Filled.DeveloperMode,
                title = "开发者选项",
                subtitle = "应用信息、日志查看与设计 Playbook",
                onClick = { onOpenSection(MeSection.DEVELOPER) },
            )
        }
        Spacer(Modifier.height(AppSpacing.xl))
    }
}

/** 一级菜单行：统一 AppListRow + 右侧 chevron。 */
@Composable
private fun MenuEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    AppListRow(
        title = title,
        subtitle = subtitle,
        leadingIcon = icon,
        onClick = onClick,
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** 二级页面骨架：返回栏 + 大标题 + 可滚动内容。 */
@Composable
internal fun DetailPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(AppSpacing.md))
        content()
    }
}

/** 分组区块：iOS 风格 —— 分组标题在卡片上方，卡片零内边距由行自控。 */
@Composable
internal fun SectionCard(
    title: String,
    edgeToEdge: Boolean = true,
    content: @Composable () -> Unit,
) {
    AppSectionHeader(title)
    AppCard(
        contentPadding = if (edgeToEdge) PaddingValues(0.dp) else PaddingValues(AppSpacing.lg),
    ) {
        content()
    }
}

/** 兼容旧调用：设置行 = 统一 AppListRow。 */
@Composable
internal fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    AppListRow(
        title = title,
        subtitle = subtitle,
        leadingIcon = icon,
        onClick = onClick,
        trailing = trailing,
    )
}

/** 数值滑杆设置：单色轨道 + 白色圆形滑块（苹果风格，无渐变）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NumberSetting(
    icon: ImageVector,
    title: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    onValue: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs)) {
        SettingRow(
            icon = icon,
            title = title,
            subtitle = "$value$suffix",
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.count() - 2).coerceAtLeast(0),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg)
                .height(24.dp),
            thumb = {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            },
            track = { sliderState ->
                val span = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                val fraction = if (span > 0f) {
                    ((sliderState.value - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            },
        )
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        icon = Icons.Filled.Info,
        title = title,
        subtitle = subtitle,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}
