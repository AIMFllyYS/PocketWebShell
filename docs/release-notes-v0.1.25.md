# 玄览 PocketWebShell 0.1.25

这是核心浏览器壳对照审查后的补齐迭代。正式 Release 说明须在用户确认同号 Debug 构建后，按 `docs/VERSIONING.md` §5 汇总本版本与上一正式版本之间的全部变更。

## 本版本更新

- 网页 `alert` / `confirm` / `prompt` / `beforeunload` 由应用对话框承接；不可见会话立即完成回调，避免卡住渲染进程。
- 存储管理新增「清除全部网站数据」：确认后关闭会话、删除 Cookie 与网站存储，并明确提示会退出登录。
- 共享默认 Profile 的网站详情不再提供会失败的「按站清理」；仅遗留独立 Profile 目录仍可单独清缓存。
- Blob 下载读取期间保护会话，避免被 WebView 池淘汰。

## 验证边界

- 已运行 `testDebugUnitTest` 与 `:app:assembleDebug`。
- 真实站点的 JS 对话框、退出登录后跨入口 Cookie 失效，以及下载中切换标签需真机确认。
