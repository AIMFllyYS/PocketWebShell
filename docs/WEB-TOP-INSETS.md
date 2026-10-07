# 网页顶部避让

「我的 → 浏览体验 → 网页顶部避让」提供三个全局模式。选择保存在 DataStore 的 `web_top_inset_mode`，旧安装或非法值回退到自动；导出/导入设置也包含该选项。

| 模式 | 行为 |
|---|---|
| 自动 | 使用当前可见状态栏/挖孔的顶部安全边界，仅补足网页宿主尚未避让的距离 |
| 强制避让 | 使用不依赖系统栏可见状态的安全边界，保留必要顶部距离 |
| 强制不避让 | 取消应用额外的网页顶部边距及传给网页的重复顶部安全区 |

系统或父容器已经预留的空间不再叠加；强制避让也只保留一次。未取得布局坐标时先保守避让，布局稳定后重新计算。没有品牌名单，也不根据网页滚动自动切换模式。

站点壳默认由原生容器处理顶部。浏览标签显示地址栏时保留原生按钮和地址栏的正常安全区，隐藏地址栏时将网页顶部归属交给 WebView 宿主。原生主页、设置页、Markdown、悬浮控件、视频独立全屏逻辑保持原有布局。

## 边距所有权

- 顶部剩余距离在同一窗口坐标系计算：`max(0, safeTop - hostTop)`。已由父层负责顶部时为零。
- 原生 PAD 模式下 `--ws-safe-top` 为零，并从传给子 WebView 的 statusBars/displayCutout 中清除顶部距离。侧边、底部、waterfall 和键盘信息保留。
- 现有内部 `CSS_ONLY` 模式的契约是页面消费 `--ws-safe-*` 自定义变量；它不依赖标准 `env()` 来处理顶部。产品界面的三模式继续使用 PAD。
- 自定义变量中的物理像素按文档的 devicePixelRatio 与 visualViewport.scale 换算为 CSS 像素。
- 调整模式不重建 WebView，不主动重新加载页面；重新布局可能改变视口尺寸，网站自己的响应式布局仍然生效。

强制不避让允许内容靠近或进入状态栏/挖孔区域。网站写死的 margin/padding、系统强制窗口限制及其他网页设计留白，不会被批量改写或保证消除。

## 已验证与待验证

本节内容随 0.1.65 发布（versionName `0.1.65`、versionCode `66`）；PR 阶段曾以 `0.1.64` 源码交付。

- 完整 `testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` 通过。
- 新增回归覆盖已有父层/系统偏移、部分偏移、未布局状态、手动模式、持久化取值兼容；另有 Android 兼容层测试确认清除顶部后侧/底挖孔、waterfall、导航栏和键盘距离仍保留。单元测试合计 384 项，无失败。
- 维护者已在 API 35 模拟器执行 `:app:connectedDebugAndroidTest`（`WebTopInsetInstrumentedTest`，1 项通过），并完成主页、站点壳三模式、浏览标签地址栏与设置项实时切换的人工冒烟；OPPO / 华为真机、键盘和厂商 WebView 差异仍建议真机对照。

在已启动且连接的 Android 设备上运行：

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.webshell.app.WebTopInsetInstrumentedTest
```

Windows 使用 `./gradlew.bat`。仪器测试使用生产站点壳与离线表单内容，检查三模式的实际顶部坐标、标准 CSS 安全区与自定义变量、切换后的同一 WebView、表单值和滚动位置；最后恢复原设置。截图写入应用的外部文件目录。

还需在 OPPO K12s 和华为上人工对照同一网页、相同滚动位置，检查：登录页与 StudySolo、三模式、地址栏显示/隐藏、横竖屏、弹出键盘、返回前台，以及切换前后登录输入和页面位置。仪器测试没有代替键盘可见性、浏览地址栏显隐与厂商 WebView 的实机验收。
