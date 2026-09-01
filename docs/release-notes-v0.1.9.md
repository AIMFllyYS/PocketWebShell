# 玄览 PocketWebShell 0.1.9

## Fixed

- 修复长按菜单永远钉在左上角：Popup 内部的全屏遮罩把弹窗内容尺寸撑成整个屏幕，定位钳制失效、菜单被压到 (margin, margin)。移除内置遮罩，外部点击关闭改由 Popup 自带的 outside-touch 机制承担。菜单现在稳定地以按压点为中心、在图标附近弹出。

## Installation and verification

下载 `PocketWebShell-v0.1.9.apk`。该 APK 使用项目发布证书签名，最低支持 Android 10（API 29）。

同时提供：

- `.apk.sha256`：安装包 SHA-256；
- `PocketWebShell-release-cert.pem`：发布者公开证书，仅用于身份核对，不包含私钥。

后台运行仍受 Android Doze、厂商策略、内存压力和站点自身节流约束。
