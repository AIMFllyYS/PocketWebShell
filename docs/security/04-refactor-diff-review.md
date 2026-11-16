# 本次重构的安全回归

对照的是文档治理、文件拆分和死代码删除，不是一份新的网络功能。

## 没有改变行为的移动

| 原位置 | 现位置 | 核对过的控制 |
|---|---|---|
| `ShellWebView` 内部 `ShellClient` | `ShellWebViewClient` | 世代检查、本机 403、证书超时取消、渲染恢复只触发一次自动重试 |
| 内部 `ShellChromeClient` | `ShellChromeClient` | 可见宿主才接收 JS 对话框；默认新窗口是新会话；`REPLACE_IN_SHELL` 仍是显式策略 |
| blob 私有方法 | `ShellBlobTransfer` | 32MiB 与分片上限、`PENDING_DOWNLOAD` 成对保护、导航时 `onMainFrameNavigated`、恢复时 `invalidate`、释放时 `detach` |
| 常量 | `ShellWebViewLimits` | 超时和错误码数值未改 |
| `WebSessionRequests.kt` 后半 | `WebSessionRequestBridge`、`WebSessionDialogs` | 地理定位仍限 https；证书对话框仍不调用 `proceed` |
| 主屏浮层 | `HomeScreenOverlays` | 文件夹分享仍用 FileProvider 和读授权 |
| Markdown 渲染 | `SafeMarkdown` | 链接仍限 http(s)，拒绝 userinfo |

`ShellWebView` 上若干字段从 `private` 改成 `internal`，可见性停在 `core/webengine` 模块内，没有变成应用外的 API。

拆分时修过一处会改变行为的笔误：`config.copy` 的具名参数曾被误写成 `host.sessionId = ...`，编译失败，已改回 `sessionId = targetSessionId`。若留在运行时，新窗口会无法编译，而不是静默开错会话。

## 删除项

| 删除 | 安全影响 |
|---|---|
| `ViewerScreen` / `ViewerViewModel` | 外部 HTML/Markdown 已由 `IncomingBrowserOpener` 和站点壳接管。ENGINE 已改成不再指向已删除类型。旧查看器拒绝收养弹窗的行为没有留在新路径上；弹窗仍是 `localAppId = null` 的浏览会话 |
| `WebViewPool` 的 lifecycle 表、`resumeSession`、`lifecycleState` | 只写不读，没有观察者。暂停渲染仍走 `suspendSession` |
| `LocalWebHost.localAppId` | 无调用方。AssetLoader 用的 `localAppIdFromHttpsUrl` 还在 |
| `splitProfileBytes` | 无调用方。存储汇总走现有的 bucket |
| `DownloadCompleteCard` | 界面未被挂上。下载胶囊路径还在 |
| 编辑栏里只被单测调用的宽度/堆叠函数 | 不参与运行时布局 |

## 残留（不是这次引入的漏洞）

1. 三个 WebView 文件现在要一起改，证书或本机资源规则才不会只更新一处。`ShellWebViewLimits` 和 `core-browser-session.md` 用来减少这种漂移，不能代替复查。
2. `BrowserViewModel.openIncomingMarkdown` 仍把 Markdown 原文放进标签。当前外部 Markdown 走站点壳，并经过 `IncomingMarkdownPolicy`。若以后有调用方直接走这个函数，需要先消毒。审查时它不是外部打开的入口。
3. `detach()` 仍不删除未完成的 blob 临时文件。重构前的 `release` 同样只抬高 token 并解除保护。缓存残留不是跨应用读取。

## 建议的手工冒烟（未在本次执行）

坏证书对话框只取消或重试、主框架 HTTP 确认、跨 `localAppId` 子资源被拒绝、导航取消 blob、外部 HTML 进浏览标签、外部 Markdown 在站点壳里不加载远程图片。仪器测试需要设备和 `:app:connectedDebugAndroidTest`，本次单元测试不能代替它。
