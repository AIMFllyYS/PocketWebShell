package com.webshell.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.webshell.core.designsystem.components.AppCard
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.designsystem.components.AppSearchField
import com.webshell.core.designsystem.components.AppSheet
import com.webshell.core.designsystem.components.SiteIcon
import com.webshell.core.designsystem.theme.AppSpacing

@Composable
internal fun SavedPagesSheet(
    entries: List<BrowserSavedPage>,
    bookmarks: Boolean,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onLoadMore: (() -> Unit)? = null,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
) {
    AppSheet(onDismissRequest = onDismiss) {
        SavedPagesContent(
            entries, bookmarks, onOpen, onRemove, onClear, onDismiss,
            Modifier.fillMaxHeight(0.74f),
            onLoadMore,
            searchQuery,
            onSearchQueryChange,
        )
    }
}

@Composable
internal fun SavedPagesContent(
    entries: List<BrowserSavedPage>,
    bookmarks: Boolean,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: (() -> Unit)? = null,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        AppNavigationBar(
            title = stringResource(if (bookmarks) R.string.browser_bookmarks else R.string.browser_history),
            actions = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_done)) } },
        )
        if (!bookmarks) {
            AppSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = stringResource(R.string.browser_history_search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (entries.isEmpty()) {
            val empty = when {
                bookmarks -> stringResource(R.string.browser_no_bookmarks)
                searchQuery.isNotBlank() -> stringResource(R.string.browser_history_empty_search)
                else -> stringResource(R.string.browser_no_history)
            }
            SheetEmptyState(empty, Modifier.weight(1f))
        } else if (bookmarks) {
            BookmarkList(entries, onOpen, onRemove, Modifier.weight(1f))
        } else {
            HistoryList(
                entries = entries,
                searching = searchQuery.isNotBlank(),
                onOpen = onOpen,
                onLoadMore = onLoadMore,
                modifier = Modifier.weight(1f),
            )
        }
        if (!bookmarks) {
            TextButton(
                onClick = onClear,
                enabled = entries.isNotEmpty() && searchQuery.isBlank(),
                modifier = Modifier.align(Alignment.End).padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.browser_clear_history), color = MaterialTheme.colorScheme.error)
            }
        } else Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun BookmarkList(
    entries: List<BrowserSavedPage>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        items(entries, key = { it.id }) { entry ->
            HistoryRow(entry, onOpen, trailing = {
                IconButton(onClick = { onRemove(entry.url) }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.browser_delete_bookmark))
                }
            })
            AppListDivider()
        }
    }
}

@Composable
private fun HistoryList(
    entries: List<BrowserSavedPage>,
    searching: Boolean,
    onOpen: (String) -> Unit,
    onLoadMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val now = remember { System.currentTimeMillis() }
    val sections = remember(entries, now) { groupHistory(entries, now) }
    var collapsed by remember {
        mutableStateOf(
            HistoryTimeGroup.entries.filterNot(::historyGroupStartsExpanded).toSet(),
        )
    }
    val listState = rememberLazyListState()
    LaunchedEffect(listState, entries.size, onLoadMore) {
        val loadMore = onLoadMore ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= 0 && last >= listState.layoutInfo.totalItemsCount - 3) loadMore()
            }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        sections.forEach { section ->
            val open = searching || section.group !in collapsed
            item(key = "h-${section.group}") {
                HistoryGroupHeader(
                    group = section.group,
                    count = section.items.size,
                    expanded = open,
                    onToggle = {
                        collapsed = if (section.group in collapsed) {
                            collapsed - section.group
                        } else {
                            collapsed + section.group
                        }
                    },
                )
            }
            if (open) {
                item(key = "b-${section.group}") {
                    AppCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        section.items.forEachIndexed { index, entry ->
                            HistoryRow(entry, onOpen)
                            if (index < section.items.lastIndex) AppListDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryGroupHeader(
    group: HistoryTimeGroup,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(start = AppSpacing.lg, end = 4.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(group.titleRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp).padding(start = 2.dp),
        )
    }
}

@Composable
private fun HistoryRow(
    entry: BrowserSavedPage,
    onOpen: (String) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val label = entry.title.ifBlank { entry.url.stripScheme() }
    AppListRow(
        title = label,
        subtitle = entry.url,
        titleMaxLines = 2,
        subtitleMaxLines = 2,
        leading = {
            SiteIcon(
                title = label,
                iconUrl = entry.iconUrl,
                size = 30.dp,
                cornerRadiusPercent = 24,
            )
        },
        onClick = { onOpen(entry.url) },
        trailing = trailing,
    )
}

private val HistoryTimeGroup.titleRes: Int
    get() = when (this) {
        HistoryTimeGroup.TODAY -> R.string.browser_history_today
        HistoryTimeGroup.YESTERDAY -> R.string.browser_history_yesterday
        HistoryTimeGroup.TWO_DAYS_AGO -> R.string.browser_history_two_days_ago
        HistoryTimeGroup.THIS_WEEK -> R.string.browser_history_this_week
        HistoryTimeGroup.THIS_MONTH -> R.string.browser_history_this_month
        HistoryTimeGroup.EARLIER -> R.string.browser_history_earlier
    }
