# 制作应用、网站图标与强制刷新：本地交接

本次从 `origin/main` 创建 `fix/browser-app-icons-refresh`，PR 目标为 `dev`。保持应用版本号，不合并、不打发布 tag、不构建正式 Release、不部署官网。最终真机验收、版本发布、原签名构建与官网 SSH 部署由维护者本地完成。

## 用户可见变化

- 浏览网页 → 左上角菜单 → 制作应用：普通 HTTP(S) 页面可进入预填编辑页，保存后成为主页中的持久应用。空白页与内部资源页不能冒充普通网站；导入 HTML 仍通过原有文件持久化路径保存。
- 图标优先接收 WebView 的 `onReceivedIcon` 位图。渲染后的 `link[rel]` 声明提供补充，支持 SVG、常见位图及 base64/百分号编码的内嵌图标。响应必须实际可解码，不能因 HTTP 200 就判定成功；失败会继续尝试后续候选。
- 自动图标存为应用私有目录的 PNG，文件名包含内容摘要，相同内容复用，更新内容使用新引用；主页、书签与历史复用结果。显式自定义图标受保护，备份保存其来源标记；旧备份中的本地图标保守视为自定义。
- 主页长按「强制刷新」会对存活会话执行绕资源缓存的重新加载；没有存活会话则持久记录下次打开的刷新请求，同时重新检查图标。完成、失败、取消或超时后恢复默认缓存策略。默认保留 Cookie 和网站业务存储，不自动重放 POST 表单。
- 我的 → 数据与存储 → 存储管理：可勾选网页/图片缓存、Cookie、全部网站数据、浏览历史。清理 Cookie 或网站数据会退出登录；网站数据包括离线内容。涉及网站数据的清理会关闭网页，已制作应用、用户导入文件、书签和自定义图标保留。
- 网站存储详情提供「重置此网站离线资源」：确认后关闭同源会话，通过不执行网站脚本的最小同源文档，调用 CacheStorage/Service Worker 接口删除离线资源并注销注册；不删除 Cookie、LocalStorage 或 IndexedDB。网站放在离线缓存中的未同步内容可能丢失。

## 能力与影响范围

- `WebView.clearCache(true)` 是所有 WebView 共享的资源缓存操作，不是按网站隔离的删除。网站数据删除优先使用 AndroidX 的公开 API 并检测能力；清理不再直接删除 Chromium 私有目录。
- 旧系统 WebView 没有完整网站数据删除接口时，只能执行可用的部分删除，界面报告未完整完成。建议在本地同时验证新版与旧版 WebView。
- HTTP 绕缓存不能保证绕过 Service Worker。站点仍返回旧离线页面时，使用明确的离线资源重置选项；不把清除登录状态当作默认强刷。
- Android WebView 没有公开、受支持的 DNS 缓存清空接口。本次没有实现或宣称系统/内核/上游 DNS 清空。重开会话不等于清掉 DNS；服务器/CDN 返回旧内容也无法靠客户端强刷保证修复。
- URL 添加/刷新时的补充 WebView 采集最多同时一个，有超时且结束后销毁；它与浏览数据清理串行，避免清理时又写回 Cookie。不会为每个主页图标常驻一个 WebView。
- 远程候选按公网地址校验、限制重定向及响应体大小；内嵌图标限制体积，使用成熟 SVG 解码库。没有关闭 TLS 校验或增加任意网页可调用的 Android JavaScript 桥。
- 本地 HTML 引用的外部文件若没有一同提供、也没有读取权限，不能凭空恢复；当前改动不扩展导入器为完整网站目录打包工具。
- 动态图标有原生回调及页面完成后的有限补充采样。任意很晚才注入、又不触发原生回调的声明，需重新打开/刷新触发采集，不进行常驻轮询。

## 本地检出与构建

已有仓库且工作区干净时：

```bash
git fetch origin
git switch --track origin/fix/browser-app-icons-refresh
./gradlew testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2
```

分支已存在时直接 `git switch fix/browser-app-icons-refresh`。Windows 将 `./gradlew` 换成 `.\gradlew.bat`；先保护本地未提交工作。环境按仓库 `AGENTS.md` 配置 JDK 17、Android SDK 37。

连接测试设备后，运行新增仪器测试：

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.webshell.app.WebIconRefreshInstrumentedTest
```

这些测试在生产 ShellWebView 上验证渲染后的内嵌 SVG → 实际 PNG 持久化、假图标回退、强制加载在 onPageStarted 仍绕缓存并在完成后恢复默认策略，以及离线资源重置删除 CacheStorage 但保留 LocalStorage。它不是完整菜单操作、DNS、Cookie 删除或厂商兼容验收的替代品。

## 真机验收清单

建议 OPPO 与华为各覆盖一轮，并记录 Android / 系统 WebView 版本。

1. 打开 `https://platform.deepseek.com/`，从浏览菜单制作应用；检查预填网址、标题、图标，保存、返回主页、重启应用后打开。验证空白页不可制作，取消不产生条目，连续点击不重复保存。
2. DeepSeek 页面当前声明的图标是 `https://fe-static.deepseek.com/platform/favicon.svg`；`/favicon.ico` 在分析时返回 HTML。检查使用真实图标，离线回到主页仍显示。真实站点未来可能变更，单元测试保留了该声明的固定样例。
3. 导入含 base64 PNG、base64 SVG、百分号编码 SVG 的单 HTML，以及脚本初始创建图标的页面；检查保存前后、本地文件持久化后和备份恢复后显示。
4. 自定义本地/远程图标，执行强刷、打开网页、备份再导入，确认用户图标未被自动覆盖。导入的自动图标仍可更新。
5. 自有测试站在 URL 不变、资源带长 max-age 时更新 HTML、JS、CSS、图标。分别在会话存活与会话已销毁时强刷，检查资源版本、下次启动结果以及 Cookie/本地存储保留；网络失败或取消后普通加载恢复正常缓存。
6. 为测试站设置离线 Service Worker；对比普通强刷和显式离线资源重置。检查同源标签影响范围，重置后离线缓存解除，Cookie/LocalStorage/IndexedDB 保留。
7. 分别清理缓存、Cookie、网站数据和历史，再组合清理。验证退出登录、历史删除、保留项和旧版 WebView 的部分完成提示。页面加载到一半时清历史，旧加载结束不应补回已清历史。
8. 快速切换标签、A→B 导航、SPA 更改地址，确认制作的是当前网页，图标不串到其他标签或登录重定向来源。
9. 若仍怀疑 DNS，使用自有测试域名和服务端请求日志区分 DNS 变化、已有连接复用、CDN 与页面缓存；不得把清缓存或重开成功当作 DNS 清理证据。上游 TTL、私人 DNS、VPN 等不在 WebView 清理接口控制范围。

## 云端验证记录与后续发布

已执行 `testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2`；399 项单元测试无失败，Debug 和仪器测试包编译通过。最后的 WebView 收尾改动另执行同模块单元测试与两类构建。具体命令与结果以本 PR 描述为准。云端无连接的 Android 设备且无 KVM，新增仪器测试仅能编译，不能宣称已在 OPPO/华为执行。自动化检查通过不代替上述真机验收。

本地验收后按 `docs/VERSIONING.md` 更新版本、CHANGELOG 与应用更新日志，按 `docs/RELEASE.md` 使用仓库外原签名，完成签名核验、发布 PR 和 GitHub Release；再按 `docs/WEBSITE.md`、`docs/OPS.md` 同步独立官网项目并通过本地 SSH 部署。签名材料和 SSH 凭据继续留在本地。
