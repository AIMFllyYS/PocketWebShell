package com.webshell.core.webengine

/**
 * 一个"网页应用壳"会话的配置。
 * @param sessionId 唯一会话 ID；非空时启用独立 Profile（cookie/存储/HTTP 缓存/SW 全隔离），
 *   相同 sessionId 的两次打开共享登录态——这是"输入网址=做出一个应用"的数据基础。
 */
data class ShellConfig(
    val sessionId: String? = null,
    val startUrl: String = "about:blank",
    /** 桌面模式：桌面 UA + UA-CH + 宽视口 */
    val desktopMode: Boolean = false,
    /** 允许 WebView 算法深色（页面未适配深色时的系统级反色） */
    val algorithmicDark: Boolean = false,
    /** 允许第三方 cookie（OAuth/SSO 登录态通常需要） */
    val thirdPartyCookies: Boolean = true,
    /** 无手势自动播放音视频（壳应用一般放开以贴近原生体验） */
    val autoplayMedia: Boolean = true,
    /** 下拉刷新 */
    val pullToRefresh: Boolean = true,
    /** 内容边距模式 */
    val insetMode: InsetMode = InsetMode.PAD,
    /** 外链（非当前站点域）策略 */
    val externalLinkPolicy: ExternalLinkPolicy = ExternalLinkPolicy.OPEN_IN_BROWSER,
    /** 文本缩放百分比（100 = 不缩放） */
    val textZoomPercent: Int = 100,
) {
    enum class InsetMode {
        /** 系统 bar/IME 以 padding 形式避让（适合未适配刘海的普通网站） */
        PAD,

        /** 全屏铺满 + 注入 --ws-safe-* CSS 变量（适合声明了 viewport-fit=cover 的页面） */
        CSS_ONLY,
    }

    enum class ExternalLinkPolicy { OPEN_IN_SAME, OPEN_IN_BROWSER }
}
