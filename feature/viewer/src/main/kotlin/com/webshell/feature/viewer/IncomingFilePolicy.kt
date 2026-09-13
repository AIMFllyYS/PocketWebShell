package com.webshell.feature.viewer

import java.util.UUID

enum class ViewerDocumentKind { HTML, MARKDOWN }

/**
 * Runtime gate for files offered by VIEW/SEND. Manifest filters stay wide so
 * WeChat/file managers can surface this app; this policy decides what actually
 * mounts. Never logs the URI or display name.
 */
object IncomingFilePolicy {
    const val MAX_BYTES: Long = 20L * 1024 * 1024
    const val SESSION_PREFIX: String = "tmp-"
    const val DEFAULT_HTML_NAME: String = "index.html"
    const val DEFAULT_MARKDOWN_NAME: String = "document.md"

    private val htmlExtensions = setOf("html", "htm")
    private val markdownExtensions = setOf("md", "markdown")
    private val htmlMimes = setOf("text/html", "application/xhtml+xml")
    private val markdownMimes = setOf("text/markdown", "text/x-markdown")
    private val deniedExtensions = setOf(
        "apk", "apks", "xapk", "exe", "dex", "so", "jar", "class",
        "bat", "cmd", "com", "scr", "pif", "msi", "dmg", "iso",
        "sh", "ps1", "vbs", "js", "wasm", "bin", "dll", "sys", "crx",
    )

    fun newSessionId(): String = SESSION_PREFIX + UUID.randomUUID()

    fun isTemporarySessionId(id: String): Boolean =
        id.startsWith(SESSION_PREFIX) && id.length > SESSION_PREFIX.length &&
            id.none { it == '/' || it == '\\' || it.isISOControl() } &&
            id != "." && id != ".."

    fun extensionOf(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val dot = base.lastIndexOf('.')
        if (dot <= 0 || dot == base.lastIndex) return ""
        return base.substring(dot + 1).lowercase()
    }

    fun isDeniedExtension(name: String): Boolean = extensionOf(name) in deniedExtensions

    fun looksLikeDeniedBinary(header: ByteArray): Boolean {
        if (header.size >= 2 && header[0] == 'M'.code.toByte() && header[1] == 'Z'.code.toByte()) return true
        if (header.size >= 4 &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
        ) {
            return true
        }
        if (header.size >= 4 &&
            header[0] == 0x7f.toByte() &&
            header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        ) {
            return true
        }
        return header.size >= 3 &&
            header[0] == 'd'.code.toByte() &&
            header[1] == 'e'.code.toByte() &&
            header[2] == 'x'.code.toByte()
    }

    fun looksLikeHtml(header: ByteArray): Boolean {
        val text = header.toString(Charsets.UTF_8).trimStart('\uFEFF', '\u0000', ' ', '\n', '\r', '\t')
        val head = text.take(32)
        return head.startsWith("<!doctype", ignoreCase = true) ||
            head.startsWith("<html", ignoreCase = true) ||
            head.startsWith("<head", ignoreCase = true)
    }

    fun mimeOf(raw: String?): String? {
        val value = raw?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return value.takeUnless { it == "*/*" }
    }

    fun resolveKind(name: String?, mime: String?, header: ByteArray? = null): ViewerDocumentKind? {
        val fileName = name.orEmpty()
        if (isDeniedExtension(fileName)) return null
        if (header != null && looksLikeDeniedBinary(header)) return null
        val ext = extensionOf(fileName)
        if (ext in htmlExtensions) return ViewerDocumentKind.HTML
        if (ext in markdownExtensions) return ViewerDocumentKind.MARKDOWN
        val normalizedMime = mimeOf(mime)
        if (normalizedMime in htmlMimes) return ViewerDocumentKind.HTML
        if (normalizedMime in markdownMimes) return ViewerDocumentKind.MARKDOWN
        if (header != null && looksLikeHtml(header)) return ViewerDocumentKind.HTML
        return null
    }

    fun sanitizeFileName(raw: String, kind: ViewerDocumentKind): String {
        val cleaned = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .trim()
        val fallback = when (kind) {
            ViewerDocumentKind.HTML -> DEFAULT_HTML_NAME
            ViewerDocumentKind.MARKDOWN -> DEFAULT_MARKDOWN_NAME
        }
        val named = cleaned.ifBlank { fallback }
        if (isDeniedExtension(named) || named == "." || named == "..") return fallback
        return named.take(120)
    }

    fun defaultName(kind: ViewerDocumentKind): String = when (kind) {
        ViewerDocumentKind.HTML -> DEFAULT_HTML_NAME
        ViewerDocumentKind.MARKDOWN -> DEFAULT_MARKDOWN_NAME
    }
}
