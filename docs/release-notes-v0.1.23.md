# 玄览 PocketWebShell 0.1.23

这是在 `0.1.22` 核心浏览器闭环基础上的边界与恢复加固版本。正式 Release 说明须在用户确认同号 Debug 构建后，按 `docs/VERSIONING.md` §5 汇总本版本与上一正式版本之间的全部变更。

## 本版本修复

- 本地导入资源读取权限绑定当前会话 `appId`；浏览器/直链会话不能读取其他本地应用文件，编码路径穿越也会被拒绝。
- 修复 Blob 下载回调解析，继续执行 512 KiB 编码/解码上限，并强制通过 FileProvider 交付。
- 修复应用级 WebView 调起外部协议时缺失 `FLAG_ACTIVITY_NEW_TASK`；`intent:` URI 不允许显式 component、package 或 selector。
- renderer 连续崩溃最多自动恢复一次；替代 WebView 再次崩溃时进入可观察失败状态，不再无限重建。
- HTTP 主文档错误会结束会话 loading；后台 `target=_blank` 窗口立即绑定持久 session listener。

## 验证边界

- 全模块 Debug 单测、Debug APK 构建和 lint 结果写入 `docs/CORE-BROWSER-VALIDATION.md`。
- 真机/模拟器 WebView provider、登录态、权限、下载、全屏、renderer 实际崩溃恢复和 API 29–32 差异仍必须在设备可用后回归；不能用无设备环境代替这些验收。
