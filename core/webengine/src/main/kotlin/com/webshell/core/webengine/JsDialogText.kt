package com.webshell.core.webengine

/** Page-supplied JS dialog text is untrusted; keep it displayable and bounded. */
object JsDialogText {
    const val MAX_MESSAGE_CHARS = 2_000

    fun sanitize(raw: String?): String {
        val value = raw.orEmpty()
        if (value.isEmpty()) return ""
        return buildString(minOf(value.length, MAX_MESSAGE_CHARS)) {
            for (ch in value) {
                if (length >= MAX_MESSAGE_CHARS) break
                append(if (ch.isISOControl() && ch != '\n' && ch != '\t') ' ' else ch)
            }
        }
    }
}
