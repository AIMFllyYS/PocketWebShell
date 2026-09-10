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
