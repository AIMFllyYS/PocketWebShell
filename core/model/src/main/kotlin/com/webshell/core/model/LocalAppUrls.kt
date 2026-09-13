package com.webshell.core.model

/** Entry filename of a `local://` app URL. Display-layer icon choice uses this, not WebView. */
object LocalAppUrls {
    fun entryName(url: String): String =
        url.substringAfterLast('/').substringBefore('?').lowercase()

    fun isMarkdown(url: String): Boolean {
        val name = entryName(url)
        return name.endsWith(".md") || name.endsWith(".markdown")
    }

    fun isHtml(url: String): Boolean {
        val name = entryName(url)
        return name.endsWith(".html") || name.endsWith(".htm")
    }
}
