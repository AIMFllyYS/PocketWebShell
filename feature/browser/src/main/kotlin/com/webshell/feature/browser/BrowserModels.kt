package com.webshell.feature.browser

import android.graphics.Bitmap
import com.webshell.core.data.IncomingSourceKey

enum class BrowserTabKind { WEB, INCOMING_HTML, INCOMING_MARKDOWN }

/** Immutable per-tab values; thumbnail snapshots remain memory-only, never persisted. */
data class BrowserTab(
    val tabId: String,
    val title: String,
    val url: String,
    /** Canonical WebViewPool id. Never reconstruct this from [tabId] with a prefix. */
    val sessionId: String = "browser-$tabId",
    /**
     * User-opened tabs may load [url] when the renderer is still blank.
     * Window-adopted renderers must not — that would replace the popup with
     * the opener or a guessed hit-test URL.
     */
    val restoreStartUrlIfBlank: Boolean = true,
    val thumbnail: Bitmap? = null,
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loadError: BrowserLoadError? = null,
    val kind: BrowserTabKind = BrowserTabKind.WEB,
    /** Display-only local path for incoming files; never a WebView URL. */
    val displayPath: String? = null,
    val markdownContent: String? = null,
    val localAppId: String? = null,
    /** Normalized incoming identity; never a WebView URL. */
    val sourceKey: String? = null,
) {
    fun addressChrome(): String = when (kind) {
        BrowserTabKind.WEB -> url.takeUnless { it.isBlank() || it == "about:blank" }.orEmpty()
        else -> IncomingSourceKey.addressLabel(displayPath, title, url)
    }

    fun secondaryLabel(): String = displayPath?.takeIf { it.isNotBlank() } ?: url.stripScheme()

    fun canBookmark(): Boolean =
        kind == BrowserTabKind.WEB &&
            url.isNotBlank() &&
            url != "about:blank" &&
            !isIncomingAssetUrl(url)

    fun canEditAddress(): Boolean = kind == BrowserTabKind.WEB
}

enum class BrowserLoadError { NETWORK, INSECURE_HTTP, RENDERER_RECOVERING }

data class FindState(
    val visible: Boolean = false,
    val query: String = "",
    val active: Int = 0,
    val total: Int = 0,
)

/** Feature views and catalog don't consume Room entities or a ViewModel. */
data class BrowserSavedPage(
    val id: Long,
    val title: String,
    val url: String,
    val iconUrl: String? = null,
    val visitedAt: Long = 0L,
)
