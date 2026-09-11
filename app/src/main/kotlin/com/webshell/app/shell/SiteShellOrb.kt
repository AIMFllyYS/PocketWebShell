package com.webshell.app.shell

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.webshell.core.designsystem.components.AppCard
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.designsystem.components.AppSectionHeader
import com.webshell.core.designsystem.components.AppSheet
import com.webshell.core.designsystem.components.staticGlassSurface
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.webshell.feature.browser.R as BrowserR

private data class SiteShellNotice(val title: String, val text: String)

/**
 * Site-shell-only assist orb. Static gray mark, no live blur, and the overlay never
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
    pageUrl: String,
    bookmarked: Boolean,
    onPlacement: (x: Float, y: Float, parked: Boolean) -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onDesktopMode: (Boolean) -> Unit,
    onBookmark: () -> Boolean,
    onOpenDownloads: () -> Unit,
    onLeave: () -> Unit,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var anchor by remember { mutableStateOf(SiteShellOrbAnchor.restored(posX, posY, parked)) }
    var liveCenter by remember { mutableStateOf<Offset?>(null) }
    var snapAnimating by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<SiteShellNotice?>(null) }
    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    val latestPlacement = rememberUpdatedState(onPlacement)
    val latestAnchor = rememberUpdatedState(anchor)
    val desktopOnTitle = stringResource(BrowserR.string.browser_desktop_on_title)
    val desktopOnText = stringResource(BrowserR.string.browser_desktop_on_message)
    val desktopOffTitle = stringResource(BrowserR.string.browser_desktop_off_title)
    val desktopOffText = stringResource(BrowserR.string.browser_desktop_off_message)
    val bookmarkNeedTitle = stringResource(BrowserR.string.browser_bookmark_need_page_title)
    val bookmarkNeedText = stringResource(BrowserR.string.browser_bookmark_need_page_message)
    val bookmarkAddedTitle = stringResource(BrowserR.string.browser_bookmark_added)
    val bookmarkAddedText = stringResource(BrowserR.string.browser_bookmark_added_message)
    val bookmarkRemovedTitle = stringResource(BrowserR.string.browser_bookmark_removed)
    val bookmarkRemovedText = stringResource(BrowserR.string.browser_bookmark_removed_message)
    LaunchedEffect(posX, posY, parked) {
        if (liveCenter == null && !snapAnimating) {
            anchor = SiteShellOrbAnchor.restored(posX, posY, parked)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().zIndex(1f)) {
        val bounds = remember(maxWidth, maxHeight) {
            SiteShellOrbBounds(maxWidth.value, maxHeight.value)
        }
        if (anchor.parked && !snapAnimating) {
            val restoreLabel = stringResource(R.string.site_shell_orb_restore)
            val parkedLabel = stringResource(R.string.site_shell_orb_parked)
            val handleW = SiteShellOrbMetrics.PARKED_WIDTH
            val handleH = SiteShellOrbMetrics.PARKED_HEIGHT
            val left = anchor.parkedLeft
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            with(density) {
                                (if (left) 0f else maxWidth.value - handleW).dp.roundToPx()
                            },
                            with(density) { (bounds.centerY(anchor) - handleH / 2).dp.roundToPx() },
                        )
                    }
                    .size(handleW.dp, handleH.dp)
                    .zIndex(1f)
                    .then(siteShellOrbChrome(parked = true, parkedLeft = left))
                    .clickable(role = Role.Button, onClickLabel = restoreLabel) {
                        val center = SiteShellOrbAnchor.expandedCenter()
                        anchor = center
                        latestPlacement.value(center.x, center.y, false)
                    }
                    .semantics {
                        contentDescription = restoreLabel
                        stateDescription = parkedLabel
                    },
            ) {
                SiteShellOrbMark(expanded = false)
            }
        } else {
            val menuLabel = stringResource(R.string.site_shell_orb)
            val settled = Offset(bounds.centerX(anchor), bounds.centerY(anchor))
            val center = liveCenter
                ?: if (snapAnimating) Offset(animX.value, animY.value) else settled
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
                    .then(siteShellOrbChrome(parked = false, parkedLeft = false))
                    .semantics {
                        role = Role.Button
                        contentDescription = menuLabel
                    }
                    .then(
                        if (snapAnimating) Modifier else Modifier.pointerInput(bounds) {
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
                                        releaseCenterX = current.x,
                                        bounds = bounds,
                                    )
                                    when (action) {
                                        SiteShellOrbRelease.TAP -> {
                                            liveCenter = null
                                            showMenu = true
                                        }
                                        SiteShellOrbRelease.PARK_LEFT,
                                        SiteShellOrbRelease.PARK_RIGHT -> {
                                            val next = SiteShellOrbAnchor.parked(
                                                left = action == SiteShellOrbRelease.PARK_LEFT,
                                                y = bounds.anchorAt(current.x, current.y).y,
                                            )
                                            val target = bounds.parkSnapCenter(next)
                                            val from = current
                                            liveCenter = null
                                            scope.launch {
                                                snapAnimating = true
                                                animX.snapTo(from.x)
                                                animY.snapTo(from.y)
                                                coroutineScope {
                                                    launch {
                                                        animX.animateTo(
                                                            target.x,
                                                            tween(
                                                                SiteShellOrbMetrics.SNAP_MS,
                                                                easing = FastOutSlowInEasing,
                                                            ),
                                                        )
                                                    }
                                                    launch {
                                                        animY.animateTo(
                                                            target.y,
                                                            tween(
                                                                SiteShellOrbMetrics.SNAP_MS,
                                                                easing = FastOutSlowInEasing,
                                                            ),
                                                        )
                                                    }
                                                }
                                                view.performHapticFeedback(
                                                    HapticFeedbackConstants.CLOCK_TICK,
                                                )
                                                anchor = next
                                                snapAnimating = false
                                                latestPlacement.value(next.x, next.y, true)
                                            }
                                        }
                                    }
                                    break
                                }
                            }
                        }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                SiteShellOrbMark(expanded = true)
            }
        }
    }

    if (showMenu) {
        SiteShellOrbMenu(
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            loading = loading,
            desktopMode = desktopMode,
            bookmarked = bookmarked,
            hasPage = pageUrl.isNotBlank() && pageUrl != "about:blank",
            onDismiss = { showMenu = false },
            onBack = { showMenu = false; onBack() },
            onForward = { showMenu = false; onForward() },
            onRefreshOrStop = {
                showMenu = false
                if (loading) onStop() else onRefresh()
            },
            onDesktopMode = {
                showMenu = false
                val next = !desktopMode
                onDesktopMode(next)
                notice = if (next) {
                    SiteShellNotice(desktopOnTitle, desktopOnText)
                } else {
                    SiteShellNotice(desktopOffTitle, desktopOffText)
                }
            },
            onBookmark = {
                showMenu = false
                val hasPage = pageUrl.isNotBlank() && pageUrl != "about:blank"
                if (!hasPage || !onBookmark()) {
                    notice = SiteShellNotice(
                        bookmarkNeedTitle,
                        bookmarkNeedText,
                    )
                } else {
                    notice = if (bookmarked) {
                        SiteShellNotice(bookmarkRemovedTitle, bookmarkRemovedText)
                    } else {
                        SiteShellNotice(bookmarkAddedTitle, bookmarkAddedText)
                    }
                }
            },
            onOpenDownloads = {
                showMenu = false
                onOpenDownloads()
            },
            onLeave = {
                showMenu = false
                onLeave()
            },
        )
    }
    notice?.let { current ->
        AppConfirmDialog(
            title = current.title,
            text = current.text,
            confirmText = stringResource(BrowserR.string.browser_js_ok),
            onConfirm = { notice = null },
            onDismiss = { notice = null },
        )
    }
}

@Composable
private fun SiteShellOrbMark(expanded: Boolean) {
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val stroke = if (onDark) Color(0xFFD0D0D2) else Color(0xFF6E6E73)
    Canvas(Modifier.fillMaxSize()) {
        val insetY = size.height * if (expanded) 0.34f else 0.22f
        val x = size.width / 2f
        drawLine(
            color = stroke,
            start = Offset(x, insetY),
            end = Offset(x, size.height - insetY),
            strokeWidth = if (expanded) 2.5.dp.toPx() else 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun siteShellOrbChrome(parked: Boolean, parkedLeft: Boolean): Modifier {
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fill = if (onDark) Color(0xFF5A5A5C) else Color(0xFFE4E4E6)
    val shape = if (parked) {
        RoundedCornerShape(
            topStart = if (parkedLeft) 0.dp else 24.dp,
            bottomStart = if (parkedLeft) 0.dp else 24.dp,
            topEnd = if (parkedLeft) 24.dp else 0.dp,
            bottomEnd = if (parkedLeft) 24.dp else 0.dp,
        )
    } else {
        CircleShape
    }
    return if (parked) {
        Modifier
            .shadow(2.dp, shape, clip = false)
            .clip(shape)
            .background(fill)
    } else {
        Modifier
            .shadow(8.dp, CircleShape, clip = false)
            .staticGlassSurface(shape = CircleShape, opacity = 0.94f)
    }
}

@Composable
private fun SiteShellOrbMenu(
    canGoBack: Boolean,
    canGoForward: Boolean,
    loading: Boolean,
    desktopMode: Boolean,
    bookmarked: Boolean,
    hasPage: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onDesktopMode: () -> Unit,
    onBookmark: () -> Unit,
    onOpenDownloads: () -> Unit,
    onLeave: () -> Unit,
) {
    AppSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            AppNavigationBar(title = stringResource(R.string.site_shell_menu))
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                stringResource(BrowserR.string.browser_back),
                            )
                        }
                        IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                stringResource(BrowserR.string.browser_forward),
                            )
                        }
                        IconButton(onClick = onRefreshOrStop, modifier = Modifier.size(48.dp)) {
                            Icon(
                                if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                                stringResource(
                                    if (loading) BrowserR.string.browser_stop else BrowserR.string.browser_refresh,
                                ),
                            )
                        }
                        IconButton(onClick = onLeave, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Home, stringResource(R.string.site_shell_leave_home))
                        }
                    }
                }
                AppSectionHeader(stringResource(BrowserR.string.browser_menu_page))
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    AppListRow(
                        title = stringResource(
                            if (bookmarked) BrowserR.string.browser_remove_bookmark else BrowserR.string.browser_add_bookmark,
                        ),
                        leadingIcon = if (bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                        onClick = onBookmark,
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
                }
                AppSectionHeader(stringResource(BrowserR.string.browser_menu_library))
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    AppListRow(
                        title = stringResource(BrowserR.string.browser_downloads),
                        leadingIcon = Icons.Filled.Download,
                        onClick = onOpenDownloads,
                    )
                    if (hasPage) {
                        AppListDivider()
                        AppListRow(
                            title = stringResource(R.string.site_shell_leave_home),
                            leadingIcon = Icons.Filled.Home,
                            onClick = onLeave,
                        )
                    }
                }
            }
        }
    }
}

private fun hypotLength(dx: Float, dy: Float): Float =
    kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
