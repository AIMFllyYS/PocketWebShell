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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.designsystem.components.AppCard
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.designsystem.components.AppSectionHeader
import com.webshell.core.designsystem.components.AppSwitch
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
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = AppSpacing.lg),
    ) {
        item(key = "settings-header") {
            Text(stringResource(R.string.me_settings), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.me_settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
        }
        item(key = "running-sessions") {
            SettingsGroup(stringResource(R.string.me_sessions)) {
                if (state.runningSessions.isEmpty()) {
                    AppListRow(
                        title = stringResource(R.string.me_sessions_empty),
                        subtitle = stringResource(R.string.me_sessions_empty_hint),
                        leadingIcon = Icons.Filled.PlayCircle,
                        leadingIconBackground = Color(0xFF34C759),
                    )
                } else {
                    state.runningSessions.forEachIndexed { index, session ->
                        AppListRow(
                            title = session.title,
                            subtitle = session.url,
                            leadingIcon = Icons.Filled.PlayCircle,
                            leadingIconBackground = Color(0xFF34C759),
                            trailing = {
                                TextButton(onClick = { onStopSession(session.sessionId) }) {
                                    Text(stringResource(R.string.me_session_end),
                                        color = MaterialTheme.colorScheme.error)
                                }
                            },
                        )
                        if (index < state.runningSessions.lastIndex) AppListDivider()
                    }
                }
            }
        }
        item(key = "display-settings") {
            SettingsGroup(stringResource(R.string.me_display_section)) {
                MenuEntry(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.me_appearance),
                    iconColor = Color(0xFFAF52DE),
                    onClick = { onOpenSection(MeSection.APPEARANCE) },
                )
                AppListDivider()
                MenuEntry(
                    icon = Icons.Filled.GridView,
                    title = stringResource(R.string.me_layout),
                    iconColor = Color(0xFF007AFF),
                    onClick = { onOpenSection(MeSection.LAYOUT) },
                )
            }
        }
        item(key = "browser-settings") {
            SettingsGroup(stringResource(R.string.me_browser_section)) {
                MenuEntry(
                    icon = Icons.Filled.BatterySaver,
                    title = stringResource(R.string.me_background),
                    iconColor = Color(0xFF34C759),
                    onClick = { onOpenSection(MeSection.BACKGROUND) },
                )
                AppListDivider()
                MenuEntry(
                    icon = Icons.Filled.Public,
                    title = stringResource(R.string.me_engine),
                    iconColor = Color(0xFF007AFF),
                    onClick = { onOpenSection(MeSection.ENGINE) },
                )
            }
        }
        item(key = "about-settings") {
            SettingsGroup(stringResource(R.string.me_about_section)) {
                MenuEntry(
                    icon = Icons.Filled.NewReleases,
                    title = stringResource(R.string.me_updates),
                    iconColor = Color(0xFF8E8E93),
                    onClick = { onOpenSection(MeSection.UPDATE_LOG) },
                )
                AppListDivider()
                MenuEntry(
                    icon = Icons.Filled.DeveloperMode,
                    title = stringResource(R.string.me_developer),
                    iconColor = Color(0xFF8E8E93),
                    onClick = { onOpenSection(MeSection.DEVELOPER) },
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    AppSectionHeader(title)
    AppCard(contentPadding = PaddingValues(0.dp), content = content)
    Spacer(Modifier.height(24.dp))
}

/** 一级菜单行：统一 AppListRow + 右侧 chevron。 */
@Composable
private fun MenuEntry(
    icon: ImageVector,
    title: String,
    iconColor: Color,
    onClick: () -> Unit,
) {
    AppListRow(
        title = title,
        leadingIcon = icon,
        leadingIconBackground = iconColor,
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

/** 固定 iOS 导航栏 + 独立滚动内容，返回入口不随长表单滚出屏幕。 */
@Composable
internal fun DetailPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        AppNavigationBar(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = AppSpacing.md),
        ) {
            content()
        }
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
    icon: ImageVector? = null,
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
    var pendingValue by remember(value) { mutableIntStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs)) {
        SettingRow(
            icon = icon,
            title = title,
            trailing = {
                Text("$pendingValue$suffix", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        )
        Slider(
            value = pendingValue.toFloat(),
            onValueChange = { pendingValue = it.toInt() },
            onValueChangeFinished = { if (pendingValue != value) onValue(pendingValue) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.count() - 2).coerceAtLeast(0),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg)
                .semantics { contentDescription = title }
                .height(48.dp),
            thumb = {
                Box(
                    Modifier
                        .size(28.dp)
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
        title = title,
        subtitle = subtitle,
        trailing = {
            AppSwitch(checked = checked, onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = title })
        },
    )
}
