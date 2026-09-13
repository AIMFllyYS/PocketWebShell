package com.webshell.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webshell.core.data.SCROLL_MODE_PAGER
import com.webshell.core.data.SCROLL_MODE_VERTICAL

/** Scaffold 给主屏预留的编辑底栏高度（两行 48dp + 与 Dock 相同的 20dp 外边距）。 */
const val HOME_EDIT_TOOLBAR_CLEARANCE_DP = 116f

internal const val LAUNCHER_EDIT_ACTION_COUNT = 4

internal fun launcherEditActionWidthDp(widthDp: Float): Float =
    ((widthDp - 64f) / LAUNCHER_EDIT_ACTION_COUNT - 24f).coerceAtLeast(1f)

internal fun launcherFooterHeightFromMeasuredAction(actionHeightDp: Float): Float {
    val buttonHeight = maxOf(48f, actionHeightDp + 16f)
    return maxOf(52f, buttonHeight + 4f)
}

/** Large type / 窄屏时底排可收成 2×2；完成始终留在第一行。 */
internal fun stackLauncherEditActions(widthDp: Float, fontScale: Float): Boolean =
    fontScale > 1.3f || (widthDp < 360f && fontScale > 1.1f)

data class HomeEditChromeState(
    val selectedCount: Int,
    val totalCount: Int,
    val scrollMode: String,
    val canRemoveFromFolder: Boolean,
    val canMoveToFolder: Boolean,
    val onDone: () -> Unit,
    val onDelete: () -> Unit,
    val onRemoveFromFolder: () -> Unit,
    val onMoveToFolder: () -> Unit,
    val onSelectAll: () -> Unit,
    val onScrollModeChange: (String) -> Unit,
)

/** 编辑态唯一菜单栏内容。玻璃壳由 Scaffold 或 Catalog 包在外面，这里不挂 Haze。 */
@Composable
fun HomeEditToolbar(
    state: HomeEditChromeState,
    modifier: Modifier = Modifier,
) {
    val allSelected = state.selectedCount == state.totalCount && state.totalCount > 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EditToolbarTextButton(
                label = stringResource(R.string.home_done),
                onClick = state.onDone,
                emphasized = true,
                modifier = Modifier.testTag("home_edit_done"),
            )
            Text(
                stringResource(R.string.home_selection_count, state.selectedCount, state.totalCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            ScrollModeSegment(
                scrollMode = state.scrollMode,
                onScrollModeChange = state.onScrollModeChange,
            )
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditToolbarTextButton(
                label = stringResource(R.string.home_delete),
                onClick = state.onDelete,
                enabled = state.selectedCount > 0,
                destructive = true,
                modifier = Modifier.weight(1f),
            )
            EditToolbarTextButton(
                label = stringResource(R.string.home_remove_from_folder),
                onClick = state.onRemoveFromFolder,
                enabled = state.canRemoveFromFolder,
                modifier = Modifier.weight(1f),
            )
            EditToolbarTextButton(
                label = stringResource(R.string.home_move_to_folder),
                onClick = state.onMoveToFolder,
                enabled = state.canMoveToFolder,
                modifier = Modifier.weight(1f),
            )
            EditToolbarTextButton(
                label = stringResource(if (allSelected) R.string.home_deselect_all else R.string.home_select_all),
                onClick = state.onSelectAll,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScrollModeSegment(
    scrollMode: String,
    onScrollModeChange: (String) -> Unit,
) {
    val pager = scrollMode != SCROLL_MODE_VERTICAL
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScrollModeChip(
            label = stringResource(R.string.home_scroll_pager),
            description = stringResource(R.string.home_scroll_pager_cd),
            selected = pager,
            onClick = { onScrollModeChange(SCROLL_MODE_PAGER) },
        )
        ScrollModeChip(
            label = stringResource(R.string.home_scroll_vertical),
            description = stringResource(R.string.home_scroll_vertical_cd),
            selected = !pager,
            onClick = { onScrollModeChange(SCROLL_MODE_VERTICAL) },
        )
    }
}

@Composable
private fun ScrollModeChip(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) colors.onPrimary else colors.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.primary else colors.surfaceVariant.copy(alpha = 0f))
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = description
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun EditToolbarTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val color = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        destructive -> colors.error
        emphasized -> colors.primary
        else -> colors.onSurface
    }
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
