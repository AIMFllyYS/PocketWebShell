# 玄览 PocketWebShell 0.1.62

本次正式版系统性修复浏览板块「桌面版」切换不生效的问题。该修复横跨 0.1.58–0.1.62 五个迭代（0.1.58–0.1.61 为未发布的 debug 验证版，版本号按规范消耗），最终在真机探针证据闭环下定稿。

## 「桌面版」切换修复（0.1.58–0.1.62 合并说明）

- **缓存复用移动版页面**：桌面/移动切换改为绕过 HTTP 缓存的 reload，主文档强制按新 UA 重新请求，服务器真正看到桌面身份。
- **注入脚本脆弱**：document-start 引导脚本全面加固（幂等守卫、MutationObserver 挂 document 根、DOMContentLoaded 兜底），不支持该特性的老 WebView 自动降级为页面回调注入。
- **viewport 改写被站点覆盖**：改写遍历页面全部 viewport meta（Blink 对多个 meta 按后解析者覆盖先前者，只改第一个等于没改）；页面完成后强制重断一次。
- **UA-CH 身份信号从未生效**：修复 `UserAgentMetadata` 构造漏填 fullVersion 必抛异常的问题（0.1.58–0.1.60 整套 Client Hints 从未真正设置）；桌面身份对齐 Windows / x86 / 64 位，支持新特性的 WebView 同步声明 Form-Factors；设置后回读校验，失败显眼报错。老内核/定制 WebView 不支持 UA-CH 时明确记日志，身份仅靠 UA 字符串。
- **缩放与布局宽**：初始缩放移除 100% 上限（宽屏铺满）；布局宽 980 → 1280，越过现代响应式站点的桌面断点（992/1024/1200）——真机实测 980 布局下连 `(min-width:980px)` 断点都不命中。
- **本地导入页面**（外部 HTML / 本地应用）禁用「桌面版」入口，消除无效切换的误导。
- **可观测性**：新增桌面模式运行时探针——身份链（`navigator.userAgentData`）与布局链（布局宽、全部 viewport meta、媒体查询断点、visualViewport 缩放）在应用日志（我的 → 开发者选项 → 日志查看）中分开验收。

## 验证与已知范围

- 全量 Gradle 单元测试通过（新增 UA-CH 构造双模式、布局宽断点、缩放公式等断言）。
- 真机（HwWebview 114 / Android 12）验收：切换桌面版后布局宽 1280、桌面断点全部命中、整页缩放与捏合放大正常。
- 已知边界：少数按 `screen.width` 物理宽、触控能力（`pointer:coarse`）等信号判定设备形态的站点，注入式方案无法改变——这是 WebView 方案的天花板。
- 本正式包使用项目长期外部发布密钥签名，并随 Release 提供 SHA-256 与公开证书。

## 安装与升级

下载 `PocketWebShell-v0.1.62.apk`，最低支持 Android 10（API 29）。这是正式签名包，不是 Debug 包；同时提供 `.apk.sha256` 校验文件和 `PocketWebShell-release-cert.pem` 公开证书。后台运行仍受 Android Doze、厂商策略、内存压力和站点自身节流约束。
