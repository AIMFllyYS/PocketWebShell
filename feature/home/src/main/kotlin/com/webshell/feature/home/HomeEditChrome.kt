package com.webshell.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

/**
 * 现代 iOS / Launcher 级精细编辑底栏：
 * 1. 状态解耦：无选中项时呈现「桌面排版」设置（左右/上下翻页模式）；
 * 2. 批量模式：选中项时切换为「批量操作栏」，包含全选切换、计数胶囊与 iOS 风格操作坞；
 * 3. 二级菜单：多文件夹操作汇聚为二级抽屉/弹窗，杜绝任何字数截断与挤压。
 */
@Composable
fun HomeEditToolbar(
    state: HomeEditChromeState,
    modifier: Modifier = Modifier,
) {
    val hasSelection = state.selectedCount > 0
    val allSelected = state.selectedCount == state.totalCount && state.totalCount > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        if (!hasSelection) {
            // 未选状态：桌面排版与翻页设置
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                EditToolbarTextButton(
                    label = stringResource(R.string.home_select_all),
                    onClick = state.onSelectAll,
                )
                Text(
                    text = stringResource(R.string.home_edit_desktop_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                EditToolbarTextButton(
                    label = stringResource(R.string.home_done),
                    onClick = state.onDone,
                    emphasized = true,
                    modifier = Modifier.testTag("home_edit_done"),
                )
            }
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                ScrollModeSegment(
                    scrollMode = state.scrollMode,
                    onScrollModeChange = state.onScrollModeChange,
                )
            }
        } else {
            // 多选状态：iOS 风格操作栏
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                EditToolbarTextButton(
                    label = stringResource(if (allSelected) R.string.home_deselect_all else R.string.home_select_all),
                    onClick = state.onSelectAll,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.home_selection_count, state.selectedCount, state.totalCount),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                EditToolbarTextButton(
                    label = stringResource(R.string.home_done),
                    onClick = state.onDone,
                    emphasized = true,
                    modifier = Modifier.testTag("home_edit_done"),
                )
            }
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 删除按钮
                EditToolbarActionButton(
                    label = stringResource(R.string.home_delete),
                    icon = Icons.Rounded.DeleteOutline,
                    onClick = state.onDelete,
                    enabled = true,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                )

                // 文件夹二级交互或直接操作
                if (state.canRemoveFromFolder && state.canMoveToFolder) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        EditToolbarActionButton(
                            label = stringResource(R.string.home_folder_actions),
                            icon = Icons.Rounded.Folder,
                            trailingIcon = Icons.Rounded.KeyboardArrowDown,
                            onClick = { menuExpanded = true },
                            enabled = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_move_to_folder)) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    menuExpanded = false
                                    state.onMoveToFolder()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_remove_from_folder)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurface)
                                },
                                onClick = {
                                    menuExpanded = false
                                    state.onRemoveFromFolder()
                                },
                            )
                        }
                    }
                } else if (state.canRemoveFromFolder) {
                    EditToolbarActionButton(
                        label = stringResource(R.string.home_remove_from_folder),
                        icon = Icons.Rounded.FolderOpen,
                        onClick = state.onRemoveFromFolder,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    EditToolbarActionButton(
                        label = stringResource(R.string.home_move_to_folder),
                        icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                        onClick = state.onMoveToFolder,
                        enabled = state.canMoveToFolder,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditToolbarActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    trailingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        destructive -> colors.error
        else -> colors.onSurface
    }
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) colors.surfaceVariant.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = tint.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
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
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(3.dp),
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
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) colors.primary else colors.surfaceVariant.copy(alpha = 0f))
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = description
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
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
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
