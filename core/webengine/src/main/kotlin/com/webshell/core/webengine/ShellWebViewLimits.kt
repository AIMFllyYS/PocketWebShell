package com.webshell.core.webengine

/** Timeouts, size caps and synthetic error codes shared by the WebView owner and its clients. */
internal object ShellWebViewLimits {
    // evaluateJavascript returns through a Binder transaction; keep the
    // payload comfortably below the platform transaction limit.
    const val MAX_BLOB_BYTES = 32 * 1024 * 1024
    const val BLOB_CHUNK_BYTES = 192 * 1024
    const val MAX_BLOB_BASE64_CHARS = BLOB_CHUNK_BYTES * 4 / 3 + 8
    const val SSL_DECISION_TIMEOUT_MS = 15_000L
    // Short: this only bridges a tab-switch/host-attach race, not a real
    // user decision window. A background session must not keep a page's
    // script blocked noticeably longer than an attached host would.
    const val JS_DIALOG_BACKGROUND_TIMEOUT_MS = 400L
    const val ERROR_LOCAL_RESOURCE_FORBIDDEN = -98
    const val ERROR_RENDERER_GONE = -99
}
