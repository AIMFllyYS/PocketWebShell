# 设计基准（DESIGN）

本文件是 PocketWebShell 视觉设计的权威基准。任何 UI 改动必须与本文件一致；AGENTS.md 引用本文件。

## 1. 配色理念

以苹果设计理念为准：

- **浅色模式 = iOS 分组灰底白卡**：背景 `#F2F2F7`（systemGroupedBackground）托纯白卡片，层次靠"灰底/白卡/发丝描边"与留白区分，不靠彩色块、不靠阴影堆叠。
- **深色模式 = 纯黑基底**：背景 `#000000` 体系，卡片 `#1C1C1E`，文字近白。
- **次要文字 AAA 级可读**：副标题/备注统一 `onSurfaceVariant = #55555B`（白卡上 7.4:1，达 WCAG AAA）；禁用踩线灰。
- **中性灰统一冷调**：全站灰阶与品牌蓝同温区，杜绝"粉肉色"观感。
- **强调色克制**：全局单一强调色，只用于选中态、关键操作与进度指示，不大面积铺色。
- 不支持动态取色的设备同样遵守以上基底，不回退到彩色主题。

## 2. 主题模式

应用提供四种主题模式，体验对齐手机系统"换壁纸即换主题"：

| 模式 | 行为 |
|---|---|
| 跟随系统 | 随系统深浅色切换纯白/纯黑基底 |
| 纯白 | 固定浅色 |
| 纯黑 | 固定深色 |
| 照片壁纸 | 用户上传照片作为主页壁纸，并从照片提取主色生成整套配色 |

照片主题规则：

- 照片只作为主页壁纸背景（`ContentScale.Crop`），网格区叠加可读性 scrim，保证图标与文字可读。
- 主色用 AndroidX Palette 从壁纸提取；取色与图片解码必须在后台线程。
- 照片复制到应用私有目录后持久化路径，不直接持久化 content URI。

## 3. 玻璃拟态（Liquid Glass）基准

底部导航及浮层组件以 iOS Liquid Glass 为视觉基准：

- 半透明材质 + 背景实时模糊（backdrop blur）；
- 边缘 1dp 量级的高光描边，模拟玻璃受光；
- 悬浮胶囊形态，大圆角，与内容之间留有间距；
- 选中态指示器动画不得改变布局边界。

实现统一使用 Haze 库（`dev.chrisbanes.haze`），高光/折射参数参考 Kyant0/AndroidLiquidGlass 调优。禁止自绘第二套模糊实现。

## 4. 组件库选型原则

- **优先引入成熟开源组件库/SDK**，不自己造轮子；自绘模仿达不到成熟库的真实效果。
- **同一功能只引入一个统一的库**（例如玻璃模糊统一用 Haze），不为同类效果堆砌多个库。
- 引入前确认：开源协议兼容（项目本身开源，Apache-2.0/MIT 等可直接使用）、维护活跃、支持 minSdk 29。
- 选型结论记录在本文件或相关模块文档中，避免重复调研。
- **禁止保留未使用的库声明**：曾声明未用的 Lottie、reorderable 已于 0.2.0 清理。

### 4.1 前端设计组件库完整清单（权威）

| 能力 | 库 / 版本 | 说明 |
|---|---|---|
| UI 基座 | Jetpack Compose BOM 2026.08.00（`ui` / `ui-graphics` / `material3`） | 唯一 UI 框架，M3 组件为素材底座 |
| 图标 | `material-icons-core` + `material-icons-extended` 1.7.8 | 全端统一图标来源，禁止引入第二套图标库 |
| 玻璃模糊 / backdrop blur | Haze 1.7.2（`dev.chrisbanes.haze:haze` + `haze-materials`） | 全屏至多 1 处实时模糊（底部导航），见 PERFORMANCE.md |
| 图片加载 | Coil 3.6.0（`coil-compose` + `coil-network-okhttp`） | 网站图标、壁纸预览统一入口 |
| 壁纸主色提取 | AndroidX Palette 1.0.0（`palette-ktx`） | 照片主题取色，后台线程执行 |
| 字体 | 内置 MiSans（regular / medium / semibold，子集化 TTF） | 全设备字度量一致，杜绝系统字体替换导致的文字截半 |
| 动效 | Compose Animation（随 BOM）+ `AppMotion` token | 150–250ms 过渡 + 弹簧曲线，不引入 Lottie/GIF 动图库 |

已确认不引入：Lottie（动效需求由 Compose Animation 覆盖）、第三方拖拽库（主页 DragLayer 为自研且受 launcher 不变量约束）。

## 5. 统一设计系统层（core/designsystem）

自 0.2.0 起，全部页面必须构建在统一设计系统层之上，禁止绕过。

### 5.1 设计 token

| token | 位置 | 规则 |
|---|---|---|
| 配色 | `theme/Color.kt` | 浅/深两套全量 M3 token 显式定义；副标题/次要文字一律 `onSurfaceVariant`，`outline`/`outlineVariant` 只用于描边与分割线 |
| 字体 | `theme/Type.kt` | MiSans 字阶：标题 Semibold、功能文本 Medium、正文 Regular；行高 ≥ 1.3× 字号 |
| 圆角 | `theme/Shape.kt` | 8 / 12 / 16 / 20 / 28 五档 |
| 间距 | `theme/Spacing.kt` | `AppSpacing`：4 / 8 / 12 / 16 / 24 / 32 六档 |
| 动效 | `theme/Motion.kt` | `AppMotion`：fast 150ms / normal 250ms / spring |

### 5.2 统一组件

| 组件 | 用途 |
|---|---|
| `AppCard` | 分组卡片：16dp 圆角、`surfaceContainerLow` 底（浅色纯白）、1dp `outlineVariant` 发丝描边、零阴影 |
| `AppListRow` | 列表行：单行 ≥56dp、双行 ≥72dp；图标 24dp primary；副标题强制 `onSurfaceVariant` |
| `AppListDivider` | 卡片内分割线，左缩进对齐文字起点 |
| `AppSectionHeader` | 分组标题，置于卡片上方（iOS 分组列表风格） |
| `AppBadge` | 数字徽标（primary 底，最大 99） |
| `AppConfirmDialog` | 确认弹窗（extraLarge 圆角，双按钮） |
| `Modifier.glassSurface` | 玻璃材质修饰符（Haze 实时模糊 + 高光描边），底部导航等浮层唯一实现 |

### 5.3 页面结构约定

- 设置类页面 = iOS 分组列表：`AppSectionHeader` + `AppCard(contentPadding = 0)` + 若干 `AppListRow` + `AppListDivider`。
- 文本输入框高度 < 56dp 时禁止使用 M3 `OutlinedTextField`（固定内边距会截字）；用 `BasicTextField` 自绘胶囊容器，文本垂直居中。
- 任何副标题、占位、说明文字使用 `onSurfaceVariant`；禁止把 `outline` 用作文字色。
- 设计验收常驻入口：我的 → 开发者选项 → 设计 Playbook（实时预览全部 token 与组件）。
