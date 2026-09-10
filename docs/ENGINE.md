# 浏览器引擎诊断与选型决策

> 版本：v1（2026-08-31，随 0.1.7 落地）。本文回答三个问题：
> ① 为什么"很多网站（B 站等）打不开"？② 为什么标签页互相串台？③ 该用什么引擎？
> 结论先行：**问题不在引擎，在引擎集成层。引擎维持 Chromium（Android System WebView），本版本重构集成层。**

## 1. 现状引擎是什么

项目 `core/webengine` 构建在 **Android System WebView** 之上（`androidx.webkit:webkit:1.17.0`）。
Android WebView 不是"自造引擎"，它就是 **Chromium**——与 Chrome 同源的渲染/网络/JS 栈，
随 Play 商店独立更新。就"能否打开网站"而言，Chromium 是事实标准，理论上没有引擎比它兼容性更好。

因此"B 站打不开"不是引擎能力问题，而是以下集成层缺陷与环境问题的叠加。

## 2. "网站打不开/登录态差"的真实根因

| # | 根因 | 位置 | 机理 |
|---|------|------|------|
| R1 | Profile 策略不一致 | `ShellSessionController` / `ShellWebView.applyProfile()` | 当前产品明确使用 WebView 默认共享 Profile；浏览器标签、桌面入口、直链和本地导入统一共享 Cookie/存储，只有未来扩展才允许非空 profileId |
| R2 | WebView 默认 UA 含 `; wv)` | `ShellWebView.configureBaseSettings()` | WebView 默认 UA 带 `Version/4.0` + `wv` 标记，部分站点（含 Google 登录、若干移动站点）识别为"内嵌壳"而拒绝/降级服务 |
| R3 | 弹窗探针 hack | `ShellWebView.onCreateWindow` | 用裸 WebView 探针截 URL、1.5s 后强销毁；依赖 `window.open` 的登录/OAuth 流程会断 |
| R4 | 设备 WebView 过旧 | 环境 | 无 Play 商店的国产机型 WebView 版本可能很旧，新网站特性/证书/安全策略不支持——这是"有的手机打不开"的最大环境变量，需要在应用内可观测（开发者中心展示 WebView 版本并提示更新） |
| R5 | Cookie 写入时机 | `CookieManager.flush` 只在 onPageFinished | 异常中断时登录态落盘不可靠（缓解：关键生命周期点 flush） |

## 3. "标签页串台/竞争"的真实根因（App 层状态管理缺陷）

详细诊断见本节，缺陷编号沿用时以 `文件:行号` 为准（行号基于 0.1.6）。

- **B1（主因）**：`BrowserScreen` 的 `progress/loading/canGoBack/pageTitle/pageUrl` 是**屏幕级单一状态**，切 tab 不重置 → A 的标题/进度/返回状态显示在 B 上。
- **B2（主因）**：后台 tab 的 `ShellWebViewHost` 出组合时 `listener=null`（`ShellWebViewHost.kt:58`），后台加载完成时的标题/URL/历史回调**全部丢失**；切回后 WebView 不重发 → 数据永久过期，切回时"显现"为串台。
- **B4（结构性）**：`WebViewPool` 是**插入序 FIFO**（注释自称 LRU），`maxLive=6` 满时**可淘汰正在显示的激活 tab**，对 attach 中的 WebView 调 `destroy()` → 黑屏/错乱；且浏览器 `browser-*`、`ShellScreen` 的 `browse-*`、网页应用 `app.id` 三方共享同一 6 格池，互相淘汰。
- **C2**：会话快照 `saveSessionState()` 是**死代码**——存进实例字段后实例即弃，`restoreSessionState` 全仓无调用方；"淘汰后恢复返回栈"的承诺不成立。
- **C3/C4**：后台 tab 从不 `onPause`（JS/音视频继续跑——"以为关了其实没关"的观感来源）；`ShellScreen.onNewWindow` 把旧 `browse-*` 会话遗留在池中无人销毁（泄漏）。
- **C1/B3/B5/B7**：关激活 tab 后尾部回调仍写旧 tabId；收藏用残留的 A 标题存 B 的 URL；地址栏与真实 URL 长期脱节；残留 canGoBack 致返回键假死。

**结论：不存在线程级 race，根因是"单一共享 listener + 屏幕级 UI 状态 + 后台回调整体丢弃"，叠加"全局 6 格 FIFO 池对活跃会话无保护"。**

## 4. 成熟引擎候选评估（2026-08 调研）

| 候选 | 内核 | 维护状态 | 体积 | 兼容性 | 集成成本 | 结论 |
|------|------|----------|------|--------|----------|------|
| Android System WebView（现状） | Chromium，随 Play 更新 | Google 持续维护 | 0（系统组件） | **最好（事实标准）** | 已集成 | **保留为主引擎** |
| GeckoView（Mozilla） | Gecko，[release 渠道持续发版](https://mvnrepository.com/artifact/org.mozilla.geckoview/geckoview-arm64-v8a)（2026-08 已到 154.x） | Mozilla 持续维护 | arm64 单 ABI AAR 约 80MB+（[参考](https://mvnrepository.com/artifact/org.mozilla.geckoview/geckoview-nightly-omni-arm64-v8a)） | 对 Chromium 优化的中文站点存在兼容长尾；Firefox Android 可正常开 B 站 | **极高**：GeckoRuntime/GeckoSession 全异 API，会话/导航/下载/权限全部重写 | 否决不迁移；留作未来可插拔后端 |
| 腾讯 X5 / TBS | Chromium 121（[官网](https://x5.tencent.com/docs.html)，2026 年仍在发安全更新） | 腾讯维护中，但闭源 | 内核约 30MB（静默下载或离线打包） | 好（Chromium），微信/QQ 同源验证 | 中（API 镜像 android.webkit，近似 drop-in） | **备选**：仅当"设备自带 WebView 过旧"被证实为主要矛盾时引入；闭源 + 依赖腾讯下发服务，不默认捆绑 |
| Crosswalk | Chromium 53 | **2017 年已停更** | 大 | 差（过旧） | 中 | 否决 |
| Chrome Custom Tabs | 用户已装 Chrome | Google 维护 | 0 | 好 | 低 | 不适合"网站即应用"壳形态（无 JS 注入/无定制控件），否决 |

**为什么不迁 GeckoView**：用户的硬需求是"理论上可访问基本所有网站"。中文互联网大量站点只对 Chromium 做兼容测试；迁到 Gecko 是用更低的站点兼容性换一个与本问题无关的引擎，同时付出约 80MB 包体积和全量重写。这与目标背道而驰。

**为什么 X5 只是备选**：X5 解决的是"设备 WebView 过旧"（R4），代价是闭源 SDK、首次静默下载内核、依赖腾讯服务存续。在 R1–R3 修复后先观测线上表现；若旧 WebView 设备仍是主要矛盾，再按 `core/webengine` 的接口边界做 X5 后端（其 API 与 android.webkit 同构，迁移面可控）。

## 5. 本版本（0.1.7）实施方案

在 `refactor/browser-session-engine` 分支实施，全部落在引擎集成层，不动引擎本身：

1. **回调按会话归属（修 B1/B2/B3）**：`ShellWebView` 增加持久 `sessionListener`（随会话存活，出组合不摘除），BrowserViewModel 为每个 tab 注册；标题/URL/进度/返回栈状态**按 tabId 存入 per-tab 状态**，后台 tab 更新不再丢失；UI 从 ViewModel 的 per-tab 状态读取。
2. **池语义修正（修 B4/C2）**：LinkedHashMap 改 access-order 真 LRU；**激活、保活、权限/文件/全屏操作中的会话受保护不被淘汰**；淘汰/关闭时快照写入池级 `sessionId → Bundle`，重建时恢复（打通 C2）；淘汰只移除 renderer，不删除标签 UI 状态。
3. **共享登录态（修 R1）**：所有当前入口统一使用 WebView **默认共享 Profile**（cookie/token 全入口共享）；`MULTI_PROFILE` 仅展示为未来能力，不作为本版本安全边界。
4. **兼容加固（修 R2/R5）**：移动模式默认使用不含 `wv` 的 Chrome 移动 UA；`thirdPartyCookies` 显式双向设置；`onPause`/`onStop` 等关键时机 flush Cookie。
5. **泄漏与生命周期（修 C3/C4/C5）**：切走的 tab `onPause` 暂停渲染/媒体，切回 `onResume`；`ShellScreen.onNewWindow` 先销毁旧 `browse-*` 会话；`closeTab` 清理 `desktopModes`。
6. **可观测（对 R4）**：开发者中心展示 WebView 包名/版本，版本过旧时引导用户到应用商店更新。

## 6. 验证

- 必跑：`gradlew testDebugUnitTest :app:assembleDebug`（AGENTS.md 测试矩阵，Browser/WebView/session 域）。
- 手测清单（需真机）：B 站首页与视频页加载、登录态跨标签共享、开 2+ 标签互切无串台、池满后激活 tab 不被杀、关闭 tab 后音频停止。

## 7. 0.1.22 核心闭环补强

- `UrlRouter` 是地址栏与 WebView 回调共用的 scheme allowlist；外部 Intent 必须通过
  `resolveActivity()`，`file/content/javascript` 不会从地址栏进入 WebView。
- `onCreateWindow` 对所有入口（浏览器标签、保存的网站、直链）统一分配真实的 pooled WebView，
  不复用来源页面自己的 WebView（0.1.26 起对保存网站/直链也是如此，见 §9）；OAuth 不再依赖短生命周期探针。
- `WebViewPool` 的保护原因集合覆盖活动、保活、文件/权限、全屏和下载；淘汰保存快照但保留
  标签 UI 状态。renderer gone 在原壳中替换 child WebView、恢复历史并自动重试一次。
  HTTP Auth 立即安全拒绝，因此不会占用认证保护槽。
- 文件、摄像头/麦克风、定位、全屏、下载、JavaScript 对话框和 SSL 请求都以当前 session 的生命周期为边界；
  默认拒绝未知能力，Blob 仅通过受限的一次性 JS 读取且有大小上限。可见宿主承接
  `alert`/`confirm`/`prompt`/`beforeunload`；后台会话立即完成回调，避免卡住渲染进程。
- 本地资源处理器先解析 appId 再做 canonical 子路径检查，故共享 Cookie/Profile 不会扩大本地
  文件读权限；元数据抓取限制响应体、重定向、最终协议并阻断私网地址。

## 8. 0.1.23 核心边界与恢复加固

- `WebViewAssetLoader` 按 `ShellConfig.localAppId` 建立会话级本地目录能力；没有该能力的浏览器、
  直链和远程网站会话不能读取 `/local/<appId>/…`，编码后的分隔符、点段和 canonical 路径也会被拒绝。
- Blob 下载的 `OK:<mime>:<base64>` 回调由独立纯解析器处理，先解开 `evaluateJavascript` 的 JSON 字符串，
  再执行 Base64/字节数上限与 FileProvider 校验；不会回退到 `Uri.fromFile`。
- 应用上下文发起外部 Activity 时按 Android 任务规则补 `FLAG_ACTIVITY_NEW_TASK`；`intent:` URI 同时拒绝
  component、package 和 selector 指定，避免嵌入页面劫持显式目标。
- renderer 连续崩溃由单次自动恢复闸门保护：稳定页面或明确用户导航才重置预算，替代 WebView 立即再次崩溃时进入失败回调而不循环重建。
- HTTP 主文档错误主动结束 loading；`target=_blank` 创建的真实 pooled WebView 在后台也立即绑定对应 tab 的持久 listener。

## 9. 0.1.26 单用户全局登录态收口

产品基线重申：本应用没有多账号/多 Profile 系统，行为对齐普通浏览器——一套 Cookie、一套站点数据，登录一次处处有效。本版本系统性修掉了几处仍然"按站点想问题"的合同缺口：

- **`onCreateWindow` 不再区分来源类型**：站点壳（保存的网站/直链）的 `window.open`/`target=_blank` 现在和浏览器标签一样，总是分配一个真实的 `browser-window-*` pooled 会话（`profileId=null`，共享 Default），绝不再 `transport.setWebView(来源页面自己的 view)`。分配前用新增的 `WebViewPool.ProtectionReason.PENDING_WINDOW` 保护来源会话，避免池满淘汰正在处理回调的源页面。`ShellScreen` 收到 `onNewWindow` 后把这个会话交给 `MainScaffold` 领养：离开站点壳、切到 Browse、`BrowserViewModel.createTabForSession(...)`。`BrowserViewModel` 现在在 `MainScaffold` 顶层用 `hiltViewModel()` 提起，站点壳打开时它依然活着。
- **收藏星标只是展示态**：`configuredSiteShell` 只看 `WebAppEntity.externalLinksToBrowser` 这一个显式开关决定外链策略；`isFavorite` 不再参与判断。主页星星不应该悄悄把 OAuth 跳转踢给系统浏览器。
- **本地文件权跟随当前主文档 origin，不跟随会话曾有的能力**：`LocalWebHost.isAllowedLocalUrl` 新增 `isMainFrame`/`documentUrl` 参数——主框架导航只看 appId 是否匹配（不变）；但子资源请求（iframe/`<script src>`/图片等）还要求当前主文档本身仍解析到同一个 `/local/<appId>/` 树。一旦主文档离开本地 host，`allowedLocalAppId` 这个会话级能力就不再对子资源生效。
- **存储管理不再假装"按站清理"**：共享 Default Profile 下没有可安全单独清理的站点边界，详情页整段"清理该站点"入口已移除。「清除全部网站数据」现在先 `CacheClearer.destroySessions`（`WebViewPool.destroyAndForget`，不留返回栈快照）再调用 `WebStorage.deleteAllData()`/`CookieManager.removeAllCookies()`，之后额外遍历删除 `IndexedDB`/`Local Storage`/`Session Storage`/`Service Worker`/`databases`/`blob_storage` 等站点数据目录（Default + 任何遗留 Profile 目录）作为纵深防御。
- **"结束会话"是生命周期动作，不是登出**：`MeViewModel.stopSessions` 只更新保活列表 UI；真正的 `WebViewPool.suspendSession` + 前台服务收尾现在桥接到 `MainScaffoldViewModel.closeSessions`（走 `ShellSessionController.closeSession`），因为 feature 模块不能依赖 `app` 模块。
- **JS 对话框只发给可见宿主**：`ShellChromeClient` 的 `onJsAlert/Confirm/Prompt/BeforeUnload` 只投给 `listener`，不再落回 `sessionListener`（否则会静默继承 `ShellListener` 接口默认实现——alert 自动确认、confirm/prompt/beforeunload 自动拒绝）。没有可见宿主时给一个很短的宽限期（`JS_DIALOG_BACKGROUND_TIMEOUT_MS`，重新检查一次 `listener`）再落到安全默认值。
- **`ShellWebView.reconfigure` 补齐结构相等短路径**：`ShellWebViewHost` 的 `SideEffect` 每次进度/标题回调都会重新调用 `reconfigure`；现在 `old.mergedWith(newConfig) == old` 时直接返回，不再每帧重跑 `configureBaseSettings()`/`CookieManager` 调用。
- **下拉刷新首帧不再被覆盖**：`SiteShellViewModel.pullToRefreshEnabled` 从 `stateIn(..., false)` 改为 `stateIn(..., null)`；`ShellScreen` 只在拿到真实值后才覆盖 `config.pullToRefresh`，避免用尚未读完的设置把 `ShellSessionController.openSession` 已经烘焙好的值抹掉。`openDirectSession` 现在也读取该设置。
