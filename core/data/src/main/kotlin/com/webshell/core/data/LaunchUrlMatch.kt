package com.webshell.core.data

import java.net.URI

/** Loose equality for launch extras that may differ by slash, host case or a www prefix. */
object LaunchUrlMatch {
    fun canonical(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isISOControl() }) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val path = uri.path.orEmpty().trimEnd('/').let { if (it == "/") "" else it }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return buildString {
            append(scheme)
            append("://")
            append(host)
            append(port)
            append(path)
            uri.rawQuery?.takeIf { it.isNotEmpty() }?.let { append('?').append(it) }
        }
    }

    fun matches(stored: String, requested: String): Boolean {
        if (stored == requested) return true
        val left = canonical(stored) ?: return false
        val right = canonical(requested) ?: return false
        return left == right
    }
}
