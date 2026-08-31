package com.webshell.feature.me

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.LocalTransitionStyle
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.WebViewCapabilities
import coil3.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开发者中心的二级目的地。 */
private enum class DeveloperSub {
    PLAYBOOK,
    LOGS,
}

/** 一级全屏菜单：开发者选项入口（iOS 设置层级：列表 → 推入全屏页）。 */
@Composable
internal fun DeveloperCenterPage(onBack: () -> Unit) {
    var sub by rememberSaveable { mutableStateOf<DeveloperSub?>(null) }
    BackHandler(enabled = sub != null) { sub = null }

    // 三级页面进入/退出：与二级页一致，跟随用户选择的动效风格。
    val transitionStyle = LocalTransitionStyle.current
    androidx.compose.animation.AnimatedContent(
        targetState = sub,
        transitionSpec = {
            AppMotion.detailEnterFor(transitionStyle) togetherWith
                AppMotion.detailExitFor(transitionStyle)
        },
        label = "developer-sub",
    ) { target ->
        when (target) {
            null -> DeveloperHome(
                onOpenSub = { sub = it },
                onBack = onBack,
            )
            DeveloperSub.PLAYBOOK -> DesignPlaybookPage(onBack = { sub = null })
            DeveloperSub.LOGS -> LogViewerPage(onBack = { sub = null })
        }
    }
}

@Composable
private fun DeveloperHome(
    onOpenSub: (DeveloperSub) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheCleared by remember { mutableStateOf(false) }

    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val capabilities = remember { WebViewCapabilities.snapshot() }

    DetailPage(title = "开发者选项", onBack = onBack) {
        SectionCard(title = "设计") {
            SettingRow(
                icon = Icons.Filled.DeveloperMode,
                title = "设计 Playbook",
                subtitle = "组件、配色、字体与动效预览",
                onClick = { onOpenSub(DeveloperSub.PLAYBOOK) },
                trailing = { Chevron() },
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionCard(title = "应用信息") {
            InfoRow(
                icon = Icons.Filled.Info,
                title = "版本",
                value = "${packageInfo?.versionName ?: "-"} (versionCode ${packageInfo?.longVersionCode ?: "-"})",
            )
            AppListDivider()
            InfoRow(
                icon = Icons.Filled.Web,
                title = "WebView 引擎",
                value = capabilities.webViewVersion ?: "未知",
            )
            AppListDivider()
            InfoRow(
                icon = Icons.Filled.Terminal,
                title = "设备 API Level",
                value = "Android ${Build.VERSION.SDK_INT}",
            )
            AppListDivider()
            InfoRow(
                icon = Icons.Filled.Smartphone,
                title = "设备型号",
                value = "${Build.MANUFACTURER} ${Build.MODEL}",
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionCard(title = "调试") {
            SettingRow(
                icon = Icons.Filled.ReceiptLong,
                title = "查看日志",
                subtitle = "最近 ${AppLog.CAPACITY} 条应用内操作日志，可复制/分享",
                onClick = { onOpenSub(DeveloperSub.LOGS) },
                trailing = { Chevron() },
            )
            AppListDivider()
            SettingRow(
                icon = Icons.Filled.DeleteSweep,
                title = "清除图标缓存",
                subtitle = if (cacheCleared) "已清除" else "清空图标的内存与磁盘缓存",
                onClick = {
                    val imageLoader = context.imageLoader
                    scope.launch(Dispatchers.IO) {
                        imageLoader.memoryCache?.clear()
                        imageLoader.diskCache?.clear()
                    }
                    cacheCleared = true
                    AppLog.log("dev", "清除图标缓存（内存 + 磁盘）")
                },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 只读信息行：右侧展示值。 */
@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String,
) {
    AppListRow(
        title = title,
        leadingIcon = icon,
        trailing = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
