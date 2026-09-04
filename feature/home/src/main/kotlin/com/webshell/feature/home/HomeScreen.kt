package com.webshell.feature.home

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.webshell.core.data.SCROLL_MODE_VERTICAL
import com.webshell.core.data.WebAppEntity
import com.webshell.core.designsystem.components.AppContextMenu
import com.webshell.core.designsystem.components.AppContextMenuItem
import com.webshell.core.designsystem.theme.LocalPhotoWallpaperPath
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 手机桌面式主页。
 *
 * 拖拽采用与 Launcher3 相同的分层思路：网格只保留占位，独立浮动层绘制跟手图标，
 * 因此拖动不会参与 LazyGrid 测量，也不会把整页撑大或缩小。
 *
 * 交互层拆分（见同包文件）：
 * - [HomeInteractionState]：全部拖拽/菜单/编辑模式会话状态的集中持有者；
 * - [HomeGesturesKt]（HomeGestures.kt）：cell 手势检测、根级拖拽会话、空白长按、
 *   双指捏合、边缘悬停翻页等手势板块。本文件只保留组合根、网格容器、
 *   菜单/对话框与浮层。
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
    // 交互会话状态（拖拽/菜单/编辑模式/布局注册表）集中在 HomeInteractionState：
    // 手势协程只捕获这个稳定对象，读写落到同一份快照状态（历史上两个 P0 缺陷
    // 都源于状态散落在数十个 remember 中被手势闭包按值捕获，见该类的文档）。
    // 这里用属性委托把各字段别名为本地变量，下方组合代码的读写无需改动。
    val ui = rememberHomeInteractionState()
    var tempPageSide by ui::tempPageSide
    val pagerState = rememberPagerState(
        pageCount = { (pages.size + if (tempPageSide != 0) 1 else 0).coerceAtLeast(1) },
    )
    // pager 页号 → 数据页号的左偏移：左侧临时屏存在时数据页 = pager 页 - 1。
    val tempLeftOffset = ui.tempLeftOffset
    // 上下滚动模式：所有页摊平成一条 LazyVerticalGrid（数据模型不变，
    // 落子时全局下标换算回 page = index / capacity、slot = index % capacity）。
    val verticalMode = settings.homeScrollMode == SCROLL_MODE_VERTICAL
    val lazyGridState = rememberLazyGridState()
    val flatCells: List<HomeCell?> = remember(pages, pageCapacity) {
        HomePages.flatten(pages, pageCapacity)
    }
    // “添加”入口在摊平列表中的下标：末页第一个空槽（自由摆放用 addSlotIndex；
    // 自动整理的末页图标压实靠左，首个空槽即 pages.last().size）。
    val addFlatIndex = remember(pages, addSlotIndex, freePlacement, pageCapacity) {
        val lastPageStart = (pages.size - 1).coerceAtLeast(0) * pageCapacity
        val slotInPage = if (freePlacement) {
            addSlotIndex
        } else {
            pages.lastOrNull()?.size ?: 0
        }
        if (slotInPage in 0 until pageCapacity) lastPageStart + slotInPage else -1
    }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val cellBounds = ui.cellBounds
    val slotBounds = ui.slotBounds
    var rootOrigin by ui::rootOrigin
    var draggingKey by ui::draggingKey
    var dragPosition by ui::dragPosition
    var dragRegistration by ui::dragRegistration
    var dragHoverTarget by ui::dragHoverTarget
    var dragTargetCellIndex by ui::dragTargetCellIndex
    var dragTargetPage by ui::dragTargetPage
    var folderCandidate by ui::folderCandidate
    var folderArmed by ui::folderArmed
    var edgeDirection by ui::edgeDirection
    var verticalEdgeDirection by ui::verticalEdgeDirection
    // iOS 顺序：长按先弹情境菜单；按住并移动超过 16dp 后菜单淡出、图标跟手。
    var menuFor by ui::menuFor
    var menuPressPoint by ui::menuPressPoint
    var blankMenuPoint by ui::blankMenuPoint
    var allAppsEntryRect by ui::allAppsEntryRect
    // 「全部应用」抽屉开关。
    var allAppsOpen by remember { mutableStateOf(false) }
    var folderOpenFor by remember { mutableStateOf<String?>(null) }
    var confirmDeleteFor by remember { mutableStateOf<HomeCell?>(null) }
    var renameFor by remember { mutableStateOf<HomeCell?>(null) }
    var iconEditFor by remember { mutableStateOf<HomeCell?>(null) }
    // ViewModel 一次性消息（刷新成功/失败等）的轻量 toast 浮层。
    var toast by remember { mutableStateOf<String?>(null) }
    // 编辑（jiggle）模式：双指捏合进入，点选图标做批量整理。
    var editMode by ui::editMode
    val editSelection = ui.editSelection

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

    LaunchedEffect(Unit) {
        viewModel.messages.collect { toast = it }
    }

    // 落子动作：手势层不直接依赖 ViewModel，动作在此装配绑定（pageCapacity 随
    // 设置变化，手势工厂内以 rememberUpdatedState 保持最新）。
    val dropActions = HomeDropActions(
        createFolder = viewModel::createFolder,
        prependPageMove = { key, slot ->
            viewModel.prependPageMove(key, slot, pageCapacity)
        },
        moveCellToSlot = { key, page, slot ->
            viewModel.moveCellToSlot(key, page, slot, pageCapacity)
        },
        moveApp = viewModel::moveApp,
        moveCell = { from, target -> viewModel.moveCell(from, target, pageCapacity) },
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            // 空白处长按菜单（Initial pass）与双指捏合编辑模式：手势板块见
            // HomeGestures.kt，状态全部落在 ui（HomeInteractionState）。
            .homeBlankAreaMenu(ui, haptics)
            .homePinchEditMode(ui, haptics),
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
        val heightPx = with(density) { maxHeight.toPx() }
        val iconSize = settings.iconSizeDp.dp.coerceAtMost(
            ((maxWidth - 24.dp) / settings.gridColumns) - 12.dp,
        ).coerceAtLeast(40.dp)
        val cellHeight = iconSize + if (settings.showLabels) 30.dp else 16.dp

        // 落点/悬停解析入口：手势层每个 MOVE 事件回调这里。pagerState.currentPage
        // 在调用瞬间现读（组合期快照在拖拽中会过期）；本 lambda 随重组重建，
        // 手势工厂内以 rememberUpdatedState 包装，协程始终调用最新版本。
        val onDragMoved: (Offset) -> Unit = { position ->
            ui.updateDropTargets(
                position = position,
                pages = pages,
                currentPage = pagerState.currentPage,
                verticalMode = verticalMode,
                containerWidthPx = widthPx,
                containerHeightPx = heightPx,
                iconSize = iconSize,
                density = density,
                haptics = haptics,
            )
        }

        // 拖拽悬停效果：文件夹合并计时、边缘翻页/开新屏状态机、上下滚动自动滚动。
        HomeDragEffects(
            state = ui,
            pagerState = pagerState,
            lazyGridState = lazyGridState,
            pages = pages,
            verticalMode = verticalMode,
            haptics = haptics,
            onDragMoved = onDragMoved,
        )

        // 单个网格槽的渲染（pager 每页 / 上下滚动摊平列表共用）：空槽注册落点区域，
        // 占用槽渲染 LauncherCell + 单通道手势状态机。
        val gridCellContent: @Composable (page: Int, slot: Int, cell: HomeCell?, isAddSlot: Boolean) -> Unit =
            { page, slot, cell, isAddSlot ->
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
                    if (isAddSlot) {
                        AddCell(
                            iconSize = iconSize,
                            showLabel = settings.showLabels,
                            cornerRadiusPercent = settings.iconCornerRadiusPercent,
                            modifier = slotModifier.clickable(onClick = onAddRequested),
                        )
                    } else {
                        Spacer(slotModifier)
                    }
                } else {
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
                        // 单通道手势状态机已抽到 HomeGestures.kt（tap/长按菜单/
                        // 编辑即拖检测，拖拽条件满足后置位 draggingKey 并交接给
                        // 下方的根级拖拽会话层）。
                        .homeCellGesture(
                            state = ui,
                            cell = cell,
                            iconSize = iconSize,
                            interactionSource = cellInteraction,
                            haptics = haptics,
                            onLaunch = onLaunch,
                            onFolderOpen = { folderOpenFor = it },
                            onDragMoved = onDragMoved,
                        ),
                )
                }
            }

        // 拖拽会话层（根级，对齐 Launcher3 DragController）：包住 pager/列表的
        // 容器承载拖拽会话主循环（实现见 HomeGestures.kt 的 homeDragSession）。
        // 会话必须活在 cell 之上：翻页/开新屏后源 cell 随旧页被 Pager 移出组合，
        // 挂在 cell 上的协程会被一并取消——状态清零、临时屏回弹，表现为
        // “翻页/开新屏没反应”（本层即为该缺陷的修复）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .homeDragSession(
                    state = ui,
                    pages = pages,
                    verticalMode = verticalMode,
                    autoArrangeHome = settings.autoArrangeHome,
                    dropActions = dropActions,
                    onDragMoved = onDragMoved,
                ),
        ) {
        if (verticalMode) {
            // 上下滚动模式：所有页摊平成一条纵向列表，空槽保留 null 占位，
            // 落点时全局下标换算回 (homePage, homeCellIndex)（数据模型不变）。
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.gridColumns),
                state = lazyGridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 18.dp,
                    end = 12.dp,
                    bottom = 20.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // 拖拽中禁用列表手势（与 pager 模式一致的不变量）
                userScrollEnabled = draggingKey == null,
            ) {
                itemsIndexed(
                    items = flatCells,
                    key = { index, cell -> cell?.key ?: "vslot-$index" },
                ) { index, cell ->
                    gridCellContent(index / pageCapacity, index % pageCapacity, cell, index == addFlatIndex)
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = draggingKey == null,
            ) { page ->
                // 临时空白屏（拖拽到末屏右缘/首屏左缘触发）：全空槽，可落子。
                // 左侧临时屏存在时 pager 页 0 为临时屏，数据页 = pager 页 - 1。
                val dataPage = page - tempLeftOffset
                val pageCells = pages.getOrNull(dataPage) ?: List(pageCapacity) { null }
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
                        items = pageCells,
                        key = { slot, cell -> cell?.key ?: "slot-$page-$slot" },
                    ) { slot, cell ->
                        gridCellContent(page, slot, cell, dataPage == pages.lastIndex && slot == addSlotIndex)
                    }
                    // 自动整理模式：“添加”入口追加在末页最后一个图标之后；
                    // 自由摆放模式已在首个空槽内渲染（见上方 itemsIndexed）。
                    if (!freePlacement && dataPage == pages.lastIndex) {
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
        }
        }

        // 页码指示器只在左右翻页模式显示
        if (!verticalMode && settings.showPageIndicator && pages.size > 1) {
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

        // 「全部应用」浮动入口：渲染在 Pager/Grid 之外的 overlay，不参与网格测量；
        // 位于 DragLayer 之下，拖拽图标时浮层始终在最上。
        if (settings.allAppsEntryVisible) {
            AllAppsEntry(
                iconSize = iconSize,
                cornerRadiusPercent = settings.iconCornerRadiusPercent,
                showLabel = settings.showLabels,
                posX = settings.allAppsEntryPosX,
                posY = settings.allAppsEntryPosY,
                containerWidthPx = widthPx,
                containerHeightPx = heightPx,
                onOpen = { allAppsOpen = true },
                onHide = { viewModel.setAllAppsEntryVisible(false) },
                onPositionChange = { x, y -> viewModel.setAllAppsEntryPosition(x, y) },
                onBoundsChanged = { allAppsEntryRect = it },
                modifier = Modifier.align(Alignment.TopStart),
            )
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

        // 轻量 toast 浮层（参考 BrowserScreen）：底部文字，2.5s 自动消失。
        toast?.let { message ->
            Text(
                message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            LaunchedEffect(message) {
                delay(2500)
                toast = null
            }
        }
    }

    // 空白处长按菜单（锚定按压点）：编辑模式入口 + 「全部应用」入口显隐。
    blankMenuPoint?.let { point ->
        AppContextMenu(
            items = listOf(
                AppContextMenuItem("编辑模式", Icons.Filled.Edit) {
                    editMode = true
                },
                AppContextMenuItem(
                    if (settings.allAppsEntryVisible) "隐藏全部应用入口" else "显示全部应用入口",
                    Icons.Filled.Apps,
                ) {
                    viewModel.setAllAppsEntryVisible(!settings.allAppsEntryVisible)
                },
            ),
            onDismiss = { blankMenuPoint = null },
            anchorPoint = IntOffset(point.x.roundToInt(), point.y.roundToInt()),
        )
    }

    // 全部应用抽屉：首字母分区网格 + 右侧字母索引条。
    if (allAppsOpen) {
        // 拼音分组只在应用列表变化时重算（remember 缓存，避免每次重组全量转换）
        val sections = remember(apps) { AllAppsIndex.buildSections(apps) }
        AllAppsDrawer(
            sections = sections,
            columns = settings.gridColumns,
            iconSize = settings.iconSizeDp.dp,
            cornerRadiusPercent = settings.iconCornerRadiusPercent,
            onLaunch = { id, url ->
                allAppsOpen = false
                onLaunch(id, url)
            },
            onDismiss = { allAppsOpen = false },
        )
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
                add(
                    AppContextMenuItem("重命名", Icons.Filled.Edit) {
                        renameFor = cell
                    },
                )
                add(
                    AppContextMenuItem("更改图标", Icons.Filled.Image) {
                        iconEditFor = cell
                    },
                )
                add(
                    AppContextMenuItem("强制刷新", Icons.Filled.Refresh) {
                        viewModel.refreshMetadata(cell.app.id)
                    },
                )
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
                if (cell.app.folderId != null) {
                    add(
                        AppContextMenuItem("移出文件夹", Icons.Filled.FolderOff) {
                            viewModel.removeFromFolder(cell.app.id)
                        },
                    )
                }
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

    renameFor?.let { cell ->
        var text by remember(cell.key) { mutableStateOf(cell.app.title) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("应用名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(cell.app.id, text)
                    renameFor = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameFor = null }) { Text("取消") }
            },
        )
    }

    iconEditFor?.let { cell ->
        // 草稿图标地址：预填当前值，留空 = 清除图标回首字母兜底。
        var draft by remember(cell.key) { mutableStateOf(cell.app.iconUrl.orEmpty()) }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        // 上传本地图片作为图标：复制到应用私有 icons 目录后作为 file 路径使用。
        val pickIcon = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    copyPickedIcon(context, uri)?.let { draft = it }
                }
            }
        }
        AlertDialog(
            onDismissRequest = { iconEditFor = null },
            title = { Text("更改图标") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(
                        app = cell.app.copy(iconUrl = draft.ifBlank { null }),
                        size = 64.dp,
                        cornerRadiusPercent = settings.iconCornerRadiusPercent,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("图标地址") },
                        placeholder = { Text("留空则使用首字母图标") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        pickIcon.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }) { Text("上传本地图片") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateIcon(cell.app.id, draft.ifBlank { null })
                    iconEditFor = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { iconEditFor = null }) { Text("取消") }
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

/** 上传的本地图片复制到应用私有 icons 目录（IO 线程），返回绝对路径供图标地址使用。 */
private suspend fun copyPickedIcon(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "icons").apply { mkdirs() }
            val dest = File(dir, "icon_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            dest.absolutePath
        }.getOrNull()
    }
