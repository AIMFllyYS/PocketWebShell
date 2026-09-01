package com.webshell.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.webshell.core.designsystem.theme.AppMotion

/**
 * 情境菜单项：左圆形图标底 + 文字（HyperOS/iOS 主屏长按样式）。
 * [iconTint] 为图标色；[iconContainer] 为圆形底色；破坏性操作红色。
 */
data class AppContextMenuItem(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val iconContainer: Color? = null,
    val iconTint: Color? = null,
    val onClick: () -> Unit,
)

/**
 * 锚点浮窗情境菜单（对齐 HyperOS/iOS 主屏长按效果）：
 * - 以按压点为准心（[anchorPoint] 为手指在根布局中的像素坐标）：
 *   水平以按压点居中展开；垂直优先向按压点上方展开，上方放不下且下方空间更大时
 *   翻转到下方（Launcher3 `ArrowPopup.orientAboutObject` 的本地化取舍，见
 *   [PressPointMenuPositionProvider]）；四边以屏幕边距钳制，任何位置都不被截断；
 * - 纵向列表项：圆形图标底 + 短标签；破坏性操作红色、分隔后置底；
 * - 半透明磨砂材质（复用主题 surface 高透明），不新增第二处实时模糊，见 docs/PERFORMANCE.md；
 * - 弹出动画统一走 [AppMotion.popupEnter]，锚点为靠近按压点的一侧。
 */
@Composable
fun AppContextMenu(
    items: List<AppContextMenuItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchorPoint: IntOffset? = null,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { AppContextMenuDefaults.screenMargin.roundToPx() }

    val positionProvider = remember(anchorPoint) {
        PressPointMenuPositionProvider(anchorPoint, marginPx)
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        // 全屏 scrim 捕捉外部点击（置于菜单之下）。
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )

        var visible by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }

        val origin = positionProvider.transformOrigin
        AnimatedVisibility(
            visible = visible,
            enter = AppMotion.popupEnter(origin),
            exit = AppMotion.popupExit(origin),
        ) {
            MenuPanel(items = items, onAction = { it.onClick(); onDismiss() }, modifier = modifier)
        }
    }
}

@Composable
private fun MenuPanel(
    items: List<AppContextMenuItem>,
    onAction: (AppContextMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val regular = items.filterNot { it.destructive }
    val destructive = items.filter { it.destructive }
    val shape = RoundedCornerShape(AppContextMenuDefaults.cornerRadius)

    Column(
        modifier = modifier
            .widthIn(min = AppContextMenuDefaults.menuWidth, max = AppContextMenuDefaults.menuWidth)
            .clip(shape)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                    alpha = AppContextMenuDefaults.panelAlpha,
                ),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(vertical = 6.dp),
    ) {
        regular.forEachIndexed { index, item ->
            MenuRow(item = item, onClick = { onAction(item) })
            if (index < regular.lastIndex) {
                RowDivider()
            }
        }
        if (destructive.isNotEmpty()) {
            if (regular.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                )
            }
            destructive.forEachIndexed { index, item ->
                MenuRow(item = item, onClick = { onAction(item) })
                if (index < destructive.lastIndex) {
                    RowDivider()
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: AppContextMenuItem, onClick: () -> Unit) {
    val destructive = item.destructive
    val containerColor = when {
        item.iconContainer != null -> item.iconContainer
        destructive -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val tintColor = when {
        item.iconTint != null -> item.iconTint
        destructive -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val textColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AppContextMenuDefaults.iconBadgeSize)
                .clip(CircleShape)
                .background(containerColor),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 14.dp + AppContextMenuDefaults.iconBadgeSize + 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * 按压点定位（Launcher3 `ArrowPopup.orientAboutObject` 的本地化派生）：
 * 1. 垂直：`spaceAbove = pressY`、`spaceBelow = windowH - pressY`；上方放得下
 *    （`spaceAbove >= menuH + gap`）或上方空间更大时向上展开，否则向下展开，
 *    菜单与按压点保留半个屏幕边距的间隙（gap = marginPx / 2）；
 * 2. 水平：以按压点为中心（`x = pressX - menuW/2`）；
 * 3. 两轴最终都 clamp 进 [margin, window - menu - margin]，任何边缘位置不截断；
 * 4. `transformOrigin` 的 pivot 始终落在按压点一侧（clamp 后重算），
 *    弹出动画从按压点展开，对应 Launcher3 的 `setPivotY(mIsAboveIcon ? height : 0)`。
 * 与 Launcher3 的差异：不画箭头、不做左/右对齐递归重试——固定宽度菜单下
 * "按压点居中 + clamp" 等价覆盖其分支，且为纯函数、更适合 Compose。
 */
private class PressPointMenuPositionProvider(
    private val pressPoint: IntOffset?,
    private val marginPx: Int,
) : PopupPositionProvider {

    /** 弹出缩放锚点：靠近按压点的那条边。 */
    var transformOrigin: TransformOrigin = TransformOrigin.Center
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val point = pressPoint ?: IntOffset(windowSize.width / 2, windowSize.height / 2)
        val menuW = popupContentSize.width
        val menuH = popupContentSize.height
        val gap = marginPx / 2

        // 垂直：默认上方，上方放不下且下方空间更大时翻转到下方
        val spaceAbove = point.y
        val spaceBelow = windowSize.height - point.y
        val openUp = spaceAbove >= menuH + gap || spaceAbove >= spaceBelow
        val rawY = if (openUp) point.y - gap - menuH else point.y + gap

        // 水平：按压点居中
        val rawX = point.x - menuW / 2

        val maxX = (windowSize.width - menuW - marginPx).coerceAtLeast(marginPx)
        val maxY = (windowSize.height - menuH - marginPx).coerceAtLeast(marginPx)
        val x = rawX.coerceIn(marginPx, maxX)
        val y = rawY.coerceIn(marginPx, maxY)
        transformOrigin = TransformOrigin(
            // clamp 后按压点可能不在菜单正中央，pivotX 需按实际偏移重算
            pivotFractionX = ((point.x - x).toFloat() / menuW).coerceIn(0f, 1f),
            pivotFractionY = if (openUp) 1f else 0f,
        )
        return IntOffset(x = x, y = y)
    }
}

/** 情境菜单规格：与 docs/DESIGN.md 第 6 节同步维护。 */
private object AppContextMenuDefaults {
    val menuWidth = 208.dp
    val cornerRadius = 16.dp
    val iconBadgeSize = 34.dp
    val screenMargin = 12.dp
    const val panelAlpha = 0.94f
}
