package com.webshell.core.webengine

import com.webshell.core.model.WebTopInsetMode

/** Both coordinates are physical pixels relative to the same window root. */
internal fun remainingWebTopInset(
    mode: WebTopInsetMode,
    safeTop: Int,
    hostTop: Int?,
    parentHandlesTop: Boolean,
): Int = when {
    parentHandlesTop || mode == WebTopInsetMode.EDGE_TO_EDGE -> 0
    else -> (safeTop.coerceAtLeast(0) - (hostTop ?: 0).coerceAtLeast(0)).coerceAtLeast(0)
}
