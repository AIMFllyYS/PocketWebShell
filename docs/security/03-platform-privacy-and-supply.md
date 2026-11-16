# 平台、隐私与供应链

## 权限与组件

清单里的权限与代码用途对应关系：

| 权限或组件 | 用途 | 审查备注 |
|---|---|---|
| `INTERNET`、`ACCESS_NETWORK_STATE` | WebView | 必要 |
| `FOREGROUND_SERVICE` 与 `SPECIAL_USE` | 用户打开的保活会话 | 服务不导出。文案没有承诺系统不会杀进程 |
| `POST_NOTIFICATIONS` | 前台服务通知 | 运行时申请 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 跳到系统电池设置 | 不能自己加入白名单 |
| 定位、摄像头、麦克风 | 仅在网页权限对话框授权之后 | 地理定位不保留；硬件 feature 不是 required |
| `READ_EXTERNAL_STORAGE` maxSdk 32 | 旧系统读公共下载目录 | 与注释一致 |
| `MANAGE_EXTERNAL_STORAGE` | 侧载时的 HTML 目录列举 | 见 F3。不据此声称 Play 合规 |
| `MainActivity` exported | 启动器与 VIEW/SEND | 必须导出。过滤器见 F9 |
| `DownloadEventsReceiver` exported | 系统下载广播 | 见 F5 |
| `FileProvider` 不导出 | 分享日志、备份、下载、拍照 | 路径表见 02 |
| `allowBackup=true` | 系统备份 | 见 F2 |
| `requestLegacyExternalStorage` | 旧式外部存储 | 只影响旧 API 行为 |

`queries` 里有 `mimeType="*/*"` 的 VIEW 和 SEND。这是包可见性，用来解析外部打开，本身不读那些应用的数据。

## 日志

抽查 `AppLog` 调用：

- 网页和下载日志走 host，不写查询串。
- 没有把 Cookie 值写进日志。存储探针记录的是 Cookie 数量和字节。
- 备份失败记录异常消息，消息有可能带上攻击者控制的短字符串。严重度低。
- 桌面探针是例外，见 F7。

日志可以导出。导出面应和「日志里允许出现什么」一起看，而不是只看单行 `log()`。

## 密钥与发布材料

本次 diff 没有加入 `.jks`、keystore、本地签名属性或令牌。`local.properties` 被 gitignore，审查环境里为了编译写入的 SDK 路径不应进入提交。

发布签名仍按 `docs/RELEASE.md`：密钥在仓库外，脚本用进程环境变量，不写进日志。本审查没有执行签名构建，也没有接触发布密钥。

## 依赖

设计系统文档要求同一能力只留一个库，并且许可证与 GPL-3.0-or-later 兼容。本次没有新增依赖。`feature/me` 的 DataStore 依赖曾经被误判为未使用：`SettingsRepository` 的公开签名带有 `Preferences`，调用模块必须能看见这个类。依赖已加回。死依赖规则见 [CODING-STANDARDS.md](../CODING-STANDARDS.md)，以「编译是否还需要这个类型」为准，不以字符串搜索为准。

`feature/viewer` 去掉了对 `feature:browser`、导航 Hilt 和 ViewModel Compose 的依赖，因为 `ViewerScreen` 已删除。剩下的 Markdown 渲染和 Hilt（`IncomingDocuments`）仍被外部打开路径使用。

## 供应链里没有做的事

- 没有跑依赖漏洞数据库。
- 没有核对 Gradle 校验和以外的传递依赖许可证全文。
- 没有检查 Play 预发布轨道的政策扫描结果。清单注释已经声明所有文件访问不面向 Play 的单文件选择场景。

这些留在登记表的「未覆盖」一节，避免把「读过源码」写成「依赖已无已知 CVE」。
