# 玄览 PocketWebShell 0.1.22

本次正式版把 PocketWebShell 的核心定位收敛为“单用户、共享登录态、接近普通移动浏览器的 WebView 壳”。保存的网站入口、浏览器标签、直接打开的链接和本地导入网站统一使用默认 WebView Profile，登录 Cookie 与站点存储可以跨入口复用。

## 浏览器核心能力

- 统一 URL 路由：明确区分 HTTP/HTTPS、`about:blank`、外部 Intent、`blob:`、`data:`、`javascript:`、`file:`、`content:` 和未知 scheme；外部协议在交给系统前检查可解析 Activity，失败时向页面显示可理解的反馈。
- `target=_blank` 与 `window.open()` 现在创建真实的浏览器会话并携带来源 session 身份，不再使用短生命周期的临时探针 WebView；新会话沿用共享 Profile 和当前配置。
- 页面导航、标题、进度、返回栈、错误、下载、权限、认证、全屏和 renderer 恢复均按 sessionId 回写，后台标签不会串台或丢失 UI 状态。
- 网站设置变化会动态应用到池中已有会话；WebView 池会保护可见、保活、文件选择、权限、下载、认证和全屏中的会话，并在淘汰后保留可恢复状态。
- 补齐摄像头、麦克风、地理定位、文件选择、视频全屏、HTTP Basic Auth、客户端证书请求和 SSL 错误的明确处理；不安全能力不会静默放行。
- 普通下载携带必要的登录态请求头并安全处理文件名；Blob 下载通过受限桥接、大小上限和 FileProvider 交付，失败与完成均有反馈。

## 恢复、预览与资源边界

- renderer 崩溃会标记会话为恢复中，保存 URL/历史状态，替换失效 WebView，重新应用配置并自动重试一次；重试失败会显示可重试错误而不是黑屏。
- 标签页缩略图在首帧后生成，绑定 session 与 URL 版本，限制尺寸和内存，并避免旧任务覆盖新页面。
- 本地导入页面继续共享浏览器登录态，但资源请求严格限制在自身 `appId` 的 canonical 目录内，不能读取其他本地应用或应用内部路径。
- 静态元数据抓取增加响应体、重定向、最终协议、HTML/Manifest 类型和私网地址校验；失败时由运行时 WebView 作为回退来源。

## 存储语义

- 存储页面改为明确展示“共享默认 Profile”：清理浏览器缓存不会删除 Cookie/LocalStorage；清除全部 WebView 数据会明确提示退出登录，并在有活动会话时先安全处理。
- 不再把不存在的按站 Profile 目录当作成功清理，也不再宣称可以独立清除某个远程网站的全部数据。

## 验证与已知范围

- 全模块 `testDebugUnitTest`：178 项通过，0 失败，0 跳过。
- `:app:assembleDebug`、`:app:lintDebug` 通过；lint 无错误，仅有既存的 API/依赖提示。
- 本正式包使用项目既有外部发布密钥签名，并随 Release 提供 SHA-256 与公开证书。
- 当前工作环境没有 Android 真机/模拟器和 `adb`，因此 B 站/OIDC 登录、真实 WebView 权限/下载/全屏、renderer 崩溃恢复、进程重建和 API 29–32 设备差异仍需在设备矩阵上人工回归；详情见 `docs/CORE-BROWSER-VALIDATION.md`。

## 安装与升级

下载 `PocketWebShell-v0.1.22.apk`，最低支持 Android 10（API 29）。这是正式签名包，不是 Debug 包；同时提供 `.apk.sha256` 校验文件和 `PocketWebShell-release-cert.pem` 公开证书。后台运行仍受 Android Doze、厂商策略、内存压力和站点自身节流约束。
