package com.webshell.feature.me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class UpdateEntry(
    val version: String,
    val date: String,
    val highlights: List<String>,
)

/** 与 CHANGELOG.md 同步的应用内更新日志。 */
private val updateEntries = listOf(
    UpdateEntry(
        version = "0.2.0",
        date = "2026-08-29",
        highlights = listOf(
            "品牌焕新：应用更名「玄览」，启用天道主题新图标",
            "底部导航栏压缩上下间距，整体更紧凑",
            "浏览器顶栏瘦身：地址栏与标签按钮高度对齐",
            "浏览器收藏、页内查找等入口统一收纳至右上角菜单",
            "「我的」改版为一/二级菜单结构，按板块进入设置",
            "「运行中的后台会话」移至页面顶部，滑杆样式美化",
            "新增「项目更新日志」页面",
        ),
    ),
    UpdateEntry(
        version = "0.1.0",
        date = "2026-08-29",
        highlights = listOf(
            "网站元数据与图标发现流程",
            "固定容量桌面分页、文件夹与持久化排序",
            "DragLayer 长按拖拽、排序落点与文件夹热点",
            "多标签浏览器与紧凑宽度标签切换",
            "托管 WebView 会话、站点设置与前台服务保活",
            "通知权限与电池优化状态面板",
        ),
    ),
)

/** 二级页：项目更新日志。 */
@Composable
internal fun UpdateLogPage(onBack: () -> Unit) {
    DetailPage(title = "项目更新日志", onBack = onBack) {
        updateEntries.forEachIndexed { index, entry ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.version,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            entry.date,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    entry.highlights.forEach { item ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(
                                "•",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(item, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
