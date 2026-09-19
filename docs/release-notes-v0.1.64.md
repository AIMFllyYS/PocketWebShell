# PocketWebShell 0.1.64

正式发布进入公开内测。上一份 GitHub Release 是 0.1.63，本说明只覆盖 0.1.64。

## 开源与政策

- 许可改为 GNU GPL-3.0-or-later：分发修改版须同样开源。0.1.63 及更早仍按当时的 Apache-2.0。
- 设置「关于 → 政策与开源」：隐私说明、使用说明、开源许可、GitHub 仓库、参与贡献。
- 仓库增加校园开源说明与个人信息处理规则。欢迎贡献，没有 CLA。

## 检查更新

- 有新版本时，「下载」在应用内浏览标签打开，`.apk` 地址直接交给下载胶囊，不再跳到系统浏览器。
- 安装仍走系统包安装器，不会静默安装。

## 验证

- `:core:webengine:testDebugUnitTest testDebugUnitTest :app:assembleDebug`
