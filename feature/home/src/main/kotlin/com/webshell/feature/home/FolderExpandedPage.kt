package com.webshell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.webshell.core.data.WebAppEntity
import com.webshell.core.designsystem.components.staticGlassSurface
import com.webshell.core.designsystem.theme.AppMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 展开文件夹内的成员拖拽会话状态（Dialog 是独立窗口，主页根级拖拽会话
 * 收不到这里的事件，故在 Dialog 内轻量自实现，模式对齐主页）：
 * 浮动图标层跟手且不参与测量；点按/长按互斥；落子/取消都清干净状态。
 */
@Stable
private class FolderMemberDragState {
    var draggingId by mutableStateOf<String?>(null)
    var fromIndex by mutableStateOf(-1)
    /** 手指位置（Dialog 窗口 root 坐标）。 */
    var dragPosition by mutableStateOf(Offset.Zero)
    /** 按下点相对图标本体左上角的偏移：锚定注册点，图标中心不在手指下跳变。 */
    var dragRegistration by mutableStateOf(Offset.Zero)
    /** 命中目标的全局成员下标（页 * 9 + 页内槽位）；null = 移出网格，松手取消。 */
    var targetIndex by mutableStateOf<Int?>(null)
    var rootOrigin by mutableStateOf(Offset.Zero)
    var pagerBounds by mutableStateOf<Rect?>(null)
    val cellBounds = mutableStateMapOf<Int, Rect>()

    /** 命中当前页 3x3 槽位：手指在 pager 内取最近 cell；移出 pager 区域视为取消。 */
    fun resolveTarget(finger: Offset, currentPage: Int, memberCount: Int): Int? {
        val bounds = pagerBounds ?: return null
        if (!bounds.contains(finger)) return null
        val pageStart = currentPage * 9
        val pageSlots = minOf(9, memberCount - pageStart)
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        for (slot in 0 until pageSlots) {
            val rect = cellBounds[pageStart + slot] ?: continue
            val distance = (rect.center - finger).getDistance()
            if (distance < bestDistance) {
                bestDistance = distance
                best = pageStart + slot
            }
        }
        return best?.coerceAtMost(memberCount - 1)
    }

    fun reset() {
        draggingId = null
        fromIndex = -1
        targetIndex = null
        dragRegistration = Offset.Zero
    }
}

/**
 * iOS folder: title outside the glass sheet, nine fixed cells per page, no unbounded column.
 *
 * 进出场为显式动画（MutableTransitionState + AnimatedVisibility，共享同一 Transition）：
 * 进入 scrim 淡入 + 面板 0.92→1 缩放淡入（[AppMotion.FolderEnterMs]）；
 * 退出请求（遮罩点击/系统返回/解散按钮）先播退场动画（[AppMotion.FolderExitMs]），
 * 播完才回调外部 onDismiss 移除 Dialog。点按成员启动应用保持瞬时关闭（响应优先）。
 */
@Composable
internal fun FolderExpandedPage(
    members: List<WebAppEntity>,
    cornerRadiusPercent: Int,
    onLaunch: (String, String) -> Unit,
    onDissolve: () -> Unit,
    onDismiss: () -> Unit,
    onRenameFolder: () -> Unit = {},
    onMoveMember: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
) {
    // 与 HomePages.aggregateCells 同一排序规则：全局下标（页*9+页内槽位）以此为准，
    // onMoveMember 的 from/to 与 ViewModel 侧重算的顺序严格一致。
    val orderedMembers = remember(members) { members.sortedWith(HomePages.folderMemberOrder()) }
    val folderPages = remember(orderedMembers) { orderedMembers.chunked(9).ifEmpty { listOf(emptyList()) } }
    val folderName = remember(orderedMembers) { orderedMembers.firstNotNullOfOrNull { it.folderName } }
    val pagerState = rememberPagerState(pageCount = { folderPages.size })
    val drag = remember { FolderMemberDragState() }
    val haptics = LocalHapticFeedback.current
    val folderLabel = folderName ?: stringResource(R.string.home_folder)
    val renameHint = stringResource(R.string.home_folder_rename_hint, folderLabel)

    val visibility = remember { MutableTransitionState(false) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        visibility.targetState = true
        // 退场动画播完（isIdle 且目标为隐藏）才真正移除 Dialog。
        snapshotFlow { visibility.isIdle && !visibility.targetState }.first { it }
        currentOnDismiss()
    }

    Dialog(
        onDismissRequest = { visibility.targetState = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) { window?.setDimAmount(0f) }
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { drag.rootOrigin = it.positionInRoot() }) {
            val density = LocalDensity.current
            val labelMeasurer = rememberTextMeasurer()
            val labelHeight = with(density) {
                labelMeasurer.measure("国Hg", style = MaterialTheme.typography.labelSmall, maxLines = 1).size.height.toDp()
            }
            // AnimatedVisibility 的内容作用域读不到 BoxWithConstraints 约束，在此先取值。
            val containerMaxHeight = maxHeight
            val sheetHeight = (containerMaxHeight * 0.58f).coerceAtMost(368.dp)
            val memberSize = ((maxWidth - 48.dp - 48.dp) / 3).coerceAtMost(60.dp).coerceAtLeast(24.dp)
            val iconShape = RoundedCornerShape(cornerRadiusPercent.coerceIn(0, 50))

            // Scrim covers system bars; only interactive folder content consumes safe insets.
            AnimatedVisibility(
                visibleState = visibility,
                enter = fadeIn(animationSpec = tween(AppMotion.FolderEnterMs)),
                exit = fadeOut(animationSpec = tween(AppMotion.FolderExitMs)),
            ) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { visibility.targetState = false },
                )
            }

            AnimatedVisibility(
                visibleState = visibility,
                enter = scaleIn(animationSpec = tween(AppMotion.FolderEnterMs), initialScale = 0.92f) +
                    fadeIn(animationSpec = tween(AppMotion.FolderEnterMs)),
                exit = scaleOut(animationSpec = tween(AppMotion.FolderExitMs), targetScale = 0.92f) +
                    fadeOut(animationSpec = tween(AppMotion.FolderExitMs)),
            ) {
                Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().heightIn(max = containerMaxHeight).padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = folderLabel,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Normal),
                            color = Color.White,
                            modifier = Modifier
                                .padding(bottom = 24.dp)
                                .semantics { contentDescription = renameHint }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onRenameFolder,
                                ),
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f, fill = false).widthIn(max = 380.dp).fillMaxWidth()
                                .staticGlassSurface(shape = RoundedCornerShape(36.dp), opacity = 0.88f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { /* Keep taps on the sheet out of the dismiss target. */ }
                                .padding(top = 18.dp, bottom = 12.dp),
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                // 成员拖拽期间禁用翻页手势（跨页拖拽不支持）。
                                userScrollEnabled = drag.draggingId == null,
                                modifier = Modifier.fillMaxWidth().height(sheetHeight)
                                    .onGloballyPositioned { drag.pagerBounds = it.boundsInRoot() },
                            ) { page ->
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(18.dp),
                                    // 3x3 固定页尺寸内不可滚动，避免与成员拖拽抢手势。
                                    userScrollEnabled = false,
                                ) {
                                    itemsIndexed(folderPages[page], key = { _, item -> item.id }) { indexInPage, member ->
                                        val globalIndex = page * 9 + indexInPage
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth().height(memberSize + labelHeight + 8.dp)
                                                .onGloballyPositioned { drag.cellBounds[globalIndex] = it.boundsInRoot() }
                                                .folderMemberGesture(
                                                    drag = drag,
                                                    member = member,
                                                    globalIndex = globalIndex,
                                                    iconSize = memberSize,
                                                    haptics = haptics,
                                                    onLaunch = onLaunch,
                                                    resolveTarget = { finger ->
                                                        drag.resolveTarget(finger, pagerState.currentPage, orderedMembers.size)
                                                    },
                                                    onMove = onMoveMember,
                                                )
                                                .alpha(if (drag.draggingId == member.id) 0.3f else 1f),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (drag.draggingId != null && drag.targetIndex == globalIndex &&
                                                    drag.draggingId != member.id
                                                ) {
                                                    // 目标槽位高亮（不参与测量）。
                                                    Box(
                                                        Modifier.matchParentSize()
                                                            .background(
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                                iconShape,
                                                            ),
                                                    )
                                                }
                                                AppIcon(member, memberSize, cornerRadiusPercent)
                                            }
                                            Text(
                                                member.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                textAlign = TextAlign.Center,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            if (folderPages.size > 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                                    repeat(folderPages.size) { index ->
                                        Box(Modifier.size(6.dp).background(
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = if (pagerState.currentPage == index) 0.9f else 0.2f),
                                            CircleShape,
                                        ))
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                // 先打开解散确认，再播退场动画；播完由 onDismiss 移除本 Dialog。
                                onDissolve()
                                visibility.targetState = false
                            },
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text(stringResource(R.string.home_folder_dissolve), color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                }
            }

            // 浮动拖拽图标层：Dialog 内容根的直接子节点，只绘制、不参与网格/pager 测量。
            val draggedMember = orderedMembers.firstOrNull { it.id == drag.draggingId }
            if (draggedMember != null) {
                AppIcon(
                    app = draggedMember,
                    size = memberSize,
                    cornerRadiusPercent = cornerRadiusPercent,
                    shadowElevation = 18.dp,
                    modifier = Modifier.graphicsLayer {
                        translationX = drag.dragPosition.x - drag.rootOrigin.x - drag.dragRegistration.x
                        translationY = drag.dragPosition.y - drag.rootOrigin.y - drag.dragRegistration.y
                    },
                )
            }
        }
    }
}

/**
 * 展开文件夹成员的单通道手势状态机（对齐主页 homeCellGesture 的编排）：
 * 长按超时窗口内提前松手 = 点按启动；位移超 touchSlop = 交还 pager 滚动；
 * 超时 = 进入拖拽。拖拽中消费位移（阻止翻页），松手落子 onMove(from, to)，
 * 取消/越界松手不做任何事；任何路径都在 finally 里清干净拖拽状态。
 */
@Composable
private fun Modifier.folderMemberGesture(
    drag: FolderMemberDragState,
    member: WebAppEntity,
    globalIndex: Int,
    iconSize: Dp,
    haptics: HapticFeedback,
    onLaunch: (String, String) -> Unit,
    resolveTarget: (Offset) -> Int?,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): Modifier {
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentResolveTarget by rememberUpdatedState(resolveTarget)
    val currentOnMove by rememberUpdatedState(onMove)
    return pointerInput(member.id) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (drag.draggingId != null) {
                // 其他成员的拖拽进行中：本 cell 不响应点按/长按（手势互斥），等本次按下结束。
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUp() || !change.pressed) break
                }
                return@awaitEachGesture
            }
            var isTap = false
            var isCancelled = false
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var accumulated = Offset.Zero
                while (true) {
                    val change = awaitPointerEvent()
                        .changes.firstOrNull { it.id == down.id }
                    if (change == null || change.isConsumed) {
                        isCancelled = true
                        return@withTimeoutOrNull
                    }
                    if (change.changedToUp()) {
                        isTap = true
                        return@withTimeoutOrNull
                    }
                    accumulated += change.positionChange()
                    if (accumulated.getDistance() > viewConfiguration.touchSlop) {
                        isCancelled = true
                        return@withTimeoutOrNull
                    }
                }
            }
            when {
                isTap -> {
                    currentOnLaunch(member.id, member.url)
                    return@awaitEachGesture
                }
                isCancelled -> return@awaitEachGesture
            }
            // 长按确认 → 拖拽开始。注册点沿用 Launcher3 onDragStart 语义：锚定按下点
            // 相对图标本体的偏移，跟手图标中心不在手指下跳变。
            val rect = drag.cellBounds[globalIndex] ?: return@awaitEachGesture
            val iconPx = iconSize.toPx()
            val iconLeft = (rect.width - iconPx) / 2f
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            drag.draggingId = member.id
            drag.fromIndex = globalIndex
            drag.dragPosition = Offset(rect.left + down.position.x, rect.top + down.position.y)
            drag.dragRegistration = Offset(
                x = (down.position.x - iconLeft).coerceIn(0f, iconPx),
                y = down.position.y.coerceIn(0f, iconPx),
            )
            drag.targetIndex = currentResolveTarget(drag.dragPosition)
            try {
                while (true) {
                    val change = awaitPointerEvent()
                        .changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUp()) {
                        val target = drag.targetIndex
                        val from = drag.fromIndex
                        if (target != null && from >= 0 && target != from) currentOnMove(from, target)
                        break
                    }
                    // 取消（非抬起）只清态不落子。
                    if (!change.pressed) break
                    // 先取位移再消费（consume 后 positionChange 归零），消费用于阻止翻页。
                    val delta = change.positionChange()
                    change.consume()
                    if (delta != Offset.Zero) {
                        drag.dragPosition += delta
                        drag.targetIndex = currentResolveTarget(drag.dragPosition)
                    }
                }
            } finally {
                drag.reset()
            }
        }
    }
}
