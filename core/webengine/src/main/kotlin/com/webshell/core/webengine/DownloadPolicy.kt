package com.webshell.core.webengine

import java.net.URI

/** Header construction kept pure so cookie/referer handling can be tested without DownloadManager. */
object DownloadPolicy {
    const val PUBLIC_FOLDER = "PocketWebShell"
    const val PUBLIC_RELATIVE_DIR = "Download/PocketWebShell/"

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

    /** Public Downloads relative path shown in the capsule and used by MediaStore. */
    fun publicRelativePath(fileName: String): String = PUBLIC_RELATIVE_DIR + safeFileName(fileName)

    /** Sub-path for [android.app.DownloadManager.Request.setDestinationInExternalPublicDir]. */
    fun publicSubPath(fileName: String): String = "$PUBLIC_FOLDER/${safeFileName(fileName)}"

    /**
     * Main-frame navigations that are almost certainly a file save, not a page.
     * WebView often loads these instead of firing DownloadListener.
     */
    fun looksLikeFileDownload(url: String): Boolean {
        if (!canEnqueue(url)) return false
        val path = runCatching { URI(url).path }.getOrNull().orEmpty()
        val name = path.substringAfterLast('/')
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return ext.isNotEmpty() && ext in FILE_DOWNLOAD_EXTENSIONS
    }

    /**
     * DownloadManager only issues GET/HEAD. data: and non-http(s) schemes must fail
     * instead of a fake enqueue into private storage.
     */
    fun canEnqueue(url: String, method: String? = null): Boolean {
        if (!method.isNullOrBlank() &&
            !method.equals("GET", ignoreCase = true) &&
            !method.equals("HEAD", ignoreCase = true)
        ) {
            return false
        }
        if (url.startsWith("data:", ignoreCase = true)) return false
        val scheme = runCatching { URI(url).scheme }.getOrNull()?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }

    /** Host only, never a query string. Safe to log. */
    fun logHost(url: String): String =
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"

    private val FILE_DOWNLOAD_EXTENSIONS = setOf(
        "zip", "rar", "7z", "gz", "tgz", "tar", "bz2",
        "apk", "aab", "ipa", "exe", "msi", "dmg", "iso",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "csv", "tsv", "rtf", "odt", "ods",
        "torrent",
    )
}
