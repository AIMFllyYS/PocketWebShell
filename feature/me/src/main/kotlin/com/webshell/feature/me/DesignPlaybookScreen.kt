package com.webshell.feature.me

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.webshell.core.designsystem.components.AppBadge
import com.webshell.core.designsystem.components.AppCard
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.components.AppContextMenu
import com.webshell.core.designsystem.components.AppContextMenuItem
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.staticGlassSurface
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.AppSpacing

/**
 * 设计 Playbook：统一展示全部设计 token 与组件，
 * 作为前端设计验收与回归的常驻入口（我的 → 开发者选项）。
 */
@Composable
internal fun DesignPlaybookPage(onBack: () -> Unit) {
    DetailPage(title = "设计 Playbook", onBack = onBack) {
        ColorSection()
        Spacer(Modifier.height(AppSpacing.xl))
        TypographySection()
        Spacer(Modifier.height(AppSpacing.xl))
        ShapeSpacingSection()
        Spacer(Modifier.height(AppSpacing.xl))
        ComponentSection()
        Spacer(Modifier.height(AppSpacing.xl))
        DialogSection()
        Spacer(Modifier.height(AppSpacing.xl))
        ContextMenuSection()
        Spacer(Modifier.height(AppSpacing.xl))
        GlassSection()
        Spacer(Modifier.height(AppSpacing.xl))
        MotionSection()
        Spacer(Modifier.height(AppSpacing.xxl))
    }
}

// ---------- 配色 ----------

@Composable
private fun ColorSection() {
    val c = MaterialTheme.colorScheme
    SectionCard(title = "配色 Color", edgeToEdge = false) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ColorSwatch("primary", c.primary, c.onPrimary, Modifier.weight(1f))
                ColorSwatch("primaryContainer", c.primaryContainer, c.onPrimaryContainer, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ColorSwatch("background", c.background, c.onBackground, Modifier.weight(1f))
                ColorSwatch("surface", c.surface, c.onSurface, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ColorSwatch("surfaceVariant", c.surfaceVariant, c.onSurfaceVariant, Modifier.weight(1f))
                ColorSwatch("surfaceContainerLow", c.surfaceContainerLow, c.onSurface, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ColorSwatch("surfaceContainer", c.surfaceContainer, c.onSurface, Modifier.weight(1f))
                ColorSwatch("surfaceContainerHigh", c.surfaceContainerHigh, c.onSurface, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ColorSwatch("outline", c.outline, c.onSurface, Modifier.weight(1f))
                ColorSwatch("outlineVariant", c.outlineVariant, c.onSurface, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                ColorSwatch("error", c.error, c.onError, Modifier.weight(1f))
                ColorSwatch("errorContainer", c.errorContainer, c.onErrorContainer, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ColorSwatch(name: String, color: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(color)
            .padding(AppSpacing.sm),
    ) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}

// ---------- 字体 ----------

@Composable
private fun TypographySection() {
    val t = MaterialTheme.typography
    SectionCard(title = "字体 Typography（MiSans）", edgeToEdge = false) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            TypeSample("displaySmall 28/36", t.displaySmall)
            TypeSample("headlineMedium 24/32", t.headlineMedium)
            TypeSample("titleLarge 18/26 SemiBold", t.titleLarge)
            TypeSample("titleMedium 16/24 Medium", t.titleMedium)
            TypeSample("titleSmall 14/20 Medium", t.titleSmall)
            TypeSample("bodyLarge 16/26", t.bodyLarge)
            TypeSample("bodyMedium 14/22", t.bodyMedium)
            TypeSample("bodySmall 12/18", t.bodySmall)
            TypeSample("labelLarge 14/20 Medium", t.labelLarge)
            TypeSample("labelSmall 11/16 Medium", t.labelSmall)
        }
    }
}

@Composable
private fun TypeSample(name: String, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(150.dp),
        )
        Text("玄览 PocketWebShell", style = style, maxLines = 1)
    }
}

// ---------- 圆角与间距 ----------

@Composable
private fun ShapeSpacingSection() {
    SectionCard(title = "圆角 Shape", edgeToEdge = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            ShapeSample("xs 8", MaterialTheme.shapes.extraSmall)
            ShapeSample("sm 12", MaterialTheme.shapes.small)
            ShapeSample("md 16", MaterialTheme.shapes.medium)
            ShapeSample("lg 20", MaterialTheme.shapes.large)
            ShapeSample("xl 28", MaterialTheme.shapes.extraLarge)
        }
    }
    Spacer(Modifier.height(AppSpacing.lg))
    SectionCard(title = "间距 Spacing", edgeToEdge = false) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            SpacingBar("xs 4", AppSpacing.xs)
            SpacingBar("sm 8", AppSpacing.sm)
            SpacingBar("md 12", AppSpacing.md)
            SpacingBar("lg 16", AppSpacing.lg)
            SpacingBar("xl 24", AppSpacing.xl)
            SpacingBar("xxl 32", AppSpacing.xxl)
        }
    }
}

@Composable
private fun ShapeSample(name: String, shape: Shape) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(44.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Text(name, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SpacingBar(name: String, size: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Box(
            Modifier
                .width(size)
                .height(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

// ---------- 组件 ----------

@Composable
private fun ComponentSection() {
    var switchOn by remember { mutableStateOf(true) }
    SectionCard(title = "列表行 AppListRow") {
        AppListRow(title = "单行标题", leadingIcon = Icons.Filled.Star)
        AppListDivider()
        AppListRow(
            title = "双行标题",
            subtitle = "副标题使用 onSurfaceVariant，对比度 ≥ 4.5:1",
            leadingIcon = Icons.Filled.Favorite,
        )
        AppListDivider()
        AppListRow(
            title = "带箭头导航",
            subtitle = "点击进入下一级",
            leadingIcon = Icons.Filled.Notifications,
            onClick = {},
            trailing = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        AppListDivider()
        AppListRow(
            title = "带开关",
            subtitle = "Switch trailing 槽位",
            leadingIcon = Icons.Filled.Notifications,
            trailing = { Switch(checked = switchOn, onCheckedChange = { switchOn = it }) },
        )
        AppListDivider()
        AppListRow(
            title = "带徽标 AppBadge",
            leadingIcon = Icons.Filled.Notifications,
            trailing = { AppBadge(8) },
        )
    }
    Spacer(Modifier.height(AppSpacing.lg))
    SectionCard(title = "按钮 Button", edgeToEdge = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Button(onClick = {}) { Text("主按钮") }
            OutlinedButton(onClick = {}) { Text("次按钮") }
            TextButton(onClick = {}) { Text("文字按钮") }
        }
    }
}

// ---------- 弹窗 ----------

@Composable
private fun DialogSection() {
    var show by remember { mutableStateOf(false) }
    SectionCard(title = "弹窗 Dialog", edgeToEdge = false) {
        Button(onClick = { show = true }) { Text("打开确认弹窗") }
    }
    if (show) {
        AppConfirmDialog(
            title = "统一弹窗样式",
            text = "extraLarge 圆角、titleLarge 标题、bodyMedium 正文，按钮右对齐。",
            confirmText = "确认",
            dismissText = "取消",
            onConfirm = { show = false },
            onDismiss = { show = false },
        )
    }
}

// ---------- 情境菜单 ----------

@Composable
private fun ContextMenuSection() {
    var show by remember { mutableStateOf(false) }
    SectionCard(title = "情境菜单 AppContextMenu", edgeToEdge = false) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(
                "HyperOS/iOS 主屏长按菜单：锚点浮窗 + 纵向列表（圆形图标底），破坏性操作红色置底；主页长按图标体验同款。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { show = true }) { Text("预览情境菜单") }
        }
    }
    if (show) {
        AppContextMenu(
            items = listOf(
                AppContextMenuItem("打开", Icons.Filled.Launch) {},
                AppContextMenuItem("桌面版网页", Icons.Filled.DesktopWindows) {},
                AppContextMenuItem("开启后台保活", Icons.Filled.Bedtime) {},
                AppContextMenuItem("删除", Icons.Filled.Delete, destructive = true) {},
            ),
            onDismiss = { show = false },
        )
    }
}

// ---------- 玻璃材质 ----------

@Composable
private fun GlassSection() {
    SectionCard(title = "玻璃材质 Glass", edgeToEdge = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(MaterialTheme.shapes.medium),
        ) {
            // Static counterpart only: the root dock already owns the sole live backdrop.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF5AC8FA),
                                Color(0xFF007AFF),
                                Color(0xFFAF52DE),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .staticGlassSurface(shape = MaterialTheme.shapes.large, opacity = 0.82f)
                    .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.md),
            ) {
                Text(
                    stringResource(R.string.me_glass_static_preview),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            stringResource(R.string.me_glass_budget_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AppSpacing.md),
        )
    }
}

// ---------- 动效 ----------

@Composable
private fun MotionSection() {
    var expanded by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    val offsetX by animateDpAsState(
        targetValue = if (expanded) 180.dp else 0.dp,
        animationSpec = AppMotion.spring(),
        label = "motionDemoOffset",
    )
    val tint by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = AppMotion.fade(),
        label = "motionDemoColor",
    )
    SectionCard(title = "动效 Motion（统一规格）", edgeToEdge = false) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(
                "spring（位移/颜色）、enterDetail（二级页滑入）、popup（菜单弹出）。全局页面与菜单过渡复用同一组规格。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Box(
                    Modifier
                        .offset(x = offsetX)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(tint)
                        .align(Alignment.CenterStart),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Button(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "复位" else "spring")
                }
                OutlinedButton(onClick = { showDetail = !showDetail }) {
                    Text(if (showDetail) "收起二级页" else "滑入二级页")
                }
            }
            // 二级页滑入过渡演示：右侧滑入 + 淡入。
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showDetail,
                    enter = AppMotion.enterDetail,
                    exit = AppMotion.exitDetail,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(72.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "二级页（滑入 + 淡入）",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                if (!showDetail) {
                    Text(
                        "点击「滑入二级页」查看 enterDetail 过渡",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}
