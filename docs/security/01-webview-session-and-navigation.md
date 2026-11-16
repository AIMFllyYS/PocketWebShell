# WebView、会话与导航

审查对象：`ShellWebView`、`ShellWebViewClient`、`ShellChromeClient`、`ShellBlobTransfer`、`UrlRouter`、`LocalWebHost`、`WebSessionRequestBridge`、`WebSessionDialogs`、`WebPageProbe`。

## 已经按规格落地的控制

这些和 [core-browser-session.md](../core-browser-session.md) 一致，审查时仍然在代码里。

- 全仓库没有 `addJavascriptInterface`。blob 下载只用 `evaluateJavascript` 把分片取回来，不向页面注册 Android 对象。
- `setAllowFileAccess(false)`、`setAllowContentAccess(false)`。`UrlRouter` 拒绝 `file` / `content`。`javascript:` 导航被吃掉。
- 混合内容是 `MIXED_CONTENT_NEVER_ALLOW`。信任锚只包含系统 CA，不信任用户 CA。
- 证书错误把 `SslErrorHandler` 交给可见宿主。引擎里确实组了一个 `proceed` 闭包（`ShellWebViewClient.onReceivedSslError`），但 `WebSessionRequestBridge` 只保存 `cancel`。对话框的「重试」和关闭都调用 `sslCancel()`，再由页面重试加载。15 秒无决定则 `cancel`。默认 `ShellListener` 的四参数重载会落到空的三参数实现，此时仍靠这 15 秒超时取消，不会放行。
- 主框架 `http://` 走 `CleartextGate`，按会话和 host 询问。不可见宿主直接取消。
- 本机应用树按 `localAppId` 限制。主文档离开该应用后，子资源再读 `/local/<appId>/` 会得到本地 403，不落到网络。路径段拒绝 `..`。
- `intent://` 在派发前拒绝 `component`、`selector` 和 `package`。
- 新窗口默认新建 `browser-*` 池会话，transport 不指回源 WebView。弹窗配置把 `localAppId` 清空，外部链接政策改为在应用内打开，避免把站点壳的「外链丢给系统浏览器」套到 OAuth 弹窗上。
- 回调带世代号。被替换的 WebView 上的旧回调直接返回。JS 对话框、文件选择和权限只问可见 `listener`，不写进后台标签的 `sessionListener`。
- 地理定位只对 https origin 弹窗，授权 `retain = false`。Web 权限只映射摄像头和麦克风，其余资源拒绝。
- HTTP 认证和客户端证书直接取消，壳不收集密码。
- blob 上限 32MiB，分片控制在 Binder 事务以内，文件写在 `cache/downloads/`，再经 FileProvider 交出。

## 发现

### W1 · 中 · 主框架可以加载 `data:` 和 `blob:`

`ShellWebView.routeUrl` 对 `UrlRoute.DATA` 和 `UrlRoute.BLOB` 返回 false，也就是交给 WebView 自己加载。`javascript:` 被拦截，`data:` 没有。

影响：页面可以把主框架切到 `data:text/html,...`，在应用内画出假登录页。地址栏通常会显示 `data:`，但紧凑界面不一定让用户看清。

这是加载策略的缺口，不是证书绕过。加固时应把主框架 `data:` 拒绝掉，并单独决定 `blob:` 文档是否还要留下。

### W2 · 中 · `REPLACE_IN_SHELL` 会在当前 WebView 里替换页面并清历史

`ShellChromeClient.onCreateWindow` 在该策略下 `transport.setWebView(host.webView)`，并置 `pendingClearHistory`。默认策略是 `ADOPT_IN_BROWSER`，会分配另一个池会话。

影响：用户或站点打开了「在当前页打开新窗口」之后，恶意页可以替换当前文档并清掉返回栈。这是已知的 tab-nabbing 形态，开关本身是产品选项。

审查不要求删掉这个选项。设置文案需要让用户知道它会替换当前页。代码注释已经写了 OAuth 不应使用这条路径。

### W3 · 低 · 渲染进程自动恢复会重载崩溃前的远程 URL

远程页（`localAppId == null`）崩溃后，`RendererRecoveryGate` 允许一次自动 `recoverRenderer(view.url)`。本地导入应用恢复到 `about:blank`，不自动重载。

影响：能把渲染进程打崩的页面会得到一次自动重试。预算用完后停止并通知用户。这是可用性取舍，不是无限重载。

### W4 · 低 · `intent://` 仍可能带上 extras

显式组件被拒绝之后，解析出的 Intent 仍按系统 `resolveActivity` 派发，extras 没有剥离。

影响取决于用户安装的其他应用是否导出了危险的 action。壳已经挡住「网页指定组件」这一类，剩下的是 Android 深链的普遍问题。

### W5 · 低 · 包内 `/assets/` 不按会话隔离

`LocalWebHost` 对 `/assets/` 前缀直接放行，远程页面只要知道 `https://appassets.androidplatform.net/assets/...` 就能取包内资源。这不是用户导入的文件，也不是 Cookie。泄露面是随包资源。

### W6 · 低 · 没有可见宿主时，400ms 后自动确认 `alert`

`JS_DIALOG_BACKGROUND_TIMEOUT_MS` 是 400。`alert` 的无人值守默认是接受，`confirm` / `prompt` / `beforeunload` 默认拒绝或取消。

目的是避免后台标签把渲染进程挂死。可见页仍走对话框。自动接受只影响后台或切换过程中的 `alert`。

### W7 · 信息 · 共享默认 Profile

正常路径 `profileId` 为空，`applyProfile()` 不切换。标签、弹窗和桌面入口共享 Cookie。这是 [core-browser-session.md](../core-browser-session.md) 写明的产品决定，不是意外。无痕和按站 Profile 仍在 ISSUES 清单里，不能用删共享数据来模拟。

### W8 · 信息 · 明文在平台层是允许的

`network_security_config.xml` 的 `cleartextTrafficPermitted` 为 true。主框架 HTTP 另有应用内确认。子资源是否全部经过同一闸门，要以具体 WebView 版本为准；混合内容仍然禁止。审查把「平台允许、应用只拦主框架」记为残留，不把它说成已经对所有子资源生效。

### W9 · 信息 · `WebPageProbe` 是带 JS 的短命 WebView

添加站点或刷新元数据时，探针 `loadUrl` 用户给出的 http(s) 地址，关文件访问、关混合内容，并在超时后销毁。它不是对内网的服务端请求。风险是用户主动触发的页面会在设备上跑一小段时间。
