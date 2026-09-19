package com.webshell.core.webengine

import java.net.URI

/**
 * Session-scoped consent for main-frame `http://` navigations.
 * Process memory only: a new process asks again. Never logs the raw URL.
 */
object CleartextGate {
    private val allowed = HashMap<String, MutableSet<String>>()

    fun isCleartextHttp(url: String): Boolean {
        val scheme = runCatching { URI(url.trim()).scheme }.getOrNull()?.lowercase()
        return scheme == "http"
    }

    fun hostOf(url: String): String? =
        runCatching { URI(url.trim()).host }.getOrNull()?.lowercase()?.takeIf { it.isNotBlank() }

    fun isAllowed(sessionId: String, host: String): Boolean =
        synchronized(allowed) { allowed[sessionId]?.contains(host.lowercase()) == true }

    fun allow(sessionId: String, host: String) {
        val key = host.lowercase()
        if (key.isBlank()) return
        synchronized(allowed) {
            allowed.getOrPut(sessionId) { mutableSetOf() }.add(key)
        }
    }

    fun forgetSession(sessionId: String) {
        synchronized(allowed) { allowed.remove(sessionId) }
    }

    fun requiresPrompt(sessionId: String, url: String): Boolean {
        if (!isCleartextHttp(url)) return false
        val host = hostOf(url) ?: return false
        return !isAllowed(sessionId, host)
    }
}
