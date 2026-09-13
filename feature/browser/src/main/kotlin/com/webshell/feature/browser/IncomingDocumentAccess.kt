package com.webshell.feature.browser

/**
 * App-owned incoming file helpers. [feature/browser] must not depend on
 * [feature/viewer]; the composition root binds the implementation.
 */
interface IncomingDocumentAccess {
    fun readMarkdownForDisplay(localAppId: String): String?
    fun deleteTemporary(localAppId: String)
    suspend fun persistShortcut(tab: BrowserTab): Boolean
}
