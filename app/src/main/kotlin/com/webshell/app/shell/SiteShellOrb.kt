package com.webshell.app.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.webshell.app.R
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.designsystem.components.AppSheet
import com.webshell.core.designsystem.components.staticGlassSurface
import kotlin.math.roundToInt
import com.webshell.feature.browser.R as BrowserR

/**
 * Site-shell-only assist orb. Static glass, no live blur, and the overlay never
 * measures or constrains the sibling WebView.
 */
@Composable
internal fun SiteShellOrb(
    posX: Float,
    posY: Float,
    parked: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    loading: Boolean,
    desktopMode: Boolean,
    onPositionChange: (Float, Float) -> Unit,
    onParkedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onDesktopMode: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    val density = LocalDensity.current
    var anchor by remember { mutableStateOf(SiteShellOrbAnchor.restored(posX, posY, parked)) }
    var liveCenter by remember { mutableStateOf<Offset?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val latestPosition = rememberUpdatedState(onPositionChange)
    val latestParked = rememberUpdatedState(onParkedChange)
    val latestRefresh = rememberUpdatedState(onRefresh)
    val latestAnchor = rememberUpdatedState(anchor)
    LaunchedEffect(posX, posY, parked) {
        if (liveCenter == null) {
            anchor = SiteShellOrbAnchor.restored(posX, posY, parked)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().zIndex(1f)) {
        val bounds = remember(maxWidth, maxHeight) {
            SiteShellOrbBounds(maxWidth.value, maxHeight.value)
        }
        if (anchor.parked) {
            val restoreLabel = stringResource(R.string.site_shell_orb_restore)
            val parkedLabel = stringResource(R.string.site_shell_orb_parked)
            val handleW = SiteShellOrbMetrics.PARKED_WIDTH
            val handleH = SiteShellOrbMetrics.PARKED_HEIGHT
            val edgeInset = SiteShellOrbMetrics.PARKED_EDGE_INSET
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            with(density) { (maxWidth - handleW.dp - edgeInset.dp).roundToPx() },
                            with(density) { (bounds.centerY(anchor) - handleH / 2).dp.roundToPx() },
                        )
                    }
                    .size(handleW.dp, handleH.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .zIndex(1f)
                    .clickable(role = Role.Button, onClickLabel = restoreLabel) {
                        latestParked.value(false)
                    }
                    .semantics {
                        contentDescription = restoreLabel
                        stateDescription = parkedLabel
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .size(SiteShellOrbMetrics.ORB_SIZE.dp)
                        .staticGlassSurface(
                            tint = MaterialTheme.colorScheme.surface,
                            opacity = 0.72f,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        } else {
            val menuLabel = stringResource(R.string.site_shell_orb)
            val settled = Offset(bounds.centerX(anchor), bounds.centerY(anchor))
            val center = liveCenter ?: settled
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            with(density) { (center.x - bounds.orbSize / 2).dp.roundToPx() },
                            with(density) { (center.y - bounds.orbSize / 2).dp.roundToPx() },
                        )
                    }
                    .size(bounds.orbSize.dp)
                    .zIndex(1f)
                    .staticGlassSurface(
                        tint = MaterialTheme.colorScheme.surface,
                        opacity = 0.72f,
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = menuLabel
                    }
                    .pointerInput(bounds) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = Offset(
                                bounds.centerX(latestAnchor.value),
                                bounds.centerY(latestAnchor.value),
                            )
                            var current = start
                            var dxDp = 0f
                            var dyDp = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val step = change.positionChange()
                                dxDp += step.x / density.density
                                dyDp += step.y / density.density
                                val dragging = hypotLength(dxDp, dyDp) >=
                                    SiteShellOrbGesture.DRAG_START_THRESHOLD_DP
                                if (dragging) {
                                    change.consume()
                                    val clamped = bounds.clampCenter(start.x + dxDp, start.y + dyDp)
                                    current = Offset(clamped.x, clamped.y)
                                    liveCenter = current
                                }
                                if (change.changedToUpIgnoreConsumed()) {
                                    val action = SiteShellOrbGesture.classifyRelease(
                                        dxDp = dxDp,
                                        dyDp = dyDp,
                                        durationMs = (change.uptimeMillis - down.uptimeMillis)
                                            .coerceAtLeast(0L),
                                        releaseCenterX = current.x,
                                        bounds = bounds,
                                    )
                                    liveCenter = null
                                    when (action) {
                                        SiteShellOrbRelease.TAP -> showMenu = true
                                        SiteShellOrbRelease.REPOSITION -> {
                                            val next = bounds.anchorAt(current.x, current.y)
                                            anchor = next
                                            latestPosition.value(next.x, next.y)
                                        }
                                        SiteShellOrbRelease.PARK_RIGHT -> {
                                            anchor = latestAnchor.value.copy(parked = true)
                                            latestParked.value(true)
                                        }
                                        SiteShellOrbRelease.REFRESH -> latestRefresh.value()
                                    }
                                    break
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MoreHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (showMenu) {
        SiteShellOrbMenu(
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            loading = loading,
            desktopMode = desktopMode,
            onDismiss = { showMenu = false },
            onBack = { showMenu = false; onBack() },
            onForward = { showMenu = false; onForward() },
            onRefreshOrStop = {
                showMenu = false
                if (loading) onStop() else onRefresh()
            },
            onDesktopMode = {
                showMenu = false
                onDesktopMode(!desktopMode)
            },
            onLeave = {
                showMenu = false
                onLeave()
            },
        )
    }
}

@Composable
private fun SiteShellOrbMenu(
    canGoBack: Boolean,
    canGoForward: Boolean,
    loading: Boolean,
    desktopMode: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onDesktopMode: () -> Unit,
    onLeave: () -> Unit,
) {
    AppSheet(onDismissRequest = onDismiss) {
        AppNavigationBar(title = stringResource(R.string.site_shell_menu))
        AppListRow(
            title = stringResource(BrowserR.string.browser_back),
            leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            onClick = if (canGoBack) onBack else null,
        )
        AppListDivider()
        AppListRow(
            title = stringResource(BrowserR.string.browser_forward),
            leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = if (canGoForward) onForward else null,
        )
        AppListDivider()
        AppListRow(
            title = stringResource(
                if (loading) BrowserR.string.browser_stop else BrowserR.string.browser_refresh,
            ),
            leadingIcon = if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
            onClick = onRefreshOrStop,
        )
        AppListDivider()
        AppListRow(
            title = stringResource(BrowserR.string.browser_desktop),
            leadingIcon = Icons.Filled.DesktopWindows,
            trailing = {
                if (desktopMode) {
                    Icon(Icons.Filled.Check, stringResource(BrowserR.string.browser_enabled))
                }
            },
            onClick = onDesktopMode,
        )
        AppListDivider()
        AppListRow(
            title = stringResource(R.string.site_shell_leave_home),
            leadingIcon = Icons.Filled.Home,
            onClick = onLeave,
        )
    }
}

private fun hypotLength(dx: Float, dy: Float): Float =
    kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
