# 历史文档

这里说明哪些记录只提供背景。文件仍留在原来的路径，避免打断发布脚本和截图链接。

使用规则：

1. 现行行为以 [../README.md](../README.md) 列出的规范文档和仓库根目录 AGENTS.md 为准。
2. 历史文档里的行号、分支名、debug APK 路径、测试次数和版本号只描述当时。
3. 发现历史文档和代码冲突时，改规范文档，或在历史文档加一句取代说明。不要把历史文档改成第二份规范。

| 路径 | 时间背景 | 不要从这里取的现行值 |
|---|---|---|
| `PROJECT_ANALYSIS.md` | 四套原型整合 | 边缘翻页 450ms。现行约 900ms，见 AGENTS |
| `docs/IOS_REDESIGN.md` | 0.1.14 呈现范围 | 当时的范围锁定 |
| `docs/IOS_CLEANUP_PROGRESS.md` | 0.1.15 任务账 | `build/ios15/` 下的 debug 包路径。现行 debug 归档是 gitignore 的 `dist/` |
| `docs/BROWSER-REFRESH-HANDOFF.md` | 图标与强制刷新交接 | 交接分支名 |
| `docs/CORE-BROWSER-VALIDATION.md` | 0.1.23 验证 | 当时的测试总数和 versionCode |
| `docs/verification/ios-0.1.14/VERIFICATION.md` | API 35 仪器测试 | 当作当前回归已通过 |
| `docs/release-notes-v*.md` | 各次 Release 笔记 | 代替 CHANGELOG。发新 Release 时仍按这个文件名在 `docs/` 下新增 |

`docs/ENGINE.md` 的决策（继续用系统 WebView）仍然有效。文中以 0.1.6–0.1.7 行号描述的缺陷清单是诊断记录，不是当前源码地图。
