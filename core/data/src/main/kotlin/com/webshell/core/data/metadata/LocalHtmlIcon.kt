package com.webshell.core.data.metadata

import java.io.File
import org.jsoup.Jsoup

/** 从本地 HTML 里读标签页图标的相对路径（不访问网络）。 */
object LocalHtmlIcon {
    fun relativePath(html: String): String? {
        val doc = Jsoup.parse(html)
        val ranked = doc.select("link[rel]").mapNotNull { el ->
            val rels = el.attr("rel").lowercase().split(Regex("\\s+"))
            val href = el.attr("href").trim()
            if (href.isEmpty() || href.contains("://") || href.startsWith("data:", ignoreCase = true)) {
                return@mapNotNull null
            }
            val score = when {
                "apple-touch-icon" in rels || "apple-touch-icon-precomposed" in rels -> 2
                "icon" in rels -> 1
                else -> return@mapNotNull null
            }
            score to href.substringBefore('?').trimStart('/', '\\').removePrefix("./")
        }
        return ranked.maxByOrNull { it.first }?.second?.takeIf { it.isNotBlank() }
    }

    fun existingPath(dir: File, entryHtmlName: String): String? {
        val htmlFile = File(dir, entryHtmlName)
        val declared = runCatching { relativePath(readHead(htmlFile)) }.getOrNull()
        if (declared != null) {
            val file = File(dir, declared)
            if (file.isFile) return file.absolutePath
        }
        for (name in listOf("favicon.png", "apple-touch-icon.png", "favicon.ico", "icon.png")) {
            val file = File(dir, name)
            if (file.isFile) return file.absolutePath
        }
        return null
    }

    internal const val HEAD_BYTES = 256 * 1024

    internal fun readHead(file: File, maxBytes: Int = HEAD_BYTES): String {
        if (!file.isFile) return ""
        return file.inputStream().use { stream ->
            val buf = ByteArray(maxBytes)
            val n = stream.read(buf)
            if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
        }
    }
}
