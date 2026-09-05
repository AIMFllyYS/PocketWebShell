package com.webshell.feature.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.data.HistoryEntity
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppNavigationBar

/** Stateless browser presentation. Session ownership stays in BrowserScreen and BrowserViewModel. */
@Composable
internal fun BrowserTopBar(
    urlInput: String,
    onUrlInputChanged: (String) -> Unit,
    onEditingChanged: (Boolean) -> Unit,
    onGo: () -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
    canGoForward: Boolean,
    onForward: () -> Unit,
    loading: Boolean,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    tabCount: Int,
    onTabSwitcher: () -> Unit,
    onNewTab: () -> Unit,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onFind: () -> Unit,
    menuExpanded: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    desktopMode: Boolean,
    onDesktopMode: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onCloseAllTabs: () -> Unit,
    progress: Int,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val addressLabel = stringResource(R.string.browser_address)
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            // Address and navigation use separate rows. No action can squeeze the editor
            // out of compact widths, and no second backdrop competes with the root dock.
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 50.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        IconButton(onClick = { onMenuToggle(!menuExpanded) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.MoreHoriz,
                                contentDescription = stringResource(R.string.browser_menu),
                                modifier = Modifier.size(24.dp))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { onMenuToggle(false) },
                            modifier = Modifier.widthIn(min = 224.dp),
                            shape = RoundedCornerShape(22.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (bookmarked)
                                    R.string.browser_remove_bookmark else R.string.browser_add_bookmark)) },
                                leadingIcon = { Icon(if (bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder, null) },
                                onClick = { onMenuToggle(false); onBookmark() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_find)) },
                                leadingIcon = { Icon(Icons.Filled.FindInPage, null) },
                                onClick = { onMenuToggle(false); onFind() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_new_tab)) },
                                leadingIcon = { Icon(Icons.Filled.Add, null) },
                                onClick = { onMenuToggle(false); onNewTab() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_desktop)) },
                                leadingIcon = { Icon(Icons.Filled.DesktopWindows, null) },
                                trailingIcon = { if (desktopMode) Icon(Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.browser_enabled)) },
                                onClick = onDesktopMode,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_history)) },
                                leadingIcon = { Icon(Icons.Filled.History, null) },
                                onClick = onHistory,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_bookmarks)) },
                                leadingIcon = { Icon(Icons.Filled.Star, null) },
                                onClick = onBookmarks,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_close_all_tabs),
                                    color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = onCloseAllTabs,
                            )
                        }
                    }
                    BasicTextField(
                        value = urlInput,
                        onValueChange = onUrlInputChanged,
                        modifier = Modifier.weight(1f).heightIn(min = 28.dp)
                            .clipToBounds()
                            .semantics { contentDescription = addressLabel }
                            .onFocusChanged {
                                focused = it.isFocused
                                onEditingChanged(it.isFocused)
                            },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Start,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = {
                            onGo()
                            focusManager.clearFocus()
                        }),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                // Keep one live editor for focus, selection and accessibility;
                                // its retained horizontal scroll must not become the idle label.
                                Box(Modifier.fillMaxWidth().graphicsLayer {
                                    alpha = if (focused) 1f else 0f
                                }) {
                                    inner()
                                }
                                if (!focused || urlInput.isEmpty()) {
                                    Text(
                                        text = urlInput.ifEmpty {
                                            stringResource(R.string.browser_address_placeholder)
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (urlInput.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface,
                                        textAlign = if (focused) TextAlign.Start else TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                                    )
                                }
                            }
                        },
                    )
                    IconButton(onClick = if (loading) onStop else onRefresh, modifier = Modifier.size(48.dp)) {
                        Icon(if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = stringResource(if (loading)
                                R.string.browser_stop else R.string.browser_refresh),
                            modifier = Modifier.size(21.dp))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.browser_back),
                        tint = if (canGoBack) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                }
                IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.browser_forward),
                        tint = if (canGoForward) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                }
                IconButton(onClick = onBookmarks, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Bookmarks,
                        contentDescription = stringResource(R.string.browser_bookmarks),
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                }
                IconButton(onClick = onNewTab, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.browser_new_tab),
                        tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onTabSwitcher, modifier = Modifier.size(48.dp)) { TabCountBadge(tabCount) }
            }
            // Reserve a fixed progress slot: loading never shifts WebView measurement.
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                if (loading) LinearProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

/** Tab switcher badge retains one stable accessibility label and a 48dp parent target. */
@Composable
private fun TabCountBadge(tabCount: Int) {
    Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Tab, contentDescription = stringResource(R.string.browser_tabs),
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
        Text(
            text = tabCount.coerceAtMost(99).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
internal fun FindBar(
    query: String,
    active: Int,
    total: Int,
    onQueryChanged: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val fieldLabel = stringResource(R.string.browser_find_placeholder)
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = fieldLabel },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(fieldLabel, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                },
            )
            Text(
                stringResource(R.string.browser_find_count, if (total > 0) active + 1 else 0, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            IconButton(onClick = onPrevious, enabled = total > 0, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.browser_find_previous))
            }
            IconButton(onClick = onNext, enabled = total > 0, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.browser_find_next))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.browser_find_close))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TabSwitcherSheet(
    tabs: List<BrowserTab>,
    activeTabId: String?,
    onActivate: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
            AppNavigationBar(
                title = stringResource(R.string.browser_tabs_count, tabs.size),
                actions = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_done)) } },
            )
            if (tabs.isEmpty()) {
                EmptyTabsPrompt(modifier = Modifier.weight(1f).fillMaxWidth(), onNewTab = onNewTab)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(132.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(tabs, key = { it.tabId }) { tab ->
                        TabCard(
                            tab = tab,
                            active = tab.tabId == activeTabId,
                            onClick = { onActivate(tab.tabId) },
                            onClose = { onClose(tab.tabId) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onNewTab) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.browser_new_tab))
                }
                TextButton(onClick = onCloseAll, enabled = tabs.isNotEmpty()) {
                    Text(stringResource(R.string.browser_close_all), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TabCard(tab: BrowserTab, active: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val borderColor = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier.clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(if (active) 2.dp else 0.5.dp, borderColor, shape)
            .semantics { selected = active }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.9f)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            val thumb = tab.thumbnail
            if (thumb != null) {
                Image(
                    bitmap = remember(thumb) { thumb.asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.Public, null,
                    modifier = Modifier.align(Alignment.Center).size(42.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(48.dp)) {
                Box(
                    Modifier.size(28.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.browser_close_tab), modifier = Modifier.size(17.dp))
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(tab.title.ifBlank { stringResource(R.string.browser_new_tab) },
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tab.url.stripScheme(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistorySheet(
    viewModel: BrowserViewModel,
    onOpen: (HistoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val history by viewModel.history.collectAsStateWithLifecycle(initialValue = emptyList())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.74f)) {
            AppNavigationBar(title = stringResource(R.string.browser_history),
                actions = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_done)) } })
            if (history.isEmpty()) {
                SheetEmptyState(stringResource(R.string.browser_no_history), Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    items(history, key = { it.id }) { entry ->
                        AppListRow(
                            title = entry.title.ifBlank { entry.url.stripScheme() },
                            subtitle = entry.url,
                            leadingIcon = Icons.Filled.Public,
                            onClick = { onOpen(entry) },
                        )
                        AppListDivider()
                    }
                }
            }
            TextButton(onClick = { viewModel.clearHistory() }, enabled = history.isNotEmpty(),
                modifier = Modifier.align(Alignment.End).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.browser_clear_history), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksSheet(
    viewModel: BrowserViewModel,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle(initialValue = emptyList())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.74f)) {
            AppNavigationBar(title = stringResource(R.string.browser_bookmarks),
                actions = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_done)) } })
            if (bookmarks.isEmpty()) {
                SheetEmptyState(stringResource(R.string.browser_no_bookmarks), Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        AppListRow(
                            title = bookmark.title.ifBlank { bookmark.url.stripScheme() },
                            subtitle = bookmark.url,
                            leadingIcon = Icons.Filled.Star,
                            onClick = { onOpen(bookmark.url) },
                            trailing = {
                                IconButton(onClick = { viewModel.removeBookmark(bookmark.url) }) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.browser_delete_bookmark),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                        )
                        AppListDivider()
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SheetEmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun EmptyTabsPrompt(modifier: Modifier = Modifier, onNewTab: () -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Public, null, modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        Text(stringResource(R.string.browser_no_tabs), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 20.dp))
        Text(stringResource(R.string.browser_start_hint), style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        TextButton(onClick = onNewTab) { Text(stringResource(R.string.browser_create_tab)) }
    }
}
