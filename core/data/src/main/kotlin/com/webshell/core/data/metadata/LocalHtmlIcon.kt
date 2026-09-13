package com.webshell.core.data.metadata

import java.io.File
import java.util.Base64
import org.jsoup.Jsoup

/** 从本地 HTML 里读标签页图标（相对文件、远程 http(s)、小体积 data URI）。 */
object LocalHtmlIcon {
    fun relativePath(html: String): String? {
        val ranked = declaredCandidates(html).mapNotNull { href ->
            if (isRemoteHref(href) || href.startsWith("data:", ignoreCase = true)) return@mapNotNull null
            val cleaned = href.substringBefore('?').trimStart('/', '\\').removePrefix("./")
            if (cleaned.isBlank() || cleaned.contains("..")) null else 1 to cleaned
        }
        return ranked.maxByOrNull { it.first }?.second?.takeIf { it.isNotBlank() }
    }

    fun existingPath(dir: File, entryHtmlName: String): String? {
        val htmlFile = File(dir, entryHtmlName)
        val html = runCatching { readHead(htmlFile) }.getOrNull().orEmpty()
        val candidates = declaredCandidates(html)
        for (href in candidates) {
            localFile(dir, href)?.let { return it }
        }
        for (href in candidates) {
            resolveCandidate(dir, href)?.let { return it }
        }
        for (name in listOf("favicon.png", "apple-touch-icon.png", "favicon.ico", "icon.png")) {
            val file = File(dir, name)
            if (file.isFile) return file.absolutePath
        }
        return null
    }

    internal fun declaredCandidates(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val ranked = ArrayList<Pair<Int, String>>()
        fun add(href: String, score: Int) {
            val cleaned = href.trim()
            if (cleaned.isBlank()) return
            if (ranked.none { it.second == cleaned }) ranked += score to cleaned
        }
        for (el in doc.select("link[rel]")) {
            val rels = el.attr("rel").lowercase().split(Regex("\\s+"))
            val href = el.attr("href").trim()
            if (href.isEmpty()) continue
            val score = when {
                "apple-touch-icon" in rels || "apple-touch-icon-precomposed" in rels -> 400
                "icon" in rels -> 200
                else -> continue
            }
            add(href, score)
        }
        doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { add(it, 40) }
        return ranked.sortedByDescending { it.first }.map { it.second }
    }

    internal fun resolveCandidate(dir: File, href: String): String? {
        val value = href.trim()
        if (value.isEmpty()) return null
        if (value.startsWith("data:image/", ignoreCase = true)) {
            return extractDataUri(dir, value)
        }
        if (value.startsWith("//")) return "https:$value"
        if (isRemoteHref(value)) return value
        return localFile(dir, value)
    }

    private fun localFile(dir: File, href: String): String? {
        if (isRemoteHref(href) || href.startsWith("//") || href.startsWith("data:", ignoreCase = true)) {
            return null
        }
        val relative = href.substringBefore('?').trimStart('/', '\\').removePrefix("./")
        if (relative.isBlank() || relative.contains("..") || relative.contains('\\')) return null
        val file = File(dir, relative)
        return file.takeIf { it.isFile }?.absolutePath
    }

    private fun isRemoteHref(href: String): Boolean =
        href.startsWith("https://", ignoreCase = true) ||
            href.startsWith("http://", ignoreCase = true)

    internal fun extractDataUri(dir: File, href: String): String? {
        val comma = href.indexOf(',')
        if (comma <= 0) return null
        val header = href.substring(5, comma).lowercase()
        val payload = href.substring(comma + 1)
        if (payload.isEmpty() || payload.length > MAX_DATA_URI_CHARS) return null
        if ("base64" !in header) return null
        val ext = when {
            header.startsWith("image/png") -> "png"
            header.startsWith("image/jpeg") || header.startsWith("image/jpg") -> "jpg"
            header.startsWith("image/webp") -> "webp"
            header.startsWith("image/x-icon") || header.startsWith("image/vnd.microsoft.icon") -> "ico"
            else -> return null
        }
        val bytes = runCatching { Base64.getDecoder().decode(payload) }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_DATA_URI_BYTES) return null
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val dest = File(dir, "extracted-favicon.$ext")
        return runCatching {
            dest.writeBytes(bytes)
            dest.absolutePath
        }.getOrNull()
    }

    internal const val HEAD_BYTES = 256 * 1024
    private const val MAX_DATA_URI_CHARS = 350_000
    private const val MAX_DATA_URI_BYTES = 256 * 1024

    internal fun readHead(file: File, maxBytes: Int = HEAD_BYTES): String {
        if (!file.isFile) return ""
        return file.inputStream().use { stream ->
            val buf = ByteArray(maxBytes)
            val n = stream.read(buf)
            if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
        }
    }
}
