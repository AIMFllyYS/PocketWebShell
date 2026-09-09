package com.webshell.core.webengine

/** Immutable target=_blank/window.open request, attributed to both sessions. */
data class NewWindowRequest(
    val sourceSessionId: String,
    val sourceUrl: String?,
    val isUserGesture: Boolean,
    val isDialog: Boolean,
    val initialUrl: String?,
    val targetSessionId: String,
)
