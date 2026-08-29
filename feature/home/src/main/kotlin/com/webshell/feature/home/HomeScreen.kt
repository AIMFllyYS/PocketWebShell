package com.webshell.feature.home

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.data.HomeSettings
import com.webshell.core.data.WebAppEntity
import kotlinx.coroutines.delay

private const val FOLDER_HOVER_MILLIS = 500L
private const val EDGE_HOVER_MILLIS = 450L

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
    val pages = remember(apps, pageCapacity) {
        HomePages.build(apps = apps, pageCapacity = pageCapacity)
    }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val cellBounds = remember { mutableStateMapOf<String, Rect>() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var dragRegistration by remember { mutableStateOf(Offset.Zero) }
    var draggedDistance by remember { mutableStateOf(Offset.Zero) }
    var dragHoverTarget by remember { mutableStateOf<String?>(null) }
    var folderCandidate by remember { mutableStateOf<String?>(null) }
    var folderArmed by remember { mutableStateOf(false) }
    var edgeDirection by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<WebAppEntity?>(null) }
    var folderOpenFor by remember { mutableStateOf<String?>(null) }
    var confirmDeleteFor by remember { mutableStateOf<WebAppEntity?>(null) }

    val draggedCell = remember(pages, draggingKey) {
        pages.flatten().firstOrNull { it.key == draggingKey }
    }

    BackHandler(enabled = folderOpenFor != null) { folderOpenFor = null }

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
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() },
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val iconSize = settings.iconSizeDp.dp.coerceAtMost(
            ((maxWidth - 24.dp) / settings.gridColumns) - 12.dp,
        ).coerceAtLeast(40.dp)
        val cellHeight = iconSize + if (settings.showLabels) 30.dp else 16.dp

        fun updateDropTargets(position: Offset) {
            val currentKeys = pages.getOrNull(pagerState.currentPage)
                .orEmpty()
                .asSequence()
                .map { it.key }
                .toHashSet()
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
                .firstOrNull { it.key == draggingKey }
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
                items(pages[page], key = { it.key }) { cell ->
                    DisposableEffect(cell.key) {
                        onDispose { cellBounds.remove(cell.key) }
                    }
                    LauncherCell(
                        cell = cell,
                        iconSize = iconSize,
                        settings = settings,
                        isSource = draggingKey == cell.key,
                        isMergeTarget = folderArmed && folderCandidate == cell.key,
                        isReorderTarget = dragHoverTarget == cell.key && !folderArmed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cellHeight)
                            .onGloballyPositioned { coords ->
                                val topLeft = coords.positionInRoot()
                                cellBounds[cell.key] = Rect(
                                    topLeft.x.toInt(),
                                    topLeft.y.toInt(),
                                    (topLeft.x + coords.size.width).toInt(),
                                    (topLeft.y + coords.size.height).toInt(),
                                )
                            }
                            .pointerInput(cell.key, iconSize) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { local ->
                                        val rect = cellBounds[cell.key]
                                            ?: return@detectDragGesturesAfterLongPress
                                        draggingKey = cell.key
                                        draggedDistance = Offset.Zero
                                        dragPosition = Offset(rect.left + local.x, rect.top + local.y)
                                        val iconPx = with(density) { iconSize.toPx() }
                                        val iconLeft = (rect.width() - iconPx) / 2f
                                        dragRegistration = Offset(
                                            x = (local.x - iconLeft).coerceIn(0f, iconPx),
                                            y = local.y.coerceIn(0f, iconPx),
                                        )
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragPosition += amount
                                        draggedDistance += amount
                                        updateDropTargets(dragPosition)
                                    },
                                    onDragEnd = {
                                        val from = draggingKey
                                        val target = dragHoverTarget
                                        when {
                                            from != null && target != null && folderArmed ->
                                                viewModel.createFolder(from, target)
                                            from != null && target != null ->
                                                viewModel.moveCell(from, target, pageCapacity)
                                            draggedDistance.getDistance() < with(density) { 8.dp.toPx() } ->
                                                menuFor = cell.app
                                        }
                                        draggingKey = null
                                        dragHoverTarget = null
                                        folderCandidate = null
                                        folderArmed = false
                                        edgeDirection = 0
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragHoverTarget = null
                                        folderCandidate = null
                                        folderArmed = false
                                        edgeDirection = 0
                                    },
                                )
                            }
                            .clickable(enabled = draggingKey == null) {
                                if (cell.isFolder) {
                                    folderOpenFor = cell.app.folderId
                                } else {
                                    onLaunch(cell.app.id, cell.app.url)
                                }
                            },
                    )
                }
                if (page == pages.lastIndex) {
                    item(key = "__add__") {
                        AddCell(
                            iconSize = iconSize,
                            showLabel = settings.showLabels,
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
                    color = MaterialTheme.colorScheme.outline,
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
                modifier = Modifier.graphicsLayer {
                    translationX = dragPosition.x - rootOrigin.x - dragRegistration.x
                    translationY = dragPosition.y - rootOrigin.y - dragRegistration.y
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = 18.dp.toPx()
                },
            )
        }
    }

    menuFor?.let { app ->
        DropdownMenu(expanded = true, onDismissRequest = { menuFor = null }) {
            if (app.folderId != null) {
                DropdownMenuItem(
                    text = { Text("移出文件夹") },
                    onClick = { viewModel.removeFromFolder(app.id); menuFor = null },
                    leadingIcon = { Icon(Icons.Filled.FolderOff, null) },
                )
            }
            DropdownMenuItem(
                text = { Text(if (app.desktopMode) "切回手机版" else "桌面版网页") },
                onClick = { viewModel.toggleDesktopMode(app.id); menuFor = null },
                leadingIcon = { Icon(Icons.Filled.DesktopWindows, null) },
            )
            DropdownMenuItem(
                text = { Text(if (app.keepAlive) "关闭后台保活" else "开启后台保活") },
                onClick = { viewModel.toggleKeepAlive(app.id); menuFor = null },
                leadingIcon = { Icon(Icons.Filled.Bedtime, null) },
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = { confirmDeleteFor = app; menuFor = null },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
            )
        }
    }

    confirmDeleteFor?.let { app ->
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

    folderOpenFor?.let { folderId ->
        val members = apps.filter { it.folderId == folderId }
        AlertDialog(
            onDismissRequest = { folderOpenFor = null },
            title = { Text("文件夹") },
            text = {
                Column {
                    members.forEach { member ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    folderOpenFor = null
                                    onLaunch(member.id, member.url)
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            AppIcon(
                                app = member,
                                size = 40.dp,
                                cornerRadiusPercent = settings.iconCornerRadiusPercent,
                            )
                            Text(
                                member.title,
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    TextButton(onClick = {
                        viewModel.dissolveFolder(folderId)
                        folderOpenFor = null
                    }) { Text("解散文件夹") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { folderOpenFor = null }) { Text("关闭") }
            },
        )
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
) {
    val targetScale by animateFloatAsState(
        targetValue = if (isMergeTarget) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
        label = "drop-target-scale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.alpha(if (isSource) 0.18f else 1f),
    ) {
        Surface(
            shape = RoundedCornerShape(settings.iconCornerRadiusPercent.coerceIn(0, 50)),
            color = androidx.compose.ui.graphics.Color.Transparent,
            border = if (isReorderTarget) {
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            } else null,
            modifier = Modifier.graphicsLayer {
                scaleX = targetScale
                scaleY = targetScale
            },
        ) {
            AppIcon(
                app = cell.app,
                size = iconSize,
                cornerRadiusPercent = settings.iconCornerRadiusPercent,
                folderPreview = cell.folderMembers.take(4),
            )
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
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    RoundedCornerShape(28),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加")
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
