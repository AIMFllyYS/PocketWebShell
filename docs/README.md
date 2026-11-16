# 文档索引

本目录是 PocketWebShell 的文档地图。改行为、改命令或改约束时，先用这里判断该更新哪一份，再动手。生命周期见 [DOC-LIFECYCLE.md](DOC-LIFECYCLE.md)，代码形状见 [CODING-STANDARDS.md](CODING-STANDARDS.md)。代理契约仍以仓库根目录 [AGENTS.md](../AGENTS.md) 为准。

## 怎么读

| 标记 | 含义 |
|---|---|
| 规范 | 必须与当前代码、`app/build.gradle.kts` 的版本号一致 |
| 运行手册 | 发布、官网、运维步骤；例子里的版本号是模板，不是当前版本 |
| 补充规格 | 某一产品行为的权威说明，AGENTS 不变量仍然优先 |
| 历史 | 某次已结束工作的记录。可以对照，不能当作现行规则 |

## 规范

| 文档 | 何时更新 |
|---|---|
| [../AGENTS.md](../AGENTS.md) | 模块边界、启动器/WebView 不变量、测试矩阵、发布门禁变化 |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | 分支、提交、PR 检查清单变化 |
| [../SECURITY.md](../SECURITY.md) | 支持范围、私下报告方式、WebView 安全边界变化 |
| [CODING-STANDARDS.md](CODING-STANDARDS.md) | 文件拆分、依赖方向、死代码、日志纪律变化 |
| [DOC-LIFECYCLE.md](DOC-LIFECYCLE.md) | 「哪类改动必须改哪份文档」变化 |
| [DESIGN.md](DESIGN.md) | 颜色、玻璃、组件库、主屏视觉规则变化 |
| [PERFORMANCE.md](PERFORMANCE.md) | 模糊、手势、解码、重组预算变化 |
| [VERSIONING.md](VERSIONING.md) | 版本号规则或 debug-first 发布门禁变化 |
| [legal/PRIVACY.md](legal/PRIVACY.md)、[legal/TERMS.md](legal/TERMS.md)、[legal/LICENSE-NOTICE.md](legal/LICENSE-NOTICE.md) | 个人信息处理、使用说明、许可摘要变化。必须同步 `feature/me` 的 raw 副本 |

## 运行手册

| 文档 | 作用 |
|---|---|
| [RELEASE.md](RELEASE.md) | 签名、验签、打 tag、GitHub Release |
| [OPS.md](OPS.md) | 正式发布后的打包、上传与冒烟。公开文本不得写入密钥、服务器地址或密钥库文件名 |
| [WEBSITE.md](WEBSITE.md) | 每次正式 Release 之后同步官网。debug 迭代不触发 |
| [OPEN-SOURCE.md](OPEN-SOURCE.md) | 校园贡献说明与 GPL 入口 |
| [../README.md](../README.md) | 面向使用者的功能、架构图和快速开始 |

## 补充规格

| 文档 | 作用 |
|---|---|
| [core-browser-session.md](core-browser-session.md) | 单一共享 Profile、sessionId 归属、池保护。扩展项见 [ISSUES-core-browser.md](ISSUES-core-browser.md) |
| [WEB-TOP-INSETS.md](WEB-TOP-INSETS.md) | 网页顶部避让的三种模式与 CSS 变量 |
| [ENGINE.md](ENGINE.md) | 「继续使用系统 WebView」的决策，以及其后的集成层记录。文首标明哪些段落已过时 |
| [../feature/browser/README.md](../feature/browser/README.md) | 浏览呈现层与会话请求门面的模块边界 |

## 历史（留在原地，不删除）

这些文件保留路径，是因为发布脚本、验证截图或后续对照还指着它们。阅读时以文首状态或下表为准，不要把里面的毫秒数、行号或 APK 路径当成现行规则。

| 文档 | 记录的是 |
|---|---|
| [historical/README.md](historical/README.md) | 历史文档怎么用 |
| [../PROJECT_ANALYSIS.md](../PROJECT_ANALYSIS.md) | 四套原型整合时的决策。边缘翻页时长已被 AGENTS 的约 900ms 取代 |
| [IOS_REDESIGN.md](IOS_REDESIGN.md)、[IOS_CLEANUP_PROGRESS.md](IOS_CLEANUP_PROGRESS.md) | 0.1.14 / 0.1.15 的 iOS 呈现范围 |
| [BROWSER-REFRESH-HANDOFF.md](BROWSER-REFRESH-HANDOFF.md) | 图标、强制刷新、存储的一次交接清单 |
| [CORE-BROWSER-VALIDATION.md](CORE-BROWSER-VALIDATION.md) | 锁在 0.1.23 的验证记录 |
| [verification/ios-0.1.14/VERIFICATION.md](verification/ios-0.1.14/VERIFICATION.md) | 0.1.14 仪器测试矩阵 |
| `release-notes-v*.md` | 各次 GitHub Release 的笔记快照。聚合规则见 VERSIONING。新的笔记仍放在 `docs/` 根下，文件名保持 `release-notes-v<versionName>.md`，发布脚本按这个路径取文件 |

## 安全审查（时点记录，不是规范）

2026-11-16 的审查只记录发现，没有在同一变更里做加固。索引和五份正文在 [security/README.md](security/README.md)。后续修复应改代码和对应规格，并在登记表里标出已关闭的编号，而不是改写当时的审查结论。

## 法律文本同步

`docs/legal/*` 是应用内页面和官网的源。应用内副本在 `feature/me/src/main/res/raw/`。改法律文本时三处一起改：本目录、raw 副本、以及正式发布后的官网（见 WEBSITE.md）。
