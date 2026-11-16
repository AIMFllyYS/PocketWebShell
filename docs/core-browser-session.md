# 浏览会话模型

本文件是单一用户、共享 Profile 这条关键路径的规格。[ISSUES-core-browser.md](ISSUES-core-browser.md) 里的后续能力都以此为锚点，不另起一套隐式 Cookie 存储。

## 共享 Profile

浏览器标签、桌面入口、直链和本地导入使用 WebView 默认 Profile。Cookie、网站存储在进程内共享。`CookieManager` 的部分行为是进程级的：清理和第三方 Cookie 开关必须显式写成调用，不能假设「新 WebView 自带干净状态」。

按站点分 Profile、无痕、多账号不在这条路径上。那些能力列在 ISSUES 文档里，落地前要单独设计，不能用删掉共享数据来模仿。

## sessionId 归属

每个 `ShellWebView` 有一个 `sessionId`。

- 池以 sessionId 复用同一个实例。切标签或切回主屏只摘掉视图，不销毁会话。
- 持久 `sessionListener` 在会话存活期间写标题、URL、进度和返回状态，并且只写注册时绑定的那个标签。
- 临时 `listener` 只属于当前可见宿主。JS 对话框、文件选择、证书和明文确认只问这个可见宿主，不落到后台标签的 sessionListener。
- `WebViewClient` / `WebChromeClient` 带着回调世代。WebView 被替换后，旧世代的回调直接返回。
- 新窗口默认分配新的 `browser-*` 会话，不把弹窗的 transport 指回源页面。源页面要留在壳内时，使用显式的 `REPLACE_IN_SHELL`，并在下一帧清历史。

## 池

`WebViewPool` 用访问序做 LRU。激活会话和带保护原因的会话不被淘汰。保护原因包括前台、保活、文件选择、权限、下载、新窗口、全屏、认证和明文确认。保护必须成对解除。

淘汰前保存会话快照，重建时恢复。用户关闭标签走销毁且不留快照。池的上限是活实例数量，不是「永久后台」承诺。

## 加载边界

- 不默认 `SslErrorHandler.proceed()`。证书决定是一次一站的用户动作，超时则取消。
- 主框架 `http://` 必须经过可见确认。混合内容保持禁止。
- `local://` 只允许当前会话声明的本地应用 id，并且主文档还停在该应用上。拒绝时返回本地 403，不落到网络。
- 不向任意远程源暴露 Android 对象。blob 下载通过 `evaluateJavascript` 回传分片，不注册 JavaScript 接口。
- 外部 intent 拒绝显式组件、包名和 selector。

## 验证

单元测试覆盖路由、默认设置和存储记账。0.1.23 的手工矩阵在 [CORE-BROWSER-VALIDATION.md](CORE-BROWSER-VALIDATION.md)，那是历史快照，不是当前版本的通过证明。
