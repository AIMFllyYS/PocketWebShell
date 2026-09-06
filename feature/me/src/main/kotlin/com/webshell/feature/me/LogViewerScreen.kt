package com.webshell.feature.me

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.data.LogEntity
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.model.AppLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 日志查看器：从 Room 倒序分页加载（每页 30 条，滚动到底自动加载更多），
 * 支持按标签过滤、复制/分享完整导出（TXT 经 FileProvider）、二次确认后清空。
 */
@Composable
internal fun LogViewerPage(
    onBack: () -> Unit,
    viewModel: LogViewerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    /** 完整导出（含头部）+ 当前过滤条件下的全部条目 */
    suspend fun fullExport(): String =
        buildExportHeader(context, state.totalCount) + "\n\n" + viewModel.exportText()

    fun copyAll() {
        scope.launch {
            val text = fullExport()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("app_log", text))
        }
    }

    fun shareAll() {
        scope.launch {
            val text = fullExport()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "logs").apply { mkdirs() }
                File(dir, "PocketWebShell-logs-$stamp.txt").apply { writeText(text) }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "分享日志"))
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppNavigationBar(title = stringResource(R.string.me_logs), onBack = onBack, actions = {
            Box {
                IconButton(onClick = { showActions = true }) {
                    Icon(Icons.Filled.MoreHoriz, stringResource(R.string.me_log_actions))
                }
                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.me_log_refresh)) },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                        onClick = { showActions = false; viewModel.refresh() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.me_log_copy)) },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        onClick = { showActions = false; copyAll() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.me_log_share)) },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = { showActions = false; shareAll() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.me_log_clear),
                        color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showActions = false; showClearConfirm = true })
                }
            }
        })

        if (state.tags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.lg),
            ) {
                FilterChip(
                    selected = state.tagFilter == null,
                    onClick = { viewModel.setTagFilter(null) },
                    label = { Text("全部") },
                )
                state.tags.forEach { tag ->
                    FilterChip(
                        selected = state.tagFilter == tag,
                        onClick = {
                            viewModel.setTagFilter(if (state.tagFilter == tag) null else tag)
                        },
                        label = { Text(tag) },
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.sm))
        }

        if (state.entries.isEmpty()) {
            Text(
                text = if (state.tagFilter == null) {
                    "暂无日志。操作主页或修改设置后，事件会记录在这里。"
                } else {
                    "该标签下暂无日志。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(AppSpacing.lg),
            )
        } else {
            val listState = rememberLazyListState()
            // 最后一个可见 item 到达末尾时自动加载下一页
            val reachedEnd by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                    last.index >= info.totalItemsCount - 1
                }
            }
            LaunchedEffect(reachedEnd) {
                if (reachedEnd) viewModel.loadMore()
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = AppSpacing.lg,
                    vertical = AppSpacing.sm,
                ),
            ) {
                items(state.entries, key = { it.id }) { entry ->
                    LogRow(entry)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                item {
                    Text(
                        text = if (state.hasMore || state.loadingMore) {
                            "加载更多…"
                        } else {
                            "已加载 ${state.entries.size} / 共 ${state.totalCount} 条"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = AppSpacing.sm),
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部日志？") },
            text = { Text("将删除数据库中的全部日志记录，此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clear()
                    },
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun LogRow(entry: LogEntity) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            AppLog.formatTime(entry.timeMillis),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Text(
            entry.tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Text(
            entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (entry.level == AppLog.Level.ERROR.name) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 导出头部：应用版本、设备与系统版本、条目总数（设备信息在 Composable 侧拼装） */
private fun buildExportHeader(context: Context, totalCount: Int): String {
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    return buildString {
        appendLine("PocketWebShell 日志导出")
        appendLine("导出时间：$exportedAt")
        appendLine("应用版本：${info?.versionName ?: "?"}(${info?.longVersionCode ?: "?"})")
        appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android：${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        append("条目总数：$totalCount")
    }
}
