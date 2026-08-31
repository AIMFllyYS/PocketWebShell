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
        version = "0.1.6",
        date = "2026-08-31",
        highlights = listOf(
            "自由摆放真正生效：关闭「自动整理桌面」后，图标拖到哪个网格位就停在哪个网格位，不再自动蹦回前排（对齐主流 Android 桌面）",
            "主页布局新增「自动整理桌面」开关，默认关闭 = 自由摆放；开启后恢复压实排列",
            "拖到空槽直接落子，拖到已占用槽位则双方交换（文件夹整体交换）；新应用追加到末页首个空槽",
            "解散/移出文件夹智能填槽：首个成员占原槽位，其余顺序填充后续空槽",
            "情境菜单方向修复：以手指按压点计算四向空间，往空间更大的一侧弹出（1 号位图标菜单从右侧弹出），边缘自动钳制",
            "修复上传的本地图标路径保存失败的问题",
        ),
    ),
    UpdateEntry(
        version = "0.1.5",
        date = "2026-08-31",
        highlights = listOf(
            "彻底修复长按/拖拽方形阴影：阴影改为图标内部按圆角投影",
            "情境菜单智能避让：图标在左半屏从右侧弹出，右半屏从左侧弹出",
            "去掉点击应用/文件夹时的整格高亮，改为图标轻缩反馈",
            "首字母图标高对比配色，任何主题下清晰可读",
            "拖拽重排支持网格吸附：松手自动对齐最近网格位，不再自动归位",
            "双指捏合进入编辑模式修复（重写手势识别）",
            "文件夹打开重设计：全屏展开页 + 3 列大图标网格",
            "添加网站支持上传本地图片作为图标",
            "外观与主题新增页面切换动效选择（滑入/淡入/缩放/无）",
        ),
    ),
    UpdateEntry(
        version = "0.1.4",
        date = "2026-08-31",
        highlights = listOf(
            "修复长按/拖拽时的方形阴影：阴影改为跟随图标大圆角形状",
            "情境菜单重做：改为贴近图标的锚点浮窗（对齐 HyperOS/iOS），纵向列表 + 圆形图标底",
            "菜单材质改半透明磨砂，弹出动画统一（从图标一侧缩放浮现）",
            "新增编辑模式：双指捏合进入，图标抖动 + 左上角勾选，双指外扩/完成/返回退出",
            "编辑模式含右上「完成」胶囊与底部操作行（全选/清空/计数）",
            "统一动效：主 Tab 淡切、二/三级页面统一右侧滑入 + 淡入",
            "设计 Playbook 新增动效演示（spring / 二级页滑入 / 菜单弹出）",
        ),
    ),
    UpdateEntry(
        version = "0.1.3",
        date = "2026-08-31",
        highlights = listOf(
            "主页图标 iOS 化：移除底座卡片，图标直接落在壁纸上，圆角由图标本体裁剪",
            "长按图标改为 iOS 顺序：先弹情境菜单，拖动超过阈值后菜单淡出、图标跟手进入拖拽",
            "新增统一情境菜单组件：网格动作、图标预览、破坏性操作分组（不新增实时模糊，保性能）",
            "收藏边框加粗至 2dp；新建入口改为描边幽灵样式",
            "「开发者选项」升级为一级全屏页面：应用/WebView/设备信息、日志查看、缓存清理",
            "新增内存环形日志（500 条，支持标签过滤与复制导出，不记录浏览内容等敏感信息）",
            "设计 Playbook 新增情境菜单演示区",
        ),
    ),
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
