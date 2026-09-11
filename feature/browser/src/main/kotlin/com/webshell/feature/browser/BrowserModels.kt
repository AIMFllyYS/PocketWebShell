package com.webshell.feature.browser

import android.graphics.Bitmap

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
)

enum class BrowserLoadError { NETWORK, INSECURE_HTTP, RENDERER_RECOVERING }

data class FindState(
    val visible: Boolean = false,
    val query: String = "",
    val active: Int = 0,
    val total: Int = 0,
)

/** Feature views and catalog don't consume Room entities or a ViewModel. */
data class BrowserSavedPage(val id: Long, val title: String, val url: String)
