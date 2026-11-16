# 发现项登记

状态一律是「已记录，未在本次变更中修复」。严重度按对这个应用的实际影响，不按通用清单的最高档。

| ID | 严重度 | 标题 | 位置 | 前提 | 本次是否回归 |
|---|---|---|---|---|---|
| F1 | 高 | 备份解压未拒绝 `..` | `BackupRepository.extractTree` | 用户导入恶意 `.pws` | 否，原有代码 |
| F2 | 高 | 系统备份未排除浏览数据 | `AndroidManifest` `allowBackup` | 设备备份或换机 | 否 |
| W1 | 中 | 主框架可加载 `data:` / `blob:` | `ShellWebView.routeUrl` | 页面导航或重定向 | 否，拆分后策略相同 |
| W2 | 中 | 可选策略在当前 WebView 打开弹窗并清历史 | `ShellChromeClient.onCreateWindow` | 设置或站点选择了 `REPLACE_IN_SHELL` | 否 |
| F3 | 中 | 清单声明所有文件访问 | Manifest `MANAGE_EXTERNAL_STORAGE` | 用户在系统里授予 | 否 |
| F4 | 中 | 完整备份含 WebView 配置 | `BackupRepository` FULL | 用户导出并分享该文件 | 否 |
| F5 | 低 | 下载完成接收器导出，但按 id 回查本应用下载 | `DownloadEventsReceiver` | 同设备应用发送显式 Intent，且系统未保护该 action | 否 |
| F6 | 中 | 导入前整包拷贝无大小上限 | `BackupRepository` 读 URI | 用户选择巨大文件 | 否 |
| F7 | 中 | 桌面探针把 viewport 文本写入可导出日志 | `ShellWebView` 桌面探针 | 桌面模式打开恶意页，再导出日志 | 否 |
| W3 | 低 | 崩溃后自动重载远程 URL 一次 | `RendererRecoveryGate` | 页面能触发渲染进程崩溃 | 否 |
| W4 | 低 | `intent://` extras 未剥离 | `launchIntentUri` | 设备上有危险的导出组件 | 否 |
| W5 | 低 | 包内 assets 可被任意会话读取 | `LocalWebHost` `/assets/` | 页面知道包内路径 | 否 |
| W6 | 低 | 无可见宿主时 400ms 后接受 `alert` | `ShellWebViewLimits` | 后台标签调用 `alert` | 否 |
| F8 | 低 | 无 host 的 URL 日志保留前 64 字符 | `logHost` | 非 http(s) 导航 | 否 |
| F9 | 低 | VIEW/SEND 过滤器宽于运行时策略 | Manifest 与 `IncomingFilePolicy` | 其他应用发起 VIEW/SEND | 否 |
| W7 | 信息 | 共享默认 Profile | `applyProfile` | 产品默认 | 否，规格如此 |
| W8 | 信息 | 平台允许明文，应用拦主框架 | `network_security_config.xml` | 访问 HTTP | 否，规格如此 |
| W9 | 信息 | 元数据探针短时间执行页面 JS | `WebPageProbe` | 用户添加或刷新该 URL | 否 |

## 明确排除

- 没有 `addJavascriptInterface`。
- 发行界面没有调用 `SslErrorHandler.proceed()`。引擎保留了闭包，浏览层不保存、不调用它。
- 混合内容保持禁止。
- 本次 diff 没有加入密钥、keystore 或签名口令。
- 重构没有把一个标签的回调改写到另一个 sessionId。

## 未覆盖

- 依赖 CVE 与许可证全文扫描。
- 真机 WebView / 厂商对所有文件访问和后台限制的行为。
- 其他应用如何处理本应用发出的 `intent://` extras。
- 渗透测试或模糊测试。zip-slip 的判断来自路径拼接的代码，没有用样例包在设备上执行。

## 建议的后续顺序（不在本次实施）

1. 在 `extractTree` 拒绝绝对路径、`..` 和 canonical 逃出 `target` 的条目，并补一个恶意 zip 的单元测试。
2. 为备份增加排除规则，至少去掉 `app_webview` 与含 Cookie 的目录，或把 `allowBackup` 关掉并在隐私文本里改对应句子。
3. 拒绝主框架 `data:`。
4. 给桌面探针日志改成固定字段（宽度、断点），不要写入页面提供的 meta 原文。
5. 给备份导入加上字节上限。
