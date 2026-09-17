# 玄览 PocketWebShell 0.1.56

本次正式版在 `0.1.55` 基础上，针对开屏启动进行了深入的渲染管线性能调优，并全面验证了应用内通过 GitHub Releases 自动检查更新的闭环。

## 开屏极致流畅优化（Zero-Recomposition）

- **零重组绘制管线（drawWithCache）**：天体微标全面迁移至 `drawWithCache` 硬件加速绘制管线。几何尺寸、画笔（Stroke）与径向渐变（RadialGradient）在尺寸稳定后仅计算与分配一次；动画帧旋转仅在 `onDrawBehind` 绘制闭包中捕获，彻底消灭 Compose 每帧重组（Recomposition）与堆内存垃圾分配。
- **免 CPU 文本重复排版**：固定衬线艺术字字距，光引展开动效全面迁移至 GPU 硬件加速的 `graphicsLayer`（缩放、透明度与垂直平移）统一驱动，消除冷启动期间每帧 CPU 文本测量（TextLayoutResult）造成的微卡顿，达成 120fps 满帧如丝般顺滑的启动体验。

## 应用内检查更新验证

- 全流程验证设置页「检查新版本」功能，自动对比 GitHub 官方 Release 资产并弹出下载更新确认。

## 验证与已知范围

- 发布前全量运行 `.\gradlew.bat testDebugUnitTest :app:assembleDebug` 全部通过。
- 本正式包使用项目既有外部发布密钥签名，并随 Release 提供 SHA-256 与公开证书。

## 安装与升级

下载 `PocketWebShell-v0.1.56.apk`，最低支持 Android 10（API 29）。这是正式签名包，不是 Debug 包；同时提供 `.apk.sha256` 校验文件和 `PocketWebShell-release-cert.pem` 公开证书。后台运行仍受 Android Doze、厂商策略、内存压力和站点自身节流约束。
