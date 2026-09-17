# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow the rules in `docs/VERSIONING.md`.

## [0.1.56] - 2026-09-17

开屏动画极致流畅优化：实现零重组（Zero-Recomposition）绘制管线与文本免测量 GPU 变换，消除首帧卡顿；全面验证应用内 GitHub Release 自动更新检查闭环。

### Performance

- 开屏零重组架构重构（`AppSplash.kt`）：天体微标迁移至 `drawWithCache` 绘制管线，画笔、渐变与几何尺寸一次性静态缓存，旋转动画偏移通过绘制闭包捕获，完全消除 Compose 重组开销。
- 文本排版免重新计算：固定衬线艺术字字距，展开动效改由 GPU 硬件加速的 `graphicsLayer`（缩放、透明度与垂直平移）统一驱动，彻底消除每帧 CPU 文本测量（TextLayoutResult）造成的微卡顿。
- 全量闭环验证应用内「检查新版本」端到端升级流程。

### Testing

- `:app:compileDebugKotlin :app:assembleDebug testDebugUnitTest`
- 模拟器手测：开屏 120fps 满帧无重组转场，应用内检查更新识别 `0.1.56` 并弹出升级下载。

## [0.1.55] - 2026-09-17

开屏全面升级为浅色白洞与深色黑洞天体动效，配以唯美艺术字与光引过渡；本地 HTML 扫描突破局限，深度全覆盖 Download 及其所有子目录；桌面多选编辑交互升级为 iOS 级操作栏与二级抽屉，彻底杜绝文字截断。

### Added

- 浅色白洞与深色黑洞开屏：Android 12+ 系统启动窗移除深色底框，分别采用浅色白洞与深色黑洞透明矢量微标；Compose 动态开屏升级为耀金白洞与深空黑洞引力动效，引入衬线艺术字与字距光引展开，60/120fps 全程硬件加速无缝淡出。
- 本地 HTML 全路径深度扫描：修复 Android 11+ OEM 存储卷回退，深度扫描 `Download` 根目录及其任意下级子目录（浏览器下载、各类网盘、聊天软件等）与公共目录，兼备并发协程、深度控制与超时保护，大幅提升扫描率与响应速度。
- 桌面多选 iOS 级操作坞：解耦桌面整体翻页模式配置与批量选中操作；多选状态下重构为清晰的计数胶囊与操作坞，文件夹批量操作整合为二级抽屉与弹窗，彻底根除「移出文...」等文字截断问题。

### Testing

- `:feature:add:testDebugUnitTest :feature:home:testDebugUnitTest :app:assembleDebug`
- 模拟器手测：浅色与深色模式冷启动开屏动效、HTML 选择器多级子文件夹深度发现、主页多选图标操作栏与二级文件夹抽屉。

## [0.1.54] - 2026-09-17

设置页可检查 GitHub 最新正式版；已是最新会说明，有新版本则弹出确认后再打开下载链接。不引入自建后端或云端存储。

### Added

- 我的 → 关于：新增「检查新版本」。对照 GitHub `releases/latest` 的 tag（`v<versionName>`）判断是否有正式版可更新。
- 已是最新：行尾显示「已是最新」，并弹出确认框。
- 发现新版本：复用 `AppConfirmDialog` 询问是否下载；确认后打开 APK 的 GitHub 下载地址（没有 APK 资产则打开 Release 页面）。
- 检查失败：可改开官网 `https://xuanlan.1037solo.com`。

### Testing

- `:core:data:testDebugUnitTest :feature:home:testDebugUnitTest :app:assembleDebug`
- 手测：点检查新版本（当前若已发布同号则为最新；人为改低本地 versionName 可验证下载弹窗）。GitHub 不可达时的失败弹窗。

## [0.1.53] - 2026-09-17

开屏重构为原生级丝滑微动效：废弃主线程高频粒子与黑洞计算，采用玄览星核 BrandMark 弹簧微入与优雅淡出，底层主屏并行预热，彻底消灭首帧卡顿。

### Changed

- 彻底重构开屏（`AppSplash.kt`）：移除复杂的黑洞引力与粒子计算，对齐苹果极简克制基准，以纯粹的玄览星核（BrandMark 太极环与鎏金星核）作为视觉主体。
- 改为双层并行预热流水线：底层的 `MainScaffold` 在开屏遮罩下立即异步静默完成初次布局与数据读取（期间禁用实时毛玻璃），开屏淡出时主线程无冲突，达成 10/10 满帧丝滑无卡顿。
- 全程动效由 GPU 硬件加速 `graphicsLayer`（Scale + Alpha）驱动，缩短总展示时长至约 550ms，视觉体验轻快、自然且高质感。
- 完善 Android 原生 SplashScreen 主题契约，窗口冷启动到应用内首帧平滑过渡。

### Testing

- `:feature:home:testDebugUnitTest :app:assembleDebug`
- 手测：冷启动无卡顿、无黑白闪烁，星核微动效流畅，淡出直接呈现完整主页。

## [0.1.52] - 2026-09-14

开屏按最终流程定稿：字幕和粒子一起上，黑洞期间字幕不收，最后整层一起淡出再进主页。

### Fixed

- 开屏不再先把字拿掉再播黑洞。字和粒子同时出现，黑洞展示期间字幕一直在；播完后字幕、黑洞、遮罩同一层淡出，然后才进主页。
- 空主页不再叠「把喜欢的网站放在主屏幕」文案，避免和开屏字幕叠在一起。

### Testing

- `:app:assembleDebug`
- 手测：冷启动字不提前消失、结束时整层一起收、空主页不再出现那句广告语。

## [0.1.51] - 2026-09-14

开屏整段重写：文案、黑洞、品牌标按顺序播放，不再共用一条会打架的进度。

### Fixed

- 开屏标题和标语单独淡入、停住、淡出后才从树上拿掉；内爆成品牌标时已经没有字。主页在文字消失之后才组。
- 去掉中文字距动画和多层透明度相乘，避免字看起来乱、动画完了字还在。

### Testing

- `:app:compileDebugKotlin :app:assembleDebug`
- 手测：冷启动开屏字和黑洞是否同步、结束后有没有残留标题。

## [0.1.50] - 2026-09-14

开屏按单一时间轴重写；更新日志先显示 20 条；浏览历史按时间折叠分组并加上搜索。

### Fixed

- 开屏动画整段重写：文案、黑洞和淡出共用一条时钟，标题不再单独动画，避免收不干净或乱闪。

### Changed

- 项目更新日志默认展示最近 20 条，底部「加载更多」。
- 浏览历史按今天 / 昨天 / 前天 / 一周内 / 一个月内 / 更早分组，可折叠；顶上增加搜索。收藏夹列表不变。

### Testing

- `:feature:browser:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
- 手测：冷启动开屏文案与黑洞同步、更新日志加载更多、历史分组折叠与搜索。

## [0.1.49] - 2026-09-13

开屏标题随黑洞收掉；网站图标跟浏览器标签页同源；收藏/历史显示各站自己的图标；历史分页加载；存储卡片用弹簧撑开。

### Fixed

- 开屏文案在黑洞内爆前收掉，不再在动画结束后还停半秒。
- 单文件 HTML 会用页面里的远程 favicon / 小体积 data URI，不再只认同目录本地文件。
- 收藏夹和历史左侧改为该站图标，不再共用星星/地球标。
- 历史默认先加载 20 条，滑到底再加一页，避免一次铺开卡顿。
- 存储占用卡片、确认框和状态提示从细点弹簧撑开，和全屏 iOS 动效一致。

### Changed

- 展示层图标兜底改为站点自己的 `/favicon.ico`，不再走 Google s2。
- Room v7：`history.iconUrl`。浏览页收到 touch icon 时写回历史。

### Testing

- `:core:data:testDebugUnitTest :feature:browser:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
- 手测：冷启动标题收掉、导入单页 HTML 图标、历史下滑加载、存储管理卡片撑开。

## [0.1.48] - 2026-09-13

开屏动画播完再组主页，减轻卡顿；外部/主屏 Markdown 改为全屏站点壳加悬浮球，去掉顶栏，支持长按选中，并用原始文件名。

### Fixed

- 开屏黑洞播完、主壳在最后一帧底下组好之后再淡出，粒子从 32 收到 12，去掉星尘、拖尾和每帧 `dp.toPx()`，避免和主页抢 60 帧。
- Markdown 打开页去掉顶部导航栏，改为全屏站点壳，并使用与网页应用相同的悬浮球（文档模式关掉收藏/桌面版，临时文件可「制作应用」）。
- Markdown 渲染包在系统选区容器里，长按走手机原生选中，不再被顶栏或手势拦截。
- 打开 Markdown 时用原始文件名作标题；微信等内容提供者路径拿不到名字时不再用垃圾段。
- Markdown 文档图标左右略加宽，主屏字标从 0.62 收到 0.70。

### Changed

- 外部 Markdown 不再进浏览标签；已保存的主屏 Markdown 同样全屏打开。外部 HTML 仍走浏览标签。

### Testing

- `:feature:viewer:testDebugUnitTest :core:designsystem:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
- 手测：冷启动开屏流畅度、外部打开 MD 全屏/悬浮球/长按选中/文件名、主屏 MD 图标宽度。

## [0.1.47] - 2026-09-13

开屏统一成黑洞并缩短一半；外部打开的 Markdown 用专用文档图标，不再和本地 HTML 共用代码块标。

### Fixed

- 外部打开或加到主屏的 Markdown 使用 Markdown 文档图标（圆角文档里的 M 与下箭头），本地 HTML 仍用代码块图标。
- 开屏去掉浅色白洞分支，浅深都走黑洞坍缩；时长约一半，主页更早叠上，结尾少卡一下。

### Changed

- 开屏粒子从 48 收到 32，星尘从 16 收到 8，仍是单 Canvas、无实时模糊。

### Testing

- `:core:model:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:data:testDebugUnitTest :app:assembleDebug`
- 手测：冷启动开屏、外部打开 MD 的标签与主屏图标、本地 HTML 图标未改。

## [0.1.46] - 2026-09-13

开屏首秒不再和主页抢主线程；外部 HTML/MD 按真实本机路径去重；浏览菜单补上「制作应用」并可滚动；悬浮球中间改成 3/4 实心点。

### Added

- 外部再次打开同一路径的 HTML/MD 时，复用已打开的浏览标签，或直接打开已加到主屏的本地应用。Room v6 为本地应用与标签条记住规范化源路径。
- 浏览菜单「当前网页」分组增加「制作应用」，点过「仅查看」之后也能再加到主屏幕。

### Fixed

- 冷启动开屏前 1.6 秒不再组主页网格/壁纸/标签恢复，粒子从 110 收到 48，去掉径向渐变辉光。
- 外部文件地址栏不再显示 `raw%3A…` 或 `tmp-` 随机段，能解析时显示 `/storage/emulated/0/…`（与电脑上的完整路径对应）。
- 浏览菜单在接近全屏的 MD 页上改为固定顶栏 + 内部滚动，底部项可以滚到。

### Changed

- 浏览顶栏左右各收 16dp，MD 正文按设计间距留白。
- 站点壳悬浮球外圈仍是 48dp 玻璃盘，中间实心点直径 36dp（3/4）；贴边胶囊和手势阈值未改。

### Testing

- `:core:data:testDebugUnitTest :feature:add:testDebugUnitTest :feature:browser:testDebugUnitTest :feature:viewer:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
- 手测：冷启动开屏前一秒、同一 MD 打开两次、制作应用、菜单滚动、悬浮球中间点。

## [0.1.45] - 2026-09-13

主屏应用可选择新窗口是否跳到浏览页；外部 HTML/MD 复用浏览标签；杀进程后标签条保留；悬浮球更小并带保活分屏；电脑端按 980 全屏缩小，证书取消不再离开页面。

### Added

- 「我的 → 悬浮球与手势」增加「新窗口打开浏览页」；添加页可按站覆盖或跟随全局。关闭后，`target=_blank` 覆盖当前站点壳并清掉返回栈。
- 外部打开的 HTML / Markdown 成为普通浏览标签：顶栏显示本机路径，底栏与标签切换器复用现有组件，可再开新标签后切回去。
- Room v5：`browser_open_tabs` 保存打开中的标签与当前选中；杀进程后清单还在。
- 站点壳悬浮球菜单顶部增加已保活应用的小分屏条，点一下切换到后台页。

### Fixed

- 浏览里切电脑端不再连 reload 两次，并补上 `setInitialScale(viewWidth * 100 / 980)`，与站点壳、系统浏览器的桌面站一致。
- 站点壳里切电脑端会写回该应用的 Room 设置。
- 证书失败点取消只关掉对话框，不再关标签或退出站点壳。从不默认放行坏证书。
- 系统返回在浏览页不能后退时回到主屏，标签继续挂着。

### Changed

- 站点壳悬浮球外圈 56dp → 48dp，内高光更大；贴边胶囊与手势阈值未改。
- 外部打开不再整页替换成独立 Viewer。

### Testing

- `:core:data:testDebugUnitTest :core:webengine:testDebugUnitTest :feature:add:testDebugUnitTest :feature:browser:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
- 手测：主屏 APP 跳转设置、微信打开 MD 后再建标签切回、杀进程后标签还在、悬浮球切保活页、浏览里电脑端全屏缩小、切电脑端后的证书对话框。

## [0.1.44] - 2026-09-13

重审 0.1.41–0.1.43：撤回假预热和过早收条，并修本机检索、外部打开加主屏的几处实锤问题。

### Fixed

- 开屏不再在动画中途新建并立刻销毁 WebView；淡出等星河/闪光/品牌标播完再走。
- 开屏期间底栏改静态玻璃，不再继续做 live Haze；外部打开文档会立刻拆开屏。
- 冷启动清临时目录只删超过 5 分钟的孤儿，避免和正在写入的 `tmp-*` 抢删。
- 换站不再因为池里有另一个会话而露出上一站的壳；`onPageCommitVisible` 不再当成加载完成。
- 无 appId 的 URL 启动按规范化匹配（去尾斜杠、host 大小写、www），重复 URL 取最早创建的那条。
- 不存在的「文档」等目录显示为空，不再报没权限。
- 文件夹内搜索会带上相对路径；系统选择器导入与文件导入一样只收 HTML 并限 20MB。
- 外部打开 Markdown 等内容出来后再问是否加主屏；HTML 等到页面结束且不是空白页再问。
- 已渲染的本地保活页再进时会补登记前台服务。

### Changed

- 开屏整段略缩短（2280ms + 180ms 淡出），深浅色窗口底对齐，减轻系统启动窗闪白。
- 外来预览 HTML 改走默认 Profile，与主屏打开同一份登录态。

### Removed

- 未使用的 `loadFollowingLinkPolicy` 与选择页 `isImporting` 死字段。

### Testing

- `:core:data:testDebugUnitTest :feature:add:testDebugUnitTest :feature:viewer:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
- 冷启动开屏、连点两个主屏图标、授权后点空「文档」、进微信目录搜文件夹名、系统选择器导入、微信打开 HTML/MD 加主屏时机，为手测清单。

## [0.1.43] - 2026-09-13

交互更顺：开屏少等一会儿，点主屏图标打开网页更快，本地页不再每次重进都清池。

### Changed

- 开屏淡出与品牌收尾重叠，并在播放中预热一次 WebView；启动时清临时文档改到后台线程。开屏期间关掉底下实时模糊。
- 点主屏图标直接用格子里的 id/url 打开，不再先查一遍数据库才切壳。
- 池里已有该站会话时不再闪「正在打开」；首帧可见（`onPageCommitVisible`）就收起加载条。
- 本地 HTML 只在第一次真正导航前清未保护会话；外来打开的加主屏对话框等首帧后再问。
- 顶栏进度收束略加快；按 URL 查找改为 DAO 单条查询。

### Removed

- 未使用的站点壳 `openWindow` 旁路。

### Testing

- `:feature:add:testDebugUnitTest :feature:viewer:testDebugUnitTest :app:assembleDebug`（及壳相关模块编译）。
- 冷启动开屏、点本地/远程快捷方式、再进同一本地页、外来 VIEW HTML 加主屏时机，为手测清单。

## [0.1.42] - 2026-09-13

外部打开文档更像浏览页，并能加到主屏幕；本机 HTML 选择页不再把「没权限」误报成「没有文件」。

### Fixed

- Android 11+ 没有「所有文件访问」时，下载/QQ 等目录不再假装空文件夹；会提示缺权限，并提供系统选择器。
- API 30+ 公共列举改为必须有 All-files；搜索会合并当前目录结果，不再只搜已发现列表。
- 大目录里排在后面的 HTML 不会被文件夹条数裁掉。

### Added

- 外部打开 HTML/MD 顶栏改为浏览样式地址栏，尽量显示真实本地路径。
- 打开成功后弹出确认框，可把该文档添加为主屏幕快捷方式（Markdown 也会持久化并可从主屏打开）。

### Testing

- 更新权限门闩与目录列举单测。
- `:feature:add:testDebugUnitTest :feature:viewer:testDebugUnitTest :app:assembleDebug`。
- 授权 All-files 后 Download/QQ 应列出 HTML；未授权应看到权限说明与系统选择器；微信打开后可添加主屏，为手测清单。

## [0.1.41] - 2026-09-12

选择 HTML 的自动查找按常见聊天目录补全，搜索不再误中整卷路径。

### Fixed

- 微信/QQ 共享目录同时探测小写 `tencent/…`；大目录里排在后面的 HTML 不再被目录截断漏掉。
- 搜索只匹配文件名和相对位置，输入 `storage` / `emulated` 不会命中全部结果。
- MediaStore 查询带条数上限；没有公共列举权时只扫应用私有目录。权限没变的 `ON_RESUME` 不再重扫。
- 从选择页返回时保留最后一帧，避免转场空白闪一下。

### Changed

- 未搜索时「找到的 HTML」默认只展示最近 30 个；文件夹里输入关键词改看全局发现结果。

### Testing

- 补种子路径、大目录后置 HTML、相对路径搜索单测。
- `:feature:add:testDebugUnitTest :app:assembleDebug`。
- 授权后落地页约 2 秒内出现下载/微信保存的 HTML，搜 `weixin` 只滤内存，为手测清单。

## [0.1.40] - 2026-09-12

选择 HTML 页跟设置里的转场走，并会自动找出本机网页文件。

### Fixed

- 进入选择 HTML（以及添加页其它子页）不再写死滑入。设置里选淡入、缩放或无动画时，这里用同一套 `AppMotion.detailEnterFor`。

### Added

- 落地页增加搜索框，按文件名和所在目录即时过滤，不每敲一次就扫盘。
- 打开选择页后在下载、文档、微信/QQ/Telegram 等常见目录做有上限的检索，并先问 MediaStore 索引。「找到的 HTML」按最近修改排列。

### Testing

- 新增检索边界、跳过 `Android/data`、搜索分词单测。
- `:feature:add:testDebugUnitTest :app:assembleDebug`。
- 改转场设置后进出选择页、本机有 HTML 时落地页能列出，为手测清单。

## [0.1.39] - 2026-09-12

系统「用其他应用打开」可把 HTML / Markdown 临时挂到玄览里看，不写进主屏幕。

### Added

- 微信、文件管理器等通过 `VIEW` / `SEND` 把 HTML 或 Markdown 交给本应用时，会出现「用玄览打开」。
- 新增独立模块 `:feature:viewer`：HTML 拷到 `localapps/tmp-*` 后走现有本地 AssetLoader；Markdown 用 Compose 渲染器在内存里显示。
- 离开查看层即销毁 WebView 并删除临时目录；启动时清掉孤儿 `tmp-*`。不会写入 Room，也不会占主屏格子。

### Changed

- 运行时仍只放行 html/htm/md（含微信常见的 `octet-stream` 再嗅探 HTML），超过 20 MB、安装包和可执行文件直接拒绝。

### Testing

- 新增扩展名 / MIME / 嗅探、Intent 解析、拷贝上限、孤儿清理、Markdown 去远程图单测。
- `:feature:viewer:testDebugUnitTest :core:webengine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`。
- 微信/文件管理器列表能否看到「玄览」、看完回主屏没有新图标，为手测清单。

## [0.1.38] - 2026-09-12

电脑端按桌面宽度整页缩小后再捏合；主屏编辑收成一条 iOS 底栏；开屏前摇再加快。

### Fixed

- 开启电脑端后把 viewport 改成 Chrome 同款 980px 布局宽，并去掉挡住整页缩小的 `initial-scale`。页面先变成很小的桌面布局，才能双指捏合放大；切回手机不再留下 980。本地导入仍不改宽度。
- 主屏编辑不再把「完成」放在右上、四动作叠在网格底、底下再露四图标 Dock。所有操作收进一条替换 Dock 的玻璃菜单栏。
- 图标标题单行省略并限制在槽宽内，勾选角标不再画出邻格。

### Changed

- 编辑底栏可切换左右翻页 / 上下滚动，写入既有桌面滑动设置。
- 开屏文案前摇再缩短一半。

### Testing

- 新增桌面 viewport 改写、编辑底栏预留与「完成」在下半屏的仪器断言。
- `:core:webengine:testDebugUnitTest :feature:home:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`。
- 电脑端整页变小再捏合、编辑态 Dock 消失、标题不溢出为手测清单。

## [0.1.37] - 2026-09-12

本地 HTML 改为自绘选择页；超大单页崩了不再把应用一起带走；站点球跟手、只有靠边才吸附。

### Added

- 添加页用自绘「选择 HTML」代替系统文档选择器。API 29–32 申请读取存储；30+ 需要时走「所有文件访问」设置链。拒绝权限仍留在选择页，不回退系统选择器。
- 「我的 → 后台与通知」增加「文件访问」入口，回到前台会刷新授权状态。

### Fixed

- 本地导入页渲染进程崩溃后，站点壳换新 WebView 并停在可重试错误态，不再自动重载同一文档把进程打崩。
- 打开本地页前会让出其它未保护会话，并关掉该会话的后退缓存；保活服务等到首页加载完成且未崩溃再启动。
- 站点球中间松手停在原处，只有靠近边缘或横甩才贴边；从贴边展开回到松手前的位置，不再飞到屏幕正中。

### Changed

- 站点球视觉更接近 iOS 辅助触控：展开是玻璃圆，贴边是短线胶囊。
- 本地资源响应带上 `Content-Length`；拦截线程不再读 `WebView.url`；导入图标只读 HTML 文件头。

### Testing

- 新增 HTML 选择权限/列目录/`importFiles`、渲染恢复闸门、站点壳 `-99` 失败态、站点球 `STAY` 几何单测。
- `:feature:add:testDebugUnitTest :core:webengine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`。
- 选择页授权与列目录、小 HTML 能开、超大解剖页崩后进程仍在、站点球中间松手不吸边为手测清单。

## [0.1.36] - 2026-09-12

新标签页底栏不再像贴底白板；主屏编辑、多指手势和网页缩放一并修好。

### Fixed

- 新标签页把底栏预留改到滚动内层，白色最近访问卡不再被裁在玻璃胶囊上沿，看起来像一张贴底卡片。
- 起始页收藏与最近访问对齐同一左边距；最近访问显示站点图标和主机名，并按显示键去掉重复行。
- 编辑模式「完成」对齐网格 header，选中计数移到左上；底栏改为删除、移出文件夹、移入文件夹和全选。
- 主屏双指捏合或平行滑动可进入编辑模式；外扩退出。原先第一指落下就结束检测，第二指永远进不去。
- 选中多个图标后长按其中一个会汇聚成组，可拖到另一页或已有文件夹；多选拖到普通图标只重排，不新建文件夹。
- 网页开启下拉刷新时，双指不再被当成下拉。桌面模式默认允许捏合；移动站可在「设置 → 悬浮球与手势」打开强制缩放。
- 开屏文案前摇更快，后面收成图标黑洞的一段略慢。

### Changed

- 浏览壳只在需要玻璃底栏时挂实时模糊源，加载进度不再带动整个底栏重组。

### Testing

- 新增起始页 `displayKey` / `host`、多指检测器、批量落子与组拖拽、viewport 改写脚本单测。
- `:feature:home:testDebugUnitTest :feature:browser:testDebugUnitTest :core:webengine:testDebugUnitTest :app:assembleDebug`。
- 新标签页底栏、双指进编辑、多选拖页、移动站捏合为手测清单。

## [0.1.35] - 2026-09-12

按站存储改为可核实证据和定向清除；辅助球、下载打开和设置分组一并收口。

### Fixed

- 存储管理不再把同一份共享数字塞进每一行。站点行只显示 Cookie / IndexedDB / 配额等可核实项，未测显示「—」；无法按站拆分的共享数据单独统计。
- 支持 `DELETE_BROWSING_DATA` 时，「清除该站点数据」按 eTLD+1 删除该站 Cookie、网络缓存和脚本可读存储，不再假装 `profiles/<appId>` 沙箱。
- 站点壳展开球改为半透明双环，贴边胶囊仍保留竖线；菜单可隐藏辅助球。
- 下载球可向右收起，点按菜单贴在球旁；纵向拖动不再被误收起。
- 「打开文件夹」在 Documents URI 被系统拒绝后会落到系统下载页或文件/分享，API 29–32 会申请读取公共下载目录。
- 网站申请摄像头/麦克风先确认再要运行时权限；`<input capture>` 可拍照或录像。
- 有真实 WebView 时底栏不再对网页做实时模糊；托管页 `text/plain` 仍解析图标，manifest 相对路径按 manifest URL 解析。

### Changed

- 「我的」分成后台保护、桌面与显示、功能设置（站点球、下载球、自动收起、下拉刷新）和 WebView 引擎信息。

### Testing

- 新增 IndexedDB 目录解析、按站归属、按站清除合约，以及下载收纳几何、存储权限、网页相机权限单测。
- `testDebugUnitTest :app:assembleDebug`；登录后清该站、打开文件夹、网页拍照、展开球与下载收纳为手测清单。

## [0.1.34] - 2026-09-12

修复添加网站图标解析和新标签页底栏玻璃样式。

### Fixed

- 添加网站会再读 apple-touch / 页面图标，失败时用常用站点官方图或 Google 标签页图标，不再整段放弃只剩字母块。
- 导入的单页 HTML 会读取同目录里的 `favicon` / `apple-touch-icon`。
- 浏览新标签页的底栏改回和其他 Compose 页一样的悬浮玻璃 Dock，不再像贴底卡片；打开真实网页后仍用可收起的浏览 Dock。
- `promo-video` 与 `xuanlan-AIMFllyYS` 不进入 Git 追踪。

### Testing

- 新增页面图标排序与本地 HTML 图标路径单测。
- `testDebugUnitTest :app:assembleDebug`；添加 GitHub/单页 HTML、新标签页底栏为手测清单。

## [0.1.33] - 2026-09-12

补上 0.1.32 存储账本还没砍到的真实 HTTP 缓存与头条口径。

### Fixed

- 清缓存会删除 `cache/WebView/`（含 `Default/HTTP Cache`），并在关会话前对活动 WebView 调用 `clearCache(true)`；不再只数到这几 MB、按钮却清不掉。
- 头条占用改为系统 `dataBytes + cacheBytes`，系统为 0 时回退磁盘遍历，不再被单独的 `dataBytes` 或 `0` 压成 0B。
- 「清除全部网站数据」补删 `Network/`、`Storage/`、`Shared Storage/`，现代 Cookie 分区不会留下。
- 磁盘遍历遇到不可读子目录只跳过该树，不再整棵归零；进入存储页或回到前台会强制重扫。

### Testing

- 新增 `walkFiles` 单测，并锁住「系统总量为 0 不覆盖遍历分桶」。
- `testDebugUnitTest :app:assembleDebug`；清缓存后 HTTP 缓存应下降、登录仍在为手测清单。

## [0.1.32] - 2026-09-12

站点壳悬浮球、页内下载、下载记录和存储占用统计的一组修复。

### Fixed

- 站点壳贴边胶囊中间的线改为竖直；展开后是带玻璃材质的圆球，不再是斜杠灰块。
- 点球菜单改成和浏览页一样的横向按钮 + 竖向列表：前进、后退、刷新、回主屏、收藏、桌面版、下载记录（没有添加）。
- 页内下载：blob 导航不再取消正在写入的文件；大文件改成分块读取，不再卡在 512KB；带常见文件后缀的主框架链接会交给下载管理器，而不是在网页里打不开。
- 存储管理不再把共享 Default Profile 里的 IndexedDB / Cache Storage 算成 0B 或「应用数据」；按站一行显示「共享存储」。磁盘扫不到但系统有占用时，用系统口径兜底。

### Added

- 浏览菜单和站点壳菜单可打开下载记录，支持查看路径并定向删除。
- 清理缓存时一并清理应用内日志；概览里单独列出日志占用。存储扫描和下载失败会写可定位的 host/文件名/原因日志，不含 Cookie 或完整网址。

### Testing

- 更新下载策略、blob 分块解析、存储分桶与分类器单测。
- `testDebugUnitTest :app:assembleDebug`；悬浮球竖线/菜单、页内下载、下载记录删除、存储占用与清日志为手测清单。

## [0.1.31] - 2026-09-12

站点壳弹出的窗口变成完整浏览标签；下载走系统 DownloadManager，全应用都能看到进度。

### Fixed

- 首页打开的网站弹出 `window.open` / `target=_blank` 后，新窗口按独立 `sessionId` 交给浏览标签，不再用 opener 的网址盖掉登录/OAuth；池里已有渲染器时即使还是 `about:blank` 也会挂上完整浏览壳（新建标签、收藏、查找、再开窗口都可用）。
- 站点壳领养窗口后切到浏览并展开底栏；浏览标签不加站点壳辅助球。
- 下载不再只弹 2.5 秒提示：http(s) 交给系统 DownloadManager，写到公共 `Download/PocketWebShell/`，杀进程后仍继续；右侧胶囊显示进度和路径，完成后从左下角弹出卡片，按文件夹 → 系统下载页 → 打开文件 → 分享的顺序交给系统应用。

### Testing

- 新增浏览标签 `sessionId` 单测、下载 Intent 链与胶囊几何单测。
- `testDebugUnitTest :app:assembleDebug`；站点壳连续弹窗、OAuth 不被 opener 盖掉、全标签下载胶囊与系统文件入口为手测清单。

## [0.1.30] - 2026-09-12

主页文件夹开合、站点壳辅助球、浏览收藏与桌面缩放的一组交互修复。

### Fixed

- 打开主页文件夹时不再先藏网格：Dialog 窗口也改成透明底，避免出现一帧空壁纸闪一下再播展开动画。
- 站点壳辅助球改成豆包式灰色斜杠；贴边胶囊加大到 18×48；左右吸附先滑到边缘再收成胶囊，位置与贴边状态一次写入，避免胶囊和整球来回闪。
- 关闭再打开「站点壳悬浮球」会回到右上角，不再沿用上次可能卡住的位置。
- 「收藏本页」在没有可收藏网页时仍可点，并用确认弹窗说明原因；收藏成功、取消收藏、切换桌面版/手机版同样用这套弹窗提示。

### Changed

- 桌面版网页打开内置捏合缩放（不显示系统缩放按钮）。
- 收藏夹与历史列表的网址默认最多两行。

### Testing

- 更新辅助球几何单测：贴边胶囊 18×48、边缘带吸附、滑到边缘的落点。
- `testDebugUnitTest :app:assembleDebug`；文件夹开合、辅助球吸附/开关重置、收藏与桌面弹窗、桌面捏合为手测清单。

## [0.1.29] - 2026-09-11

站点壳辅助球改成系统悬浮球那套：边缘短胶囊 ↔ 屏幕中间玻璃球。

### Changed

- 点按贴边短胶囊，辅助球出现在屏幕正中，变为 56dp 玻璃球。
- 把玻璃球滑向左或右，松手吸附到对应边缘，重新收成短胶囊（无图标，可视与点击同一块）。
- 点按展开的玻璃球仍打开动作表；左甩不再单独刷新，刷新留在菜单里。

### Testing

- 更新辅助球几何单测：居中展开、左右吸附、轻点不收纳。
- `testDebugUnitTest :app:assembleDebug` 通过；胶囊展开/左右吸附为手测清单。

## [0.1.28] - 2026-09-11

站点壳辅助球贴边手感与添加/浏览页图标文案统一。

### Changed

- 站点壳辅助球展开态改为 56dp；贴边后只露出与碰撞箱一致的半圆切片，不再用内侧一块空的大方盒去点一条细线。
- 添加页主图标与「导入本地 HTML」左侧图标统一为蓝色玄览黑洞标。
- 「导入本地 HTML」说明改为「选择一个或多个网页文件，本地可用」；浏览空态「或新建一个标签页开始浏览」去掉句号。

### Testing

- 更新辅助球几何单测：展开 56dp、贴边切片窄于整球。
- `testDebugUnitTest :app:assembleDebug` 通过；贴边手感、添加页图标与浏览空态文案为手测清单。

## [0.1.27] - 2026-09-11

站点壳辅助球与浏览空态图标：把临时补丁收成可拖动、可贴边、可开关的产品形态，浏览标签页不加这颗球。

### Added

- 浏览空态（暂无标签页、站点壳打开中 / 不可用）改用可着色的玄览黑洞标，颜色贴近原来的主题蓝。
- 从主屏打开的站点壳右上角改为可拖动辅助球：点按打开动作表，松手停在落下位置，甩向右侧或落在右缘收成短竖线，点按贴边条恢复，明显左甩刷新。
- 辅助球动作表提供后退、前进、刷新 / 停止、桌面模式和返回主屏。
- 「我的 → 浏览体验」新增「站点壳悬浮球」开关（默认开），位置写入设置备份。

### Changed

- 站点壳不再钉一颗只能刷新的右上角菜单钮；关闭悬浮球后可用下拉刷新。

### Testing

- 新增 BrandMark tint / 空态半径、站点壳辅助球贴边与左甩判定、AppearanceDefaults 开关默认值单元测试。
- `testDebugUnitTest :app:assembleDebug` 通过；无设备环境，站点壳拖球 / 贴边 / 左甩刷新、浏览空态图标与设置开关为手测清单，未在本轮验证。

## [0.1.26] - 2026-09-11

单用户全局登录态收口：本版本系统性修复了浏览器壳"应当像普通浏览器一样只有一份登录态"的一批合同缺口，不引入任何多账号/多 Profile 机制。

### Changed

- 主页收藏星标不再改变外链策略：只有站点编辑器里显式的「外链在系统浏览器打开」开关才会把跨域链接踢给系统浏览器，收藏本身只是展示态。
- 网站壳（保存的站点/直链）`window.open`/`target=_blank` 现在总是分配一个真实的池化会话（与其它入口共享同一份默认登录态），不再复用当前页面的 WebView；弹出的 OAuth/登录窗口会交给浏览器标签承接。
- 存储管理详情页去掉会误导的「按站清理」入口——本产品所有入口共享同一份 Default Profile，不存在可安全单独清理的站点边界；「清除全部网站数据」现在会彻底销毁全部会话（不留返回栈快照）并额外清理 IndexedDB / Service Worker / Cache Storage 等站点数据目录。
- 「结束后台会话」现在会真正挂起会话渲染并在无会话时停止前台服务，不再只是从保活列表摘除。

### Fixed

- 已离开本地导入页面的会话不能再让同页 iframe/脚本继续读取 `/local/<appId>/` 下的文件——本地文件访问权跟随当前主文档 origin，不跟随会话曾经持有的能力。
- 网页 JS 对话框（alert/confirm/prompt/beforeunload）现在只发给当前可见的宿主；不再落回持久 session listener 的默认实现（后台标签不会再静默自动确认或拒绝）。
- 关闭地理定位提示不再连带取消其他正在等待的文件选择/权限/JS 对话框请求。
- 浏览器切标签时加载进度条不再从上一个标签的进度"爬"到当前标签的进度。
- 浏览器起始页/空标签页的内容不再被悬浮 Dock 遮住底部。
- 打开网站壳的第一帧不会再用尚未读取完成的下拉刷新设置覆盖已经生效的配置；直接链接现在也会读取下拉刷新设置。
- Blob 下载回调 token 不匹配时也会安全解除下载保护，不再可能永久占用保护槽。

### Testing

- 新增/更新单元测试：收藏不再改变外链策略、本地资源跨 origin 边界、`ShellConfig` 重复配置的结构等价短路径。
- `testDebugUnitTest :app:assembleDebug` 通过；无设备环境，跨入口登录共享、OAuth 弹窗接管、本地导入越权、退出登录后重新登录等场景为手测清单，未在本轮验证。

## [0.1.25] - 2026-09-10

### Added

- 网页 `alert` / `confirm` / `prompt` / `beforeunload` 由应用对话框承接，后台标签立即完成回调，避免登录或支付页静默卡住。
- 存储管理新增「清除全部网站数据」确认流：关闭会话后清除 Cookie 与网站存储，并明确提示会退出登录。

### Changed

- 共享默认 Profile 下的网站详情不再提供会失败的「按站清理」；遗留独立 Profile 目录仍可单独清缓存。
- 清理全部可清理缓存的确认文案改为共享缓存语义，不再暗示可以按站独立释放。

### Fixed

- Blob 下载读取期间为会话加上 `PENDING_DOWNLOAD` 保护，避免大文件脚本执行时被池淘汰。
- 池保护说明与实现对齐：覆盖下载，不再宣称未接线的认证保护槽。

### Testing

- 新增 JS 对话框文本净化、UrlRouter 其余 scheme 与下载保护组合单元测试。
- Debug 构建与全模块单元测试结果随本版本验收。

## [0.1.24] - 2026-09-10

### Added

- 浏览与网站壳统一使用“半真半假”顶栏加载进度；新标签页改为收藏夹 + 最近访问的起始页。
- 全部会话页支持选择、全选和批量结束，并复用二次确认弹窗；二级页隐藏底部标签栏，改为操作工具条。
- 设置中新增「下拉刷新网页」开关，默认关闭；网站壳菜单提供手动刷新。

### Changed

- 「我的」与「添加」页大标题显式使用 `onSurface`，深色模式不再出现黑字。
- 「我的」底栏改为与内容叠层的悬浮玻璃，去掉选中态色块；后台与通知改用圆角彩色图标。
- 添加页改用玄览品牌标，并缩短引导文案避免换行。
- 开屏粒子改为 Logo 同色的鎏金星轨，收束时聚合成太极环与伴星。

### Fixed

- 下拉刷新不再要求“不能后退”才生效；仅在页面已到顶部且用户打开开关时拦截手势。
- 后台会话标题与网址默认单行省略，避免把设置列表撑开。

### Testing

- 新增加载进度映射与共享 Profile 下拉刷新默认值单元测试。
- Debug 构建与全模块单元测试结果随本版本验收。

## [0.1.23] - 2026-09-10

### Fixed

- 将本地导入资源的读取权限绑定到当前会话的 `appId`；浏览器/直链会话不能读取任意 `/local/<appId>/` 文件，编码后的路径穿越也会被拒绝。
- 修复 Blob 下载结果解析错误：`OK:<mime>:<base64>` 现在能正确交付，并继续执行大小上限与 FileProvider 校验。
- 修复应用级 WebView 调起外部协议时缺少 `FLAG_ACTIVITY_NEW_TASK` 的问题，并阻止 `intent:` URI 指定显式 package/component/selector。
- Renderer 崩溃自动恢复最多连续尝试一次；替代 WebView 立即崩溃时进入可观察失败状态，不再无限重建。
- HTTP 主文档错误会结束当前会话的 loading 状态；后台标签创建的真实窗口会立即绑定持久 session listener，避免孤儿 WebView。

### Testing

- 新增本地 appId/编码路径边界、Blob 回调协议和 renderer 恢复闸门单元测试。
- Debug 构建与全模块单元测试结果随本版本验收记录更新；真机 WebView 矩阵仍需设备可用后执行。

## [0.1.22] - 2026-09-10

### Added

- 浏览器壳核心能力整合：保存的网站入口与浏览器标签统一使用单用户共享 WebView Profile，跨入口复用 Cookie 与登录态。
- 地址栏与外部协议路由增加 scheme 校验、Intent 可解析性检查和失败反馈；新增定位权限申请链路。
- 完成 pooled `target=_blank`/`window.open` 真窗口、会话保护原因（活动/权限/文件/全屏/保活）与动态配置重应用；标签缩略图在首帧后按尺寸和 URL 版本安全更新。
- 新增全屏视频承载、HTTP/HTTPS 主文档错误分类、HTTP Auth/客户端证书安全拒绝提示、普通下载登录态请求头和受限 Blob 下载。
- 本地导入资源改为 appId 目录级 canonical 边界；元数据抓取加入 HTML/manifest 体积、重定向、最终协议与私网地址校验。

### Fixed

- 修复保存的网站仍使用独立 Profile、导致浏览器登录态无法复用的问题。
- 修复外部 Intent 可直接启动显式组件的风险，并为无处理程序的电话、邮件、地图等链接提供失败回调。
- 修复 renderer gone 后仅记录不恢复、共享缓存被误当作单网站缓存、以及池淘汰导致标签 UI 状态丢失的问题。

### Testing

- `testDebugUnitTest`（全模块）与 `:app:assembleDebug` 通过。
- 真机 WebView 弹窗、下载、全屏、renderer 崩溃恢复及跨入口 B 站登录态仍需手动回归。

## [0.1.21] - 2026-09-09

### Changed

- 「全部应用」视图切换按钮移至最右上角，「取消」按钮左移让位；图标改用全局 Rounded 风格（`Icons.Rounded.GridView` / `AutoMirrored.Rounded.ViewList`）并显式指定 `onSurface` 着色，深色模式下为浅色图标。
- 开屏动画重排演出顺序：文案先行（开场即淡入，字距收紧 + 轻微上浮），星河收束随后在下方上演；整体舞台缩小为"小星河"（吸积环半径约原 48%，粒子散布收窄到局部椭圆域并下移），文案占据上半屏。黑洞/白洞细节增强：远景星尘微微闪烁营造纵深、吸积环多普勒不对称弧（顺行侧更亮）、事件视界外缘光子环、粒子椭圆轨道；性能同步优化（粒子 120→110、辉光层数 6→4、发光粒子占比 1/7→1/9，仍单 Canvas 无实时模糊）。

### Fixed

- 修复深色模式下「全部应用」视图按钮图标颜色不显眼的问题（显式 `onSurface` tint）。

### Testing

- `testDebugUnitTest`（全模块）与 `:app:assembleDebug` 通过。
- 手动冒烟未执行：深色模式视图按钮观感、开屏新演出顺序与"小星河"观感需真机确认。

## [0.1.20] - 2026-09-09

### Added

- 「全部应用」资源库右上角新增视图选择：可在字母分区网格（现状默认）、列表 · 按首字母、列表 · 按时间三种视图间切换。列表视图采用小图标（40dp）+ 完整应用名称的整行布局，长列表更易定位；按首字母的列表保留分区头与右侧字母索引条，按时间（新建在前）不显示索引条；切换视图或搜索自动回到列表顶部。视图偏好会话内保留（`rememberSaveable`，随进程状态恢复，不写 DataStore）。

### Fixed

- 修复深色模式下「全部应用」页文字不可见：页面标题与应用名此前未显式指定颜色（默认黑），深色背景下黑字不可读；现统一使用 `colorScheme.onSurface`，明暗主题均正常。

### Testing

- `AllAppsIndexTest` 新增 2 例（时间序「新建在前 + 同刻标题稳定」、时间视图与搜索过滤组合）；`testDebugUnitTest`（全模块）与 `:app:assembleDebug` 通过。
- 手动冒烟未执行：深色模式观感、列表视图两档排序与字母索引条显隐需真机确认。

## [0.1.19] - 2026-09-09

### Changed

- 开屏动画重写为「黑洞 / 白洞」汇聚效果：120 颗粒子沿螺旋轨道加速坠入吸积环，核心闪耀后收束；浅色主题为暖金白洞、深色主题为蓝紫黑洞（含事件视界黑盘与光子环闪耀）；主时长约 2.4s + 0.22s 淡出，单 Canvas 无模糊。
- 数据导出成功后直接调起系统分享面板，并弹出导出成功对话框展示文件名、大小与保存路径；导出文件同步写入系统「下载/PocketWebShell/」目录（MediaStore，API 29+ 免权限），对话框提供「完成 / 再次分享」。
- 存储管理进入网站详情改用全局统一页面转场（`AppMotion.detailEnterFor/detailExitFor`），返回手势与列表滚动位置保持行为不变。

### Fixed

- 修复存储统计可能全部显示 0 B：概览大数字改用 `StorageStatsManager.queryStatsForPackage`（与系统设置一致的数据源，自包名免权限）作为权威值，磁盘遍历结果作为回退；扫描输出逐桶诊断日志（应用内日志查看器可看），系统报告有数据而遍历为 0 时额外告警。
- WebView 独立 Profile 应用（`WebViewCompat.setProfile`）提前到 `ShellWebView` 初始化最前（先于 settings 配置），失败不再静默而是记录日志——此前若晚置失败会导致站点数据落回默认共享 Profile，按站统计显示 0 B。

### Testing

- `testDebugUnitTest`（全模块）与 `:app:assembleDebug` 通过；`StorageAccountingTest` 新增系统统计透传/缺省两例。
- 手动冒烟未执行：导出分享面板与下载目录可见性、按站统计真机数值、开屏黑/白洞观感需真机确认。

## [0.1.18] - 2026-09-09

### Added

- 我的 → 数据与存储：新增「存储管理」与「数据管理」入口。
- 存储管理：基于每个网站实例的独立 WebView Profile（`app_webview/profiles/<appId>`）实现真实按站统计；顶部概览展示已统计占用、可清理缓存与互斥不重复的三分类分段占用条（可清理缓存 / 网站数据 / 应用与其他数据），附图例与确切数值；网站列表支持占用/名称排序与搜索，详情页展示按站分段条；清理只删除 Profile 内的 Cache/Code Cache/GPUCache，保留登录相关数据；会话运行时先询问再关闭清理，结果按 before/after 实测反馈，部分失败可重试。多 Profile 不受支持的设备如实展示限制。
- 数据管理：三种导出方式（仅网址 / 网址+必要数据 / 全量·实验性），导出前确认弹窗、生成后走 FileProvider + 系统分享面板（新增 `backup/` provider 路径）；导入走系统文件选择器，逐文件校验类型/版本/结构；仅网址、全量、文件夹分享为追加导入（全新 id、不去重、重名「（1）（2）」编号、沿用现有分页），网址+必要数据为原位置恢复（位置冲突/布局不兼容/结构无效则整次拒绝）；全量导入后设置需用户单独确认才应用，默认保留现有设置。
- 文件夹分享：主页长按文件夹菜单新增「分享文件夹」，导出 `.pws`（zip 容器 + `backup.json` 清单）并经系统分享面板发送；接收方经数据管理导入，文件夹作为整体追加、内部顺序保留。
- 新依赖：kotlinx-serialization-json（备份清单编解码）；备份核心位于 `core/data/backup/`，存储统计与清理位于 `core/webengine/storage/`。

### Testing

- 新增 52 个单测（core/data 备份 34、core/webengine 存储 18）：编解码往返与拒绝、重名编号、四类导入的追加/冲突/拒绝规则、跨页分配不碰撞、路径分类与统计互斥不重复。
- `testDebugUnitTest`（全模块）与 `:app:assembleDebug` 通过。
- 手动冒烟未执行：分享面板实际弹出、运行中会话清理的影响、导入导出真机往返需人工确认。

## [0.1.17] - 2026-09-09

### Added

- 开屏动画：冷启动时播放约 1 秒的粒子动画——80 颗彩色粒子从全屏散落位置汇聚成桌面图标点阵并收拢描边轮廓，配合「把互联网 收进你的桌面」文案字距收紧淡入，随后整体淡出进入主页；单 Canvas 绘制、无实时模糊，性能友好。存在保持中的浏览会话（WebView 池或保活登记非空）或经 URL 直达启动时跳过开屏，回到网页零打扰。
- 文件夹重命名：展开的文件夹顶部标题可直接点击改名；长按文件夹的菜单同步新增「重命名」项。文件夹名持久化在全部成员行上（隐式分组模型）。
- 文件夹内长按成员图标拖拽排序：跟手浮动图标层不参与测量、注册点不跳变、目标槽位高亮，松手落子并持久化（`folderCellIndex`）；拖拽期间禁用翻页手势。

### Changed

- 文件夹展开/收起改为显式动画（原先为系统默认瞬时动画）：进场为遮罩淡入 + 面板 0.92→1 缩放淡入共 380ms，退场 240ms 播完再移除弹窗。

### Data

- Room v3 → v4（`MIGRATION_3_4`）：`web_apps` 新增 `folderName`/`folderCellIndex` 两列，存量数据不动。

### Testing

- `:feature:home:testDebugUnitTest` 与 `:app:assembleDebug` 通过；`HomePagesTest` 新增 11 个用例（文件夹名聚合、folderCellIndex 排序优先与 null 回落、resolveFolderMemberMove 边界与稠密序号、解散/移出清理字段）。
- 手动冒烟未执行：开屏动画观感、文件夹展开动画与夹内拖拽需真机人工确认。文件夹内拖拽不支持跨页（>9 成员需先翻页再拖）。

## [0.1.16] - 2026-09-07

### Added

- 解散文件夹二次确认：主页长按菜单与文件夹展开页的「解散文件夹」均先弹出确认对话框（复用 `AppConfirmDialog`），确认后才解散；文件夹成员移回主屏幕，应用本身不会被删除。
- 长按应用 Logo 的情境菜单新增「复制应用链接」，一键把站点 URL 写入剪贴板并弹出底部浮层提示。
- 设置页「后台会话」收敛为最多展示前 3 条；超过 3 条时提供「全部会话」入口，进入独立页面统一管理全部保活会话（含逐条结束）。

### Testing

- `testDebugUnitTest`、`:app:assembleDebug` 与 `:app:assembleDebugAndroidTest` 通过。
- `:app:connectedDebugAndroidTest` 在 `webshell-ios-qa-api35`（API 35）上全部通过。
- 手动冒烟（API 35 模拟器）：长按图标菜单出现「复制应用链接」并复制成功；解散文件夹弹确认弹窗、取消不生效、确认才解散；后台会话超过 3 条时出现「全部会话」入口并可逐条结束。

## [0.1.15] - 2026-09-07

### Changed

- 清理页面层旧控件与重复实现，统一表单、图标、选择行、弹窗、面板和设置分组；应用层 Playbook 聚合各模块的真实生产组件，并使用隔离夹具演示。
- 设置 → 外观与主题新增 MiSans、系统字体、Noto Sans SC 和 90%–130% 界面字号；字号只影响应用界面，不覆盖网站自身的文字缩放。
- 浏览器顶部收敛为菜单、地址和标签的一行工具栏；进入网页后底部导航直接收成玻璃 Orb（进入网页、切换标签或页内导航时立即收缩，不再依赖滚动检测），Orb 支持边缘停靠并可点击召回完整导航。

### Fixed

- 恢复添加网址时的站点图标发现：补齐 Coil 3 网络加载组件（`coil-network-okhttp`），抓取的网站图标重新作为应用 Logo 显示，无图标站点仍回退为首字符。
- 修复深色模式下浏览器菜单按钮（⋯）呈灰色难以辨认的问题，图标改用 `onSurface` 前景色，随主题自动适配。
- 修复浏览网页时内容区域闪烁：浏览器底部 Dock/Orb 改为静态玻璃材质，不再对 WebView 逐帧实时模糊采样；同时移除阅读手势的 JS 探测注入。
- 保留已有网站的 `textZoomPercent`，新添加的网站固定从 100% 网页字号开始；修复页面层重构过程中旧网站缩放被应用字体设置覆盖的风险。
- 修正 WebView 宿主安全区、会话监听和原生视图绑定，确保回调按 `sessionId` 更新对应标签页，并避免重复网页壳造成的状态串扰。
- 目录回归使用本地化资源查找交互入口，滚动页面的端侧查找只接受实际位于视口内的节点，避免误点被 Dock 覆盖的控件。

### Testing

- `testDebugUnitTest`、`:app:assembleDebug` 与 `:app:assembleDebugAndroidTest` 通过。
- `:app:connectedDebugAndroidTest` 在 `webshell-ios-qa-api35`（API 35）上 17/17 通过；滚动检测手势及其用例随功能一并移除。
- 手动冒烟（API 35 模拟器）：添加 github.com 后主屏显示抓取的网站图标；进入网页、页内导航与新建标签页时底部导航立即收成 Orb，点击 Orb 召回完整 Dock；深色模式下浏览器菜单按钮为白色。
- API 30/31 回退实机/模拟器验收未执行：当前工作环境没有可用的 API 30/31 设备；未将其标记为通过。

## [0.1.14] - 2026-09-06

### Changed

- 主屏视觉对齐 iOS：全屏静态流线壁纸、四图标悬浮 Dock、约束驱动的桌面网格、白色阴影标题、搜索/页码胶囊与 3×3 文件夹预览。已有照片壁纸、图标设置及稀疏位置数据继续保留。
- 统一玻璃材质、文字字阶、分组列表、情境菜单与导航层级；添加页面和浏览器工具栏采用一致的 iOS 风格原生呈现。
- 呈现层职责拆分：应用壳集中管理安全区/壁纸/Dock，主页分离几何与单元呈现，主题状态经 Hilt ViewModel 单独投影，避免位置设置使整棵主题树重组。

### Fixed

- 壁纸延伸至 Dock 后方，修复玻璃采样到空白底色的问题；实时模糊半径显式限制在 24dp 以内，其他浮层使用静态材质。
- 非编辑状态不再维持图标抖动动画；照片取色修复路径清除后的旧色残留、超长图片采样及临时位图回收。
- 安全区随真实 Android 系统栏变化，浅/深主题更新系统栏对比度；外部 URL 打开时保活登记不再被全屏提前返回跳过。

### Testing

- 新增桌面约束几何、壁纸取色采样与端侧视觉/导航回归；以独立 Android 15 模拟器检查标准宽度、紧凑宽度、深色主题、文件夹与多页交互。

## [0.1.13] - 2026-09-02

### Fixed

- 翻页模式拖到右缘/左缘悬停开新屏"没反应"的根因修复（端测截图 + 日志实证）：拖拽跟踪协程此前挂在被拖图标的 composable 上，翻页动画落定后源页被 Pager 移出组合，协程被一并取消——状态清零、临时屏回弹、落子丢失。拖拽会话主循环上移到根级容器（对齐 Launcher3 DragController 分层），翻页/开新屏后源 cell 被 dispose 也不再影响会话。
- 临时屏创建同帧立即滚动会被旧 pageCount 钳制回 0（pageCount 随重组才生效）：写 `tempPageSide` 后等重组帧再翻页。
- 边缘悬停翻页改为单 `snapshotFlow` 循环状态机：旧实现依赖 `LaunchedEffect` key 重启，翻页后 finally 同帧重算的边缘方向（1→0→1）对组合期 key 不可见，二次翻页/连续跳页永远不会发生。

### Changed

- feature/home 交互层拆分重构：新增 `HomeInteractionState`（集中持有拖拽/菜单/编辑模式会话状态与布局注册表，手势协程只捕获该稳定持有者，杜绝组合期快照过期）与 `HomeGestures.kt`（`homeCellGesture` cell 手势检测、`homeDragSession` 根级拖拽会话、`homeBlankAreaMenu` 空白长按菜单、`homePinchEditMode` 双指捏合、`HomeDragEffects` 边缘悬停状态机）；`HomeScreen.kt` 只保留组合根、网格容器与菜单/对话框浮层。
- 新增主页手势仪表化测试 `HomeGestureInstrumentedTest`（真实 MotionEvent 注入 + Room 落库断言）：页内交换拖拽、长按菜单、右缘/左缘开新屏、空白长按菜单、编辑模式即按即拖、全部应用抽屉共 7 条验收路径。

## [0.1.12] - 2026-09-02

### Fixed

- 拖到末屏右边缘无法开新屏：临时屏状态是翻页 `LaunchedEffect` 的 key，写入瞬间 effect 自我取消、正在执行的翻页动画被掐死，随后进入"已有临时屏"分支永久卡住。改为状态不作为 effect key（Launcher3 extra empty screen 语义不变：松手未落子自动裁掉）。
- 拖拽中的页号映射改用现算偏移：组合期捕获的偏移快照在临时屏插入后会过期，导致落点解析/落子页号错误。

### Added

- 首屏左边缘开新屏：拖拽中停在首屏左缘约 900ms 在左侧插入临时空白屏，落子后被拖图标落到新首屏，其余页面整体后移一页（`HomePages.resolvePrependMove`）；右缘行为保持末屏追加。
- 编辑（jiggle）模式即按即拖：图标位移超触摸阈值（touchSlop）直接进入拖拽，原地松手仍为勾选切换，全程不弹长按菜单（对齐 iOS/HyperOS jiggle）。

## [0.1.11] - 2026-09-02

### Added

- 桌面滑动模式设置（主页布局 → 桌面滑动）：左右翻页（默认，现状行为）/ 上下滚动两种模式可切。上下滚动模式把所有页摊平成一条纵向列表（自由摆放保留空槽占位），数据模型不变——落子时全局下标换算回 `(homePage, homeCellIndex)`；拖拽中手指悬停顶部/底部边缘自动滚动并重解析落点；页码指示器仅在该模式隐藏。
- 左右翻页模式：拖拽中手指停在最后一屏右边缘约 900ms 自动追加临时空白页并翻过去，可直接落子；松手未落子则临时页自动裁掉，「默认只有一屏」不被破坏。
- 「全部应用」浮动入口：主屏右下角半透明圆角图标（可自由拖动，位置归一化持久化），点按打开全部应用抽屉，长按弹菜单可隐藏；主页布局新增「显示全部应用入口」开关，空白处长按菜单亦可切换显隐。
- 全部应用抽屉：按标题首字符分区（ASCII 字母大写，汉字经 pinyin4j 取拼音首字母，其余归 #），A→Z、# 殿后；右侧字母索引条支持点按与按住滑动连续跳转，当前字母放大气泡提示，无应用分区置灰不可点。
- 空白处长按菜单（Initial pass 手势通道，不与图标/pager 手势竞争）：「编辑模式」进入 jiggle 批量整理；「隐藏/显示全部应用入口」。

## [0.1.10] - 2026-09-01

### Added

- 应用内日志持久化：`AppLog` 新增 INFO/WARN/ERROR 级别与可插拔持久化汇，日志经 Room（新增 `app_log` 表，v2→v3 显式迁移，不丢存量数据）落盘，进程结束后仍可回看。
- 崩溃捕获：接管未捕获异常，把崩溃堆栈作为 ERROR 级「crash」日志在进程死亡前同步写入数据库，再转发原默认 handler。
- 监测点扩展：页面加载/加载完成/加载失败/SSL 错误（只记 host）、浏览器标签新建/切换/关闭、网页应用会话打开/关闭、前台保活服务启停、添加网站成功与失败（含失败原因）、应用启动版本号。
- 日志查看页新增红色清空按钮（二次确认弹窗），清空全部持久化日志。

### Changed

- 日志查看页重构：从 Room 倒序分页读取（每页 30 条，滚动到底自动加载更多，不再受内存环形缓冲 500 条上限约束），标签过滤走数据库查询。
- 复制/分享改为完整导出：含导出头部（导出时间、应用版本、设备型号、Android SDK、条目总数）与当前过滤条件下的全部条目；分享生成 TXT 文件经 FileProvider 以 `EXTRA_STREAM` 发出。

## [0.1.9] - 2026-09-01

### Fixed

- 长按菜单永远钉在左上角：Popup 内部的全屏 scrim 把 popupContentSize 撑成整个窗口，定位公式的 clamp 把所有结果压到 (margin, margin)。移除内置 scrim——外部点击关闭由 focusable Popup 的 outside-touch → onDismissRequest 承担（该 scrim 本身无视觉背景，无观感损失）。菜单恢复以按压点为中心、在图标附近弹出。

## [0.1.8] - 2026-09-01

### Fixed

- 长按图标误触打开应用：旧实现 clickable 与 detectDragGesturesAfterLongPress 双通道并存，长按原地松手会同时弹菜单并打开应用。图标手势重构为单通道 `awaitEachGesture` 状态机——长按与点击天然互斥，超时前松手算点击、超时前位移超 touchSlop 取消交还 pager；长按弹菜单后继续移动累计位移超过 16dp（Launcher3 `deep_shortcuts_start_drag_threshold`）才进入拖拽，≤16dp 松手菜单保持打开。
- 首字母兜底图标白底无字：远端 logo 加载失败（404/网络拦截/格式不支持）时回退首字母色块（`SubcomposeAsyncImage` error 兜底），不再只剩白底空块；首字母色板新增深色主题配色（深饱和底 + 近白字）。

### Added

- 主页长按菜单新增「重命名」「更改图标」「强制刷新」：重命名走对话框；更改图标支持输入地址或上传本地图片（复制到应用私有 icons 目录）；强制刷新重新抓取站点标题与 logo，结果轻量 toast 提示。

### Changed

- 官方 logo 贴边裁剪：远端 logo 以 `ContentScale.Crop` 等比放大铺满圆角方块（四边贴到圆角边缘），不再内缩留白边。
- 情境菜单定位重写为 Launcher3 派生公式：垂直优先向按压点上方展开（上方放不下且下方空间更大时翻转到下方），水平以按压点居中，四边 clamp 屏幕边距，弹出动画锚点（transformOrigin）始终指向按压点。
- `SiteMetadataFetcher` 从 feature/add 迁至 core/data（含 Hilt 模块、单测与 okhttp/jsoup 依赖），feature/add 与 feature/home 强制刷新共用。

## [0.1.7] - 2026-09-01

### Fixed

- 标签页串台：浏览器进度/加载/返回前进/标题/URL 从屏幕级单一状态改为 per-tab 状态，WebView 回调按 sessionId 归属写回自己的标签，开多标签互切不再互相污染。
- 后台标签回调丢失：`ShellWebView` 新增随会话存活的持久 `sessionListener`（不随 Compose 组合摘除），后台标签加载完成后的标题/URL/历史记录不再丢失。
- 池淘汰失控：`WebViewPool` 从插入序 FIFO 改为真 LRU（access-order），激活会话受保护永不淘汰；淘汰/重建通过池级快照保留返回栈；淘汰时回调通知浏览器同步移除标签。
- 登录态不共享：浏览器标签统一使用 WebView 默认共享 Profile（原先每个会话独立 Profile，cookie/token 互不共享）；网页应用壳保留独立 Profile 隔离。
- 移动模式 UA 改为不含 `Version/4.0` 与 `wv` 标记的 Chrome Android UA，修复部分站点（Google 登录、若干移动站）拒绝/降级服务。
- ShellScreen 新窗口会话泄漏：`onNewWindow` 复用当前会话加载 URL，不再遗留无人销毁的 `browse-*` 孤儿会话。

### Changed

- 切走的后台标签 `onPause` 暂停渲染与媒体，切回 `onResume`（后台音视频不再继续播放）。
- 关闭标签改为彻底销毁（不留会话快照），并同步清理桌面模式记忆与会话监听器。
- `thirdPartyCookies` 显式双向设置，避免跨实例状态泄漏。
- 引擎诊断与选型决策沉淀为 `docs/ENGINE.md`：引擎维持 Chromium（Android System WebView），本版本重构集成层。

## [0.1.6] - 2026-08-31

### Fixed

- Free placement finally works like a real launcher: with 自动整理桌面 off, a dragged icon stays on the exact grid slot it was dropped on (slot 2 → slot 12/9/4/7 stays there) instead of snapping back to the compacted front positions. The home grid is now a sparse model — every cell keeps its persisted `(page, slot)`, empty slots are real placeholders, and drops resolve to the nearest registered slot bounds (including empty ones).
- Context-menu direction was inverted in some cases: the menu now anchors on the actual finger press point and opens toward the side with more room (icon at slot 1 on the left edge opens the menu to its right), computed from the four-direction free space around the press point with edge clamping.
- Custom local-icon paths (starting with `/`) were rejected when saving an app, losing the uploaded icon.

### Added

- 主页布局新增「自动整理桌面」开关（默认关闭 = 自由摆放，对齐主流 Android 桌面；开启后恢复压实排列）。设置项为兼容性契约，旧设备默认进入自由摆放。
- Free-placement drag semantics: dropping on an empty slot moves there directly; dropping on an occupied slot swaps the two cells (folders swap as a whole); new apps append to the first empty slot of the last page instead of always compacting.
- Folder dissolve / remove-from-folder in free-placement mode: the first member keeps the folder's slot, remaining members fill the following empty slots in order; a removed member is placed in the first empty slot near the folder.
- Unit tests for `buildSparse`, `resolveSlotMove`, dissolve and remove-from-folder slot allocation (`HomePagesTest`).

## [0.1.5] - 2026-08-31

### Fixed

- Drag-layer shadow truly follows the icon's large corner radius now: the shadow is cast inside `AppIcon` with the rounded shape and `clip=false`, instead of an outer `graphicsLayer { clip = true }` that clipped the rounded shadow into a square (the recurring "square shadow" on long-press/drag).
- Context-menu horizontal avoidance is now intelligent: an icon on the left half of the screen pops the menu from its right side, and vice versa (previously it only centered near the icon).
- Removed the ugly whole-cell "grid highlight" when tapping an app/folder: the default ripple is disabled and replaced by a subtle press-scale on the icon itself.
- Letter-fallback icons are readable in every theme: the tile color is picked from a high-contrast palette by title hash with near-black text, replacing the low-contrast `primaryContainer` pair.

### Added

- Drag-to-reorder now snaps to the nearest grid slot: the drop target resolves to the closest grid slot (hovered cell, nearest cell center, or page end) and the app snaps there on release instead of jumping back, like a real launcher/desktop.
- Pinch-to-edit gesture rewritten with `awaitEachGesture` two-finger distance tracking (the old `detectTransformGestures` was swallowed by the pager), so pinch-in reliably enters edit mode and pinch-out exits.
- Folder open is redesigned as a HyperOS-style full-screen expanded page: dimmed backdrop, folder name + member count, and a 3-column grid of rounded member icons, replacing the plain AlertDialog list.
- Custom app icon upload in the add flow: pick a local image (copied to the private icons dir) when a site has no logo, with the home grid rendering local file icons edge-to-edge.
- Page-transition style setting under 外观与主题: 滑入 / 淡入 / 缩放 / 无动画 presets, wired through `LocalTransitionStyle` into 我的 sections and the developer center.

## [0.1.4] - 2026-08-31

### Fixed

- Drag-layer shadow now follows the icon's large corner radius instead of rendering as an ugly square rectangle (long-press / drag no longer shows a right-angled shadow).

### Changed

- `AppContextMenu` rebuilt as an anchored floating panel (HyperOS/iOS home-screen style): a `Popup` anchored beside the long-pressed icon with smart vertical flipping, replacing the previous full-screen centered dialog with a giant title and icon grid.
- Context-menu actions are now a vertical list — circular icon badge (primary / errorContainer) + short label, with the destructive action in red at the bottom — and use a semi-transparent frosted surface (no extra live blur, per PERFORMANCE.md).
- Menu pop animation unified via `AppMotion.popupEnter/popupExit` (scale 0.92→1 + fade, anchored to the icon side).

### Added

- Edit (jiggle) mode on the home screen: pinch-in with two fingers enters edit mode (icons wiggle iOS-style, circular selection badges on their top-start corner, a translucent "完成" capsule top-end, and a bottom action row with select-all/clear); pinch-out, the done button or the back gesture exits. Tapping toggles selection instead of launching.
- Unified motion system in `AppMotion`: main-tab `Crossfade`, detail-page `enterDetail/exitDetail` (slide-in-from-right + fade) wired into 我的 sections and the developer center's third-level pages via `AnimatedContent`, and shared popup specs.
- Design Playbook motion section now demonstrates spring, detail slide-in and popup transitions; DESIGN.md §6.2–6.5 updated for the anchored menu, edit mode and unified motion.

## [0.1.3] - 2026-08-31

### Added

- iOS-style home-screen icons: icon bodies now sit directly on the wallpaper with no pedestal card; corner radius is clipped by the icon body itself (sites, letter fallbacks, folders, add cell as outlined ghost).
- `AppContextMenu` design-system component: iOS home-screen context menu with blurred scrim, spring pop-in, optional icon preview, 4-column action grid and separated destructive actions (semi-opaque solid + hairline instead of a second live blur, per `docs/PERFORMANCE.md`).
- Long-press interaction reordered to the iOS sequence: press first opens the menu; dragging past touch-slop fades the menu out and moves the icon into drag mode; releasing in place keeps the menu.
- Developer center is now a full-screen first-level page (我的 → 开发者选项): app/WebView/device info, log viewer, icon cache clear, design Playbook entry.
- In-memory ring-buffer log (`AppLog`, 500 entries, tag filter, copy/share export, auto-cleared when the process dies; no browsing content, cookies or credentials are ever logged).
- Design Playbook gains a context-menu demo section for visual acceptance.

### Changed

- Favorite border thickened to 2dp; folder preview keeps the unified corner radius.
- Home grid long-press logging hooks (`HomeViewModel`) and Me settings logging hooks (`MeViewModel`) feed the new log viewer.

## [0.1.2] - 2026-08-31

### Changed

- Light theme rebuilt on the iOS grouped-list standard: gray background `#F2F2F7` now hosts pure-white cards, replacing the previous white-background/light-gray-card layering.
- All secondary text (`onSurfaceVariant`, `secondary`, `tertiary`) deepened to `#55555B` — 7.4:1 on white cards (WCAG AAA), up from a marginal 4.74:1.
- `surfaceVariant` deepened to `#E5E5EA` so icon placeholders and browser input areas stay visible on the new gray background.
- Dark-theme card surface aligned to iOS grouped dark (`#1C1C1E`).
- Bottom glass bar: selected tab now shows a capsule indicator fill (primary at 12% alpha, color-only animation, no layout change).

### Added

- `AppCard` gains a 1dp `outlineVariant` hairline border (static edge-light, zero performance cost).

### Fixed

- Eliminated the "dirty pink" card appearance caused by near-white gray cards over a pure-white background (simultaneous-contrast effect).

## [0.1.1] - 2026-08-30

### Added

- Unified design-system layer in `core/designsystem`: full light/dark color tokens, MiSans typography scale, shape/spacing/motion tokens, and shared components (`AppCard`, `AppListRow`, `AppListDivider`, `AppSectionHeader`, `AppBadge`, `AppConfirmDialog`, `glassSurface`).
- Bundled subsetted MiSans font (regular/medium/semibold) so text metrics are identical on every device.
- Design Playbook under 我的 → 开发者选项: live preview of color tokens, typography, shapes, spacing, list rows, buttons, dialogs, glass material and motion specs.
- Component-library inventory documented in `docs/DESIGN.md`.

### Changed

- Me settings pages rebuilt on the unified components with iOS-style grouped sections (section header above edge-to-edge cards).
- Browser top bar rebuilt: self-drawn capsule address field keeps text vertically centered; tab-count button uses neutral surface tokens; tonal-elevation tint removed.
- Bottom bar glass effect now consumes the shared `glassSurface` component instead of a one-off implementation.
- Layout sliders use a single primary-color track with a white thumb (gradient track removed).

### Fixed

- Settings subtitle text no longer uses the low-contrast `outline` token (all secondary text now `onSurfaceVariant`, ≥4.5:1 on card backgrounds).
- Browser address-bar placeholder text no longer clipped in half by the fixed 46dp field height.
- Purple tint on cards and badges eliminated by defining every Material 3 color token explicitly.
- Hard-coded Google-blue fallback icon colors replaced with `primaryContainer`/`onPrimaryContainer` theme tokens.

### Removed

- Dropped unused library declarations (Lottie, reorderable) to keep one unified library per capability.

## [0.1.0] - 2026-08-30

> 版本计数从本版本起重置（旧线止于 0.2.0 / versionCode=2）。新线为 versionCode=1 起；
> 已安装旧版 0.2.0 的设备需先卸载再安装。

### Added

- Apple-style theme system: pure-white / pure-black base schemes, follow-system mode, and a photo-wallpaper mode that extracts the accent color from the user's photo (Me → 外观与主题).
- iOS Liquid Glass floating capsule bottom bar powered by the Haze library (live backdrop blur + specular edge highlight).
- Project specification documents: `docs/DESIGN.md`, `docs/VERSIONING.md`, `docs/PERFORMANCE.md`, referenced from `AGENTS.md`.

### Changed

- Version counting restarted from 0.0.0 (versionCode 1); default increment is the third digit only.

## [旧线 0.2.0] - 2026-08-29

### Added

- In-app update-log page under Me → 项目更新日志, kept in sync with this file.
- Two-level settings structure on the Me page with per-section detail pages.

### Changed

- Rebranded the app to 「玄览」 with a new tian-dao themed adaptive launcher icon.
- Compacted the bottom navigation bar height.
- Slimmed the browser top bar into a single row; aligned the address field and tab-switcher button heights; moved bookmark, find-in-page and new-tab actions into the overflow menu.
- Moved running background sessions to the top of the Me page and restyled layout sliders with a gradient track and bordered thumb.

## [旧线 0.1.0] - 2026-08-29

### Added

- Website metadata and icon discovery flow.
- Fixed-capacity launcher pages with folders and persisted ordering.
- DragLayer-based long-press dragging, reorder targets, folder hotspot and edge paging.
- Multi-tab browser with a compact-width tab switcher.
- Managed WebView sessions, site settings and foreground-service integration.
- Notification permission and battery-optimization status surfaces.
- Project, contribution, agent and signed-release operating standards.

### Fixed

- Prevented remote icons and drag scaling from changing launcher cell geometry.
- Rebound WebView listeners and current-tab callbacks after tab switches.
- Applied persisted home-grid settings and paginated large app collections deterministically.
- Connected global keep-alive settings to persistent state and service control.

[0.2.0]: https://github.com/AIMFllyYS/PocketWebShell/releases
[0.1.0]: https://github.com/AIMFllyYS/PocketWebShell/releases
[旧线 0.2.0]: https://github.com/AIMFllyYS/PocketWebShell/releases/tag/v0.2.0
[旧线 0.1.0]: https://github.com/AIMFllyYS/PocketWebShell/releases/tag/v0.1.0

