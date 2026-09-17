package com.webshell.feature.me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.webshell.core.designsystem.components.AppCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private data class UpdateEntry(
    val version: String,
    val date: String,
    val highlights: List<String>,
)

/** 与 CHANGELOG.md 同步的应用内更新日志。 */
private const val UPDATE_PAGE_SIZE = 20

private val updateEntries = listOf(
    UpdateEntry(
        version = "0.1.56",
        date = "2026-09-17",
        highlights = listOf(
            "开屏零重组绘制管线重构：采用 drawWithCache 缓存几何尺寸与画笔渐变，消除每帧重组",
            "艺术文字排版免 CPU 重算：展开动效全面迁移至 GPU 硬件加速缩放与透明度，消除首帧卡顿",
            "应用内「检查新版本」端到端升级闭环实测验证",
        ),
    ),
    UpdateEntry(
        version = "0.1.55",
        date = "2026-09-17",
        highlights = listOf(
            "开屏全面升级为浅色白洞与深色黑洞天体动效，配以唯美艺术字与光引过渡，120fps 全程硬件加速",
            "本地 HTML 扫描深度覆盖 Download 及其所有下级子目录与公共目录，兼备并发协程与超时保护",
            "桌面多选编辑交互升级为 iOS 级操作栏与二级抽屉，彻底杜绝文字截断",
        ),
    ),
    UpdateEntry(
        version = "0.1.54",
        date = "2026-09-17",
        highlights = listOf(
            "我的页可以检查 GitHub 最新正式版：已是最新会说明，有新版本会弹出是否下载",
            "更新判断只读公开的 GitHub Release，不另做云端存储或自建后端",
        ),
    ),
    UpdateEntry(
        version = "0.1.53",
        date = "2026-09-17",
        highlights = listOf(
            "开屏重构为原生级丝滑微动效：废弃主线程黑洞粒子运算，采用玄览星核 BrandMark 弹簧微动效与优雅淡出",
            "底层主屏并行静默预热，开屏淡出时主线程无冲突，彻底消灭首帧卡顿",
            "完善原生启动屏主题契约，窗口冷启动到应用内无缝咬合",
        ),
    ),
    UpdateEntry(
        version = "0.1.52",
        date = "2026-09-14",
        highlights = listOf(
            "开屏：字和粒子一起出来，黑洞播的时候字不收，最后字幕、黑洞和遮罩一起淡出再进主页",
            "空主页不再叠「把喜欢的网站放在主屏幕」，避免开屏结束后还留一句广告",
        ),
    ),
    UpdateEntry(
        version = "0.1.51",
        date = "2026-09-14",
        highlights = listOf(
            "开屏整段重写：字先出现，再播黑洞，字先收干净，再收成品牌标，最后才进主页",
        ),
    ),
    UpdateEntry(
        version = "0.1.50",
        date = "2026-09-14",
        highlights = listOf(
            "开屏按一条时间轴重写，标题和黑洞不再各走各的时钟",
            "更新日志默认先看最近 20 条，需要时再加载更早的版本",
            "浏览历史按今天、昨天、前天、一周内、一个月内、更早分组，可折叠，并加了搜索",
        ),
    ),
    UpdateEntry(
        version = "0.1.49",
        date = "2026-09-13",
        highlights = listOf(
            "开屏标题跟着动画一起收掉，不会在结束后再停一下",
            "单页 HTML 和普通网站都能用页面自己的图标；收藏和历史不再共用一个地球标",
            "历史先加载 20 条，往下滑再继续；存储占用卡片用弹簧撑开",
        ),
    ),
    UpdateEntry(
        version = "0.1.48",
        date = "2026-09-13",
        highlights = listOf(
            "开屏播完再进主页，粒子更少，冷启动不那么卡",
            "打开 Markdown 改成全屏加悬浮球，去掉顶栏，长按可以选中文字",
            "文档名用原来的文件名；图标左右稍微加宽一点",
        ),
    ),
    UpdateEntry(
        version = "0.1.47",
        date = "2026-09-13",
        highlights = listOf(
            "开屏改成统一的黑洞，时间大约一半，播完进主页少卡一下",
            "从外面打开的 Markdown 用文档图标，不再跟本地 HTML 共用代码块标",
        ),
    ),
    UpdateEntry(
        version = "0.1.46",
        date = "2026-09-13",
        highlights = listOf(
            "开屏前一秒不再卡：星河播着的时候先不组主页",
            "从外面再打开同一个 HTML / Markdown，会回到原来的标签或主屏应用",
            "浏览菜单补上「制作应用」，全屏时菜单可以上下滑；悬浮球中间改成一个大点",
        ),
    ),
    UpdateEntry(
        version = "0.1.45",
        date = "2026-09-13",
        highlights = listOf(
            "主屏应用的跳转可以选：去浏览页，或留在当前应用里覆盖打开",
            "从外面打开的 HTML / Markdown 变成浏览标签，顶栏显示本机路径，还能再开标签切回去",
            "杀掉应用后再打开，之前的浏览标签还在；电脑端按整屏缩小，证书取消不再退出页面",
        ),
    ),
    UpdateEntry(
        version = "0.1.44",
        date = "2026-09-13",
        highlights = listOf(
            "开屏不再半路预热网页引擎，星河播完再淡出；底下底栏开屏时不再做实时模糊",
            "打开网页等真正加载完再收进度条；换站不会先露出上一站",
            "选择 HTML：空文件夹不再报没权限，搜索带上当前目录，系统选择器也只收网页文件",
        ),
    ),
    UpdateEntry(
        version = "0.1.43",
        date = "2026-09-13",
        highlights = listOf(
            "开屏再快一截：淡出提前叠上，并在播放里预热网页引擎",
            "点主屏图标打开更快，已打开过的页面不再先闪「正在打开」",
            "本地网页首帧出来就收加载条；从外部打开时等看清内容再问是否加到主屏幕",
        ),
    ),
    UpdateEntry(
        version = "0.1.42",
        date = "2026-09-13",
        highlights = listOf(
            "外部打开 HTML / Markdown 顶栏显示本地路径，并可选择添加到主屏幕",
            "导入本地 HTML：没有「所有文件访问」时不再把下载文件夹误报成空，可改用系统选择器",
            "搜索会带上当前文件夹里的结果；授权后才能自动扫公共目录",
        ),
    ),
    UpdateEntry(
        version = "0.1.41",
        date = "2026-09-12",
        highlights = listOf(
            "选择 HTML 会去小写的微信/QQ 共享目录里找网页文件，大文件夹也不再漏掉后面的 HTML",
            "搜索只按文件名和所在文件夹过滤，不会因为路径里有 storage 就全中",
            "没有存储权限时只看应用自己的目录；权限没变回到前台也不会重新扫一遍",
        ),
    ),
    UpdateEntry(
        version = "0.1.40",
        date = "2026-09-12",
        highlights = listOf(
            "选择 HTML 的进出动画跟设置里选的转场一致，不再自己滑一下",
            "打开后会自动查找下载、文档和常见聊天目录里的网页文件",
            "顶上增加搜索框，按文件名或文件夹过滤，不用自己翻遍整个存储",
        ),
    ),
    UpdateEntry(
        version = "0.1.39",
        date = "2026-09-12",
        highlights = listOf(
            "微信或文件管理器里用其他应用打开 HTML / Markdown 时，可以选择玄览",
            "打开是临时预览，看完回到主屏幕，不会多出一个图标",
            "Markdown 用应用内渲染器显示；不是网页或笔记的文件不会打开",
        ),
    ),
    UpdateEntry(
        version = "0.1.38",
        date = "2026-09-12",
        highlights = listOf(
            "电脑端按桌面宽度整页缩小，再双指捏合放大，不再切过去还是手机排版",
            "主屏编辑收成一条底部菜单栏：完成、计数、左右/上下滑动和批量操作都在这里，底栏导航会隐藏",
            "图标名称单行省略，不再画出格子；开屏前摇再快一截",
        ),
    ),
    UpdateEntry(
        version = "0.1.37",
        date = "2026-09-12",
        highlights = listOf(
            "添加本地 HTML 改为自绘选择页，按系统版本申请存储或所有文件访问，拒绝也不退回系统选择器",
            "本地页把渲染进程打崩后停在可重试错误页，不再自动重开同一份文件把应用带走",
            "站点球跟手移动，只有靠边或横甩才吸附；贴边展开回到松手前的位置，不再飞到正中",
        ),
    ),
    UpdateEntry(
        version = "0.1.36",
        date = "2026-09-12",
        highlights = listOf(
            "新标签页底栏不再像贴底白板；收藏和最近访问对齐，最近访问带图标并去掉重复行",
            "编辑模式「完成」对齐网格；可批量删除、移出或移入文件夹，多选图标能汇聚拖到另一页",
            "主屏双指捏合或平行滑动进入编辑；网页双指不再被下拉抢走，移动站可在设置里打开捏合",
            "开屏前摇更快，收成图标黑洞的一段略慢",
        ),
    ),
    UpdateEntry(
        version = "0.1.35",
        date = "2026-09-12",
        highlights = listOf(
            "存储管理按站显示 Cookie / 离线库等可核实占用，并可定向清除该站数据",
            "站点壳展开球改为半透明双环；下载球可收起，菜单贴在球旁，两边都能隐藏",
            "打开下载文件夹会回退到系统下载页；网页用相机或拍照会先确认再申请权限",
            "「我的」重新分成后台保护、桌面与显示、功能设置",
        ),
    ),
    UpdateEntry(
        version = "0.1.34",
        date = "2026-09-12",
        highlights = listOf(
            "添加网站能再次解析官方图标；解析不到时用常用站点图或标签页图标",
            "导入的单页 HTML 会带上自己的 favicon",
            "浏览新标签页底栏恢复成悬浮玻璃，不再像一张贴底卡片",
        ),
    ),
    UpdateEntry(
        version = "0.1.33",
        date = "2026-09-12",
        highlights = listOf(
            "清缓存会真正删掉网页 HTTP 缓存，不再只统计到、清不掉",
            "存储头条按系统数据加缓存合计，系统报 0 时改用磁盘扫描",
            "清除全部网站数据会清掉现代 Cookie 分区；进存储页会重新统计",
        ),
    ),
    UpdateEntry(
        version = "0.1.32",
        date = "2026-09-12",
        highlights = listOf(
            "站点壳贴边胶囊中间改成竖线，展开是玻璃圆球；菜单和浏览页一样，可收藏、回主屏和看下载记录",
            "页内下载能识别文件链接和 blob 保存，不再只靠跳转新网址",
            "浏览和站点壳菜单可打开下载记录并删除",
            "存储管理会统计共享网站数据，不再全是 0B；清理缓存也会清应用日志",
        ),
    ),
    UpdateEntry(
        version = "0.1.31",
        date = "2026-09-12",
        highlights = listOf(
            "网站弹出的新窗口会变成完整浏览标签，可以再开标签、收藏和查找，登录页不会被原网站盖掉",
            "下载改为系统下载管理器，全应用右侧胶囊显示进度，文件进 Download/PocketWebShell",
            "下载完成后左下角弹出卡片，点按打开系统文件或下载页",
        ),
    ),
    UpdateEntry(
        version = "0.1.30",
        date = "2026-09-12",
        highlights = listOf(
            "打开主页文件夹不再先闪一帧空壁纸，再播展开动画",
            "站点壳辅助球改成灰色斜杠、加大贴边点击区，吸附有滑动手感；开关重开回到右上角",
            "收藏本页、收藏结果和切换桌面版都用确认弹窗说明；桌面版可双指捏合缩放",
            "收藏夹与历史里的网址默认只显示两行",
        ),
    ),
    UpdateEntry(
        version = "0.1.29",
        date = "2026-09-11",
        highlights = listOf(
            "站点壳辅助球改为系统悬浮球交互：点边缘短胶囊到屏幕中间变成玻璃球，滑向左或右再吸回胶囊",
            "点按玻璃球仍打开后退/前进/刷新等动作表",
        ),
    ),
    UpdateEntry(
        version = "0.1.28",
        date = "2026-09-11",
        highlights = listOf(
            "站点壳辅助球加大，贴边后可见体积和点击区域对齐，不再点到一块空白",
            "添加页与浏览空态统一蓝色黑洞标；导入说明改为「本地可用」，浏览提示去掉句号",
        ),
    ),
    UpdateEntry(
        version = "0.1.27",
        date = "2026-09-11",
        highlights = listOf(
            "浏览空态换成可着色的玄览黑洞标，不再用蓝色地球",
            "从主屏打开的网站右上角改为可拖动辅助球：可贴右边隐藏、左甩刷新，点按打开后退/前进/刷新/桌面模式/返回主屏",
            "「我的 → 浏览体验」可关闭站点壳悬浮球；浏览标签页不加这颗球",
        ),
    ),
    UpdateEntry(
        version = "0.1.26",
        date = "2026-09-11",
        highlights = listOf(
            "收藏星标不再改变外链策略；只有站点里显式的「外链在系统浏览器打开」开关才生效",
            "网站壳弹出的登录/OAuth 窗口改为真实浏览器标签承接，不再复用当前页面",
            "存储管理去掉误导的按站清理；清除全部网站数据现在会彻底退出登录并清理站点数据",
            "本地导入页面离开后，同会话不能再读取本地文件；网页弹窗只发给当前可见页面",
        ),
    ),
    UpdateEntry(
        version = "0.1.25",
        date = "2026-09-10",
        highlights = listOf(
            "网页弹窗（提示、确认、输入、离开网站）改为应用内对话框，登录和支付页不再静默失败",
            "存储管理可清除全部网站数据并退出登录；共享默认存储的网站不再显示会失败的按站清理",
            "下载大文件时会话不会被后台池误淘汰",
        ),
    ),
    UpdateEntry(
        version = "0.1.24",
        date = "2026-09-10",
        highlights = listOf(
            "深色模式下「我的」页「设置」标题改为浅色；底部导航改回悬浮磨砂，不再像一块实心长条",
            "后台会话改为单行省略，结束前二次确认；全部会话支持选择、全选和批量结束，并隐藏底栏换成操作条",
            "网页加载增加统一顶栏进度；新标签页改为收藏夹和最近访问；添加页换上品牌标并避免引导文案换行",
            "下拉刷新默认关闭，仅在页面到顶时生效，设置里可打开；网站壳菜单可手动刷新。开屏收束为与 Logo 一致的星核",
        ),
    ),
    UpdateEntry(
        version = "0.1.23",
        date = "2026-09-10",
        highlights = listOf(
            "本地导入资源读取权限绑定当前会话 appId，浏览器/直链不能读取其他本地应用文件，编码路径穿越也会被拒绝",
            "修复 Blob 下载解析、应用级 WebView 外部协议启动、HTTP 错误 loading 收口和后台标签真实窗口 listener 绑定",
            "renderer 崩溃自动恢复限制为一次连续重试，替代 WebView 再次崩溃时进入可观察失败状态",
        ),
    ),
    UpdateEntry(
        version = "0.1.22",
        date = "2026-09-10",
        highlights = listOf(
            "浏览器标签、桌面网站入口、直接链接统一共享单用户 WebView 登录态：在浏览器登录后，从桌面打开同站点会自动复用 Cookie",
            "地址栏与网页外链增加协议校验和 Intent 处理器检查，电话、邮件、地图等外部链接失败时会明确反馈",
            "新增网页定位权限申请；权限结果按当前会话归属，切换标签不会串到其他页面",
        ),
    ),
    UpdateEntry(
        version = "0.1.21",
        date = "2026-09-09",
        highlights = listOf(
            "「全部应用」视图切换按钮移到最右上角，「取消」左移让位；按钮图标换成与全局一致的圆角风格，深色模式下显示为浅色",
            "开屏动画改为文案先出场（淡入加字距收紧），星河随后在下方收束；整体缩小成一小片星河，黑洞白洞增加星尘闪烁、光环明暗不对称等细节，粒子与辉光开销同步下调",
        ),
    ),
    UpdateEntry(
        version = "0.1.20",
        date = "2026-09-09",
        highlights = listOf(
            "「全部应用」右上角新增视图选择：网格、列表·按首字母、列表·按时间三种视图任意切换",
            "列表视图用小图标加完整应用名称的整行布局，应用多时更容易找到目标；按时间排序时最新添加的排在最前",
            "修复深色模式下「全部应用」页面文字是黑色、背景也是黑色导致看不见的问题",
        ),
    ),
    UpdateEntry(
        version = "0.1.19",
        date = "2026-09-09",
        highlights = listOf(
            "开屏动画升级为黑洞/白洞：粒子螺旋坠入吸积环、核心闪耀收场，浅色为暖金白洞、深色为蓝紫黑洞，时长约 2.6 秒",
            "导出成功后直接打开系统分享面板，弹窗显示文件名、大小和保存路径；文件同时存入系统「下载/PocketWebShell/」目录，可在文件管理器中找到",
            "存储管理进入网站详情改用统一页面转场动画",
            "修复存储统计可能全部显示 0：占用总览改用与系统设置一致的数据源，独立 Profile 设置失败不再静默，按站统计更可靠",
        ),
    ),
    UpdateEntry(
        version = "0.1.18",
        date = "2026-09-09",
        highlights = listOf(
            "我的 → 数据与存储：新增存储管理与数据管理。存储管理可查看已统计占用、可清理缓存与按网站占用排行，进入网站详情可清理缓存（只删临时资源，保留登录数据），也可一键清理全部可清理缓存",
            "数据管理支持三种导出：仅导出网址、导出网址和必要数据（原位置恢复，冲突则拒绝）、导出全部数据（实验性，含可迁移运行数据），导出后直接调起系统分享",
            "数据导入支持 .pws 备份/分享文件：追加导入不重名覆盖，重名自动编号；主页长按文件夹的菜单新增「分享文件夹」，接收方从数据管理导入即可",
        ),
    ),
    UpdateEntry(
        version = "0.1.17",
        date = "2026-09-09",
        highlights = listOf(
            "新增开屏动画：冷启动时彩色粒子汇聚成桌面图标点阵，配合「把互联网 收进你的桌面」文案，约 1 秒播完；有保持中的网页会话时自动跳过，直接回到网页",
            "文件夹支持重命名：展开后点击顶部标题即可改名，长按文件夹的菜单也新增「重命名」",
            "文件夹打开状态下长按成员图标可拖拽调整顺序，松手落子并保存",
            "文件夹展开/收起动画放慢并改为显式缩放淡入淡出，过渡更顺滑",
        ),
    ),
    UpdateEntry(
        version = "0.1.16",
        date = "2026-09-07",
        highlights = listOf(
            "解散文件夹新增二次确认弹窗，确认后才解散，应用本身不会被删除",
            "长按应用 Logo 的菜单新增「复制应用链接」，一键复制网址到剪贴板",
            "设置页「后台会话」最多显示前 3 条；更多时提供「全部会话」入口，进入独立页面统一管理全部保活会话",
        ),
    ),
    UpdateEntry(
        version = "0.1.15",
        date = "2026-09-07",
        highlights = listOf(
            "清理旧控件与重复实现：统一表单、图标、选择行、弹窗和面板；添加网页不再显示旧字号滑杆，保留已有站点字号",
            "设置 → 外观与主题新增字体与字号：MiSans、系统字体、Noto Sans SC，界面字号90%至130%，不改变网页缩放",
            "浏览器精简为一行菜单、地址、标签；进入网页后导航立即收成玻璃小球，可拖至侧边，点击召回完整导航和地址栏",
            "恢复添加网址自动抓取网站图标作为应用 Logo，无图标的站点仍显示首字符",
            "修复深色模式下浏览器菜单按钮发灰看不清、浏览网页时内容区域闪烁的问题",
            "Playbook 改由应用层聚合所有模块的真实生产组件，涵盖常态、空态、错误、编辑、菜单和弹窗，演示不读写业务数据",
            "修正浏览会话原生视图绑定、安全区及旧网页壳重复会话问题；添加输入、图标导入和日志过滤补充状态保护",
        ),
    ),
    UpdateEntry(
        version = "0.1.14",
        date = "2026-09-06",
        highlights = listOf(
            "iOS 风格主屏：全屏流线壁纸、四图标玻璃 Dock、约束自适应网格、白色阴影标题、搜索/页码胶囊与九宫格文件夹预览",
            "统一玻璃浮层、情境菜单、分组设置、添加页面与浏览器工具栏；保留原有手势、排序和会话隔离",
            "性能重构：壁纸与 Dock 由应用壳独立管理，非编辑状态停用抖动动画，主题只订阅外观字段，模糊半径限制在 24dp 内",
            "修复照片取色旧色残留与超长图片采样，回收临时位图；系统安全区与浅/深色状态栏同步适配",
            "增加网格、图片采样和端侧视觉/导航回归，覆盖多页桌面、紧凑宽度、文件夹与深色模式",
        ),
    ),
    UpdateEntry(
        version = "0.1.13",
        date = "2026-09-02",
        highlights = listOf(
            "修复翻到左右边缘悬停开新屏“没反应”：拖拽会话上移到根级容器（对齐 Launcher3），翻页后不再被页面重组打断，左右两侧悬停约 1 秒均可稳定开新屏",
            "修复边缘悬停翻页状态机：翻页后再次悬停可连续跳页（旧实现第二次翻页永不触发）",
            "内部重构：主页手势层拆分为独立模块（交互状态集中管理 + 手势分层），并新增 7 条真机手势自动化回归测试",
        ),
    ),
    UpdateEntry(
        version = "0.1.12",
        date = "2026-09-02",
        highlights = listOf(
            "修复拖到末屏右边缘无法开新屏：临时屏状态不再中断翻页动画（旧实现自我取消），悬停约 1 秒稳定开出新屏",
            "首屏左边缘同样支持开新屏：悬停约 1 秒在左侧插入新首屏，落子后其余页面整体后移一页",
            "编辑模式改为即按即拖：图标位移超触摸阈值直接拖拽（对齐 iOS/HyperOS jiggle），原地松手仍是勾选，全程不弹长按菜单",
        ),
    ),
    UpdateEntry(
        version = "0.1.11",
        date = "2026-09-02",
        highlights = listOf(
            "桌面滑动模式可切换：左右翻页（默认）/ 上下滚动（所有页摊平成一条纵向列表，空槽保留，数据模型不变）",
            "上下滚动模式拖拽：手指悬停顶部/底部边缘自动滚动并重解析落点；页码指示器仅翻页模式显示",
            "拖到最后一屏右边缘约 1 秒自动开新屏：追加临时空白页可落子，松手未落子自动裁掉",
            "新增「全部应用」浮动入口：右下角半透明图标，可自由拖动（位置持久化），点按打开全部应用抽屉，长按可隐藏",
            "全部应用抽屉：按首字母/拼音首字母分区（A→Z、# 殿后），右侧字母索引条支持点按与按住滑动跳转，当前字母气泡提示",
            "空白处长按弹菜单：一键进入编辑模式、切换全部应用入口显隐；设置页同步新增对应开关",
        ),
    ),
    UpdateEntry(
        version = "0.1.10",
        date = "2026-09-01",
        highlights = listOf(
            "日志改为 Room 持久化存储：进程结束后仍可回看，数据库 v2→v3 显式迁移不影响存量数据",
            "日志分 INFO/WARN/ERROR 三级，新增崩溃捕获：未捕获异常堆栈自动落盘（crash 标签）",
            "新增监测点：页面加载与失败、SSL 错误（只记 host）、标签新建/切换/关闭、会话开关、保活服务启停、添加网站成败、应用启动版本",
            "日志查看页重构：滚动到底自动加载更多（每页 30 条），不再受 500 条内存上限约束",
            "复制/分享升级为完整导出：带版本与设备信息头部，分享生成 TXT 文件经 FileProvider 发出",
            "查看页新增红色清空按钮（二次确认），一键清空全部日志",
        ),
    ),
    UpdateEntry(
        version = "0.1.9",
        date = "2026-09-01",
        highlights = listOf(
            "修复长按菜单永远钉在左上角：Popup 里的全屏遮罩把内容尺寸撑成整个屏幕，定位钳制失效；移除内置遮罩，改用 Popup 自带的外部点击关闭",
            "菜单现在稳定地以按压点为中心、在图标附近弹出",
        ),
    ),
    UpdateEntry(
        version = "0.1.8",
        date = "2026-09-01",
        highlights = listOf(
            "修复长按图标误触打开应用：手势重构为单通道状态机，长按与点击互斥，长按原地松手只弹菜单",
            "长按弹菜单后移动超过 16dp 才进入拖拽（对齐 Launcher3 两段式阈值），小位移松手菜单保留",
            "修复远端 logo 加载失败只剩白底无字：失败时回退首字母色块，首字母配色适配深色主题",
            "官方 logo 贴边裁剪：等比放大铺满圆角图标，不再内缩留白边",
            "长按菜单新增「重命名」「更改图标」「强制刷新」（支持上传本地图片作图标）",
            "情境菜单定位重写：以按压点居中展开、垂直优先向上，边缘自动钳制，动画锚点指向按压点",
        ),
    ),
    UpdateEntry(
        version = "0.1.7",
        date = "2026-09-01",
        highlights = listOf(
            "修复标签页串台：进度/标题/返回状态改为按标签独立记录，多标签互切不再互相污染",
            "修复后台标签回调丢失：新增随会话存活的持久监听器，后台加载的标题/历史不再丢",
            "会话池改真 LRU 且激活标签受保护：不再误杀正在显示的标签，淘汰后返回栈可恢复",
            "浏览器标签、桌面网站入口、直链和本地导入统一使用默认共享 Profile：Cookie、LocalStorage 和登录态全局复用；本地文件目录仍按 appId 严格隔离",
            "移动版 UA 换成标准 Chrome Android：修复 Google 登录等站点拒绝服务的问题",
            "切走的后台标签暂停音视频播放；关闭标签彻底清理会话与桌面模式记忆",
            "修复新窗口会话泄漏：不再遗留无人销毁的孤儿会话",
        ),
    ),
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
    var shown by rememberSaveable { mutableIntStateOf(UPDATE_PAGE_SIZE) }
    val visible = updateEntries.take(shown)
    DetailPage(title = stringResource(R.string.me_updates), onBack = onBack) {
        visible.forEachIndexed { index, entry ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            AppCard(modifier = Modifier.fillMaxWidth()) {
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
        if (shown < updateEntries.size) {
            TextButton(
                onClick = { shown = (shown + UPDATE_PAGE_SIZE).coerceAtMost(updateEntries.size) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.me_updates_load_more))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
