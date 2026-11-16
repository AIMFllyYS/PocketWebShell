# 安全审查（2026-11-16）

这次审查读的是重构之后的当前树。审查本身不改产品代码。发现项留在本目录，加固另开变更。

## 范围

- WebView 宿主、导航、证书、明文、本机资源、blob 下载、新窗口
- 备份导入、外部 VIEW/SEND、本机 HTML、下载与 FileProvider
- Manifest、权限、网络配置、日志、Android 备份
- 本次重构是否改变上述路径的行为

未覆盖：真机上的 WebView 版本差异、厂商对 `MANAGE_EXTERNAL_STORAGE` 的拦截、以及未安装的第三方应用对 `intent://` extras 的具体处理。

## 方法

1. 对照 AGENTS / SECURITY.md 里已经写明的边界（不静默 `proceed`、按 sessionId 归属、不把密钥写入仓库）。
2. 读 Manifest、网络安全配置、FileProvider 路径和对应 Kotlin。
3. 三条并行阅读：WebView 与导航、文件与备份、本次 diff 是否引入回归。
4. 行号以审查时的源码为准。后续提交会让行号漂移，以符号名为准复查。

## 结论摘要

没有确认到可远程、无需用户动作的 Critical 问题。

需要单独排期的高优先级项：

1. 备份 zip 解压没有拒绝 `..`（应用 UID 内的 zip-slip）。
2. `android:allowBackup="true"` 且没有排除 WebView 配置与 Cookie。

其余是产品已接受的边界（共享 Profile、用户确认后的明文、可选的「在当前页打开弹窗」）、需要用户主动授予或导出的中等问题，或低严重度残留。下载完成广播的接收器虽然导出，但会按 action 和本应用的下载 id 回查，不单列为中等。

本次文件拆分和死代码删除没有发现中等及以上的安全回归。证书取消、本机资源 403、intent 显式组件拦截、blob 大小上限都还在拆出去的类型里。

## 文件

| 文档 | 内容 |
|---|---|
| [01-webview-session-and-navigation.md](01-webview-session-and-navigation.md) | 引擎、证书、明文、本机资源、弹窗 |
| [02-local-files-backup-incoming.md](02-local-files-backup-incoming.md) | 备份、导入、下载、外部打开 |
| [03-platform-privacy-and-supply.md](03-platform-privacy-and-supply.md) | Manifest、权限、日志、依赖与签名 |
| [04-refactor-diff-review.md](04-refactor-diff-review.md) | 这次重构的回归审查 |
| [05-findings-register.md](05-findings-register.md) | 发现项总表 |

这些文件是时点记录，不是规范。规范仍是 [../core-browser-session.md](../core-browser-session.md) 和仓库根目录 `SECURITY.md`。
