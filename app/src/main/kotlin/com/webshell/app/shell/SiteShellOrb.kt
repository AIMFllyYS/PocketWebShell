package com.webshell.app.shell

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import com.webshell.core.designsystem.theme.AppSpacing
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
    /** 本地导入页桌面模式只换 UA 不改布局——菜单禁用该入口。 */
    desktopCapable: Boolean = true,
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
    onHideOrb: () -> Unit,
    onLeave: () -> Unit,
    documentMode: Boolean = false,
    canAddToHome: Boolean = false,
    onAddToHome: () -> Unit = {},
    currentSessionId: String? = null,
    onSwitchKeepAlive: (sessionId: String, url: String) -> Unit = { _, _ -> },
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
    val lastFree = remember { SiteShellOrbLastFree() }
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
                        val next = expandFromParked(
                            parked = latestAnchor.value,
                            lastFree = lastFree.value,
                            bounds = bounds,
                        )
                        lastFree.value = next
                        val from = bounds.parkSnapCenter(latestAnchor.value)
                        val target = Offset(bounds.centerX(next), bounds.centerY(next))
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
                            anchor = next
                            snapAnimating = false
                            latestPlacement.value(next.x, next.y, false)
                        }
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
                            val startAnchor = latestAnchor.value
                            val start = Offset(
                                bounds.centerX(startAnchor),
                                bounds.centerY(startAnchor),
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
                                        SiteShellOrbRelease.STAY -> {
                                            val clamped = bounds.clampCenter(current.x, current.y)
                                            val next = bounds.anchorAt(clamped.x, clamped.y)
                                            liveCenter = null
                                            anchor = next
                                            lastFree.value = next
                                            latestPlacement.value(next.x, next.y, false)
                                        }
                                        SiteShellOrbRelease.PARK_LEFT,
                                        SiteShellOrbRelease.PARK_RIGHT -> {
                                            if (!startAnchor.parked) lastFree.value = startAnchor
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
            desktopCapable = desktopCapable,
            bookmarked = bookmarked,
            hasPage = pageUrl.isNotBlank() && pageUrl != "about:blank",
            documentMode = documentMode,
            canAddToHome = canAddToHome,
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
            onHideOrb = {
                showMenu = false
                onHideOrb()
            },
            onLeave = {
                showMenu = false
                onLeave()
            },
            onAddToHome = {
                showMenu = false
                onAddToHome()
            },
            currentSessionId = currentSessionId,
            onSwitchKeepAlive = { id, url ->
                showMenu = false
                onSwitchKeepAlive(id, url)
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
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxSize()) {
        if (!expanded) {
            val half = 4.dp.toPx()
            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.45f),
                start = Offset(size.width / 2f, size.height / 2f - half),
                end = Offset(size.width / 2f, size.height / 2f + half),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }
        val dotRadius = SiteShellOrbMetrics.MARK_DOT_DIAMETER.dp.toPx() / 2f
        drawCircle(
            color = onSurface.copy(alpha = if (onDark) 0.62f else 0.56f),
            radius = dotRadius,
        )
    }
}

@Composable
private fun siteShellOrbChrome(parked: Boolean, parkedLeft: Boolean): Modifier {
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val opacity = when {
        parked && onDark -> 0.88f
        parked -> 0.92f
        onDark -> 0.82f
        else -> 0.90f
    }
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
    return Modifier
        .shadow(2.dp, shape, clip = false)
        .staticGlassSurface(shape = shape, opacity = opacity)
}

@Composable
private fun SiteShellOrbMenu(
    canGoBack: Boolean,
    canGoForward: Boolean,
    loading: Boolean,
    desktopMode: Boolean,
    desktopCapable: Boolean = true,
    bookmarked: Boolean,
    hasPage: Boolean,
    documentMode: Boolean = false,
    canAddToHome: Boolean = false,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onDesktopMode: () -> Unit,
    onBookmark: () -> Unit,
    onOpenDownloads: () -> Unit,
    onHideOrb: () -> Unit,
    onLeave: () -> Unit,
    onAddToHome: () -> Unit = {},
    currentSessionId: String? = null,
    onSwitchKeepAlive: (sessionId: String, url: String) -> Unit = { _, _ -> },
) {
    val others = remember(currentSessionId) {
        com.webshell.core.webengine.KeepAliveRegistry.entries
            .filter { it.sessionId != currentSessionId }
    }
    AppSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            AppNavigationBar(title = stringResource(R.string.site_shell_menu))
            Column(Modifier.padding(horizontal = AppSpacing.lg)) {
                if (others.isNotEmpty()) {
                    AppSectionHeader(stringResource(R.string.site_shell_switch_apps), startPadding = 0.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    ) {
                        others.forEach { entry ->
                            Surface(
                                modifier = Modifier
                                    .width(132.dp)
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .clickable { onSwitchKeepAlive(entry.sessionId, entry.url) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shadowElevation = 0.dp,
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        entry.title.ifBlank { entry.url },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                    Text(
                                        entry.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
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
                AppSectionHeader(stringResource(BrowserR.string.browser_menu_page), startPadding = 0.dp)
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    AppListRow(
                        title = stringResource(
                            if (bookmarked) BrowserR.string.browser_remove_bookmark else BrowserR.string.browser_add_bookmark,
                        ),
                        leadingIcon = if (bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                        onClick = if (documentMode) null else onBookmark,
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
                        onClick = if (documentMode || !desktopCapable) null else onDesktopMode,
                    )
                    if (canAddToHome) {
                        AppListDivider()
                        AppListRow(
                            title = stringResource(BrowserR.string.browser_make_app),
                            leadingIcon = Icons.AutoMirrored.Filled.AddToHomeScreen,
                            onClick = onAddToHome,
                        )
                    }
                }
                AppSectionHeader(stringResource(BrowserR.string.browser_menu_library), startPadding = 0.dp)
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    AppListRow(
                        title = stringResource(BrowserR.string.browser_downloads),
                        leadingIcon = Icons.Filled.Download,
                        onClick = onOpenDownloads,
                    )
                    AppListDivider()
                    AppListRow(
                        title = stringResource(R.string.site_shell_hide_orb),
                        leadingIcon = Icons.Filled.VisibilityOff,
                        onClick = onHideOrb,
                    )
                    if (hasPage || documentMode) {
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

private class SiteShellOrbLastFree {
    var value: SiteShellOrbAnchor? = null
}

private fun hypotLength(dx: Float, dy: Float): Float =
    kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
