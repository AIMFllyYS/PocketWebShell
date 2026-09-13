package com.webshell.feature.add

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webshell.core.designsystem.components.AppCard
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.designsystem.components.AppPrimaryButton
import com.webshell.core.designsystem.components.AppSearchField
import com.webshell.core.designsystem.components.AppSectionHeader
import com.webshell.core.designsystem.components.staticGlassSurface
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.designsystem.theme.LocalOverlayClearance
import java.util.Date

@Composable
internal fun HtmlFilePickerScreen(
    state: AddUiState.PickingLocal,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onGrantStorage: () -> Unit,
    onOpenSystemPicker: () -> Unit,
    onOpenRoot: (HtmlPickerRootUi) -> Unit,
    onOpenDir: (HtmlPickerDirUi) -> Unit,
    onImportFile: (HtmlPickerFileUi) -> Unit,
    onOpenCrumb: (Int) -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val onLanding = state.crumbs.isEmpty()
    val query = state.query
    val searching = query.isNotBlank()
    val crumbLocation = state.crumbs.drop(1).joinToString("/") { it.title }
    val folders = when {
        onLanding -> emptyList()
        searching -> state.folders.filter { HtmlDiscovery.matchesQuery(query, it.name, crumbLocation) }
        else -> state.folders
    }
    val localFiles = when {
        onLanding -> emptyList()
        searching -> state.files.filter {
            HtmlDiscovery.matchesListedFile(query, it.name, it.location, crumbLocation)
        }
        else -> state.files
    }
    val discoveredMatches = state.discovered.filter {
        HtmlDiscovery.matchesQuery(query, it.name, it.location)
    }
    val discovered = when {
        searching && !onLanding -> (discoveredMatches + localFiles).distinctBy { it.path }
        searching -> discoveredMatches
        else -> discoveredMatches.take(HtmlDiscovery.MAX_DISPLAY_RECENTS)
    }
    val showFoundMore = !searching && discoveredMatches.size > HtmlDiscovery.MAX_DISPLAY_RECENTS
    val searchCd = stringResource(R.string.add_html_picker_cd_search)
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AppNavigationBar(
            title = stringResource(R.string.add_html_picker_title),
            onBack = onBack,
            actions = {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.add_html_picker_cancel))
                }
            },
        )
        AppSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.add_html_picker_search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = AppSpacing.sm)
                .semantics { contentDescription = searchCd },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = AppSpacing.sm,
                bottom = LocalOverlayClearance.current + AppSpacing.lg,
            ),
        ) {
            if (!onLanding) {
                item {
                    PathBar(crumbs = state.crumbs, onOpenCrumb = onOpenCrumb)
                    Spacer(Modifier.height(AppSpacing.lg))
                }
            }
            state.permissionBanner?.let { banner ->
                item {
                    PermissionCard(
                        banner = banner,
                        onGrantStorage = onGrantStorage,
                        onOpenSystemPicker = onOpenSystemPicker,
                    )
                    Spacer(Modifier.height(AppSpacing.xl))
                }
            }
            if (state.isLoading) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (onLanding) {
                item {
                    AppSectionHeader(stringResource(R.string.add_html_picker_roots))
                    AppCard(contentPadding = PaddingValues(0.dp)) {
                        state.roots.forEachIndexed { index, root ->
                            if (index > 0) AppListDivider()
                            RootRow(
                                root = root,
                                onOpenRoot = onOpenRoot,
                                onLocked = onGrantStorage,
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(AppSpacing.xl))
                    FoundHtmlSection(
                        discovered = discovered,
                        isDiscovering = state.isDiscovering,
                        query = query,
                        showFoundMore = showFoundMore,
                        needsPermission = state.permissionBanner != null,
                        onImportFile = onImportFile,
                    )
                }
            } else if (searching) {
                if (folders.isNotEmpty()) {
                    item {
                        AppSectionHeader(stringResource(R.string.add_html_picker_folders))
                        AppCard(contentPadding = PaddingValues(0.dp)) {
                            folders.forEachIndexed { index, folder ->
                                if (index > 0) AppListDivider()
                                FolderRow(folder = folder, onOpenDir = onOpenDir)
                            }
                        }
                        Spacer(Modifier.height(AppSpacing.xl))
                    }
                }
                item {
                    FoundHtmlSection(
                        discovered = discovered,
                        isDiscovering = state.isDiscovering,
                        query = query,
                        showFoundMore = false,
                        needsPermission = state.permissionBanner != null,
                        onImportFile = onImportFile,
                    )
                }
            } else {
                when (state.listingStatus) {
                    HtmlPickerListingStatus.AccessDenied -> item {
                        DeniedState(onOpenSystemPicker = onOpenSystemPicker)
                    }
                    HtmlPickerListingStatus.Empty -> item { EmptyState() }
                    HtmlPickerListingStatus.Ready -> {
                        if (folders.isNotEmpty()) {
                            item {
                                AppSectionHeader(stringResource(R.string.add_html_picker_folders))
                                AppCard(contentPadding = PaddingValues(0.dp)) {
                                    folders.forEachIndexed { index, folder ->
                                        if (index > 0) AppListDivider()
                                        FolderRow(folder = folder, onOpenDir = onOpenDir)
                                    }
                                }
                            }
                        }
                        if (localFiles.isNotEmpty()) {
                            item {
                                if (folders.isNotEmpty()) Spacer(Modifier.height(AppSpacing.xl))
                                AppSectionHeader(stringResource(R.string.add_html_picker_files))
                                AppCard(contentPadding = PaddingValues(0.dp)) {
                                    localFiles.forEachIndexed { index, file ->
                                        if (index > 0) AppListDivider()
                                        FileRow(file = file, onImportFile = onImportFile)
                                    }
                                }
                            }
                        }
                        if (folders.isEmpty() && localFiles.isEmpty() && !state.isLoading) {
                            item { EmptyState() }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(AppSpacing.xl)) }
        }
    }
}

@Composable
private fun FoundHtmlSection(
    discovered: List<HtmlPickerFileUi>,
    isDiscovering: Boolean,
    query: String,
    showFoundMore: Boolean,
    needsPermission: Boolean,
    onImportFile: (HtmlPickerFileUi) -> Unit,
) {
    AppSectionHeader(stringResource(R.string.add_html_picker_found))
    when {
        isDiscovering && discovered.isEmpty() -> ScanningState()
        discovered.isNotEmpty() -> {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                discovered.forEachIndexed { index, file ->
                    if (index > 0) AppListDivider()
                    FileRow(file = file, onImportFile = onImportFile)
                }
            }
            if (showFoundMore) {
                Text(
                    stringResource(R.string.add_html_picker_found_more, HtmlDiscovery.MAX_DISPLAY_RECENTS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.sm),
                )
            }
            if (isDiscovering) {
                Spacer(Modifier.height(AppSpacing.md))
                ScanningState()
            }
        }
        query.isNotBlank() -> EmptySearchState()
        needsPermission -> EmptyFoundNeedsPermissionState()
        else -> EmptyFoundState()
    }
}

@Composable
private fun PathBar(crumbs: List<HtmlPickerCrumb>, onOpenCrumb: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .staticGlassSurface(shape = RoundedCornerShape(20.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) {
                Text(
                    " › ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                crumb.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onOpenCrumb(index) },
            )
        }
    }
}

@Composable
private fun PermissionCard(
    banner: HtmlPickerPermissionBanner,
    onGrantStorage: () -> Unit,
    onOpenSystemPicker: () -> Unit,
) {
    AppCard {
        Text(
            stringResource(R.string.add_html_storage_rationale_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(
                if (banner.showManageRationale) {
                    R.string.add_html_storage_rationale_manage
                } else {
                    R.string.add_html_storage_rationale_read
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AppSpacing.sm),
        )
        if (banner.showXiaomiHint) {
            Text(
                stringResource(R.string.add_html_storage_xiaomi_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.sm),
            )
        }
        AppPrimaryButton(
            text = stringResource(
                if (banner.permanentlyDenied && !banner.showManageRationale) {
                    R.string.add_html_storage_open_settings
                } else {
                    R.string.add_html_storage_grant
                },
            ),
            onClick = onGrantStorage,
            modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.lg),
        )
        TextButton(
            onClick = onOpenSystemPicker,
            modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.sm),
        ) {
            Text(stringResource(R.string.add_html_picker_system))
        }
    }
}

@Composable
private fun RootRow(
    root: HtmlPickerRootUi,
    onOpenRoot: (HtmlPickerRootUi) -> Unit,
    onLocked: () -> Unit,
) {
    val title = stringResource(
        when (root.kind) {
            HtmlImportRootKind.Download, HtmlImportRootKind.MiuiDownloads ->
                R.string.add_html_picker_root_download
            HtmlImportRootKind.Documents -> R.string.add_html_picker_root_documents
            HtmlImportRootKind.Storage -> R.string.add_html_picker_root_storage
        },
    )
    val subtitle = if (root.locked) stringResource(R.string.add_html_storage_locked_root) else null
    AppListRow(
        title = title,
        subtitle = subtitle,
        leadingIcon = rootIcon(root.kind),
        onClick = { if (root.locked) onLocked() else onOpenRoot(root) },
        trailing = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun FolderRow(folder: HtmlPickerDirUi, onOpenDir: (HtmlPickerDirUi) -> Unit) {
    val description = stringResource(R.string.add_html_picker_cd_folder, folder.name)
    AppListRow(
        title = folder.name,
        leadingIcon = Icons.Rounded.Folder,
        onClick = { onOpenDir(folder) },
        modifier = Modifier.semantics { contentDescription = description },
        trailing = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun FileRow(file: HtmlPickerFileUi, onImportFile: (HtmlPickerFileUi) -> Unit) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, file.sizeBytes)
    val date = DateFormat.getDateFormat(context).format(Date(file.lastModifiedMillis))
    val description = stringResource(R.string.add_html_picker_cd_file, file.name)
    val subtitle = if (file.location.isBlank()) {
        stringResource(R.string.add_html_picker_file_meta, size, date)
    } else {
        stringResource(R.string.add_html_picker_file_location, file.location, size, date)
    }
    AppListRow(
        title = file.name,
        subtitle = subtitle,
        leadingIcon = Icons.Rounded.Description,
        onClick = { onImportFile(file) },
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxl, horizontal = AppSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Text(
            stringResource(R.string.add_html_picker_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.add_html_picker_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScanningState() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.add_html_picker_found_scanning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyFoundNeedsPermissionState() {
    Text(
        stringResource(R.string.add_html_picker_found_need_permission),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.md),
    )
}

@Composable
private fun EmptyFoundState() {
    Text(
        stringResource(R.string.add_html_picker_found_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.md),
    )
}

@Composable
private fun EmptySearchState() {
    Text(
        stringResource(R.string.add_html_picker_search_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.md),
    )
}

@Composable
private fun DeniedState(onOpenSystemPicker: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxl, horizontal = AppSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Text(
            stringResource(R.string.add_html_picker_denied),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onOpenSystemPicker) {
            Text(stringResource(R.string.add_html_picker_system))
        }
    }
}

private fun rootIcon(kind: HtmlImportRootKind): ImageVector = when (kind) {
    HtmlImportRootKind.Download, HtmlImportRootKind.MiuiDownloads -> Icons.Rounded.Download
    HtmlImportRootKind.Documents -> Icons.Rounded.Description
    HtmlImportRootKind.Storage -> Icons.Rounded.Folder
}
