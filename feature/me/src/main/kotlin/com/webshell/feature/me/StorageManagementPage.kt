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

/** 存储管理：概览（已统计占用/可清理/分段条）+ 站点列表（搜索/排序）+ 站点详情子页。 */
@Composable
internal fun StorageManagementPage(
    onBack: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var detailAppId by rememberSaveable { mutableStateOf<String?>(null) }
    val listScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    LaunchedEffect(viewModel) {
        viewModel.scan(forceRefresh = true)
        viewModel.toasts.collect { toast ->
            val text = toast.arg?.let { context.getString(toast.resId, it) } ?: context.getString(toast.resId)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.scan(forceRefresh = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = detailAppId != null) { detailAppId = null }

    val statsById = state.overview?.sites?.associateBy { it.appId }.orEmpty()
    val detailApp = apps.firstOrNull { it.id == detailAppId }
    LaunchedEffect(apps, detailAppId) {
        if (detailAppId != null && detailApp == null && apps.isNotEmpty()) detailAppId = null
    }

    val transitionStyle = LocalTransitionStyle.current
    AnimatedContent(
        targetState = detailAppId != null,
        transitionSpec = { AppMotion.detailEnterFor(transitionStyle) togetherWith AppMotion.detailExitFor(transitionStyle) },
        label = "storage-detail",
    ) { showDetail ->
        if (showDetail) {
            detailApp?.let { app ->
                StorageDetailPage(
                    app = app,
                    apps = apps,
                    stats = statsById[app.id],
                    state = state,
                    viewModel = viewModel,
                    onBack = { detailAppId = null },
                )
            }
        } else {
            StorageListPage(
                state = state,
                apps = apps,
                statsById = statsById,
                listScrollState = listScrollState,
                viewModel = viewModel,
                onOpenSite = { detailAppId = it },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun StorageListPage(
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
private fun StorageOverviewSection(
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

@Composable
private fun StorageDetailPage(
    app: WebAppEntity,
    apps: List<WebAppEntity>,
    stats: SiteStorageStats?,
    state: StorageUiState,
    viewModel: StorageViewModel,
    onBack: () -> Unit,
) {
    var erasePreview by remember { mutableStateOf<EraseSitePreview?>(null) }
    val canErase = viewModel.canEraseSite() && !app.isLocal
    val siblings = remember(app.id, apps) {
        val host = hostOfUrl(app.url).orEmpty()
        val key = siteKeyOf(app.id, host, app.isLocal)
        apps.filter { other ->
            other.id != app.id && siteKeyOf(other.id, hostOfUrl(other.url).orEmpty(), other.isLocal) == key
        }
    }

    DetailPage(title = app.title, onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SiteIcon(
                title = app.title,
                iconUrl = app.iconUrl,
                size = 64.dp,
                glyph = siteIconGlyph(app.isLocal, app.url),
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(app.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                hostOf(app.url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stats?.signedIn == true) {
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    stringResource(R.string.me_storage_signed_in),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        AppSettingsSection(stringResource(R.string.me_storage_usage_section), Modifier.padding(bottom = 24.dp)) {
            Column(Modifier.padding(AppSpacing.lg)) {
                if (stats == null) {
                    Text(
                        stringResource(
                            if (state.scanning) R.string.me_storage_measuring else R.string.me_storage_unmeasurable,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DetailStatRow(
                        stringResource(R.string.me_storage_attributable),
                        if (stats.allMetricsUnavailable()) {
                            stringResource(R.string.me_storage_metric_unavailable)
                        } else {
                            formatStorageBytes(stats.attributableBytes)
                        },
                    )
                    Text(
                        stringResource(R.string.me_storage_attributable_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    DetailStatRow(stringResource(R.string.me_storage_cookie), cookieMetricLabel(stats.cookie))
                    DetailStatRow(
                        stringResource(R.string.me_storage_indexeddb),
                        longMetricLabel(stats.indexedDbBytes),
                    )
                    DetailStatRow(
                        stringResource(R.string.me_storage_quota),
                        longMetricLabel(stats.quotaUsageBytes),
                    )
                    Text(
                        stringResource(R.string.me_storage_quota_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (app.isLocal || stats.localImportBytes is Metric.Measured) {
                        DetailStatRow(
                            stringResource(R.string.me_storage_local_import),
                            longMetricLabel(stats.localImportBytes),
                        )
                    }
                    if (siblings.isNotEmpty()) {
                        Spacer(Modifier.height(AppSpacing.md))
                        Text(
                            stringResource(
                                R.string.me_storage_shared_entries,
                                siblings.joinToString("、") { it.title },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(
                if (canErase) {
                    R.string.me_storage_site_shared_hint_can_erase
                } else {
                    R.string.me_storage_site_shared_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canErase) {
            Spacer(Modifier.height(AppSpacing.lg))
            TextButton(
                onClick = { erasePreview = viewModel.eraseSitePreview(app.id) },
                enabled = !state.clearingAll && !state.erasingSite,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(
                        if (state.erasingSite) R.string.me_storage_erasing else R.string.me_storage_erase_site,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    erasePreview?.let { preview ->
        val others = preview.affectedTitles.filter { it != app.title }
        val text = buildString {
            append(stringResource(R.string.me_storage_erase_site_text, preview.siteDomain))
            if (others.isNotEmpty()) {
                append('\n')
                append(stringResource(R.string.me_storage_erase_site_others, others.joinToString("、")))
            }
            if (preview.willSignOut) {
                append('\n')
                append(stringResource(R.string.me_storage_erase_site_sign_out))
            }
            append('\n')
            append(stringResource(R.string.me_storage_erase_scope_warning))
        }
        AppConfirmDialog(
            title = stringResource(R.string.me_storage_erase_site_title),
            text = text,
            confirmText = stringResource(R.string.me_storage_confirm_erase_site),
            dismissText = stringResource(R.string.me_cancel),
            destructive = true,
            onConfirm = {
                erasePreview = null
                viewModel.eraseSite(app.id)
            },
            onDismiss = { erasePreview = null },
        )
    }
}

@Composable
private fun SiteStorageRow(
    app: WebAppEntity,
    stats: SiteStorageStats?,
    scanning: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.lg, vertical = 8.dp),
    ) {
        SiteIcon(
            title = app.title,
            iconUrl = app.iconUrl,
            size = 40.dp,
            glyph = siteIconGlyph(app.isLocal, app.url),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                app.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                siteSubtitle(app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(AppSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 132.dp)) {
            when {
                stats == null -> Text(
                    stringResource(if (scanning) R.string.me_storage_measuring else R.string.me_storage_unmeasurable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                stats.allMetricsUnavailable() -> Text(
                    stringResource(R.string.me_storage_metric_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                else -> Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatStorageBytes(stats.attributableBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        if (stats.signedIn) {
                            stringResource(R.string.me_storage_signed_in)
                        } else {
                            cookieMetricLabel(stats.cookie)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            SettingsChevron()
        }
    }
}

@Composable
private fun siteSubtitle(app: WebAppEntity): String {
    val host = hostOf(app.url)
    val folder = app.folderName
    return if (folder.isNullOrBlank()) host else host + stringResource(R.string.me_storage_folder_suffix, folder)
}

@Composable
private fun DetailStatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun longMetricLabel(metric: Metric<Long>): String = when (metric) {
    is Metric.Measured -> formatStorageBytes(metric.value)
    Metric.Absent -> formatStorageBytes(0)
    Metric.Unavailable -> stringResource(R.string.me_storage_metric_unavailable)
}

@Composable
private fun cookieMetricLabel(metric: Metric<CookieFacts>): String = when (metric) {
    is Metric.Measured -> stringResource(
        R.string.me_storage_cookie_value,
        metric.value.count,
        formatStorageBytes(metric.value.serializedBytes),
    )
    Metric.Absent -> stringResource(R.string.me_storage_cookie_absent)
    Metric.Unavailable -> stringResource(R.string.me_storage_metric_unavailable)
}

private val SiteDataColor = Color(0xFFFF9500)
private val AppDataColor = Color(0xFF8E8E93)

/** 固定 8dp 高的圆角分段条 + 逐类图例（色点 + 名称 + 精确大小）。segments 为 0 的部分不画。 */
@Composable
private fun StorageUsageBar(
    segments: List<Pair<Color, Long>>,
    legend: List<Pair<Color, Pair<String, Long>>>,
) {
    Row(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        segments.forEach { (color, bytes) ->
            if (bytes > 0) {
                Box(Modifier.weight(bytes.toFloat()).fillMaxHeight().background(color))
            }
        }
    }
    Spacer(Modifier.height(AppSpacing.md))
    legend.forEach { (color, labelBytes) ->
        val (label, bytes) = labelBytes
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(AppSpacing.sm))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                formatStorageBytes(bytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 域名展示：URI 解析失败或本地页面回退原始 url。 */
private fun hostOf(url: String): String = hostOfUrl(url) ?: url
