# 官网同步与部署规范（WEBSITE）

本文件是 PocketWebShell 产品官网（玄览官网）随版本发布同步更新与打包部署的**权威规范**。每次正式发布（GitHub Release 上线）后**必须**按本文件执行官网同步；`AGENTS.md` 与 `docs/RELEASE.md` 引用本文件。

条款中的 **必须 / 禁止 / 应当** 按 RFC 2119 语义解读。

## 1. 官网项目位置与性质

- 官网是**独立项目**，不随本仓库提交，位于本仓库的兄弟目录：

  ```text
  D:\projects\Dev-Android\Android-Web\PocketWebShell-site\
  ```

- **官方正式域名**：`https://xuanlan.1037solo.com`（1037SOLO 生态挂载站点）。
- 技术栈：React 19 + vinext（Next.js App Router 兼容层）+ Vite 8 + Tailwind v4 + Shadcn/Base UI，纯静态导出（`next.config.ts` 中 `output: 'export'`）。
- 构建产物为 `dist/client/`（`index.html`、`changelog.html`、`404.html`、`_next/static/`、`screens/`、`icons/` 等），**无服务端函数、无数据库**，可部署到任意静态托管。
- 运行环境要求：Node 22.13+，依赖以仓库内 `package-lock.json` 为准（`npm ci` 安装）。

## 2. 触发时机

- 每次 `docs/RELEASE.md` 流程完成、GitHub Release 正式上线后，**必须**在当日完成本文件的同步与部署流程。
- 仅发布 debug 迭代版本（`docs/VERSIONING.md` §5 W1–W3）时**不需要**同步官网；官网只跟随正式 Release。
- 官网内容**禁止**出现尚未正式发布的版本下载链接；开发迭代记录只作为版本档案展示，不提供下载。

## 3. 同步清单（每次 Release 必做）

以下所有路径均相对官网项目根目录 `PocketWebShell-site/`。执行顺序**必须**自下节步骤 1 至步骤 6 依次进行。

### 步骤 1：更新更新日志快照

用本仓库根目录的 `CHANGELOG.md` **全量替换**官网项目的 `content/CHANGELOG.md`。该文件是官网版本档案的唯一输入源。

### 步骤 2：重新生成版本档案数据

`lib/releases.json` 由脚本生成，**禁止**手工编辑。运行前**必须**先更新 `scripts/sync-changelog.mjs` 中的三处硬编码：

| 位置（锚点） | 内容 | 更新动作 |
|---|---|---|
| `knownPublished` 集合 | 正式发布版本白名单 | 加入本次发布的版本号 |
| `titles` 表 | 每个版本的中文标题 | 为新版本拟一句中文标题并加入 |
| 文件末尾断言 | `releases.length` 条数与首个版本号 | 条数 +1，首版本改为新版本号 |

然后运行：

```bash
node scripts/sync-changelog.mjs
```

### 步骤 3：更新发布配置源

`lib/product.ts` 是官网唯一的发布/下载/校验配置源，**必须**更新以下字段：

- `version`：新版本号；
- `download`：新 APK 的 GitHub Release 下载链接；
- `checksumFile`：新 `.apk.sha256` 文件的下载链接；
- `checksum`：新 APK 的 SHA-256（取自本仓库 `dist/PocketWebShell-v<version>.apk.sha256`，与 GitHub Release 资产一致）；
- `size`：新 APK 的实际体积（以 GitHub Release 资产大小为准）；
- `certificate`：通常不变；**禁止**在未更换正式签名证书的情况下修改该链接。

### 步骤 4：清扫组件内散落的版本号硬编码

以下位置含有随版本变化的硬编码文案，**必须**逐一核对更新：

- `app/page.tsx`：首屏 pill 文案中的版本号；
- `components/release-archive.tsx`：最新版本判断逻辑（多处）；
- `components/product-experience.tsx`：下载区 version-object 展示块。

> [!NOTE]
> `app/layout.tsx` 中的 `metadataBase` 必须始终保持为官方正式域名 `https://xuanlan.1037solo.com`，严禁配置或残留为开发/脚手架临时域名。

兜底检查（在官网项目根目录执行）：全局搜索旧版本号，确认除历史版本档案内容外无遗漏：

```bash
git grep -n "<旧版本号>" -- app components lib scripts
```

### 步骤 5：更新静态验收脚本断言

`scripts/verify-static.mjs` 中的版本相关断言**必须**同步更新：

- `releases.length` 与版本唯一性断言的条数（+1）；
- 客户端 bundle 中必须包含的 SHA-256 值（改为新校验和）；
- 末尾输出文案中的条数。

### 步骤 6（条件执行）：更新截图素材

仅当本次发布改变了主屏、文件夹、浏览器多标签、设置中任一界面的视觉时执行：按 `content/ASSETS.md` 记录的方式在 Android 模拟器中重新截取对应场景，替换 `public/screens/` 下同名文件，并同步更新 `content/ASSETS.md` 的来源记录。素材命名规范**必须**保持不变（场景名命名，代码按名拼接引用）。

## 4. 验证（全部通过方可打包）

在官网项目根目录依次执行，任一失败**必须**修复后重跑：

```bash
npm run lint:app        # 手写应用组件 lint
npx tsc --noEmit        # 类型检查
npm run build           # 生产构建（vinext 静态导出）
npm run verify:static   # 构建产物静态验收
npm audit               # 依赖安全审计，已知漏洞必须为 0
```

## 5. 打包与部署

### 打包

验证通过后，将 `dist/client/` 的全部内容打成 zip，输出到官网项目 `outputs/` 目录（该目录已被 gitignore，仅作交付暂存）。文件名**必须**带版本号：

```powershell
Compress-Archive -Path dist\client\* -DestinationPath outputs\xuanlan-website-html-v<version>.zip -Force
```

### 部署

- zip 解压后即为完整静态站点，上传到托管方（静态托管 / 对象存储 / Pages 类服务）的网站根目录即可。
- **官方正式域名**：`https://xuanlan.1037solo.com`。
- **部署约束**：HTML 内部使用根相对路径，站点**必须**部署在域名根路径下，不能挂在子路径。
- 上传后**必须**做线上冒烟：访问 `https://xuanlan.1037solo.com/` 与 `https://xuanlan.1037solo.com/changelog/`，核对下载区版本号、SHA-256 与 GitHub Release 资产完全一致，并实际点击一次 APK 下载链接确认可下载。

## 6. 内容与信任红线

- **禁止**在官网链接任何 debug 包；下载区只允许出现正式签名 APK、其 SHA-256 与公开证书。
- **禁止**伪造用户评价、评分或下载量；没有真实反馈时保持"邀请反馈"的表述。
- 截图与第三方网站图标的使用**必须**持续符合 `content/ASSETS.md` 的来源与商标免责记录。
- 官网仓库（`PocketWebShell-site/`）**禁止**提交任何签名材料、凭据；`outputs/`、`work/` 保持 gitignored。

## 7. 参考

- 发布主流程：`docs/RELEASE.md`
- 版本号规则：`docs/VERSIONING.md`
- 官网项目自述与本地开发说明：`PocketWebShell-site/README.md`
- 素材来源记录：`PocketWebShell-site/content/ASSETS.md`
