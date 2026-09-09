package com.webshell.core.webengine

import java.net.URI

/**
 * The one scheme policy used by the address bar and WebView callbacks.
 * Keeping this in the engine prevents the browser and saved-site shell from
 * gradually acquiring different security rules.
 */
enum class UrlRoute {
    WEB,
    ABOUT_BLANK,
    EXTERNAL_INTENT,
    BLOB,
    DATA,
    JAVASCRIPT,
    BLOCKED,
    UNKNOWN,
}

data class UrlDecision(val route: UrlRoute, val normalized: String, val reason: String? = null)

object UrlRouter {
    private val externalSchemes = setOf("tel", "sms", "mailto", "geo", "market")

    fun classify(raw: String): UrlDecision {
        val value = raw.trim()
        if (value.isEmpty()) return UrlDecision(UrlRoute.BLOCKED, "", "empty")
        val scheme = runCatching { URI(value).scheme?.lowercase() }.getOrNull()
        return when (scheme) {
            "http", "https" -> if (isSafeHttpUrl(value)) {
                UrlDecision(UrlRoute.WEB, value)
            } else UrlDecision(UrlRoute.BLOCKED, "", "invalid-http-url")
            "about" -> if (value.equals("about:blank", ignoreCase = true)) {
                UrlDecision(UrlRoute.ABOUT_BLANK, "about:blank")
            } else UrlDecision(UrlRoute.BLOCKED, "", "about-url-not-allowed")
            "intent" -> UrlDecision(UrlRoute.EXTERNAL_INTENT, value)
            in externalSchemes -> UrlDecision(UrlRoute.EXTERNAL_INTENT, value)
            "blob" -> UrlDecision(UrlRoute.BLOB, value)
            "data" -> UrlDecision(UrlRoute.DATA, value)
            "javascript" -> UrlDecision(UrlRoute.JAVASCRIPT, value)
            "file", "content" -> UrlDecision(UrlRoute.BLOCKED, "", "privileged-scheme")
            null -> UrlDecision(UrlRoute.UNKNOWN, value, "missing-scheme")
            else -> UrlDecision(UrlRoute.UNKNOWN, value, "unknown-scheme")
        }
    }

    /** Address-bar normalization. Search engines remain an explicit future feature. */
    fun normalizeAddressBar(raw: String): UrlDecision {
        val value = raw.trim()
        if (value.isEmpty()) return UrlDecision(UrlRoute.BLOCKED, "", "empty")
        val explicitScheme = value.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (explicitScheme.isNotEmpty()) return classify(value)
        if (value.contains('.') && !value.any(Char::isWhitespace)) {
            return classify("https://$value")
        }
        return UrlDecision(UrlRoute.BLOCKED, "", "not-a-web-address")
    }

    private fun isSafeHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.port in -1..65535 && !value.any(Char::isISOControl) && !value.any(Char::isWhitespace)
    }

}
