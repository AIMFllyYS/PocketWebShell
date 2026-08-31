package com.webshell.feature.home

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.webshell.core.data.HomeSettings
import com.webshell.core.data.WebAppEntity
import com.webshell.core.designsystem.components.AppContextMenu
import com.webshell.core.designsystem.components.AppContextMenuItem
import com.webshell.core.designsystem.theme.LocalPhotoWallpaperPath
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val FOLDER_HOVER_MILLIS = 500L
private const val EDGE_HOVER_MILLIS = 450L
private const val PINCH_IN_THRESHOLD = 0.86f
private const val PINCH_OUT_THRESHOLD = 1.16f

/**
 * 手机桌面式主页。
 *
 * 拖拽采用与 Launcher3 相同的分层思路：网格只保留占位，独立浮动层绘制跟手图标。
 * 因此拖动不会参与 LazyGrid 测量，也不会把整页撑大或缩小。
 */
@Composable
fun HomeScreen(
    onLaunch: (appId: String, url: String) -> Unit = { _, _ -> },
    onAddRequested: () -> Unit = {},
    viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pageCapacity = (settings.gridColumns * settings.gridRows).coerceAtLeast(1)
    // 自由摆放（默认）：稀疏网格，空槽为 null，图标停在松手的网格位；
    // 自动整理：密集压实（传统行为）。
    val freePlacement = !settings.autoArrangeHome
    val pages: List<List<HomeCell?>> = remember(apps, pageCapacity, freePlacement) {
        if (freePlacement) {
            HomePages.buildSparse(apps = apps, pageCapacity = pageCapacity)
        } else {
            HomePages.build(apps = apps, pageCapacity = pageCapacity)
                .map { page -> page.map { cell -> cell as HomeCell? } }
        }
    }
    // 自由摆放下“添加”入口占末页第一个空槽（buildSparse 保证末页必有空槽）。
    val addSlotIndex = if (freePlacement) {
        pages.lastOrNull()?.indexOfFirst { it == null } ?: -1
    } else {
        -1
    }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val cellBounds = remember { mutableStateMapOf<String, Rect>() }
    // 全部网格槽（含空槽）的屏幕区域："page:slot" → Rect，用于松手吸附最近槽位。
    val slotBounds = remember { mutableStateMapOf<String, Rect>() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var dragRegistration by remember { mutableStateOf(Offset.Zero) }
    var draggedDistance by remember { mutableStateOf(Offset.Zero) }
    var dragHoverTarget by remember { mutableStateOf<String?>(null) }
    // 拖拽吸附的最近网格插槽（当前页内 cellIndex，可为末尾追加位）。
    var dragTargetCellIndex by remember { mutableStateOf(-1) }
    var folderCandidate by remember { mutableStateOf<String?>(null) }
    var folderArmed by remember { mutableStateOf(false) }
    var edgeDirection by remember { mutableIntStateOf(0) }
    // iOS 顺序：长按先弹情境菜单；按住并移动超过 touchSlop 后菜单淡出、图标跟手。
    var menuFor by remember { mutableStateOf<HomeCell?>(null) }
    // 长按按压点（根布局坐标）：情境菜单以此为四向空间准心。
    var menuPressPoint by remember { mutableStateOf<Offset?>(null) }
    var pendingDragCell by remember { mutableStateOf<HomeCell?>(null) }
    var pendingDragLocal by remember { mutableStateOf(Offset.Zero) }
    var folderOpenFor by remember { mutableStateOf<String?>(null) }
    var confirmDeleteFor by remember { mutableStateOf<HomeCell?>(null) }
    // 编辑（jiggle）模式：双指捏合进入，点选图标做批量整理。
    var editMode by remember { mutableStateOf(false) }
    val editSelection = remember { mutableStateMapOf<String, Boolean>() }

    val draggedCell = remember(pages, draggingKey) {
        pages.flatten().firstOrNull { it?.key == draggingKey }
    }

    BackHandler(enabled = folderOpenFor != null) { folderOpenFor = null }
    BackHandler(enabled = editMode) {
        editMode = false
        editSelection.clear()
    }

    fun exitEditMode() {
        editMode = false
        editSelection.clear()
    }

    LaunchedEffect(draggingKey, folderCandidate) {
        folderArmed = false
        val candidate = folderCandidate ?: return@LaunchedEffect
        if (draggingKey == null || candidate == draggingKey) return@LaunchedEffect
        delay(FOLDER_HOVER_MILLIS)
        if (folderCandidate == candidate && draggingKey != null) {
            folderArmed = true
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            // 双指捏合（内划）进入编辑模式；外扩退出。仅在非拖拽时响应，
            // 与单指长按拖拽手势互不干扰（不同 pointerInput 通道）。
            .pointerInput(Unit) {
                // 手动跟踪双指间距变化：比 detectTransformGestures 更可靠，
                // 且不被 HorizontalPager 的单指拖动手势抢占。
                awaitEachGesture {
                    var startDistance = 0f
                    var triggered = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size < 2) {
                            // 双指抬起，复位等待下一次捏合
                            startDistance = 0f
                            triggered = false
                            break
                        }
                        if (draggingKey != null) break
                        val d = (pressed[0].position - pressed[1].position).getDistance()
                        if (startDistance == 0f) {
                            startDistance = d
                            continue
                        }
                        val ratio = d / startDistance
                        if (!triggered && ratio < PINCH_IN_THRESHOLD && !editMode) {
                            triggered = true
                            editMode = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (!triggered && ratio > PINCH_OUT_THRESHOLD && editMode) {
                            triggered = true
                            exitEditMode()
                        }
                        if (triggered) {
                            pressed.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        // 照片壁纸主题：壁纸铺底 + 可读性 scrim；不参与网格测量，见 docs/DESIGN.md
        val wallpaperPath = LocalPhotoWallpaperPath.current
        if (wallpaperPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(wallpaperPath))
                    .size(1080)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
            )
        }

        val widthPx = with(density) { maxWidth.toPx() }
        val iconSize = settings.iconSizeDp.dp.coerceAtMost(
            ((maxWidth - 24.dp) / settings.gridColumns) - 12.dp,
        ).coerceAtLeast(40.dp)
        val cellHeight = iconSize + if (settings.showLabels) 30.dp else 16.dp

        fun updateDropTargets(position: Offset) {
            val currentPageCells = pages.getOrNull(pagerState.currentPage).orEmpty()
            val currentKeys = currentPageCells.asSequence().mapNotNull { it?.key }.toHashSet()
            val target = cellBounds.entries.firstOrNull { (key, rect) ->
                key != draggingKey && key in currentKeys &&
                    rect.contains(position.x.toInt(), position.y.toInt())
            }
            val targetKey = target?.key
            if (targetKey != dragHoverTarget) {
                dragHoverTarget = targetKey
                if (targetKey != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            // 网格吸附：在当前页全部网格槽（含空槽）中取几何中心距手指最近的槽。
            // 与真实安卓桌面一致——空槽也是合法落点，松手即停在最近网格位。
            val slotPrefix = "${pagerState.currentPage}:"
            dragTargetCellIndex = slotBounds.entries
                .filter { (key, _) -> key.startsWith(slotPrefix) }
                .minByOrNull { (_, rect) ->
                    val dx = rect.exactCenterX() - position.x
                    val dy = rect.exactCenterY() - position.y
                    dx * dx + dy * dy
                }
                ?.key?.substringAfter(':')?.toIntOrNull() ?: -1
            val inFolderHotspot = target?.value?.let { rect ->
                val centerX = rect.exactCenterX()
                val centerY = rect.top + with(density) { iconSize.toPx() } / 2f
                val radius = with(density) { iconSize.toPx() } * 0.55f
                val dx = position.x - centerX
                val dy = position.y - centerY
                dx * dx + dy * dy <= radius * radius
            } == true
            // Do not depend on [draggedCell] here: it is derived during recomposition,
            // while the first MOVE event can arrive before that recomposition has run.
            // A launcher must be able to arm a folder even when the pointer enters the
            // target in a single movement and then remains still.
            val sourceCanMerge = pages.asSequence()
                .flatten()
                .firstOrNull { it?.key == draggingKey }
                ?.isFolder == false
            folderCandidate = targetKey.takeIf { inFolderHotspot && sourceCanMerge }

            val localX = position.x - rootOrigin.x
            val edgeZone = widthPx.coerceAtLeast(1f) * 0.10f
            edgeDirection = when {
                localX < edgeZone && pagerState.currentPage > 0 -> -1
                localX > widthPx - edgeZone && pagerState.currentPage < pages.lastIndex -> 1
                else -> 0
            }
        }

        // 边缘悬停翻页：翻页后按最后手指位置重新解析最近网格槽，
        // 避免松手时用上页的槽位索引落子。
        LaunchedEffect(draggingKey, edgeDirection, pagerState.currentPage) {
            val direction = edgeDirection
            if (draggingKey == null || direction == 0) return@LaunchedEffect
            val targetPage = (pagerState.currentPage + direction).coerceIn(0, pages.lastIndex)
            if (targetPage == pagerState.currentPage) return@LaunchedEffect
            delay(EDGE_HOVER_MILLIS)
            if (draggingKey != null && edgeDirection == direction) {
                pagerState.animateScrollToPage(targetPage)
                dragHoverTarget = null
                folderCandidate = null
                folderArmed = false
                edgeDirection = 0
                updateDropTargets(dragPosition)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = draggingKey == null,
        ) { page ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 18.dp,
                    end = 12.dp,
                    bottom = if (settings.showPageIndicator) 44.dp else 20.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                itemsIndexed(
                    items = pages[page],
                    key = { slot, cell -> cell?.key ?: "slot-$page-$slot" },
                ) { slot, cell ->
                    val slotKey = "$page:$slot"
                    DisposableEffect(slotKey) {
                        onDispose { slotBounds.remove(slotKey) }
                    }
                    if (cell == null) {
                        // 自由摆放的空槽：末页首个空槽渲染“添加”入口，其余纯占位。
                        // 空槽同样注册区域，是合法的拖拽落点。
                        val slotModifier = Modifier
                            .fillMaxWidth()
                            .height(cellHeight)
                            .onGloballyPositioned { coords ->
                                val topLeft = coords.positionInRoot()
                                slotBounds[slotKey] = Rect(
                                    topLeft.x.toInt(),
                                    topLeft.y.toInt(),
                                    (topLeft.x + coords.size.width).toInt(),
                                    (topLeft.y + coords.size.height).toInt(),
                                )
                            }
                        if (page == pages.lastIndex && slot == addSlotIndex) {
                            AddCell(
                                iconSize = iconSize,
                                showLabel = settings.showLabels,
                                cornerRadiusPercent = settings.iconCornerRadiusPercent,
                                modifier = slotModifier.clickable(onClick = onAddRequested),
                            )
                        } else {
                            Spacer(slotModifier)
                        }
                        return@itemsIndexed
                    }
                    DisposableEffect(cell.key) {
                        onDispose { cellBounds.remove(cell.key) }
                    }
                    val cellInteraction = remember { MutableInteractionSource() }
                    val cellPressed by cellInteraction.collectIsPressedAsState()
                    LauncherCell(
                        cell = cell,
                        iconSize = iconSize,
                        settings = settings,
                        isSource = draggingKey == cell.key,
                        isMergeTarget = folderArmed && folderCandidate == cell.key,
                        isReorderTarget = dragHoverTarget == cell.key && !folderArmed,
                        isEditMode = editMode,
                        isEditSelected = editSelection[cell.key] == true,
                        isPressed = cellPressed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cellHeight)
                            .onGloballyPositioned { coords ->
                                val topLeft = coords.positionInRoot()
                                val rect = Rect(
                                    topLeft.x.toInt(),
                                    topLeft.y.toInt(),
                                    (topLeft.x + coords.size.width).toInt(),
                                    (topLeft.y + coords.size.height).toInt(),
                                )
                                cellBounds[cell.key] = rect
                                slotBounds[slotKey] = rect
                            }
                            .pointerInput(cell.key, iconSize) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { local ->
                                        // 编辑模式下不弹菜单/不进入拖拽。
                                        if (editMode) return@detectDragGesturesAfterLongPress
                                        // iOS 顺序：长按确认后先弹情境菜单（图标留在原格），
                                        // 后续移动超过 touchSlop 才进入拖拽。
                                        draggedDistance = Offset.Zero
                                        pendingDragCell = cell
                                        pendingDragLocal = local
                                        menuFor = cell
                                        // 记录按压点（根布局坐标）：菜单以此为四向空间准心。
                                        menuPressPoint = cellBounds[cell.key]?.let { rect ->
                                            Offset(rect.left + local.x, rect.top + local.y)
                                        }
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        draggedDistance += amount
                                        val pendingCell = pendingDragCell
                                        if (draggingKey == null && pendingCell != null) {
                                            // 菜单态容忍 touchSlop 内的抖动；超出后切换为拖拽。
                                            if (draggedDistance.getDistance() <= viewConfiguration.touchSlop) {
                                                return@detectDragGesturesAfterLongPress
                                            }
                                            val key = pendingCell.key
                                            val rect = cellBounds[key]
                                            if (rect == null) {
                                                pendingDragCell = null
                                                return@detectDragGesturesAfterLongPress
                                            }
                                            menuFor = null
                                            draggingKey = key
                                            val iconPx = with(density) { iconSize.toPx() }
                                            val iconLeft = (rect.width() - iconPx) / 2f
                                            val local = pendingDragLocal
                                            dragPosition = Offset(rect.left + local.x, rect.top + local.y)
                                            dragRegistration = Offset(
                                                x = (local.x - iconLeft).coerceIn(0f, iconPx),
                                                y = local.y.coerceIn(0f, iconPx),
                                            )
                                            pendingDragCell = null
                                        }
                                        dragPosition += amount
                                        updateDropTargets(dragPosition)
                                    },
                                    onDragEnd = {
                                        val from = draggingKey
                                        val target = dragHoverTarget
                                        val slotIndex = dragTargetCellIndex
                                        when {
                                            from != null && target != null && folderArmed ->
                                                viewModel.createFolder(from, target)
                                            // 自由摆放：落在最近网格槽（空槽直落，占用则交换）。
                                            from != null && slotIndex >= 0 && !settings.autoArrangeHome ->
                                                viewModel.moveCellToSlot(
                                                    draggedKey = from,
                                                    toPage = pagerState.currentPage,
                                                    toSlot = slotIndex,
                                                    pageCapacity = pageCapacity,
                                                )
                                            // 自动整理：保持压实重排语义。
                                            from != null && slotIndex >= 0 -> {
                                                val dragged = pages.flatten().firstOrNull { it?.key == from }
                                                if (dragged != null && !dragged.isFolder) {
                                                    viewModel.moveApp(
                                                        appId = dragged.app.id,
                                                        toPage = pagerState.currentPage,
                                                        toCellIndex = slotIndex,
                                                    )
                                                } else if (target != null) {
                                                    viewModel.moveCell(from, target, pageCapacity)
                                                }
                                            }
                                        }
                                        pendingDragCell = null
                                        draggingKey = null
                                        dragHoverTarget = null
                                        dragTargetCellIndex = -1
                                        folderCandidate = null
                                        folderArmed = false
                                        edgeDirection = 0
                                    },
                                    onDragCancel = {
                                        pendingDragCell = null
                                        draggingKey = null
                                        dragHoverTarget = null
                                        dragTargetCellIndex = -1
                                        folderCandidate = null
                                        folderArmed = false
                                        edgeDirection = 0
                                    },
                                )
                            }
                            .clickable(
                                enabled = draggingKey == null,
                                // 去除整格 ripple（"网格高亮"），按下反馈由图标自身缩放承担。
                                interactionSource = cellInteraction,
                                indication = null,
                            ) {
                                if (editMode) {
                                    // 编辑模式：点按切换勾选，而非启动。
                                    editSelection[cell.key] = !(editSelection[cell.key] ?: false)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else if (cell.isFolder) {
                                    folderOpenFor = cell.app.folderId
                                } else {
                                    onLaunch(cell.app.id, cell.app.url)
                                }
                            },
                    )
                }
                // 自动整理模式：“添加”入口追加在末页最后一个图标之后；
                // 自由摆放模式已在首个空槽内渲染（见上方 itemsIndexed）。
                if (!freePlacement && page == pages.lastIndex) {
                    item(key = "__add__") {
                        AddCell(
                            iconSize = iconSize,
                            showLabel = settings.showLabels,
                            cornerRadiusPercent = settings.iconCornerRadiusPercent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight)
                                .clickable(onClick = onAddRequested),
                        )
                    }
                }
            }
        }

        if (settings.showPageIndicator && pages.size > 1) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(50),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages.size) { index ->
                        val selected = pagerState.currentPage == index
                        Box(
                            Modifier
                                .size(if (selected) 8.dp else 6.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(50),
                                ),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = apps.isEmpty() && draggingKey == null,
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有应用", style = MaterialTheme.typography.titleMedium)
                Text(
                    "点按添加，把常用网站放到桌面",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 独立于 Pager/LazyGrid 的 DragLayer：只绘制，不参与网格测量。
        draggedCell?.let { cell ->
            val scale by animateFloatAsState(
                targetValue = if (draggingKey != null) 1.08f else 1f,
                animationSpec = spring(dampingRatio = 0.68f, stiffness = 520f),
                label = "drag-icon-scale",
            )
            AppIcon(
                app = cell.app,
                size = iconSize,
                cornerRadiusPercent = settings.iconCornerRadiusPercent,
                folderPreview = cell.folderMembers.take(4),
                shadowElevation = 18.dp,
                modifier = Modifier.graphicsLayer {
                    translationX = dragPosition.x - rootOrigin.x - dragRegistration.x
                    translationY = dragPosition.y - rootOrigin.y - dragRegistration.y
                    scaleX = scale
                    scaleY = scale
                },
            )
        }

        // 编辑模式覆盖层：顶部"完成"胶囊 + 底部批量操作行。
        if (editMode) {
            EditModeOverlay(
                selectedCount = editSelection.values.count { it },
                totalCount = pages.sumOf { page -> page.count { it != null } },
                onSelectAll = {
                    val allSelected = editSelection.values.count { it } ==
                        pages.sumOf { page -> page.count { it != null } }
                    pages.flatten().filterNotNull().forEach { editSelection[it.key] = !allSelected }
                },
                onClearSelection = { editSelection.clear() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            // 完成胶囊：右上。
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable(onClick = { exitEditMode() }),
                ) {
                    Text(
                        "完成",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    menuFor?.let { cell ->
        val isFolder = cell.isFolder
        val items = buildList {
            if (isFolder) {
                add(
                    AppContextMenuItem("打开文件夹", Icons.Filled.FolderOpen) {
                        folderOpenFor = cell.app.folderId
                    },
                )
                add(
                    AppContextMenuItem("解散文件夹", Icons.Filled.FolderOff) {
                        viewModel.dissolveFolder(cell.app.folderId.orEmpty())
                    },
                )
            } else {
                add(
                    AppContextMenuItem("打开", Icons.Filled.Launch) {
                        onLaunch(cell.app.id, cell.app.url)
                    },
                )
                if (cell.app.folderId != null) {
                    add(
                        AppContextMenuItem("移出文件夹", Icons.Filled.FolderOff) {
                            viewModel.removeFromFolder(cell.app.id)
                        },
                    )
                }
                add(
                    AppContextMenuItem(
                        if (cell.app.desktopMode) "切回手机版" else "桌面版网页",
                        Icons.Filled.DesktopWindows,
                    ) {
                        viewModel.toggleDesktopMode(cell.app.id)
                    },
                )
                add(
                    AppContextMenuItem(
                        if (cell.app.keepAlive) "关闭后台保活" else "开启后台保活",
                        Icons.Filled.Bedtime,
                    ) {
                        viewModel.toggleKeepAlive(cell.app.id)
                    },
                )
            }
            add(
                AppContextMenuItem(
                    if (isFolder) "删除文件夹" else "删除",
                    Icons.Filled.Delete,
                    destructive = true,
                ) {
                    confirmDeleteFor = cell
                },
            )
        }
        AppContextMenu(
            items = items,
            onDismiss = {
                menuFor = null
                menuPressPoint = null
            },
            anchorPoint = menuPressPoint?.let {
                IntOffset(it.x.roundToInt(), it.y.roundToInt())
            },
        )
    }

    confirmDeleteFor?.let { cell ->
        if (cell.isFolder) {
            AlertDialog(
                onDismissRequest = { confirmDeleteFor = null },
                title = { Text("删除文件夹？") },
                text = {
                    Text("将同时删除文件夹内的 ${cell.folderMembers.size} 个应用图标；网页登录态与本地数据不会删除。")
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteFolder(cell.app.folderId.orEmpty())
                        confirmDeleteFor = null
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteFor = null }) { Text("取消") }
                },
            )
        } else {
            val app = cell.app
            AlertDialog(
                onDismissRequest = { confirmDeleteFor = null },
                title = { Text("删除「${app.title}」？") },
                text = { Text("网页登录态与本地数据不会删除；删除的只是主页图标。") },
                confirmButton = {
                    TextButton(onClick = {
                        val folderId = app.folderId
                        if (folderId != null && apps.count { it.folderId == folderId } <= 2) {
                            viewModel.dissolveFolder(folderId)
                        }
                        viewModel.delete(app.id)
                        confirmDeleteFor = null
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteFor = null }) { Text("取消") }
                },
            )
        }
    }

    folderOpenFor?.let { folderId ->
        val members = apps.filter { it.folderId == folderId }
        FolderExpandedPage(
            members = members,
            cornerRadiusPercent = settings.iconCornerRadiusPercent,
            onLaunch = { id, url ->
                folderOpenFor = null
                onLaunch(id, url)
            },
            onDissolve = {
                viewModel.dissolveFolder(folderId)
                folderOpenFor = null
            },
            onDismiss = { folderOpenFor = null },
        )
    }
}

/**
 * 文件夹展开页（对齐 HyperOS）：全屏暗化 + 顶部文件夹名 + 3 列大网格成员图标。
 * 替代旧的 AlertDialog 列表，成员以与主屏一致的圆角图标 + 名称呈现。
 */
@Composable
private fun FolderExpandedPage(
    members: List<WebAppEntity>,
    cornerRadiusPercent: Int,
    onLaunch: (String, String) -> Unit,
    onDissolve: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) { window?.setDimAmount(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.86f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(28.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* 吞掉点击 */ }
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Text(
                    "文件夹",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${members.size} 个应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                // 3 列大网格成员
                val columns = 3
                members.chunked(columns).forEach { rowMembers ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    ) {
                        rowMembers.forEach { member ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onLaunch(member.id, member.url) }
                                    .padding(vertical = 6.dp),
                            ) {
                                AppIcon(
                                    app = member,
                                    size = 56.dp,
                                    cornerRadiusPercent = cornerRadiusPercent,
                                )
                                Text(
                                    member.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                        // 补齐空位保持网格对齐
                        repeat(columns - rowMembers.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                TextButton(onClick = onDissolve) {
                    Text("解散文件夹", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 编辑模式底部操作行：选中计数 + 全选/清空，半透明磨砂底。 */
@Composable
private fun EditModeOverlay(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                "已选 $selectedCount / $totalCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onSelectAll) {
                    Text(
                        if (selectedCount == totalCount) "取消全选" else "全选",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = onClearSelection, enabled = selectedCount > 0) {
                    Text("清空")
                }
            }
        }
    }
}

@Composable
private fun LauncherCell(
    cell: HomeCell,
    iconSize: Dp,
    settings: HomeSettings,
    isSource: Boolean,
    isMergeTarget: Boolean,
    isReorderTarget: Boolean,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    isEditSelected: Boolean = false,
    isPressed: Boolean = false,
) {
    val targetScale by animateFloatAsState(
        targetValue = if (isMergeTarget) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
        label = "drop-target-scale",
    )
    // 点按反馈：图标自身轻微回缩（替代整格 ripple 的"网格高亮"）。
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "press-scale",
    )
    // 编辑（jiggle）模式：图标做 iOS 式小幅旋转抖动。
    val jiggleTransition = rememberInfiniteTransition(label = "jiggle")
    val jiggleAnim by jiggleTransition.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 130, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "jiggle-rotation",
    )
    val jiggleRotation = if (isEditMode) jiggleAnim else 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.alpha(if (isSource) 0.18f else 1f),
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(settings.iconCornerRadiusPercent.coerceIn(0, 50)),
                color = androidx.compose.ui.graphics.Color.Transparent,
                border = if (isReorderTarget) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                } else null,
                modifier = Modifier.graphicsLayer {
                    scaleX = targetScale * pressScale
                    scaleY = targetScale * pressScale
                    rotationZ = jiggleRotation
                },
            ) {
                AppIcon(
                    app = cell.app,
                    size = iconSize,
                    cornerRadiusPercent = settings.iconCornerRadiusPercent,
                    folderPreview = cell.folderMembers.take(4),
                )
            }
            // 编辑模式勾选角标：左上圆形，选中主色实心 + 对勾，未选中描边空心。
            if (isEditMode) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (isEditSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            },
                        )
                        .border(
                            1.5.dp,
                            if (isEditSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            CircleShape,
                        ),
                ) {
                    if (isEditSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        if (settings.showLabels) {
            Text(
                text = if (cell.isFolder) "文件夹" else cell.app.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
private fun AddCell(
    iconSize: Dp,
    showLabel: Boolean,
    cornerRadiusPercent: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(cornerRadiusPercent.coerceIn(0, 50))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "添加",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showLabel) {
            Text(
                "添加",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}
