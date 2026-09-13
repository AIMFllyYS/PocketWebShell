package com.webshell.core.data

import com.webshell.core.model.LocalAppUrls
import java.net.URLDecoder

/**
 * Display-only filesystem identity for incoming HTML/MD.
 * Never a WebView URL. Filename-only strings are not keys.
 */
object IncomingSourceKey {
    const val PRIMARY_ROOT = "/storage/emulated/0"

    private val primaryAliases = listOf(
        PRIMARY_ROOT,
        "/sdcard",
        "/storage/self/primary",
        "/mnt/sdcard",
        "/mnt/shell/emulated/0",
    )

    fun fromFilesystemPath(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.any { it.isISOControl() }) return null
        var path = raw.trim().replace('\\', '/')
        if (path.startsWith("file:", ignoreCase = true)) {
            path = path.substringAfter(':')
            while (path.startsWith("//")) path = path.removePrefix("/")
            if (!path.startsWith("/")) path = "/$path"
        }
        if (path.startsWith("raw:", ignoreCase = true)) {
            path = path.substring(4)
        }
        path = path.replace(Regex("/{2,}"), "/")
        if (path.length > 1 && path.endsWith('/')) path = path.dropLast(1)
        if (!path.startsWith("/")) return null
        val segments = path.split('/')
        if (segments.any { it == ".." }) return null
        return foldPrimaryVolume(path).takeIf { it.length > 1 }
    }

    fun fromContentUri(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        val trimmed = uriString.trim()
        if (!trimmed.startsWith("content://", ignoreCase = true)) return null
        if (trimmed.contains("fileprovider", ignoreCase = true)) return null
        return trimmed
    }

    fun reuseToken(html: Boolean, key: String): String =
        if (html) "html:$key" else "md:$key"

    fun localAppMatchesKind(localUrl: String, html: Boolean): Boolean =
        if (html) LocalAppUrls.isHtml(localUrl) else LocalAppUrls.isMarkdown(localUrl)

    fun decodeRepeated(raw: String): String {
        var current = raw
        repeat(2) {
            val next = runCatching {
                URLDecoder.decode(current, Charsets.UTF_8.name())
            }.getOrDefault(current)
            if (next == current) return current
            current = next
        }
        return current
    }

    fun addressLabel(displayPath: String?, title: String?, url: String?): String {
        val path = displayPath?.trim().orEmpty()
        fromFilesystemPath(path)?.let { return it }
        if (path.isNotBlank()) {
            val decoded = decodeRepeated(path)
            fromFilesystemPath(decoded)?.let { return it }
            val stripped = decoded.substringAfterLast(':')
            fromFilesystemPath(stripped)?.let { return it }
            if (decoded.startsWith("/") && ".." !in decoded.split('/')) return decoded
        }
        val fileName = title?.trim()?.takeIf { candidate ->
            candidate.isNotBlank() &&
                !candidate.startsWith("tmp-") &&
                !candidate.contains("appassets.androidplatform.net")
        }
        if (fileName != null) return fileName
        val fallback = url?.trim().orEmpty()
        if (fallback.startsWith("/") ) {
            fromFilesystemPath(fallback)?.let { return it }
        }
        if (fallback.isNotBlank() &&
            !fallback.startsWith("about:", ignoreCase = true) &&
            !fallback.contains("appassets.androidplatform.net") &&
            !fallback.startsWith("tmp-")
        ) {
            return fallback
        }
        return fileName ?: "document"
    }

    private fun foldPrimaryVolume(path: String): String {
        for (alias in primaryAliases) {
            if (path.length >= alias.length &&
                path.regionMatches(0, alias, 0, alias.length, ignoreCase = true)
            ) {
                val rest = path.substring(alias.length)
                if (rest.isEmpty() || rest.startsWith("/")) {
                    return PRIMARY_ROOT + rest
                }
            }
        }
        return path
    }
}
