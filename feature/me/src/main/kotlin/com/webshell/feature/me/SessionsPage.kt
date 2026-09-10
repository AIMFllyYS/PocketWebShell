package com.webshell.feature.me

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.webshell.core.designsystem.components.AppCard
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.designsystem.theme.LocalOverlayClearance
import com.webshell.core.webengine.KeepAliveRegistry

/** 全部后台会话的统一管理页；行样式与设置首页的会话区块共享。 */
@Composable
internal fun SessionsPage(
    sessions: List<KeepAliveRegistry.Entry>,
    onStopSessions: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var pendingIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val selectedInList = selectedIds.intersect(sessions.map { it.sessionId }.toSet())
    val allSelected = sessions.isNotEmpty() && selectedInList.size == sessions.size

    BackHandler(enabled = selecting) {
        selecting = false
        selectedIds = emptySet()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppNavigationBar(
            title = if (selecting) {
                stringResource(R.string.me_sessions_selected, selectedInList.size)
            } else {
                stringResource(R.string.me_sessions)
            },
            onBack = {
                if (selecting) {
                    selecting = false
                    selectedIds = emptySet()
                } else onBack()
            },
            backLabel = stringResource(if (selecting) R.string.me_cancel else com.webshell.core.designsystem.R.string.designsystem_back),
            actions = {
                if (sessions.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            if (!selecting) {
                                selecting = true
                            } else {
                                selectedIds = if (allSelected) emptySet() else sessions.map { it.sessionId }.toSet()
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                when {
                                    !selecting -> R.string.me_select
                                    allSelected -> R.string.me_deselect_all
                                    else -> R.string.me_select_all
                                },
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = if (selecting) 8.dp else LocalOverlayClearance.current),
        ) {
            AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                SessionsContent(
                    sessions = sessions,
                    selecting = selecting,
                    selectedIds = selectedInList,
                    onToggle = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    },
                    onRequestStop = { pendingIds = listOf(it) },
                )
            }
        }
        if (selecting) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .padding(bottom = LocalOverlayClearance.current),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        selectedIds = if (allSelected) emptySet() else sessions.map { it.sessionId }.toSet()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(if (allSelected) R.string.me_deselect_all else R.string.me_select_all),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(
                    onClick = { pendingIds = selectedInList.toList() },
                    enabled = selectedInList.isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.me_end_selected),
                        color = if (selectedInList.isNotEmpty()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }

    if (pendingIds.isNotEmpty()) {
        SessionStopConfirmDialog(
            count = pendingIds.size,
            onConfirm = {
                val ending = pendingIds
                onStopSessions(ending)
                selectedIds = selectedIds - ending.toSet()
                pendingIds = emptyList()
                if (selecting && sessions.size <= ending.size) selecting = false
            },
            onDismiss = { pendingIds = emptyList() },
        )
    }
}

@Composable
internal fun SessionStopConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppConfirmDialog(
        title = if (count <= 1) {
            stringResource(R.string.me_session_end_title)
        } else {
            stringResource(R.string.me_sessions_end_title, count)
        },
        text = if (count <= 1) {
            stringResource(R.string.me_session_end_text)
        } else {
            stringResource(R.string.me_sessions_end_text)
        },
        confirmText = stringResource(R.string.me_session_end),
        dismissText = stringResource(R.string.me_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}

@Composable
internal fun SessionsContent(
    sessions: List<KeepAliveRegistry.Entry>,
    onStopSession: (String) -> Unit = {},
    selecting: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggle: (String) -> Unit = {},
    onRequestStop: (String) -> Unit = onStopSession,
) {
    if (sessions.isEmpty()) {
        SessionEmptyRow()
    } else sessions.forEachIndexed { index, session ->
        SessionRow(
            session = session,
            onStop = { onRequestStop(session.sessionId) },
            selecting = selecting,
            selected = session.sessionId in selectedIds,
            onToggle = { onToggle(session.sessionId) },
        )
        if (index < sessions.lastIndex) AppListDivider()
    }
}
