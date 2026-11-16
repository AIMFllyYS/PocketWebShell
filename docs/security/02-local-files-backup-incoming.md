# 本地文件、备份、下载与外部打开

## 已经落地的控制

- FileProvider `exported="false"`，路径限于 `cache/logs`、`cache/backup`、`cache/downloads`、`cache/captures` 和公共目录 `Download/PocketWebShell/`。
- 下载入队只接受 http(s)，文件名经过清洗，Referer 只在同一 host 时附上。持久化的下载记录不保存完整 URL 和 Cookie。Cookie 头只交给 `DownloadManager`。
- 外部文件拷贝有大小上限，拒绝路径逃出 `localapps/`。策略层拒绝一批可执行扩展名，并用魔数丢掉 PE / ZIP / ELF / DEX。
- `SafeMarkdown` 只把 http(s) 且没有 userinfo 的链接交给外部查看器。
- 本地应用 URL 在渲染前映射到 AssetLoader 的 https 主机，不把 `file://` 交给 WebView 加载。
- 调试用的 `CatalogHarnessActivity` 只在 debug manifest，且 `exported="false"`。前台服务 `exported="false"`。

## 发现

### F1 · 高 · 备份解压没有挡住 zip-slip

`BackupRepository.extractTree`（约 485–494 行）把 zip 条目名去掉前缀后直接 `File(target, relative)`，没有拒绝 `..`，也没有在写入前做 canonical 路径比较。

```kotlin
val relative = entry.name.removePrefix(prefix)
val out = File(target, relative)
out.parentFile?.mkdirs()
zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
```

前提：用户导入一份恶意 `.pws`。影响留在应用 UID 内：解压目标如果在缓存或暂存目录，`../` 可以写到该目录之外，包括应用私有目录里导入逻辑随后会移动或加载的位置。这不是跨用户的远程代码执行，但足够改掉本应用即将当作本地网页或配置读入的文件。

导入规划对 URL scheme 有白名单，图标使用固定文件名。这些不能代替解压路径检查。同文件里的 `isSafeLocalPath` 风格（拒绝 `..`、再比较 canonical）应该用在这里。本次审查按要求不改这段代码。

### F2 · 高 · 允许系统备份，且没有排除浏览数据

`AndroidManifest.xml` 中 `android:allowBackup="true"`，没有 `fullBackupContent` 或 `dataExtractionRules` 把 `app_webview`、DataStore 和 Room 排除在外。

影响：系统备份或换机可以带走 Cookie、网站存储和本地导入文件。隐私说明已经提到设备备份，但清单没有把范围收窄。和用户在应用里点「导出」不是同一条路径，后者至少要主动选择。

### F3 · 中 · 安装清单声明了所有文件访问

`MANAGE_EXTERNAL_STORAGE` 在清单里，注释写明不声称符合 Play 的「用户只选一个文件」政策。授权之后，HTML 选择器可以枚举下载和常见聊天目录。发现过程有深度、时间和数量上限，并跳过 `Android/data` 与 `Android/obb`。拷贝仍有字节上限。

这是隐私面，不是远程利用。侧载包才会走到系统的所有文件访问授权页。

### F4 · 中 · 完整备份包带有 WebView 配置目录

`BackupType.FULL` 会打包 `app_webview/profiles/<appId>`。分享这个文件等于分享其中的 Cookie 和站点存储。导出类型是用户选的。恢复时若目标配置目录已存在则跳过覆盖，避免用备份无声换掉当前登录态，但第一次恢复仍会写入。

产品可以保留完整备份，但导出确认应当写明里面有登录态。审查不改文案。

### F5 · 低 · 下载完成接收器必须导出

`DownloadEventsReceiver` 是 `exported="true"`，过滤 `DOWNLOAD_COMPLETE` 和 `DOWNLOAD_NOTIFICATION_CLICKED`。清单注释写明这两条是受保护的系统广播，导出是为了进程死后仍能收到 `DownloadManager`。

`onReceive` 先按 action 分支。完成事件用 id 再查本应用的 `DownloadManager`，查不到且本地也没有这条记录时什么都不做。通知点击只打开仓库里已经有的条目，或本应用 `getUriForDownloadedFile` 能解析的文件。第三方不能靠伪造 id 读走其他应用的下载。

残留：若某个系统版本并没有把这两条 action 标成受保护广播，同设备应用仍可发显式 Intent。即便如此，查询结果仍限制在本应用自己的下载 id 上，最多让一条已跟踪的下载提前刷新状态。这不是文件读取。

### F6 · 中 · 导入时先把整个 zip 拷进缓存

`BackupRepository` 在校验清单之前，把所选 URI 的流全部 `copyTo` 临时文件，没有上限。用户选一个巨大文件可以打满缓存。临时文件在 `finally` 里删除。这是本地拒绝服务，不是代码执行。

### F7 · 中 · 桌面模式探针把页面 viewport 元数据写进可导出日志

`ShellWebView` 的桌面探针读取 viewport meta 的 content，和会话号一起 `AppLog.log`。日志进 Room，日志页可以经 FileProvider 导出成 TXT。

这比「只记 host」宽。页面可以在 viewport meta 里放一段自己控制的文本，随后出现在导出的日志里。不是 Cookie，但是不可信内容进了可分享存储。

### F8 · 低 · `logHost` 在解析不出 host 时保留 URL 前 64 个字符

`blob:`、`local://` 或其他没有 host 的地址会留下前缀。常规 https 地址只留下 host。下载路径另有 `DownloadPolicy.logHost`。

### F9 · 低 · 外部打开的 MIME 过滤器比运行时策略宽

清单为了微信的 `application/octet-stream` 和无扩展名注册了较宽的 VIEW/SEND。真正挂载时 `IncomingFilePolicy` 看扩展名、MIME 和文件头，并限制大小。`text/plain` 若看起来像 HTML，会按 HTML 打开，这是查看器的预期，也意味着用户必须信任发送方。HTML 一旦挂成带 `localAppId` 的会话，脚本在该本地源上运行，跨应用读取仍受 `LocalWebHost` 限制。

### F10 · 信息 · 下载 URI 在 FileProvider 拒绝时的回退

`DownloadIntents.shareableUri` 在路径不在 provider 根内时可能退回原始 URI。正常下载落在 DownloadManager 或 `cache/downloads/`，这两处都在 provider 根里。回退不是一条把任意路径授权给其他应用的成功路径。
