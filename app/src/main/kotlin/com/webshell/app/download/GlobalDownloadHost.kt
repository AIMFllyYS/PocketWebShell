package com.webshell.app.download

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.app.R
import com.webshell.core.designsystem.components.AppContextMenu
import com.webshell.core.designsystem.components.AppContextMenuItem
import com.webshell.core.designsystem.components.staticGlassSurface
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.model.DownloadItem
import com.webshell.core.model.DownloadStatus
import kotlin.math.roundToInt


@Composable
fun GlobalDownloadHost(
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val item by viewModel.capsuleItem.collectAsStateWithLifecycle()
    val menuOpen by viewModel.menuOpen.collectAsStateWithLifecycle()
    val historyOpen by viewModel.historyOpen.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val capsuleEnabled by viewModel.capsuleEnabled.collectAsStateWithLifecycle()
    val parked by viewModel.capsuleParked.collectAsStateWithLifecycle()
    val anchorY by viewModel.capsuleY.collectAsStateWithLifecycle()
    val openFailed by viewModel.openFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.download_share_title)
    var menuAnchor by remember { mutableStateOf(IntOffset.Zero) }
    val readPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.lastOpenCommand()?.let { finishOpen(context, viewModel, it, shareTitle) }
    }
    LaunchedEffect(shareTitle) {
        viewModel.openCommands.collect { command ->
            val needed = DownloadStorageAccess.runtimeReadPermission()
            if (needed != null &&
                DownloadStorageAccess.needsLegacyRead(command.target) &&
                ContextCompat.checkSelfPermission(context, needed) != PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { readPermissionLauncher.launch(needed) }.onFailure {
                    finishOpen(context, viewModel, command, shareTitle)
                }
            } else {
                finishOpen(context, viewModel, command, shareTitle)
            }
        }
    }
    if (historyOpen) {
        DownloadHistorySheet(
            items = records,
            onOpen = viewModel::openRecord,
            onRemove = viewModel::removeRecord,
            onDismiss = viewModel::dismissHistory,
        )
    }
    if (!capsuleEnabled) return
    val current = item ?: return
    Box(Modifier.fillMaxSize().zIndex(8f)) {
        DownloadCapsule(
            item = current,
            parked = parked,
            anchorY = anchorY,
            onPlacement = viewModel::setCapsulePlacement,
            onTap = viewModel::onCapsuleTap,
            onAnchorChanged = { menuAnchor = it },
        )
        if (menuOpen) {
            DownloadCapsuleMenu(
                item = current,
                openFailed = openFailed,
                anchorPoint = menuAnchor,
                onOpenFolder = viewModel::openFolder,
                onOpenSystemDownloads = viewModel::openSystemDownloads,
                onOpenFile = viewModel::openFile,
                onShare = viewModel::shareFile,
                onHideCapsule = viewModel::dismissCapsule,
                onHistory = viewModel::showHistory,
                onHideOrb = viewModel::hideOrb,
                onDismiss = viewModel::dismissMenu,
            )
        }
    }
}

private fun finishOpen(
    context: Context,
    viewModel: DownloadViewModel,
    command: DownloadOpenCommand,
    shareTitle: String,
) {
    val ok = launchDownloadCommand(context, command, shareTitle)
    if (ok) viewModel.dismissCapsule() else viewModel.markOpenFailed()
}

private fun launchDownloadCommand(
    context: Context,
    command: DownloadOpenCommand,
    shareTitle: String,
): Boolean = when (command.target) {
    DownloadOpenTarget.Folder -> DownloadIntents.launchAll(context, DownloadIntents.folderOpenIntents())
    DownloadOpenTarget.SystemDownloads ->
        DownloadIntents.launch(context, DownloadIntents.systemDownloadsIntent())
    DownloadOpenTarget.File -> {
        val uri = (command.fileUri ?: command.documentUri)?.let { DownloadIntents.shareableUri(context, it) }
        if (uri == null) false else DownloadIntents.launch(context, DownloadIntents.fileViewIntent(uri))
    }
    DownloadOpenTarget.Share -> {
        val uri = (command.fileUri ?: command.documentUri)?.let { DownloadIntents.shareableUri(context, it) }
        if (uri == null) false else DownloadIntents.launch(context, DownloadIntents.shareIntent(uri, shareTitle))
    }
    null -> DownloadIntents.launch(context, command.documentUri, command.fileUri, shareTitle)
}
