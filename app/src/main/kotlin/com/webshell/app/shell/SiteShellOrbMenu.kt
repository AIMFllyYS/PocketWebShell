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


@Composable
internal fun SiteShellOrbMenu(
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
