package com.webshell.app.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.webshell.app.R
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.designsystem.components.AppSheet
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.model.DownloadItem
import com.webshell.core.model.DownloadStatus

@Composable
internal fun DownloadHistorySheet(
    items: List<DownloadItem>,
    onOpen: (DownloadItem) -> Unit,
    onRemove: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.74f)) {
            AppNavigationBar(title = stringResource(R.string.download_history_title), onBack = onDismiss)
            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.download_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(AppSpacing.lg),
                )
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    items.asReversed().forEachIndexed { index, item ->
                        val status = when (item.status) {
                            DownloadStatus.Queued, DownloadStatus.Running ->
                                stringResource(R.string.download_history_running)
                            DownloadStatus.Success -> item.relativePath
                            DownloadStatus.Failed -> stringResource(R.string.download_history_failed)
                        }
                        AppListRow(
                            title = item.displayName,
                            subtitle = status,
                            leadingIcon = if (item.status == DownloadStatus.Success) {
                                Icons.Outlined.DownloadDone
                            } else {
                                Icons.Outlined.Download
                            },
                            onClick = { onOpen(item) },
                            trailing = {
                                IconButton(onClick = { onRemove(item.id) }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.download_history_delete),
                                    )
                                }
                            },
                        )
                        if (index < items.lastIndex) AppListDivider()
                    }
                }
            }
        }
    }
}
