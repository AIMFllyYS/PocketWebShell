package com.webshell.feature.browser

import com.webshell.core.webengine.LocalWebHost
import com.webshell.core.webengine.UrlRouter

internal fun isIncomingAssetUrl(url: String): Boolean {
    val appId = LocalWebHost.localAppIdFromHttpsUrl(url) ?: return false
    return appId.startsWith("tmp-")
}

internal fun String.stripScheme(): String =
    if (this == "about:blank") "" else removePrefix("https://").removePrefix("http://")

/** Display host only; does not change the URL held by a tab or repository key. */
internal fun String.host(): String {
    val parsed = parseDisplayUrl()
    return if (parsed.keepLiteral) this else parsed.host
}

/**
 * Presentation identity for start-page recents. Lowercased host + path, without
 * scheme, a leading `www.`, fragment, or a trailing slash. `local://` and
 * `about:` stay literal so they are not merged with web hosts.
 */
internal fun String.displayKey(): String {
    val parsed = parseDisplayUrl()
    if (parsed.keepLiteral) return this
    return parsed.host + parsed.path.trimEnd('/')
}

/** URL entry only; adding a search engine is a separate product capability. */
internal fun normalizeUrl(raw: String): String {
    return UrlRouter.normalizeAddressBar(raw).normalized
}

private data class DisplayUrl(val host: String, val path: String, val keepLiteral: Boolean)

private fun String.parseDisplayUrl(): DisplayUrl {
    val raw = trim()
    if (raw.startsWith("local://", ignoreCase = true) ||
        raw.startsWith("about:", ignoreCase = true)
    ) {
        return DisplayUrl(raw, "", keepLiteral = true)
    }
    var rest = raw
    when {
        rest.startsWith("https://", ignoreCase = true) -> rest = rest.substring(8)
        rest.startsWith("http://", ignoreCase = true) -> rest = rest.substring(7)
    }
    rest = rest.substringBefore('#')
    val authority = rest.substringBefore('/').substringBefore('?')
    val host = authority.lowercase().removePrefix("www.")
    val pathWithQuery = if ('/' in rest) rest.substring(rest.indexOf('/')) else ""
    val path = pathWithQuery.substringBefore('?')
    return DisplayUrl(host, path, keepLiteral = false)
}
