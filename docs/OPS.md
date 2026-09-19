# 发布与官网部署运维规范（OPS）

本文件是 PocketWebShell **正式发布之后** 的操作手册：从合入 `main`、签名打包、GitHub Release，到官网静态包上传、解压覆盖、冒烟验收、再回到 `dev`。它根据 0.1.63 的完整实操写成。条款中的 **必须 / 禁止 / 应当** 按 RFC 2119 语义解读。

版本号、闸门与 Release 说明规则以 `docs/VERSIONING.md` 为准。签名命令以 `docs/RELEASE.md` 为准。官网内容清单以 `docs/WEBSITE.md` 为准。发生冲突时，本文件只补充「机器上具体敲什么」，不覆盖上述三份的产品规则。

## 0. 开源可见性与最小权限

本仓库是开源的，本文也是公开的。**禁止**把维护者本机或服务器上的机密写进任何已提交文件（包括本文、`docs/WEBSITE.md`、issue、PR 描述、提交说明）。

| 禁止出现在仓库里 | 可以写、也应当写 |
|---|---|
| SSH 私钥、公钥文件路径、`IdentityFile`、`known_hosts` 内容 | 本机 SSH 配置里的 Host 别名：`ssh MainECS` |
| 服务器公网/内网 IP、登录用户名、面板账号与口令 | 公开域名 `https://xuanlan.1037solo.com` |
| 发布密钥库文件名、别名、口令、DPAPI 凭据文件名 | 「密钥库在仓库外，只用 `scripts/build-release.ps1`」 |
| 维护者本机绝对路径（如 `D:\Users\...`、`C:\Users\...`） | 相对路径：`../PocketWebShell-site/`、`dist/`、`outputs/` |
| debug APK、`.env`、签名私钥、用户备份 | 正式 APK、`.apk.sha256`、公开证书 `.pem` |

连接生产机的方式：**本机 OpenSSH 客户端已经配置好 Host 别名 `MainECS`**。维护者在 PowerShell 里直接执行 `ssh MainECS` 即可进入站点所在机器，**禁止**在文档或脚本里复述密钥如何生成、密钥放在哪、主机地址是什么。

新维护者若 `ssh MainECS` 失败，只在本机 `~/.ssh/config` 补 Host 别名，**不要**把那份 config 提交进仓库。

## 1. 何时执行

**必须**同时满足：

1. 用户已经用**同一** `versionName` / `versionCode` 的 debug 包完成真机验收，并明确指示发布（`docs/VERSIONING.md` W1/W2）。
2. 发布提交已在 `dev`，且准备经 PR 进入 `main`（不要在 `main` 上直接开发）。
3. 本机可以运行 `.\scripts\build-release.ps1`（仓库外密钥可用）、`gh` 已登录、`ssh MainECS` 可免交互登录。

仅有 debug 迭代、用户未点头时：**禁止**打 tag、**禁止**创建 GitHub Release、**禁止**更新官网。

## 2. 总流程（按顺序，不要跳）

```text
dev 上的发布提交
  → PR：dev → main（线性历史）
  → 检出 origin/main
  → 签名构建 + apksigner 核对证书
  →  annotated tag v<version> 并 push
  → gh release create（APK + sha256 + 公开证书，不要 debug 包）
  → 同步 ../PocketWebShell-site/ 并本地验证
  → 打 zip
  → scp 到官网文档根 → unzip -o 覆盖 → 删除 zip → 恢复属主
  → HTTPS 冒烟
  → 把 origin/main 合回 origin/dev，切回 dev
```

下面每一步都给出 **0.1.63 验证过** 的命令。把 `0.1.63` 换成当前版本即可。

## 3. 合入 `main`

`main` 受保护：必须走 PR，必须线性历史，必须通过 `test-and-build`。

```powershell
gh pr create --base main --head dev --title "Release 0.1.63" --body "Publish 0.1.63."
gh pr checks --watch
```

首选 rebase（与 `docs/RELEASE.md` 一致）：

```powershell
gh pr merge <PR号> --rebase
```

若 GitHub 返回 `This branch can't be rebased`（`dev` 上仍有 merge commit 时会出现）：**禁止**用 merge commit。改用 squash，提交说明必须带上 PR 号，以保持 `main` 线性：

```powershell
gh pr merge <PR号> --squash --subject "release: PocketWebShell 0.1.63 (#<PR号>)"
```

0.1.62、0.1.63 都是 squash 合入的。合入后：

```powershell
git fetch origin
git checkout main
git reset --hard origin/main
git log --oneline --graph -5
```

此时 `HEAD` **必须**等于 `origin/main`。未跟踪的验收截图、审计草稿**不要**加入这次发布提交。

## 4. 签名、校验、打 tag、GitHub Release

在已经对齐 `origin/main` 的工作树上：

```powershell
.\scripts\build-release.ps1 -Version 0.1.63
```

脚本会写出（`dist/` 已被 gitignore，**禁止** `git add dist/`）：

```text
dist/PocketWebShell-v0.1.63.apk
dist/PocketWebShell-v0.1.63.apk.sha256
dist/PocketWebShell-release-cert.pem
```

发布前核对三件事：

1. `apksigner verify --print-certs` 的证书 SHA-256 **必须**与上一份正式版相同（证书未轮换时）。
2. `.apk.sha256` 第一列与 GitHub 即将上传的 APK 一致；格式是 `hash` + 两个空格 + 文件名。
3. 体积写成官网用的 MiB 时，用 `字节数 / 1048576`，保留两位小数（0.1.63 为 `76.03 MiB`）。

```powershell
git tag -a v0.1.63 -m "PocketWebShell 0.1.63"
git push origin v0.1.63

gh release create v0.1.63 `
  .\dist\PocketWebShell-v0.1.63.apk `
  .\dist\PocketWebShell-v0.1.63.apk.sha256 `
  .\dist\PocketWebShell-release-cert.pem `
  --verify-tag `
  --title "PocketWebShell 0.1.63" `
  --notes-file .\docs\release-notes-v0.1.63.md
```

**禁止**把 `dist/PocketWebShell-0.1.63-debug.apk` 或任何文件名含 `debug` 的 APK 挂到 Release。公开证书 `PocketWebShell-release-cert.pem` 可以分发，它不能签名。

验收：

```powershell
gh release view v0.1.63 --json url,tagName,isDraft,isPrerelease,assets
```

资产必须恰好三件：`PocketWebShell-v<ver>.apk`、同名 `.apk.sha256`、`PocketWebShell-release-cert.pem`。`isDraft` / `isPrerelease` 必须为 false。

Release 说明按 `docs/VERSIONING.md` W5：覆盖自上一份**已发布**正式版以来的全部版本。0.1.63 的上一份正式版是 0.1.62，中间没有只存在 debug 形态的号码，因此说明只写 0.1.63。

## 5. 同步官网源码

官网是**独立项目**，不随本仓提交，相对本仓根目录为 `../PocketWebShell-site/`。官方域名：`https://xuanlan.1037solo.com`。逐步清单以 `docs/WEBSITE.md` 为准，下面是 0.1.63 实际改过的位置。

在官网项目根目录：

1. 用本仓 `CHANGELOG.md` **全量替换** `content/CHANGELOG.md`。
2. 编辑 `scripts/sync-changelog.mjs`：`knownPublished` 加入 `0.1.63`；`titles` 加入一句中文标题（0.1.63 为「明文确认，继续访问」）；末尾断言改为 `releases.length === 64` 且 `releases[0].version === '0.1.63'`。
3. 运行 `node scripts/sync-changelog.mjs`，生成 `lib/releases.json`。**禁止**手改该 JSON。
4. 更新 `lib/product.ts`：`version`、`release`、`download`、`checksumFile`、`checksum`、`size`。`certificate` 在未换证时保持指向首次公布该证书的 Release（当前为 `v0.1.14`）。
5. 清扫硬编码：`app/page.tsx`（pill 与「正式版」文案）、`app/changelog/page.tsx`（metadata 描述）、`components/release-archive.tsx`（水印、默认展开、最新标记、高亮句）。
6. 更新 `scripts/verify-static.mjs` 的条数、下载路径、SHA-256。
7. 全局搜索旧版本号：除历史档案外不应再把旧正式版当作「当前」。

验证（任一失败都**禁止**打包）：

```powershell
Set-Location ..\PocketWebShell-site
npm run lint:app
npx tsc --noEmit
npm run build
npm run verify:static
npm audit
```

`npm audit` **必须**为 0 个漏洞。构建产物在 `dist/client/`（`index.html`、`changelog.html`、`404.html`、`_next/`、`icons/`、`screens/` 等）。

## 6. 打包 zip

在 `../PocketWebShell-site/` 下把 `dist/client/` **整棵树**打成 zip，输出到已 gitignore 的 `outputs/`。文件名**必须**带版本号。

**必须**用 Python 标准库打包，不要用 PowerShell `Compress-Archive`：后者写入的 zip 会让 Linux `unzip` 打印 `appears to use backslashes as path separators`，退出码为 1。若远程脚本开了 `set -e`，会在「已经解压成功」之后误停，zip 删不掉、属主也改不成。

```powershell
Set-Location ..\PocketWebShell-site
python -c "import shutil; shutil.make_archive(r'outputs\xuanlan-website-html-v0.1.63', 'zip', r'dist\client')"
```

本地可抽查：

```powershell
python -c "import zipfile; z=zipfile.ZipFile(r'outputs\xuanlan-website-html-v0.1.63.zip'); assert z.testzip() is None; assert not any('\\' in n for n in z.namelist()); print(len(z.namelist()), 'entries')"
```

zip 里**必须**能看到 `index.html`、`changelog.html`、`_next/`，**禁止**打进 `node_modules/`、`work/`、密钥或 debug APK。

## 7. 上传、解压覆盖、删除 zip

官网是纯静态站点。Nginx 中 `server_name xuanlan.1037solo.com` 的 `root` 就是文档根：

```text
/www/wwwroot/xuanlan.1037solo.com
```

站点**必须**挂在域名根路径，不能挂子目录。HTML 使用根相对路径。

### 7.1 上传

在官网项目根目录：

```powershell
scp .\outputs\xuanlan-website-html-v0.1.63.zip MainECS:/www/wwwroot/xuanlan.1037solo.com/
```

`scp` 与 `ssh` 使用同一个 Host 别名 `MainECS`，不要在命令里写 IP 或 `-i` 密钥路径。

### 7.2 在服务器上解压

```powershell
ssh MainECS
```

进入会话后（以下为远程 shell）：

```bash
cd /www/wwwroot/xuanlan.1037solo.com

# 先记下现有首页的属主，再解压。解压后新文件会变成当前 SSH 用户，必须改回去。
OWNER=$(stat -c %U:%G index.html)

unzip -o xuanlan-website-html-v0.1.63.zip

# 确认首页已是新版本后再删 zip，避免解压失败时既没新文件也丢了包
grep -o '0\.1\.63' index.html | head

rm -f xuanlan-website-html-v0.1.63.zip

chown -R "$OWNER" \
  404.html changelog.html changelog.txt \
  privacy.html terms.html license.html contribute.html \
  _headers icon.svg index.html index.txt vinext-client-entry-manifest.json \
  .assetsignore icons screens _next .vite

# 文档根里不得留下 zip
ls ./*.zip 2>/dev/null && echo 'ERROR: zip leftover' && exit 1
```

说明：

- `unzip -o` 覆盖同名文件，**不会**删除本次 zip 里没有的旧文件（例如过期的 `_next/static/chunks/<旧哈希>`）。当前 HTML 不再引用它们，可以留到以后再清。**禁止**为了「干净」而 `rm -rf` 整个文档根。
- **禁止**把 zip 留在文档根。外网必须对 `https://xuanlan.1037solo.com/xuanlan-website-html-v<ver>.zip` 返回 404。
- **禁止**上传 debug APK、`.env`、`.git`、密钥库、本机 `outputs/` 以外的东西。
- 不要对整个文档根 `chown -R`。只改本次解压出来的名字，以免动到证书验证目录或主机管理文件。
- 若误用了 `Compress-Archive`，`unzip` 可能警告反斜杠并返回 1：先 `ls index.html` 确认已更新，再手动 `rm` zip 并 `chown`，下次改用第 6 节的 Python 命令。

一次性退出远程会话：

```powershell
ssh MainECS "cd /www/wwwroot/xuanlan.1037solo.com && OWNER=\$(stat -c %U:%G index.html) && unzip -o xuanlan-website-html-v0.1.63.zip && rm -f xuanlan-website-html-v0.1.63.zip && chown -R \"\$OWNER\" 404.html changelog.html changelog.txt privacy.html terms.html license.html contribute.html _headers icon.svg index.html index.txt vinext-client-entry-manifest.json .assetsignore icons screens _next .vite"
```

在 Windows PowerShell 里嵌套远程引号容易把 `stat -c %U:%G` 拆坏。**应当**优先用交互式 `ssh MainECS` 再贴第 7.2 节命令。

## 8. 更新日志路径（一次性 Nginx 规则）

静态导出得到的是 `changelog.html`，页面内链是 `/changelog`。站点默认还拦了一条「URI 以 changelog 结尾」的规则，用来避免把仓库里的 `CHANGELOG` 源文件暴露到网上，结果会让 `/changelog` 直接 404。

**必须**用精确匹配（`location =`），**禁止**写成 `location / { try_files $uri $uri.html ... }`：后者会与现有的 HTTP→HTTPS 跳转打架，首页会 301 到自己。

在该站点 Nginx 的自定义/伪静态配置中加入（只需做一次，以后发版不用改）。更新日志、隐私、使用说明、许可、贡献都是静态导出的 `*.html`，站内链是不带扩展名的路径；其中「URI 以 changelog 结尾则 404」那条默认规则还会误伤 `/changelog`：

```nginx
location = /changelog {
    try_files /changelog.html =404;
}
location = /changelog/ {
    try_files /changelog.html =404;
}
location = /privacy {
    try_files /privacy.html =404;
}
location = /privacy/ {
    try_files /privacy.html =404;
}
location = /terms {
    try_files /terms.html =404;
}
location = /terms/ {
    try_files /terms.html =404;
}
location = /license {
    try_files /license.html =404;
}
location = /license/ {
    try_files /license.html =404;
}
location = /contribute {
    try_files /contribute.html =404;
}
location = /contribute/ {
    try_files /contribute.html =404;
}
```

保存后：

```bash
nginx -t && nginx -s reload
```

`nginx -t` 失败时**禁止** reload。不要在开源文档里记录证书私钥路径、面板目录或其它站点的 server 块。

## 9. 线上冒烟（必须做完才能宣布发布结束）

在本机（不要走会拦截该域名的代理）执行：

```powershell
curl.exe -sS -o NUL -w "home %{http_code}`n" https://xuanlan.1037solo.com/
curl.exe -sS -o NUL -w "changelog %{http_code}`n" https://xuanlan.1037solo.com/changelog
curl.exe -sS -o NUL -w "changelog_slash %{http_code}`n" https://xuanlan.1037solo.com/changelog/
curl.exe -sS -o NUL -w "changelog_html %{http_code}`n" https://xuanlan.1037solo.com/changelog.html
curl.exe -sS -o NUL -w "privacy %{http_code}`n" https://xuanlan.1037solo.com/privacy
curl.exe -sS -o NUL -w "terms %{http_code}`n" https://xuanlan.1037solo.com/terms
curl.exe -sS -o NUL -w "license %{http_code}`n" https://xuanlan.1037solo.com/license
curl.exe -sS -o NUL -w "contribute %{http_code}`n" https://xuanlan.1037solo.com/contribute
curl.exe -sS -o NUL -w "zip %{http_code}`n" https://xuanlan.1037solo.com/xuanlan-website-html-v0.1.63.zip
curl.exe -sS -o NUL -w "env %{http_code}`n" https://xuanlan.1037solo.com/.env
curl.exe -sS -I -o NUL -w "apk %{http_code}`n" https://github.com/AIMFllyYS/PocketWebShell/releases/download/v0.1.63/PocketWebShell-v0.1.63.apk
```

通过标准（0.1.63 实测值）：

| URL | 期望 |
|---|---|
| `https://xuanlan.1037solo.com/` | 200，正文含新版本号与中文标题，下载链指向 `.../releases/download/v<ver>/PocketWebShell-v<ver>.apk`，**不得**出现 `debug.apk` |
| `/changelog`、`/changelog/`、`/changelog.html` | 均为 200，档案水印为新版本 |
| `.../xuanlan-website-html-v<ver>.zip` | 404 |
| `/.env`、`/.git/HEAD` | 404 |
| GitHub APK 地址 | 302 或 200，最终能下载正式包 |

还应当在浏览器打开首页与更新日志，点一次「下载 Android 版」，确认落到刚刚创建的 GitHub Release。

## 10. 回到 `dev`（VERSIONING W6）

```powershell
git fetch origin
git checkout dev
git merge origin/main --no-edit
git push origin dev
```

之后的功能分支从 `dev` 拉。下一轮开发按 `docs/VERSIONING.md` 取新版本号。本文档这类纯运维说明走 `docs/<topic>` 分支合入 `dev`，**不必**为此递增应用版本号。

## 11. 禁止事项（再列一遍）

- **禁止**跳过 debug 同号验收直接发正式版。
- **禁止**把 `*-debug.apk` 上传为 GitHub Release 资产或挂到官网。
- **禁止**修改已签名 APK。
- **禁止**在仓库、官网源码、zip、文档根留下密钥、`.env`、SSH 私钥、绝对本机路径。
- **禁止**把 zip 留在文档根「以备回滚」；回滚用本机 `outputs/` 里那份再传一次。
- **禁止**在开源文档里写 SSH 密钥路径、服务器 IP、面板口令、密钥库文件名。
- **禁止**对文档根 `rm -rf` 后重传；只覆盖 zip 内的文件。

## 12. 参考

- `docs/VERSIONING.md` — 版本号与 debug 先行闸门
- `docs/RELEASE.md` — 签名、tag、GitHub Release
- `docs/WEBSITE.md` — 官网内容同步清单
- `../PocketWebShell-site/README.md` — 官网本地开发
- GitHub CLI：https://cli.github.com/manual/gh_release_create
