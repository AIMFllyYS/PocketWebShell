package com.webshell.feature.me

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.data.WebAppEntity
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.components.AppContextMenu
import com.webshell.core.designsystem.components.AppContextMenuItem
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppPrimaryButton
import com.webshell.core.designsystem.components.AppSearchField
import com.webshell.core.designsystem.components.AppSettingsSection
import com.webshell.core.designsystem.components.RevealFromPoint
import com.webshell.core.designsystem.components.SiteIcon
import com.webshell.core.designsystem.components.siteIconGlyph
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.designsystem.theme.LocalTransitionStyle
import com.webshell.core.webengine.storage.CookieFacts
import com.webshell.core.webengine.storage.Metric
import com.webshell.core.webengine.storage.SiteStorageStats
import com.webshell.core.webengine.storage.hostOfUrl
import com.webshell.core.webengine.storage.siteKeyOf


@Composable
internal fun StorageListPage(
    state: StorageUiState,
    apps: List<WebAppEntity>,
    statsById: Map<String, SiteStorageStats>,
    listScrollState: ScrollState,
    viewModel: StorageViewModel,
    onOpenSite: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var sortAnchor by remember { mutableStateOf(IntOffset.Zero) }
    var clearAllPreview by remember { mutableStateOf<ClearAllPreview?>(null) }
    var clearWebsiteDataConfirm by remember { mutableStateOf(false) }

    val rows = remember(apps, statsById, state.query, state.sortMode) {
        val q = state.query.trim().lowercase()
        val filtered = apps.filter { app ->
            q.isEmpty() || app.title.lowercase().contains(q) || hostOf(app.url).lowercase().contains(q)
        }
        val comparator = when (state.sortMode) {
            StorageSortMode.SIZE -> compareByDescending<WebAppEntity> {
                statsById[it.id]?.attributableBytes ?: 0L
            }.thenBy { it.title.lowercase() }
            StorageSortMode.NAME -> compareBy { it.title.lowercase() }
        }
        val known = filtered.filter { statsById[it.id]?.allMetricsUnavailable() != true }
        val unknown = filtered.filter { statsById[it.id]?.allMetricsUnavailable() == true }
        known.sortedWith(comparator) + unknown.sortedBy { it.title.lowercase() }
    }

    DetailPage(
        title = stringResource(R.string.me_storage_title),
        onBack = onBack,
        scrollState = listScrollState,
        actions = {
            IconButton(onClick = { viewModel.scan(forceRefresh = true) }) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.me_storage_refresh))
            }
            Box(
                Modifier.onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    sortAnchor = IntOffset(
                        (position.x + coordinates.size.width / 2).toInt(),
                        (position.y + coordinates.size.height).toInt(),
                    )
                },
            ) {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.me_storage_sort_action))
                }
            }
        },
    ) {
        BrowsingDataControls(viewModel, state.clearingAll || state.erasingSite)
        StorageOverviewSection(
            state = state,
            onClearAll = { clearAllPreview = viewModel.clearAllPreview() },
            onClearWebsiteData = { clearWebsiteDataConfirm = true },
        )
        AppSettingsSection(stringResource(R.string.me_storage_sites_section), Modifier.padding(bottom = 24.dp)) {
            if (apps.isNotEmpty()) {
                AppSearchField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.me_storage_search_hint),
                    modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                )
            }
            when {
                apps.isEmpty() -> Text(
                    stringResource(R.string.me_storage_empty_sites),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(AppSpacing.lg),
                )
                rows.isEmpty() -> Column(Modifier.fillMaxWidth().padding(AppSpacing.lg)) {
                    Text(
                        stringResource(R.string.me_storage_empty_search),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { viewModel.setQuery("") }) {
                        Text(stringResource(R.string.me_storage_clear_search))
                    }
                }
                else -> rows.forEachIndexed { index, app ->
                    SiteStorageRow(
                        app = app,
                        stats = statsById[app.id],
                        scanning = state.scanning,
                        onClick = { onOpenSite(app.id) },
                    )
                    if (index < rows.lastIndex) AppListDivider(hasLeadingIcon = false)
                }
            }
        }
    }

    if (showSortMenu) {
        AppContextMenu(
            items = listOf(
                AppContextMenuItem(stringResource(R.string.me_storage_sort_size), Icons.Filled.ArrowDownward) {
                    viewModel.setSortMode(StorageSortMode.SIZE)
                },
                AppContextMenuItem(stringResource(R.string.me_storage_sort_name), Icons.Filled.SortByAlpha) {
                    viewModel.setSortMode(StorageSortMode.NAME)
                },
            ),
            onDismiss = { showSortMenu = false },
            anchorPoint = sortAnchor,
        )
    }

    clearAllPreview?.let { preview ->
        val base = stringResource(
            R.string.me_storage_clear_all_text,
            formatStorageBytes(preview.estimatedBytes),
        )
        val runningSuffix = if (preview.runningSessions > 0) {
            "；" + stringResource(R.string.me_storage_clear_all_running_suffix, preview.runningSessions)
        } else {
            ""
        }
        AppConfirmDialog(
            title = stringResource(R.string.me_storage_clear_all_title),
            text = base + runningSuffix,
            confirmText = stringResource(R.string.me_storage_confirm_clear),
            dismissText = stringResource(R.string.me_cancel),
            onConfirm = { clearAllPreview = null; viewModel.clearAll() },
            onDismiss = { clearAllPreview = null },
        )
    }

    if (clearWebsiteDataConfirm) {
        AppConfirmDialog(
            title = stringResource(R.string.me_storage_clear_data_title),
            text = stringResource(R.string.me_storage_clear_data_text),
            confirmText = stringResource(R.string.me_storage_confirm_clear_data),
            dismissText = stringResource(R.string.me_cancel),
            destructive = true,
            onConfirm = { clearWebsiteDataConfirm = false; viewModel.clearWebsiteData() },
            onDismiss = { clearWebsiteDataConfirm = false },
        )
    }
}

@Composable
internal fun StorageOverviewSection(
    state: StorageUiState,
    onClearAll: () -> Unit,
    onClearWebsiteData: () -> Unit,
) {
    AppSettingsSection(stringResource(R.string.me_storage_usage_section), Modifier.padding(bottom = 24.dp)) {
        val overview = state.overview
        if (overview == null) {
            Text(
                stringResource(if (state.scanFailed) R.string.me_storage_scan_failed else R.string.me_storage_measuring),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(AppSpacing.lg),
            )
        } else {
            RevealFromPoint {
            Column(Modifier.padding(AppSpacing.lg)) {
                val walkedTotal = overview.clearableBytes + overview.siteDataBytes + overview.appBytes
                val total = overview.systemTotalBytes?.takeIf { it > 0L } ?: walkedTotal
                Text(formatStorageBytes(total), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.me_storage_total_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppSpacing.md))
                Text(
                    formatStorageBytes(overview.clearableBytes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.me_storage_clearable_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (walkedTotal > 0) {
                    Spacer(Modifier.height(AppSpacing.lg))
                    StorageUsageBar(
                        segments = listOf(
                            MaterialTheme.colorScheme.primary to overview.clearableBytes,
                            SiteDataColor to overview.siteDataBytes,
                            AppDataColor to overview.appBytes,
                        ),
                        legend = listOf(
                            MaterialTheme.colorScheme.primary to
                                (stringResource(R.string.me_storage_clearable_label) to overview.clearableBytes),
                            SiteDataColor to
                                (stringResource(R.string.me_storage_cat_site_data) to overview.siteDataBytes),
                            AppDataColor to
                                (stringResource(R.string.me_storage_cat_app) to overview.appBytes),
                        ),
                    )
                }
                if (overview.systemTotalBytes?.let { it > 0L } == true && walkedTotal > 0L) {
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        stringResource(R.string.me_storage_overview_footnote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (overview.unattributableSharedBytes > 0L) {
                    Spacer(Modifier.height(AppSpacing.md))
                    Text(
                        formatStorageBytes(overview.unattributableSharedBytes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.me_storage_unattributable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.me_storage_unattributable_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.logBytes > 0L) {
                    Spacer(Modifier.height(AppSpacing.md))
                    Text(
                        formatStorageBytes(state.logBytes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.me_storage_logs_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.me_storage_logs_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(AppSpacing.md))
                Text(
                    stringResource(
                        if (state.effectiveCapabilities().deleteForSite) {
                            R.string.me_storage_profile_limited_can_erase
                        } else {
                            R.string.me_storage_profile_limited
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.scanning) {
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        stringResource(R.string.me_storage_measuring),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(AppSpacing.lg))
                AppPrimaryButton(
                    text = if (state.clearingAll) {
                        stringResource(R.string.me_storage_clearing)
                    } else {
                        stringResource(R.string.me_storage_clear_all)
                    },
                    onClick = onClearAll,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = (overview.clearableBytes + state.logBytes) > 0 &&
                        !state.clearingAll && !state.erasingSite,
                    loading = state.clearingAll,
                )
                Spacer(Modifier.height(AppSpacing.sm))
                TextButton(
                    onClick = onClearWebsiteData,
                    enabled = !state.clearingAll && !state.erasingSite,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.me_storage_clear_data),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    stringResource(R.string.me_storage_clear_data_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.clearAllFailedSites > 0) {
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        stringResource(R.string.me_storage_clear_failed_count, state.clearAllFailedSites),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            }
        }
    }
}
