package com.webshell.feature.home

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.data.HomeSettings
import com.webshell.core.data.WebAppEntity
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 主屏菜单、对话框、抽屉和文件夹展开层。
 * 拖拽测量与网格留在 [HomeScreen]；这里只消费已经确定的会话状态。
 */
@Composable
internal fun HomeScreenOverlays(
    ui: HomeInteractionState,
    viewModel: HomeViewModel,
    apps: List<WebAppEntity>,
    settings: HomeSettings,
    onLaunch: (String, String) -> Unit,
    cellsByKey: Map<String, HomeCell>,
    allAppsOpenState: MutableState<Boolean>,
    folderOpenForState: MutableState<String?>,
    toastState: MutableState<String?>,
    confirmDeleteSelectedState: MutableState<Boolean>,
    folderPickerOpenState: MutableState<Boolean>,
) {
    var allAppsOpen by allAppsOpenState
    var folderOpenFor by folderOpenForState
    var toast by toastState
    var confirmDeleteSelected by confirmDeleteSelectedState
    var folderPickerOpen by folderPickerOpenState
    var menuFor by ui::menuFor
    var menuPressPoint by ui::menuPressPoint
    var blankMenuPoint by ui::blankMenuPoint
    var editMode by ui::editMode
    val editSelection = ui.editSelection
    var allAppsView by rememberSaveable { mutableStateOf(AllAppsView.GRID) }
    var confirmDeleteFor by remember { mutableStateOf<HomeCell?>(null) }
    var confirmDissolveFor by remember { mutableStateOf<HomeCell?>(null) }
    var renameFor by remember { mutableStateOf<HomeCell?>(null) }
    var renameFolderFor by remember { mutableStateOf<HomeCell?>(null) }
    var iconEditFor by remember { mutableStateOf<HomeCell?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val linkCopiedMessage = stringResource(R.string.home_link_copied)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareInProgress by remember { mutableStateOf(false) }
    val shareFolderTitle = stringResource(R.string.home_share_folder)
    val shareGeneratingMessage = stringResource(R.string.home_share_folder_generating)
    val shareFailedMessage = stringResource(R.string.home_share_folder_failed)

    blankMenuPoint?.let { point ->
        HomeBlankMenu(
            entryVisible = settings.allAppsEntryVisible,
            anchorPoint = IntOffset(point.x.roundToInt(), point.y.roundToInt()),
            onEdit = { editMode = true },
            onToggleEntry = { viewModel.setAllAppsEntryVisible(!settings.allAppsEntryVisible) },
            onDismiss = { blankMenuPoint = null },
        )
    }

    // 全部应用抽屉：首字母分区网格 + 右侧字母索引条；可切换列表视图（按首字母/时间）。
    if (allAppsOpen) {
        val sections by viewModel.allAppsSections.collectAsStateWithLifecycle()
        AllAppsDrawer(
            sections = sections,
            columns = settings.gridColumns,
            iconSize = settings.iconSizeDp.dp,
            cornerRadiusPercent = settings.iconCornerRadiusPercent,
            view = allAppsView,
            onViewChange = { allAppsView = it },
            onLaunch = { id, url ->
                allAppsOpen = false
                onLaunch(id, url)
            },
            onDismiss = { allAppsOpen = false },
        )
    }

    menuFor?.let { cell ->
        HomeCellMenu(
            cell = cell,
            anchorPoint = menuPressPoint?.let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) },
            onOpen = {
                if (cell.isFolder) folderOpenFor = cell.app.folderId else onLaunch(cell.app.id, cell.app.url)
            },
            onDissolve = { confirmDissolveFor = cell },
            onCopyLink = {
                clipboardManager.setText(AnnotatedString(cell.app.url))
                toast = linkCopiedMessage
            },
            onRename = { if (cell.isFolder) renameFolderFor = cell else renameFor = cell },
            onShare = {
                val folderId = cell.app.folderId
                if (folderId != null && !shareInProgress) {
                    shareInProgress = true
                    toast = shareGeneratingMessage
                    scope.launch {
                        val file = viewModel.exportFolderShare(folderId)
                        shareInProgress = false
                        if (file == null) {
                            toast = shareFailedMessage
                        } else {
                            toast = null
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, shareFolderTitle))
                        }
                    }
                }
            },
            onChangeIcon = { iconEditFor = cell },
            onRefresh = { viewModel.refreshMetadata(cell.app.id) },
            onToggleDesktop = { viewModel.toggleDesktopMode(cell.app.id) },
            onToggleKeepAlive = { viewModel.toggleKeepAlive(cell.app.id) },
            onRemoveFromFolder = { viewModel.removeFromFolder(cell.app.id) },
            onDelete = { confirmDeleteFor = cell },
            onDismiss = { menuFor = null; menuPressPoint = null },
        )
    }

    renameFor?.let { cell ->
        HomeRenameDialog(
            app = cell.app,
            onConfirm = { title -> viewModel.rename(cell.app.id, title); renameFor = null },
            onDismiss = { renameFor = null },
        )
    }

    renameFolderFor?.let { cell ->
        HomeFolderRenameDialog(
            initialName = cell.folderName.orEmpty(),
            onConfirm = { name ->
                viewModel.renameFolder(cell.app.folderId.orEmpty(), name)
                renameFolderFor = null
            },
            onDismiss = { renameFolderFor = null },
        )
    }

    iconEditFor?.let { cell ->
        HomeIconEditRoute(
            app = cell.app,
            cornerRadiusPercent = settings.iconCornerRadiusPercent,
            onImportIcon = viewModel::importIcon,
            onConfirm = { url -> viewModel.updateIcon(cell.app.id, url.ifBlank { null }); iconEditFor = null },
            onDismiss = { iconEditFor = null },
        )
    }

    if (confirmDeleteSelected) {
        val count = editSelection.values.count { it }
        HomeDeleteManyDialog(
            count = count,
            onConfirm = {
                viewModel.deleteSelected(editSelection.filterValues { it }.keys)
                editSelection.clear()
                confirmDeleteSelected = false
            },
            onDismiss = { confirmDeleteSelected = false },
        )
    }

    if (folderPickerOpen) {
        val folders = remember(apps) {
            apps.filter { it.folderId != null }
                .groupBy { it.folderId!! }
                .map { (id, members) ->
                    id to (members.firstNotNullOfOrNull { it.folderName }.orEmpty())
                }
                .sortedBy { it.second }
        }
        HomeFolderPickerDialog(
            folders = folders,
            onSelect = { folderId ->
                viewModel.moveSelectedToFolder(editSelection.filterValues { it }.keys, folderId)
                folderPickerOpen = false
            },
            onDismiss = { folderPickerOpen = false },
        )
    }

    confirmDeleteFor?.let { cell ->
        HomeDeleteDialog(
            cell = cell,
            onConfirm = {
                if (cell.isFolder) {
                    viewModel.deleteFolder(cell.app.folderId.orEmpty())
                } else {
                    val folderId = cell.app.folderId
                    if (folderId != null && apps.count { it.folderId == folderId } <= 2) {
                        viewModel.dissolveFolder(folderId)
                    }
                    viewModel.delete(cell.app.id)
                }
                confirmDeleteFor = null
            },
            onDismiss = { confirmDeleteFor = null },
        )
    }

    // 解散二次确认：取消时保留展开的文件夹，确认后一并关闭。
    confirmDissolveFor?.let { cell ->
        HomeDissolveDialog(
            cell = cell,
            onConfirm = {
                viewModel.dissolveFolder(cell.app.folderId.orEmpty())
                confirmDissolveFor = null
                folderOpenFor = null
            },
            onDismiss = { confirmDissolveFor = null },
        )
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
                cellsByKey.values.firstOrNull { it.isFolder && it.app.folderId == folderId }
                    ?.let { confirmDissolveFor = it }
            },
            onDismiss = { folderOpenFor = null },
            onRenameFolder = {
                cellsByKey.values.firstOrNull { it.isFolder && it.app.folderId == folderId }
                    ?.let { renameFolderFor = it }
            },
            onMoveMember = { from, to -> viewModel.moveFolderMember(folderId, from, to) },
        )
    }
}
