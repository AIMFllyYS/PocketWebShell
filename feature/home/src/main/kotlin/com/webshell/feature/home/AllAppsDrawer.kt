package com.webshell.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.webshell.core.data.WebAppEntity
import kotlinx.coroutines.launch

/**
 * 全部应用抽屉（对齐 AOSP/MIUI 应用抽屉体验）：全屏 Dialog +
 * 分区 header（字母）+ 应用图标网格；右侧 A→Z/# 字母索引条，
 * 支持点按跳转与按住滑动连续跳转，当前字母显示放大气泡。
 * 无应用的分区字母置灰且不可点。
 */
@Composable
fun AllAppsDrawer(
    sections: List<AllAppsIndex.Section>,
    columns: Int,
    iconSize: Dp,
    cornerRadiusPercent: Int,
    onLaunch: (appId: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) { window?.setDimAmount(0f) }

        // 扁平化结果含各分区首项下标，字母索引条按它 scrollToItem 跳转
        val flat = remember(sections) { AllAppsIndex.flatten(sections) }
        val gridState = rememberLazyGridState()
        val scope = rememberCoroutineScope()
        val letters = remember { ('A'..'Z').map { it.toString() } + AllAppsIndex.OTHER_SECTION }
        val present = remember(sections) { sections.mapTo(HashSet()) { it.letter } }
        var activeLetter by remember { mutableStateOf<String?>(null) }
        var barHeightPx by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 24.dp,
                    end = 40.dp, // 给右侧字母索引条留位
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = flat.items,
                    key = { _, item ->
                        when (item) {
                            is AllAppsIndex.Item.Header -> "header-${item.letter}"
                            is AllAppsIndex.Item.Entry -> item.app.id
                        }
                    },
                    span = { _, item ->
                        // 分区头独占整行
                        if (item is AllAppsIndex.Item.Header) {
                            GridItemSpan(maxLineSpan)
                        } else {
                            GridItemSpan(1)
                        }
                    },
                ) { _, item ->
                    when (item) {
                        is AllAppsIndex.Item.Header -> Text(
                            item.letter,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                        is AllAppsIndex.Item.Entry -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onLaunch(item.app.id, item.app.url) }
                                .padding(vertical = 4.dp),
                        ) {
                            AppIcon(
                                app = item.app,
                                size = iconSize,
                                cornerRadiusPercent = cornerRadiusPercent,
                            )
                            Text(
                                item.app.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }
            }

            // 右侧字母索引条：A→Z + # 等分纵向排列；点按与按住滑动共用一条手势通道，
            // 按 y 等比映射字母；无应用的分区置灰不跳转。
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.72f)
                    .width(28.dp)
                    .padding(end = 4.dp)
                    .onSizeChanged { barHeightPx = it.height.toFloat() }
                    .pointerInput(letters, present) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun jumpTo(y: Float) {
                                if (barHeightPx <= 0f) return
                                val index = ((y / barHeightPx) * letters.size)
                                    .toInt()
                                    .coerceIn(0, letters.size - 1)
                                val letter = letters[index]
                                if (letter in present) {
                                    activeLetter = letter
                                    flat.sectionFirstIndex[letter]?.let { firstIndex ->
                                        scope.launch { gridState.scrollToItem(firstIndex) }
                                    }
                                }
                            }
                            jumpTo(down.position.y)
                            while (true) {
                                val change = awaitPointerEvent()
                                    .changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (change.changedToUp()) break
                                change.consume()
                                jumpTo(change.position.y)
                            }
                            activeLetter = null
                        }
                    },
            ) {
                letters.forEach { letter ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        Text(
                            letter,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (letter in present) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                // 无应用的分区置灰且不可点
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        )
                    }
                }
            }

            // 当前字母放大气泡（索引条左侧）
            activeLetter?.let { letter ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 44.dp)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        letter,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
