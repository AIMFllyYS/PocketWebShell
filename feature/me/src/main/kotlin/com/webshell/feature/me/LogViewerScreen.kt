package com.webshell.feature.me

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.model.AppLog
import com.webshell.core.model.AppLog.Entry

/**
 * 日志查看器：倒序展示 AppLog 环形缓冲，支持按标签过滤、复制与分享。
 * 纯内存日志，进程结束即清空。
 */
@Composable
internal fun LogViewerPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(AppLog.entries()) }
    var tagFilter by remember { mutableStateOf<String?>(null) }

    val visible = remember(entries, tagFilter) {
        val filtered = if (tagFilter == null) entries else entries.filter { it.tag == tagFilter }
        filtered.asReversed() // 最新在前
    }
    val tags = remember(entries) { entries.map { it.tag }.distinct() }

    fun exportVisible(): String = if (tagFilter == null) {
        AppLog.exportText()
    } else {
        visible.asReversed().joinToString("\n") { "${AppLog.formatTime(it.timeMillis)}  [${it.tag}]  ${it.message}" }
    }

    fun copyAll() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("app_log", exportVisible()))
    }

    fun shareAll() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, exportVisible())
        }
        context.startActivity(Intent.createChooser(send, "分享日志"))
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：返回 + 标题 + 复制/分享/刷新
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xs),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("日志", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { entries = AppLog.entries() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
            IconButton(onClick = { copyAll() }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
            }
            IconButton(onClick = { shareAll() }) {
                Icon(Icons.Filled.Share, contentDescription = "分享")
            }
        }

        if (tags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.lg),
            ) {
                FilterChip(
                    selected = tagFilter == null,
                    onClick = { tagFilter = null },
                    label = { Text("全部 (${entries.size})") },
                )
                tags.forEach { tag ->
                    FilterChip(
                        selected = tagFilter == tag,
                        onClick = { tagFilter = if (tagFilter == tag) null else tag },
                        label = { Text("$tag (${entries.count { it.tag == tag }})") },
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.sm))
        }

        if (visible.isEmpty()) {
            Text(
                text = if (entries.isEmpty()) {
                    "暂无日志。操作主页或修改设置后，事件会记录在这里。"
                } else {
                    "该标签下暂无日志。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(AppSpacing.lg),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = AppSpacing.lg,
                    vertical = AppSpacing.sm,
                ),
            ) {
                items(visible) { entry ->
                    LogRow(entry)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: Entry) {
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
            modifier = Modifier.weight(1f),
        )
    }
}
