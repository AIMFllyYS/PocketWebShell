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
