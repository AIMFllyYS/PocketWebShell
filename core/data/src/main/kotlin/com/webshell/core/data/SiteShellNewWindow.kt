package com.webshell.core.data

/** Global / per-site storage values for site-shell `window.open` / `target=_blank`. */
const val SITE_SHELL_NEW_WINDOW_ADOPT = "adopt_browser"
const val SITE_SHELL_NEW_WINDOW_REPLACE = "replace_shell"

fun normalizeSiteShellNewWindowPolicy(raw: String?): String =
    if (raw == SITE_SHELL_NEW_WINDOW_REPLACE) SITE_SHELL_NEW_WINDOW_REPLACE else SITE_SHELL_NEW_WINDOW_ADOPT

/** Per-app override cycle: follow global → adopt → replace → follow global. */
fun nextSiteShellNewWindowOverride(current: String?): String? = when (current) {
    null -> SITE_SHELL_NEW_WINDOW_ADOPT
    SITE_SHELL_NEW_WINDOW_ADOPT -> SITE_SHELL_NEW_WINDOW_REPLACE
    else -> null
}
