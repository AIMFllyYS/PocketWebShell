package com.webshell.feature.browser

import com.webshell.core.data.BrowserOpenTabEntity

internal const val HISTORY_PAGE_SIZE = 20

internal fun sanitizeHistoryIconUrl(url: String): String? {
    val value = url.trim()
    if (value.isEmpty()) return null
    if (!(value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true))) {
        return null
    }
    if (value.any { it.isISOControl() || it.isWhitespace() }) return null
    return value
}

internal fun parseBrowserTabKind(raw: String): BrowserTabKind = when (raw) {
    "incoming_html" -> BrowserTabKind.INCOMING_HTML
    "incoming_markdown" -> BrowserTabKind.INCOMING_MARKDOWN
    else -> BrowserTabKind.WEB
}

internal fun BrowserTabKind.storageName(): String = when (this) {
    BrowserTabKind.WEB -> "web"
    BrowserTabKind.INCOMING_HTML -> "incoming_html"
    BrowserTabKind.INCOMING_MARKDOWN -> "incoming_markdown"
}

internal fun BrowserOpenTabEntity.toBrowserTab(
    kind: BrowserTabKind,
    markdown: String?,
): BrowserTab = BrowserTab(
    tabId = tabId,
    title = title,
    url = url,
    sessionId = sessionId,
    restoreStartUrlIfBlank = restoreStartUrlIfBlank,
    kind = kind,
    displayPath = displayPath,
    markdownContent = markdown,
    localAppId = localAppId,
    sourceKey = sourceKey,
)

internal fun BrowserTab.toOpenTabEntity(
    position: Int,
    desktopMode: Boolean,
    updatedAt: Long,
): BrowserOpenTabEntity = BrowserOpenTabEntity(
    tabId = tabId,
    sessionId = sessionId,
    url = url,
    title = title,
    position = position,
    desktopMode = desktopMode,
    restoreStartUrlIfBlank = restoreStartUrlIfBlank,
    kind = kind.storageName(),
    displayPath = displayPath,
    localAppId = localAppId,
    sourceKey = sourceKey,
    updatedAt = updatedAt,
)
