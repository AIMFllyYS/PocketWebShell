# 文档生命周期

规范文档必须描述当前代码。历史文档只加状态说明，不用「改到和最新行为一致」的方式重写。版本号以 `app/build.gradle.kts` 的 `versionName` / `versionCode` 为准。

## 改动对照

| 改动 | 要更新 |
|---|---|
| 主页拖拽、分页、文件夹、编辑模式 | AGENTS 的启动器不变量；必要时 DESIGN、PERFORMANCE。跨页行为补 `feature/home` 单测 |
| 浏览标签、WebView、池、证书、明文、下载 | AGENTS 的浏览器不变量；[core-browser-session.md](core-browser-session.md)；行为变化时更新 ENGINE 里仍标记为规范的段落，不要改历史行号诊断去「对齐」 |
| 网页顶部避让 | [WEB-TOP-INSETS.md](WEB-TOP-INSETS.md) 与设置文案 |
| DataStore 键或 Room 表 | 迁移或文档化的破坏性例外；AGENTS 数据节。不静默改键名 |
| 颜色、玻璃、组件库 | DESIGN.md，库的取舍同时看 CODING-STANDARDS 的死依赖规则 |
| 用户可见行为 | CHANGELOG.md，以及应用内更新日志 `UpdateLogEntries.kt`。两处文案保持同一事实 |
| 正式 GitHub Release | 另写 `docs/release-notes-v<versionName>.md`。只在发 Release 时新增，不在普通 PR 里补历史笔记 |
| 法律文本 | `docs/legal/*` 与 `feature/me` raw 副本。正式发布后再按 WEBSITE.md 同步官网 |
| 只改文档命令或贡献流程 | README、CONTRIBUTING、本索引中对应的那一行 |
| 安全审查 | 新的时点记录放在 `docs/security/`，并在 [README.md](README.md) 挂上索引。审查不自动改产品代码 |

版本号递增只按 [VERSIONING.md](VERSIONING.md)。重构分支本身不消耗版本号；交付给用户安装的 debug 包才进入该流程。

## 历史文档

`PROJECT_ANALYSIS.md`、`IOS_*`、`BROWSER-REFRESH-HANDOFF.md`、`CORE-BROWSER-VALIDATION.md`、`verification/**` 和 `release-notes-v*.md` 是历史。发现它们和代码不一致时：

- 在历史文档加一句「已被哪份规范取代」
- 改规范文档
- 不把历史文档改写成第二份规范

发布笔记继续放在 `docs/release-notes-v*.md`。不要挪进子目录，[RELEASE.md](RELEASE.md) 按这个文件名取笔记。

## 代理

先读最近的 AGENTS.md，再读 [README.md](README.md) 里和本次改动对应的那一行。不要为了「整理」去重写历史交接记录。不要把审查结论直接改成产品代码，除非任务明确要求修复。
