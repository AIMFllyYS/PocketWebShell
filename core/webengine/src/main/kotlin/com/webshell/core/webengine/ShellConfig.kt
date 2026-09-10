package com.webshell.core.webengine

/**
 * 一个"网页应用壳"会话的配置。
 * @param sessionId 唯一会话 ID，仅用于池内实例复用（切 tab/主页往返不丢状态）。
 * @param profileId 可选的未来扩展 Profile ID；null = 使用 WebView 默认共享 Profile。
 *   PocketWebShell 的所有当前入口都显式传 null，确保标签页、桌面入口、直链和本地导入
 *   共享 Cookie/LocalStorage/IndexedDB/Service Worker。非空仅供未来多用户/隐身能力使用。
 */
data class ShellConfig(
    val sessionId: String? = null,
    /** Optional future isolated Profile ID; null is the product-default shared profile. */
    val profileId: String? = null,
    val startUrl: String = "about:blank",
    /**
     * The only imported-local-app directory this session may read. A null value
     * deliberately denies `/local/<appId>/…` resources; remote/browser sessions
     * must never inherit access merely because they share the default WebView
     * profile. Packaged `/assets/…` resources remain available to all sessions.
     */
    val localAppId: String? = null,
    /** 桌面模式：桌面 UA + UA-CH + 宽视口 */
    val desktopMode: Boolean = false,
    /** 允许 WebView 算法深色（页面未适配深色时的系统级反色） */
    val algorithmicDark: Boolean = false,
    /** 允许第三方 cookie（OAuth/SSO 登录态通常需要） */
    val thirdPartyCookies: Boolean = true,
    /** 无手势自动播放音视频（壳应用一般放开以贴近原生体验） */
    val autoplayMedia: Boolean = true,
    /** 下拉刷新；产品默认关闭，仅在设置打开且页面已到顶部时拦截。 */
    val pullToRefresh: Boolean = false,
    /** 内容边距模式 */
    val insetMode: InsetMode = InsetMode.PAD,
    /** 外链（非当前站点域）策略 */
    val externalLinkPolicy: ExternalLinkPolicy = ExternalLinkPolicy.OPEN_IN_BROWSER,
    /** 文本缩放百分比（100 = 不缩放） */
    val textZoomPercent: Int = 100,
) {
    /** Merge persisted settings without allowing a UI refresh to change identity/profile. */
    fun mergedWith(updated: ShellConfig): ShellConfig = updated.copy(
        sessionId = sessionId,
        profileId = profileId,
        startUrl = startUrl,
        localAppId = localAppId,
    )

    enum class InsetMode {
        /** 系统 bar/IME 以 padding 形式避让（适合未适配刘海的普通网站） */
        PAD,

        /** 全屏铺满 + 注入 --ws-safe-* CSS 变量（适合声明了 viewport-fit=cover 的页面） */
        CSS_ONLY,
    }

    enum class ExternalLinkPolicy { OPEN_IN_SAME, OPEN_IN_BROWSER }
}
