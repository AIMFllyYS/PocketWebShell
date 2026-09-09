package com.webshell.feature.browser

import com.webshell.core.webengine.UrlRouter

internal fun String.stripScheme(): String =
    if (this == "about:blank") "" else removePrefix("https://").removePrefix("http://")

/** URL entry only; adding a search engine is a separate product capability. */
internal fun normalizeUrl(raw: String): String {
    return UrlRouter.normalizeAddressBar(raw).normalized
}
