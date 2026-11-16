# 代码规范

本文件补充 [AGENTS.md](../AGENTS.md)，不重复启动器几何、拖拽和 WebView 会话归属那些不变量。两者冲突时以 AGENTS 为准。

## 模块方向

依赖只能朝下走：

```text
app  →  feature/*  →  core/data、core/webengine、core/designsystem  →  core/model
```

- `feature` 之间默认不互相依赖。`feature/viewer` 只保留外部 VIEW/SEND 的解析、拷贝和 Markdown 渲染；浏览标签与站点壳在 `app` 和 `feature/browser`。
- `core/model` 不依赖 Android 框架，除非某个类型离开 Android 就无法表达。
- Composable 不调用 DAO、DataStore、原始 WebView 或前台服务。平台对象留在已有生命周期所有者里。
- 新代码用 Kotlin。Hilt 构造注入。不新增服务定位器或可变全局单例。`WebViewPool` 是既有的进程级池，不照着它再做一个。

## 一个文件一件事

软目标：一个逻辑文件不超过约 400 行。超过约 500 行时，只要文件里出现第二个变更理由，就拆到同一包的新文件，保持原有可见性能够编译（同模块用 `internal`，不把模块内部类型提升成公共 API）。

适合拆出去的缝：

- 静态数据目录和绘制它的 Composable（更新日志）
- 平台请求状态、Activity Result 桥、对话框（浏览会话请求）
- WebView 的 `WebViewClient` / `WebChromeClient`、blob 下载状态机、与宿主类分开
- 网格组合根和菜单/对话框浮层
- 只被测试引用、生产路径已经不用的几何辅助函数：删除，而不是再拆一个文件养着

不要为了行数拆开下面这些。它们各自是一个状态机或一个纯策略对象，拆开会复制私有助手并让不变量漂移：

| 文件 | 留在一起的原因 |
|---|---|
| `HomeGestures.kt` | 单元格手势、根级拖拽会话、边缘翻页是一条通道。AGENTS 要求拖拽循环不挂在会被分页销毁的 cell 上 |
| `HomePages.kt` | 容量、落点和密度下标是同一套纯函数，测试按这个对象锁定 |
| `SiteShellViewModel.kt` | 一次打开解析、一个按 session 归属的监听者、Markdown 分支 |

允许超过约 800 行、且本次已经把第二个职责拆出去的所有者：

| 文件 | 大约行数 | 已经拆出 | 仍留在所有者里的原因 |
|---|---:|---|---|
| `ShellWebView.kt` | 950 | `ShellWebViewClient`、`ShellChromeClient`、`ShellBlobTransfer`、`ShellWebViewLimits`、`ShellJsDialogResults` | 回调世代、监听者归属、路由、渲染进程恢复和设置必须对着同一个 WebView 实例。再拆路由会把证书、明文和本机资源判断从所有者上拉开 |
| `BrowserViewModel.kt` | 850 | `BrowserOpenTabMappings` | 多标签编排。按 session 注册的监听者留在 ViewModel 里，避免捕获的 tabId 和写入函数分开之后串台 |
| `UpdateLogEntries.kt` | 700 | 分页 UI 在 `UpdateLogScreen.kt` | 纯目录，没有逻辑。新增版本只改这份数据和 CHANGELOG |

`HomeViewModel`、`AddViewModel`、`BackupRepository` 目前各约 570 行，仍是单一编排器。出现第二种持久化边界（例如导入执行与导出打包不再共享私有状态）时再拆，不要按函数个数平均切开。

拆分时：

- 同一包、同一模块。先 `internal`，不要为了搬文件放宽到其他模块。
- 公共函数签名保持不变，调用方不必改包名。
- 拖拽浮层仍然在 `HorizontalPager` / `LazyVerticalGrid` 的测量之外。
- WebView 回调仍然按构造时的 sessionId / tabId 写回，不读「当前激活标签」。

## 死代码

下面这些不算在用，发现就删，并删掉只为它存在的测试、字符串和 Gradle 依赖：

- 生产路径没有引用，仪器测试、Hilt 绑定、导航路由、资源引用里也没有
- 只被单测调用、而界面已经不再根据它布局的函数
- 只写不读的状态（没有观察者的生命周期表）
- 声明了但源码没有用到其类型的库。某个依赖如果只出现在别的模块的公开函数签名里（例如 `SettingsRepository` 暴露 DataStore 的 `Preferences`），调用方模块仍然要保留它，不能只因为本模块没有写出 `DataStore` 三个字就删掉

保留：调试目录 `CatalogHarness`、Playbook 样本、`ShellListener` 的默认空实现、以及为了会话归属而故意留在监听者上的空回调。

URL 规范化保持两条政策：添加/保存用 `AddUrl.normalize`，地址栏用 `UrlRouter` / `BrowserUrl`。不要合成一个函数。主机名展示若要合并，必须先让失败时的回退（空串或原 URL）有测试锁住。

## Compose 与资源

- 从约束测量，不从机型宽度测量。远程图片不能决定格子的宽高。
- 可点目标尽量不小于 48dp，并写有意义的 content description。
- 用 `collectAsStateWithLifecycle`。重组期不要启动会改持久状态的副作用。
- 新增用户可见文案放进资源，不把中文或英文字面量写进新的 Composable。已有日志与开发者诊断字符串维持现状，不借重构批量改文案。

## 日志与秘密

日志只记 host、状态码、有界原因。不记完整 URL、查询串、Cookie、凭据、密钥库路径或签名材料。释放签名不进仓库。细节以 AGENTS 和 SECURITY.md 为准。

## 测试

按 AGENTS 的测试矩阵跑最窄的相关任务，再跑该矩阵要求的最终命令。本次这类跨模块拆分的最终命令是 `testDebugUnitTest` 与 `:app:compileDebugKotlin`（或 `assembleDebug`，二者在验证编译产物时等价；交付 debug 包时才需要 assemble 并归档 `dist/`）。

主页手势的仪器测试仍是 `app/src/androidTest/.../HomeGestureInstrumentedTest.kt`，需要真机或模拟器，不在单元测试里冒充通过。
