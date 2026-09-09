package com.webshell.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.webshell.core.designsystem.theme.LocalIsDarkTheme

/** Window/inset ownership stays separate from the pure resource-library presentation. */
@Composable
fun AllAppsDrawer(
    sections: List<AllAppsIndex.Section>,
    columns: Int,
    iconSize: Dp,
    cornerRadiusPercent: Int,
    view: AllAppsView,
    onViewChange: (AllAppsView) -> Unit,
    onLaunch: (appId: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // LocalView 返回 android.view.View；改名避免与 AllAppsView 参数 view 遮蔽。
        val dialogView = LocalView.current
        val window = (dialogView.parent as? DialogWindowProvider)?.window
        val darkTheme = LocalIsDarkTheme.current
        LaunchedEffect(window) { window?.setDimAmount(0f) }
        DisposableEffect(window, dialogView, darkTheme) {
            val controller = window?.let { WindowCompat.getInsetsController(it, dialogView) }
            val previousStatus = controller?.isAppearanceLightStatusBars
            val previousNavigation = controller?.isAppearanceLightNavigationBars
            controller?.isAppearanceLightStatusBars = !darkTheme
            controller?.isAppearanceLightNavigationBars = !darkTheme
            onDispose {
                previousStatus?.let { controller?.isAppearanceLightStatusBars = it }
                previousNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
            }
        }
        AllAppsContent(
            sections = sections, columns = columns, iconSize = iconSize,
            cornerRadiusPercent = cornerRadiusPercent, view = view, onViewChange = onViewChange,
            onLaunch = onLaunch, onDismiss = onDismiss,
        )
    }
}
