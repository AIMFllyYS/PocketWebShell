# 云端 Android 环境初始化

在实际克隆的 PocketWebShell 根目录执行。先检查 `AGENTS.md`、`app/build.gradle.kts`、`gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties` 与 CI；以下记录当前项目使用 JDK 17、compileSdk 37，依赖以最新源码为准。

## 工具与 SDK

1. 检查 `git`、`gh` 或可用 GitHub 工具、`java`、`sdkmanager`、`adb`、磁盘和内存。已有工具链优先复用；不要为新任务删除已有缓存或终止无关进程。
2. 缺 JDK 时安装官方 OpenJDK / Temurin 17；缺 Android SDK 时从 Android 官方安装 Command-line Tools，布局为 `<SDK>/cmdline-tools/latest/bin/sdkmanager`。下载工具链校验官方校验值，不禁用 TLS 校验。
3. 使用实际工具路径设置当前进程的 `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT` 和 `PATH`，不要覆盖 `HOME` 或 `CODEX_HOME`。查看 `sdkmanager --list`，安装 platform-tools、与 compileSdk 匹配的平台和 AGP 要求的 build-tools，按 SDK 条款接受许可。
4. Android 37 在本次 SDK 列表中的平台包名为 `platforms;android-37.0`，构建使用 `build-tools;36.0.0`；后续先查列表和源码，不假定平台包一定叫 `platforms;android-37`。
5. 在仓库创建 gitignored `local.properties`，内容为实际 SDK 路径，例如 `sdk.dir=/workspace/toolchains/android-sdk`。Linux 示例路径只是当前环境布局，新环境先确认存在。

SDK 安装示例（在已配置代理和信任后）：

```bash
sdkmanager --list
sdkmanager --licenses
sdkmanager 'platform-tools' 'platforms;android-37.0' 'build-tools;36.0.0'
```

## 代理与 Java 信任

保留云平台注入的 `HTTP_PROXY`、`HTTPS_PROXY`、认证与 CA 配置。Java / Gradle 不一定自动使用 shell 的 HTTP 代理；下载失败时根据实际代理为当前进程增加 JVM 的 http / https 代理属性。不要把带账号密码的代理地址写入文档或提交文件。

本次环境的无认证 sidecar 是 `http://proxy:8080`，已信任平台 CA 的 Java truststore 是 `/etc/ssl/certs/java/cacerts`。仅在新环境检查确认两者相同且 truststore 可用时，才可使用此示例：

```bash
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts=localhost|127.* -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
```

这段配置应只加载一次，不重复追加。环境不同就按其运行说明调整；不清空既有 JVM 参数，不绕开平台代理，不关闭证书验证。需要保存环境脚本时放在已忽略的 `.tmp/` 或仓库外，不提交机器路径。

## 基线与端侧能力

```bash
java -version
./gradlew --version
./gradlew :app:assembleDebug --max-workers=2
adb devices
```

使用已有 Wrapper，不安装全局 Gradle。内存有限时减少 Gradle worker；只有进程被确认属于本次任务且影响构建时，才考虑停止它。初始化基线构建与改动完成后的测试分别记录；纯文档任务不必重新编译整个应用。

有设备才运行 `connectedDebugAndroidTest`。没有设备时可编译 `:app:assembleDebugAndroidTest`，但它不执行端侧测试。缺 KVM 的软件模拟器可能无法在合理时间内启动；报告限制并交接本地真机，最终验收仍由用户完成。

GitHub 访问异常时检查实际错误和连接就绪状态。已有认证时正常使用 CLI，必要时用 `gh auth git-credential` 作为 Git credential helper；不输出 token、不把 token 拼进 remote URL，不无故重新登录。提交多行 PR 描述时写文件再使用 `gh pr create --base dev --head <branch> --body-file <file>`。
