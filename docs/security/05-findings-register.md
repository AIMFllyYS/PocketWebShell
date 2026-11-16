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

## 加固记录

上面的表是 2026-11-16 审查当时的状态，不改写成「已经修好」。下面是随后在 `fix/security-hardening` 上按 PADC（计划、分析、实现、核对）落地的结果。

| ID | 结果 |
|---|---|
| F1 | 已修。`BackupImportGuard` 在写入前拒绝绝对路径、`..` / `.` 段、空段、反斜杠、NUL 和百分号编码穿越，并用 canonical 路径确认落在目标目录内。任一坏条目使这次导入失败，不把缺树当成成功。 |
| F6 | 已修。选中的备份先按 512 MiB 上限拷进缓存，超出则删除临时文件并返回固定中文错误。解压后的展开体积仍没有单独上限。 |
| F2 | 已修。`allowBackup` 仍为 true，以便保留启动器布局和设置。`backup_rules.xml` 与 `data_extraction_rules.xml` 在云备份和换机传输中排除 `app_webview`（`root` 与 `device_root`）以及旧的 `webview.db`、`webviewCookiesChromium.db`。隐私文本两份副本已改成同一句。 |
| F4 | 文案已收紧。应用内「导出全部数据」仍包含 Cookie 和网站存储，提示和确认说明里写明了这一点。导出格式没有改。 |
| W1 | 已修。主框架 `data:` / `blob:` 被壳消费并报固定错误；子资源仍交给 WebView。 |
| F7 | 已修。桌面探针只记录宽度、断点、viewport meta 条数等数值。页面提供的 meta 原文、UA 字符串和异常消息不会进入日志。 |
| F8 | 已修。`ShellWebView.logHost` 在没有 host 时只返回短 scheme 或 `(no host)`，不再保留 URL 前 64 个字符。 |
| W4 | 部分已修。派发前去掉四个 URI 授权 flag 和 clip data，嵌套 Intent extra 直接拒绝。普通字符串 extra 保留，避免拆掉正常深链。 |
| W2 | 未改行为。设置文案已经写明「覆盖当前应用」和「没有返回历史」。 |
| F3、F5、W3、W5、W6、F9、W7、W8、W9 | 未改。它们是权限模型、已有回查、或规格里接受的边界。 |

## 审查当时的建议顺序

1. 在 `extractTree` 拒绝绝对路径、`..` 和 canonical 逃出 `target` 的条目，并补一个恶意 zip 的单元测试。已由 F1 完成。
2. 为备份增加排除规则，至少去掉 `app_webview` 与含 Cookie 的目录，或把 `allowBackup` 关掉并在隐私文本里改对应句子。已由 F2 完成，采用排除规则而不是关掉 `allowBackup`。
3. 拒绝主框架 `data:`。已由 W1 完成，主框架 `blob:` 一并拒绝。
4. 给桌面探针日志改成固定字段（宽度、断点），不要写入页面提供的 meta 原文。已由 F7 完成。
5. 给备份导入加上字节上限。已由 F6 完成。压缩包展开后的体积仍没有单独上限。
