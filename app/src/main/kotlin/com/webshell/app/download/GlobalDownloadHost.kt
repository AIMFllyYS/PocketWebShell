package com.webshell.app.download

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.app.R
import com.webshell.core.designsystem.components.staticGlassSurface
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.model.DownloadItem
import com.webshell.core.model.DownloadStatus
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun GlobalDownloadHost(
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val item by viewModel.capsuleItem.collectAsStateWithLifecycle()
    val cardOpen by viewModel.cardOpen.collectAsStateWithLifecycle()
    val openFailed by viewModel.openFailed.collectAsStateWithLifecycle()
    val historyOpen by viewModel.historyOpen.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.download_share_title)
    LaunchedEffect(shareTitle) {
        viewModel.openCommands.collect { command ->
            val ok = DownloadIntents.launch(context, command.documentUri, command.fileUri, shareTitle)
            if (ok) viewModel.dismissCapsule() else viewModel.markOpenFailed()
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
    val current = item ?: return
    Box(Modifier.fillMaxSize().zIndex(8f)) {
        DownloadCapsule(
            item = current,
            onTap = viewModel::onCapsuleTap,
        )
        if (cardOpen) {
            DownloadCompleteCard(
                item = current,
                openFailed = openFailed,
                onOpen = viewModel::openDestination,
                onDismiss = viewModel::dismissCard,
            )
        }
    }
}

@Composable
private fun DownloadCapsule(
    item: DownloadItem,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    var parked by remember(item.id) { mutableStateOf(false) }
    var anchorY by remember(item.id) { mutableStateOf(DownloadCapsuleGeometry.DEFAULT_Y) }
    var liveY by remember { mutableStateOf<Float?>(null) }
    val latestTap = rememberUpdatedState(onTap)
    val latestParked = rememberUpdatedState(parked)
    val latestY = rememberUpdatedState(anchorY)
    val complete = item.status == DownloadStatus.Success || item.status == DownloadStatus.Failed
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val bounds = remember(maxWidth, maxHeight) {
            DownloadCapsuleBounds(maxWidth.value, maxHeight.value)
        }
        val centerY = liveY ?: bounds.centerY(anchorY)
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
                    .shadow(2.dp, parkedShape, clip = false)
                    .staticGlassSurface(shape = parkedShape, opacity = 0.92f)
                    .clickable(onClick = { parked = false })
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
                    .shadow(4.dp, shape, clip = false)
                    .staticGlassSurface(shape = shape, opacity = 0.94f)
                    .semantics {
                        role = Role.Button
                        contentDescription = hideLabel
                    }
                    .pointerInput(item.id, bounds, item.inProgress, complete) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var dxDp = 0f
                            var dyDp = 0f
                            var dragging = false
                            val startY = bounds.centerY(latestY.value)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val step = change.positionChange()
                                dxDp += step.x / density.density
                                dyDp += step.y / density.density
                                if (hypot(dxDp.toDouble(), dyDp.toDouble()) >=
                                    DownloadCapsuleGeometry.DRAG_START_THRESHOLD_DP
                                ) {
                                    dragging = true
                                    change.consume()
                                    liveY = bounds.clampCenterY(startY + dyDp)
                                }
                                if (change.changedToUpIgnoreConsumed()) {
                                    if (!dragging) {
                                        if (latestParked.value) {
                                            parked = false
                                        } else if (complete) {
                                            latestTap.value()
                                        }
                                    } else {
                                        val releaseX = bounds.width - width / 2f + dxDp
                                        if (DownloadCapsuleGeometry.parksToRight(dxDp, releaseX, bounds.width)) {
                                            parked = true
                                        }
                                        anchorY = bounds.normalizeFromCenter(liveY ?: startY)
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

private val parkedShape = RoundedCornerShape(
    topStart = 24.dp,
    bottomStart = 24.dp,
    topEnd = 0.dp,
    bottomEnd = 0.dp,
)
