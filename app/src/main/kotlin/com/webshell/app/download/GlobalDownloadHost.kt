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

@Composable
private fun DownloadCapsule(
    item: DownloadItem,
    parked: Boolean,
    anchorY: Float,
    onPlacement: (parked: Boolean, y: Float) -> Unit,
    onTap: () -> Unit,
    onAnchorChanged: (IntOffset) -> Unit,
) {
    val density = LocalDensity.current
    var liveY by remember { mutableStateOf<Float?>(null) }
    val latestTap = rememberUpdatedState(onTap)
    val latestPlacement = rememberUpdatedState(onPlacement)
    val latestParked = rememberUpdatedState(parked)
    val latestY = rememberUpdatedState(anchorY)
    val complete = item.status == DownloadStatus.Success || item.status == DownloadStatus.Failed
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val bounds = remember(maxWidth, maxHeight) {
            DownloadCapsuleBounds(maxWidth.value, maxHeight.value)
        }
        val centerY = liveY ?: bounds.centerY(anchorY)
        val reportAnchor = Modifier.onGloballyPositioned { coords ->
            val pos = coords.positionInWindow()
            onAnchorChanged(
                IntOffset(
                    (pos.x + coords.size.width / 2f).roundToInt(),
                    (pos.y + coords.size.height / 2f).roundToInt(),
                ),
            )
        }
        if (parked) {
            val restore = stringResource(R.string.download_capsule_show)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            0,
                            with(density) { (centerY - DownloadCapsuleGeometry.PARKED_HEIGHT / 2f).dp.roundToPx() },
                        )
                    }
                    .size(
                        DownloadCapsuleGeometry.PARKED_WIDTH.dp,
                        DownloadCapsuleGeometry.PARKED_HEIGHT.dp,
                    )
                    .then(reportAnchor)
                    .shadow(2.dp, parkedShape, clip = false)
                    .staticGlassSurface(shape = parkedShape, opacity = 0.92f)
                    .clickable(onClick = { latestPlacement.value(false, latestY.value) })
                    .semantics {
                        role = Role.Button
                        contentDescription = restore
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (complete) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(12.dp),
                )
            }
        } else {
            val hideLabel = stringResource(R.string.download_capsule)
            val width = if (item.inProgress) {
                DownloadCapsuleGeometry.EXPANDED_WIDTH
            } else {
                DownloadCapsuleGeometry.COMPLETE_SIZE
            }
            val height = if (item.inProgress) {
                DownloadCapsuleGeometry.EXPANDED_HEIGHT
            } else {
                DownloadCapsuleGeometry.COMPLETE_SIZE
            }
            val shape = if (item.inProgress) {
                RoundedCornerShape(28.dp)
            } else {
                CircleShape
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            with(density) { (-AppSpacing.sm.toPx()).roundToInt() },
                            with(density) { (centerY - height / 2f).dp.roundToPx() },
                        )
                    }
                    .then(
                        if (item.inProgress) Modifier.width(width.dp) else Modifier.size(height.dp),
                    )
                    .then(reportAnchor)
                    .shadow(4.dp, shape, clip = false)
                    .staticGlassSurface(shape = shape, opacity = 0.94f)
                    .semantics {
                        role = Role.Button
                        contentDescription = hideLabel
                    }
                    .pointerInput(item.id, bounds) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var dxDp = 0f
                            var dyDp = 0f
                            val startY = bounds.centerY(latestY.value)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val step = change.positionChange()
                                dxDp += step.x / density.density
                                dyDp += step.y / density.density
                                if (hypotTravel(dxDp, dyDp) >=
                                    DownloadCapsuleGeometry.DRAG_START_THRESHOLD_DP
                                ) {
                                    change.consume()
                                    liveY = bounds.clampCenterY(startY + dyDp)
                                }
                                if (change.changedToUpIgnoreConsumed()) {
                                    val action = DownloadCapsuleGeometry.classifyRelease(
                                        dxDp = dxDp,
                                        dyDp = dyDp,
                                    )
                                    val nextY = bounds.normalizeFromCenter(liveY ?: startY)
                                    when (action) {
                                        DownloadCapsuleRelease.TAP -> {
                                            if (latestParked.value) {
                                                latestPlacement.value(false, nextY)
                                            } else {
                                                latestTap.value()
                                            }
                                        }
                                        DownloadCapsuleRelease.PARK -> {
                                            latestPlacement.value(true, nextY)
                                        }
                                        DownloadCapsuleRelease.MOVE -> {
                                            latestPlacement.value(false, nextY)
                                        }
                                    }
                                    liveY = null
                                    break
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (item.inProgress) {
                    DownloadProgressContent(item)
                } else {
                    Icon(
                        imageVector = if (item.status == DownloadStatus.Failed) {
                            Icons.Outlined.Download
                        } else {
                            Icons.Outlined.DownloadDone
                        },
                        contentDescription = null,
                        tint = if (item.status == DownloadStatus.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCapsuleMenu(
    item: DownloadItem,
    openFailed: Boolean,
    anchorPoint: IntOffset,
    onOpenFolder: () -> Unit,
    onOpenSystemDownloads: () -> Unit,
    onOpenFile: () -> Unit,
    onShare: () -> Unit,
    onHideCapsule: () -> Unit,
    onHistory: () -> Unit,
    onHideOrb: () -> Unit,
    onDismiss: () -> Unit,
) {
    val finished = !item.inProgress
    val items = buildList {
        add(
            AppContextMenuItem(
                stringResource(R.string.download_open_folder),
                Icons.Filled.FolderOpen,
                onClick = onOpenFolder,
            ),
        )
        add(
            AppContextMenuItem(
                stringResource(R.string.download_open_system),
                Icons.Filled.Download,
                onClick = onOpenSystemDownloads,
            ),
        )
        if (finished) {
            add(
                AppContextMenuItem(
                    stringResource(R.string.download_open_file),
                    Icons.AutoMirrored.Filled.InsertDriveFile,
                    onClick = onOpenFile,
                ),
            )
            add(
                AppContextMenuItem(
                    stringResource(R.string.download_share),
                    Icons.Filled.Share,
                    onClick = onShare,
                ),
            )
        }
        add(
            AppContextMenuItem(
                stringResource(R.string.download_capsule_hide),
                Icons.Filled.Close,
                onClick = onHideCapsule,
            ),
        )
        add(
            AppContextMenuItem(
                stringResource(R.string.download_history_title),
                Icons.Filled.History,
                onClick = onHistory,
            ),
        )
        add(
            AppContextMenuItem(
                stringResource(R.string.download_hide_capsule),
                Icons.Filled.VisibilityOff,
                destructive = true,
                onClick = onHideOrb,
            ),
        )
    }
    Box(Modifier.fillMaxSize()) {
        AppContextMenu(items = items, onDismiss = onDismiss, anchorPoint = anchorPoint)
        if (openFailed) {
            Text(
                text = stringResource(R.string.download_open_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(AppSpacing.lg),
            )
        }
    }
}

@Composable
private fun DownloadProgressContent(item: DownloadItem) {
    val fraction = item.progress
    val statusText = if (fraction != null) {
        stringResource(R.string.download_progress, (fraction * 100f).toInt())
    } else {
        stringResource(R.string.download_progress_indeterminate)
    }
    val path = stringResource(R.string.download_path_short, item.displayName)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = path,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, bottom = AppSpacing.xs),
        )
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun hypotTravel(dx: Float, dy: Float): Float =
    kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

private val parkedShape = RoundedCornerShape(
    topStart = 24.dp,
    bottomStart = 24.dp,
    topEnd = 0.dp,
    bottomEnd = 0.dp,
)
