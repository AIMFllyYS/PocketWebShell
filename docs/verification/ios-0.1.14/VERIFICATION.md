# 0.1.14 · iOS 风格与性能重构验收

日期：2026-09-06（本地 UTC+13）

分支：`feat/ios-launcher-redesign`，从干净的 `dev` 创建

交付形态：Debug APK，`versionName=0.1.14` / `versionCode=15`

## 验证环境

- Windows，Microsoft JDK 17.0.19，仓库 Gradle Wrapper，Android SDK 37。
- 专用新建 AVD：`webshell-ios-qa-api35`，Android 15 / API 35 / x86_64。未覆盖既有模拟器内的用户数据。
- 标准屏：1080×2400px / density 420，约 411dp 宽，字体 100%。
- 紧凑屏：960×1920px / density 480，即 320×640dp，字体 130%。
- 网页加载使用仓库内 WebViewAssetLoader 的离线 SPA 夹具；桌面截图使用临时网站入口、文件夹和首字母兜底图标，不是用户浏览数据。截图为应用真实运行截图，不是渲染稿。

## 检查结果

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | 62 项，0 失败、0 错误、0 跳过 |
| `:app:assembleDebug` | 成功 |
| 标准屏 `:app:connectedDebugAndroidTest` | 13 项，0 失败、0 错误、0 跳过 |
| 320dp / 字体 130% `:app:connectedDebugAndroidTest` | 13 项，0 失败、0 错误、0 跳过 |
| `:app:lintDebug` | 0 错误、21 警告；未关闭任何 lint 检查 |
| `git diff --check` | 通过 |
| `apksigner verify --verbose --print-certs` | 验签通过，Android Debug 证书，APK Signature Scheme v2 |
| 最终 APK 安装与冷启动 | 专用 API 35 模拟器安装成功，MainActivity 启动成功 |
| 新装空白主屏空闲采样 | 清零 `dumpsys gfxinfo` 后等待 5 秒，新增应用渲染帧为 0；不是交互帧率基准 |

APK：`dist/PocketWebShell-v0.1.14-debug.apk`，84,667,113 字节（约 80.74 MiB）。

SHA-256：`2da88b53642a310447bb74c08ecbe133e7dadc47bc70c55d49de6a50bb7ca1ec`

安装信息：`com.webshell.app`，Android 10+（minSdk 29），targetSdk 36。它是可安装的调试包，不是生产签名包；签名不同的正式版不能直接被它覆盖，安装前应保留现有数据。

单元测试包含：21 项页模型、7 项网格几何、2 项拖拽清理、8 项资源库索引/搜索、6 项设计系统采样/菜单位置、4 项浏览器地址呈现、6 项元数据图标排序与 8 项日志模型测试。几何覆盖 255dp 低高度、320dp 紧凑宽度、3–6 列、3–8 行及放大字体。

端侧测试包含：页内交换、长按菜单、左右边缘新建页、空白长按、编辑态即拖、旧浮动资源库入口、纵向桌面、浅深主题、资源库搜索、文件夹与第二页、浏览器本地页面加载/双标签/关闭后的返回、照片加载与清除。

最后一处资源库系统栏修正后再次完整执行标准屏 13 项（2m 8s，零人工干预）；320dp / 130% 字体的资源库搜索、取消与编辑路径又单独复跑 1 项（31s）。最终 APK 与构建目录 APK 的 SHA-256 完全一致。端侧测试结束后已恢复专用模拟器的显示/字体设置。

## 验收中修正的问题

- 完成按钮覆盖首排 → 固定预留编辑头部，进入编辑态不移动网格。
- 横屏页脚预留不足 → 先扣除真实 48dp 触控区及 4dp 间距。
- 放大字体资源库覆盖搜索、标题截字 → 真实头部测量、剩余高度布局、按字号计算单元。
- 文件夹面板透出清晰的背景图标 → 仅关闭背景网格的绘制，保留测量和状态；全屏 scrim 正确覆盖系统栏。
- 半透明文件夹中央矩形阴影 → 文件夹不使用硬件阴影；普通不透明图标仍保留轻阴影。
- 离开主屏期间白色标签失去壁纸 → 壁纸、内容和视口跟随同一转场分支。
- 打开全屏网站后保存状态容器销毁 → 状态容器提升至全屏分支之外。
- 拖拽取消后的迟到翻页回调重建落点 → 非活动会话忽略 `updateDropTargets`。
- 长 URL 绘制溢出至按钮 → 裁剪编辑器，失焦时省略号呈现同一 URL。
- Playbook 造成第二处实时模糊 → 演示改静态材质，实时 Haze 仅保留 Dock。
- 本机 `local.properties` 的 SDK 盘符缺少转义 → 修正为等价路径；该机器配置仍被 Git 忽略。

新测试本身也经过修正：弹窗退出后等待 UI 完成更新；点击输入框获得真实焦点后提交；空白长按按邻居的实测位置定位，不再用会在紧凑屏落入搜索胶囊的屏幕百分比。关闭 ActivityScenario 前明确退出编辑态，避免正常持续抖动让测试的“主队列空闲”等待拖延。失败或需要人工解除等待的轮次不作为最终自动化通过记录，清理修正后再次完整执行。

## 截图

- [浅色主屏](home-light.png) / [深色主屏](home-dark.png)
- [文件夹](folder.png) / [编辑态](home-edit.png) / [情境菜单](context-menu.png)
- [分组设置](settings-light.png) / [添加网站](add.png) / [浏览器双标签](browser-tabs.png)
- [320dp · 130% 字体主屏](compact-home.png) / [320dp · 130% 字体资源库](compact-library.png)

## 未覆盖与发布边界

- API 29–32 的 Haze 降级分支保留，没有对应设备端测。厂商 WebView、GPU 驱动、后台限制和真机手感仍需要实际硬件验证。
- 不把模拟器截图/单元测试等同于真机帧率或电耗基准；本次性能结论是已落地的结构性优化，没有声称“全机型稳定 120fps”等未测指标。
- Lint 的 21 条警告来自目标 API、现有依赖可更新提示、旧 API 守卫、后台电池策略和 KTX 建议，没有通过升级一整套依赖或屏蔽检查来消除它们。
- 原有浏览器摄像头/麦克风权限资源映射仍需独立专项验证，本次未重写权限或 WebView 引擎。
- 风格依据 iOS 26 的公开设计，但保留 MiSans、统一 Material 图标库和真实 Android 系统 UI；Haze 不是 Apple 私有 Liquid Glass 光学引擎，不宣称逐像素 1:1。
- 本记录描述 Debug 交付时状态：当时未构建正式 Release、未触碰发布私钥、未合并 main、未推送、未打 tag。后续正式发布以用户明确批准及 GitHub Release 为准。

设计与架构详情见 [IOS_REDESIGN.md](../../IOS_REDESIGN.md)。
