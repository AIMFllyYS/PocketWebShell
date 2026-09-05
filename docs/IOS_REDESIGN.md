# iOS 风格呈现重构 · 0.1.14

## 项目定位与保留边界

玄览（PocketWebShell）不是普通书签页：它把网站和本地 HTML 包组织为桌面入口，支持固定容量分页、稀疏自由摆放、文件夹、多标签浏览及托管 WebView 会话。

| 层 | 职责 | 本次策略 |
|---|---|---|
| app | 主题装配、页面切换、系统安全区、会话入口 | 分离主题订阅、壁纸、Dock；系统栏遵循当前表面的对比度 |
| core/designsystem | 颜色、字体、玻璃、菜单、列表、导航 | 一套原生 Compose iOS 视觉语法 |
| feature/home | 页模型、固定网格、图标、文件夹、拖拽会话 | 把几何和单元呈现移出大屏幕文件；保留根级手势会话 |
| feature/add / me / browser | 添加、设置、浏览器 UI | 保留功能，统一控件与页面层次 |
| core/data / model | Room、DataStore、业务实体 | 不改数据库 schema 和键名；未设置过的默认图标从 56dp 调为 60dp，旧浮动资源库入口默认关闭（已有显隐值保留） |
| core/webengine | WebView 所有权、监听、会话隔离 | 不重写引擎；安全和回调归属不变 |

## 视觉基准

参考 Apple 公开的 [iOS 26 主屏与 Liquid Glass 设计](https://www.apple.com/newsroom/2025/06/apple-elevates-the-iphone-experience-with-ios-26/) 和 [主屏个性化说明](https://support.apple.com/guide/iphone/customize-apps-and-widgets-on-the-home-screen-iph385473442/26/ios/26)。目标是桌面比例、材质、层级和操作感的接近，而不是伪装成运行 iOS 的设备。

- 主页：全屏、静态、分辨率无关的流线壁纸；用户照片仍可替换。标题直接落在壁纸上，4×5 为原有默认格数；依可用宽高约束缩小图标以避免密集网格溢出。
- Dock：桌面为 88dp 高的四图标托盘，二级功能为 66dp 高标签栏；安全区另外计算。全局最多一个实时模糊层。
- 图标与文件夹：图标本体裁剪，文件夹九宫格预览；展开面板按页容纳成员，避免超屏。
- 菜单：文字在左、单色功能图标在右，删除动作红色置底。静态半透明材质，不开启第二处实时 blur。
- 设置：iOS 分组灰底/白卡与黑底/深灰卡，固定返回导航、分组行、绿色开关与本地预览滑杆。
- 字体使用仓库已有 MiSans，而不是打包未经授权的 Apple 平台字体；图标继续使用项目统一的 Material 图标来源。Android 状态栏、手势和权限弹窗保留真实平台行为。

## 性能与结构调整

1. `AppThemeViewModel` 把 DataStore 流投影为仅外观字段并 `distinctUntilChanged`。主题 Composable 不再自行构造 Repository，图标位置变化不会使主题根节点接收无关设置。
2. `LauncherBackdrop` 位于页面内容 padding 之外。默认壁纸通过 `drawWithCache` 保存 Path 与 Brush，没有无限动画或逐帧 CPU 位图模糊；照片由唯一 Coil 实例按屏幕约束加载。
3. Dock 的 Haze 半径显式固定为 20dp，低于 24dp 预算。其他材质只有静态填充、边缘光和描边。
4. 编辑抖动时钟仅在编辑态存在；拖拽图标仍在网格测量之外，热状态尽可能在布局/绘制阶段读取。优化方向依据 [Compose 性能阶段说明](https://developer.android.com/develop/ui/compose/performance/phases)。
5. Palette 降采样按最长边计算，长宽不再要求同时超界；取色任务在后台线程，结束回收临时 Bitmap，结果与路径绑定防止上一张照片颜色残留。
6. 设置滑杆的每次移动只更新本地预览，松手后提交 DataStore；避免一次拖动变成大量磁盘写入。

## 验证与局限

单元测试、端侧截图和手势验收结果见本任务最终交付说明与 `docs/verification/ios-0.1.14/VERIFICATION.md`。端侧新增 `IosPresentationInstrumentedTest` 使用临时夹具并在结束后恢复原始入口/设置；测试截图不包含真实浏览记录。

Android/Haze 并不提供 Apple 私有 Liquid Glass 光学引擎。材质近似、平台字体、图标形状和系统 UI 的差异是明确边界；不会声称像素级 1:1 或未经测量的帧率提升。API 29–32 兼容实现保留，但是否通过端侧验收以验证记录为准。
