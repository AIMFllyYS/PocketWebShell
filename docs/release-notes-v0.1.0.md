# PocketWebShell 0.1.0

首个公开版本将网站添加、手机桌面和多会话 WebView 主流程整理成一套可安装应用。

## Highlights

- 自动解析网站标题和多来源图标候选。
- 固定容量桌面分页，支持文件夹和持久化顺序。
- 独立 DragLayer 长按拖动，支持普通重排、中心悬停合并和边缘翻页。
- 多标签浏览器与紧凑屏幕标签切换器。
- 站点显示策略、后台服务总开关、通知权限和电池优化状态。
- 新增 README、AGENTS.md、贡献、安全和签名发布规范。

## Installation and verification

下载 `PocketWebShell-v0.1.0.apk`。该 APK 使用项目发布证书签名，最低支持 Android 10（API 29）。

同时提供：

- `.apk.sha256`：安装包 SHA-256；
- `PocketWebShell-release-cert.pem`：发布者公开证书，仅用于身份核对，不包含私钥。

后台运行仍受 Android Doze、厂商策略、内存压力和站点自身节流约束。

