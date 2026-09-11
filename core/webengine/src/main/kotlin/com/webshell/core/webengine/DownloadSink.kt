package com.webshell.core.webengine

import android.net.Uri

/**
 * Process-wide download port installed from Application.
 * [ShellWebView] is not Hilt-created, so it reaches the repository through [WebViewPool.downloadSink].
 *
 * Cookie and the raw URL may be used to build the request. They must never be logged.
 */
interface DownloadSink {
    /** Enqueue an HTTP(S) GET download. Returns the DownloadManager id, or null if rejected/failed. */
    fun startHttp(
        url: String,
        fileName: String,
        mimeType: String?,
        userAgent: String?,
        referer: String?,
        cookie: String?,
    ): Long?

    /** Record a finished blob write (already on disk / a shareable URI). */
    fun completeBlob(fileName: String, uri: Uri)
}
