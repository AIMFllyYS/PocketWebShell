package com.webshell.core.webengine

/**
 * Consecutive renderer-crash guard. A session gets one automatic replacement
 * attempt; a stable completed navigation or an explicit user navigation starts
 * a fresh budget. This prevents an immediately crashing replacement from
 * spinning forever while still allowing a user-requested retry.
 */
internal class RendererRecoveryGate(
    private val maxAutomaticAttempts: Int = 1,
) {
    private var attempts = 0

    fun tryBeginRecovery(): Boolean {
        if (attempts >= maxAutomaticAttempts) return false
        attempts++
        return true
    }

    fun markStable() {
        attempts = 0
    }

    fun resetForExplicitNavigation() {
        attempts = 0
    }
}

/**
 * Automatic recovery may replace a dead WebView, but must not immediately
 * reload the same local document (the first WebGL/compile peak often
 * crashes the replacement too). Remote sessions keep one auto-reload;
 * a second gone before [markStable] is already rejected by the gate.
 */
internal object RendererRecoveryPolicy {
    fun shouldAutoReloadDocument(localAppId: String?): Boolean = localAppId == null

    fun currentUrlAfterAutomaticRecovery(localAppId: String?, crashedUrl: String?): String =
        if (localAppId != null) {
            "about:blank"
        } else {
            crashedUrl?.takeIf { it.isNotBlank() } ?: "about:blank"
        }

    fun retryLoadUrl(pendingRecoveryUrl: String?, currentUrl: String?, startUrl: String): String {
        fun usable(url: String?) =
            url?.takeIf { it.isNotBlank() && !it.equals("about:blank", ignoreCase = true) }
        return usable(pendingRecoveryUrl) ?: usable(currentUrl) ?: startUrl
    }
}
