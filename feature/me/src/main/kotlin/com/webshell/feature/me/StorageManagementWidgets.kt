package com.webshell.feature.me

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.data.WebAppEntity
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.components.AppContextMenu
import com.webshell.core.designsystem.components.AppContextMenuItem
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppPrimaryButton
import com.webshell.core.designsystem.components.AppSearchField
import com.webshell.core.designsystem.components.AppSettingsSection
import com.webshell.core.designsystem.components.RevealFromPoint
import com.webshell.core.designsystem.components.SiteIcon
import com.webshell.core.designsystem.components.siteIconGlyph
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.designsystem.theme.LocalTransitionStyle
import com.webshell.core.webengine.storage.CookieFacts
import com.webshell.core.webengine.storage.Metric
import com.webshell.core.webengine.storage.SiteStorageStats
import com.webshell.core.webengine.storage.hostOfUrl
import com.webshell.core.webengine.storage.siteKeyOf


@Composable
internal fun SiteStorageRow(
    app: WebAppEntity,
    stats: SiteStorageStats?,
    scanning: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.lg, vertical = 8.dp),
    ) {
        SiteIcon(
            title = app.title,
            iconUrl = app.iconUrl,
            size = 40.dp,
            glyph = siteIconGlyph(app.isLocal, app.url),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                app.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                siteSubtitle(app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(AppSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 132.dp)) {
            when {
                stats == null -> Text(
                    stringResource(if (scanning) R.string.me_storage_measuring else R.string.me_storage_unmeasurable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                stats.allMetricsUnavailable() -> Text(
                    stringResource(R.string.me_storage_metric_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                else -> Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatStorageBytes(stats.attributableBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        if (stats.signedIn) {
                            stringResource(R.string.me_storage_signed_in)
                        } else {
                            cookieMetricLabel(stats.cookie)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            SettingsChevron()
        }
    }
}

@Composable
internal fun siteSubtitle(app: WebAppEntity): String {
    val host = hostOf(app.url)
    val folder = app.folderName
    return if (folder.isNullOrBlank()) host else host + stringResource(R.string.me_storage_folder_suffix, folder)
}

@Composable
internal fun DetailStatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun longMetricLabel(metric: Metric<Long>): String = when (metric) {
    is Metric.Measured -> formatStorageBytes(metric.value)
    Metric.Absent -> formatStorageBytes(0)
    Metric.Unavailable -> stringResource(R.string.me_storage_metric_unavailable)
}

@Composable
internal fun cookieMetricLabel(metric: Metric<CookieFacts>): String = when (metric) {
    is Metric.Measured -> stringResource(
        R.string.me_storage_cookie_value,
        metric.value.count,
        formatStorageBytes(metric.value.serializedBytes),
    )
    Metric.Absent -> stringResource(R.string.me_storage_cookie_absent)
    Metric.Unavailable -> stringResource(R.string.me_storage_metric_unavailable)
}

internal val SiteDataColor = Color(0xFFFF9500)
internal val AppDataColor = Color(0xFF8E8E93)

/** 固定 8dp 高的圆角分段条 + 逐类图例（色点 + 名称 + 精确大小）。segments 为 0 的部分不画。 */
@Composable
internal fun StorageUsageBar(
    segments: List<Pair<Color, Long>>,
    legend: List<Pair<Color, Pair<String, Long>>>,
) {
    Row(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        segments.forEach { (color, bytes) ->
            if (bytes > 0) {
                Box(Modifier.weight(bytes.toFloat()).fillMaxHeight().background(color))
            }
        }
    }
    Spacer(Modifier.height(AppSpacing.md))
    legend.forEach { (color, labelBytes) ->
        val (label, bytes) = labelBytes
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(AppSpacing.sm))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                formatStorageBytes(bytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 域名展示：URI 解析失败或本地页面回退原始 url。 */
internal fun hostOf(url: String): String = hostOfUrl(url) ?: url
