package com.webshell.core.webengine

import java.net.URI

/** Header construction kept pure so cookie/referer handling can be tested without DownloadManager. */
object DownloadPolicy {
    fun requestHeaders(
        url: String,
        userAgent: String?,
        referer: String?,
        cookie: String?,
    ): Map<String, String> = buildMap {
        userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
        val targetHost = runCatching { URI(url).host }.getOrNull()
        val refererHost = runCatching { URI(referer.orEmpty()).host }.getOrNull()
        if (!referer.isNullOrBlank() && targetHost != null && targetHost.equals(refererHost, ignoreCase = true)) {
            put("Referer", referer)
        }
        // CookieManager already applies domain/path matching for the requested URL.
        cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
    }

    fun safeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim().trim('.').take(120)
        return cleaned.ifBlank { "download" }
    }
}
