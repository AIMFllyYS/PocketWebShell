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
        version = "0.1.2",
        date = "2026-08-31",
        highlights = listOf(
            "浅色模式改为 iOS 分组规范：灰底（#F2F2F7）托纯白卡片，层级分明",
            "次要文字加深至 AAA 级对比度（#55555B，白卡上 7.4:1），备注不再看不清",
            "中性灰统一冷调，消除卡片「粉肉色」观感",
            "卡片新增 1dp 发丝描边（静态拟态边缘光）",
            "底部玻璃栏选中态新增胶囊指示底（弹簧动画，不改布局）",
            "深色模式卡片底色对齐 iOS 深色分组（#1C1C1E）",
        ),
    ),
    UpdateEntry(
        version = "0.1.1",
        date = "2026-08-30",
        highlights = listOf(
            "统一设计系统：全套浅/深色 token、MiSans 字阶、圆角/间距/动效规范",
            "内置 MiSans 字体（子集化），全设备字度量一致，杜绝文字截半",
            "新增统一组件：AppCard / AppListRow / AppSectionHeader / AppBadge / AppConfirmDialog / glassSurface",
            "「我的」新增「开发者选项」：设计 Playbook 实时预览全部组件与动效",
            "浏览器顶栏重做：胶囊地址栏文字垂直居中，修复占位文字截半",
            "修复设置页副标题低对比度（outline → onSurfaceVariant）与卡片紫色偏差",
            "滑杆改单色轨道 + 白色滑块；底部玻璃栏收编为统一组件",
            "清理未使用的组件库声明（Lottie、reorderable）",
        ),
    ),
    UpdateEntry(
        version = "0.1.0",
        date = "2026-08-30",
        highlights = listOf(
            "版本计数重置：新线从 0.1.0 起（versionCode=1）",
            "全新主题系统：纯白 / 纯黑 / 跟随系统三种苹果式配色",
            "照片壁纸主题：上传照片作主页壁纸，自动从照片提取主题色",
            "底部导航重做：iOS 液态玻璃风格悬浮胶囊（Haze 实时模糊 + 高光描边）",
            "「我的」新增「外观与主题」设置页",
            "新增设计 / 版本号 / 性能三份项目规范文档",
            "注意：旧版 0.2.0 设备需先卸载再安装本版本",
        ),
    ),
    UpdateEntry(
        version = "旧线 0.2.0",
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
        version = "旧线 0.1.0",
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
