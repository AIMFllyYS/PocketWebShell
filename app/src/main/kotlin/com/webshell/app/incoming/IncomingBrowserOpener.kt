package com.webshell.app.incoming

import androidx.core.net.toUri
import com.webshell.core.data.HomeSlotAllocator
import com.webshell.core.data.IncomingSourceKey
import com.webshell.core.data.SettingsRepository
import com.webshell.core.data.WebAppDao
import com.webshell.core.data.WebAppEntity
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.LocalWebHost
import com.webshell.feature.browser.BrowserTab
import com.webshell.feature.browser.BrowserTabKind
import com.webshell.feature.browser.IncomingDocumentAccess
import com.webshell.feature.viewer.IncomingDocuments
import com.webshell.feature.viewer.IncomingFilePolicy
import com.webshell.feature.viewer.IncomingIntentParser
import com.webshell.feature.viewer.IncomingMarkdownPolicy
import com.webshell.feature.viewer.IncomingOpenCandidate
import com.webshell.feature.viewer.IncomingStore
import com.webshell.feature.viewer.IncomingTooLargeException
import com.webshell.feature.viewer.IncomingUnsupportedException
import com.webshell.feature.viewer.ViewerDocumentKind
import com.webshell.feature.viewer.ViewerFailure
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface IncomingMountResult {
    data class Html(
        val startUrl: String,
        val title: String,
        val displayPath: String,
        val localAppId: String,
        val sourceKey: String? = null,
    ) : IncomingMountResult

    data class Markdown(
        val title: String,
        val displayPath: String,
        val content: String,
        val localAppId: String,
        val sourceKey: String? = null,
    ) : IncomingMountResult

    data class ReuseHome(val appId: String, val url: String) : IncomingMountResult

    data class ReuseTab(val sourceKey: String, val html: Boolean) : IncomingMountResult

    data class Failed(val reason: ViewerFailure) : IncomingMountResult
}

@Singleton
class IncomingBrowserOpener @Inject constructor(
    private val documents: IncomingDocuments,
    private val webAppDao: WebAppDao,
    private val settingsRepository: SettingsRepository,
) : IncomingDocumentAccess {

    suspend fun mount(
        candidate: IncomingOpenCandidate,
        existingReuseTokens: Set<String> = emptySet(),
    ): IncomingMountResult = withContext(Dispatchers.IO) {
        runCatching { mountOnIo(candidate, existingReuseTokens) }.getOrElse { error ->
            when (error) {
                is IncomingTooLargeException -> IncomingMountResult.Failed(ViewerFailure.TOO_LARGE)
                is IncomingUnsupportedException -> IncomingMountResult.Failed(ViewerFailure.UNSUPPORTED)
                else -> IncomingMountResult.Failed(ViewerFailure.UNREADABLE)
            }
        }
    }

    override fun readMarkdownForDisplay(localAppId: String): String? {
        if (!IncomingFilePolicy.isTemporarySessionId(localAppId)) return null
        val file = File(IncomingStore.sessionDir(documents.filesDir, localAppId), IncomingFilePolicy.DEFAULT_MARKDOWN_NAME)
        if (!file.isFile) return null
        return runCatching {
            IncomingMarkdownPolicy.forDisplay(file.readText())
        }.getOrNull()
    }

    override fun deleteTemporary(localAppId: String) {
        IncomingStore.deleteSession(documents.filesDir, localAppId)
    }

    override suspend fun persistShortcut(tab: BrowserTab): Boolean = withContext(Dispatchers.IO) {
        runCatching { persistShortcutOnIo(tab) }.onFailure {
            AppLog.warn("incoming", "Incoming document home add failed")
        }.isSuccess
    }

    private suspend fun mountOnIo(
        candidate: IncomingOpenCandidate,
        existingReuseTokens: Set<String>,
    ): IncomingMountResult {
        val uri = runCatching { candidate.uriString.toUri() }.getOrNull()
            ?: throw IncomingUnsupportedException()
        if (!IncomingIntentParser.isSupportedUri(candidate.uriString)) throw IncomingUnsupportedException()
        documents.tryPersistRead(uri)
        val displayName = documents.queryDisplayName(uri)
        val resolved = documents.resolvePathInfo(uri, displayName)
        val displayPath = resolved.displayPath
        val sourceKey = resolved.sourceKey
        val stream = documents.openStream(uri) ?: throw IOException("unreadable")
        return stream.use { input ->
            val header = ByteArray(512)
            val read = input.read(header).coerceAtLeast(0)
            val prefix = header.copyOf(read)
            val kind = IncomingFilePolicy.resolveKind(displayName, candidate.mimeType, prefix)
                ?: throw IncomingUnsupportedException()
            val html = kind == ViewerDocumentKind.HTML
            sourceKey?.let { IncomingSourceKey.reuseToken(html, it) }?.let { token ->
                if (token in existingReuseTokens) {
                    return@use IncomingMountResult.ReuseTab(sourceKey, html)
                }
            }
            if (sourceKey != null) {
                val home = webAppDao.findLocalBySourceKey(sourceKey)
                if (home != null && IncomingSourceKey.localAppMatchesKind(home.url, html)) {
                    return@use IncomingMountResult.ReuseHome(home.id, home.url)
                }
            }
            val title = IncomingFilePolicy.sanitizeFileName(
                displayName ?: IncomingFilePolicy.defaultName(kind),
                kind,
            )
            val chained = IncomingStore.prependedStream(prefix, input)
            when (kind) {
                ViewerDocumentKind.HTML -> mountHtml(title, displayPath, chained, sourceKey)
                ViewerDocumentKind.MARKDOWN -> mountMarkdown(title, displayPath, chained, sourceKey)
            }
        }
    }

    private fun mountHtml(
        title: String,
        displayPath: String,
        input: java.io.InputStream,
        sourceKey: String?,
    ): IncomingMountResult.Html {
        val localAppId = IncomingFilePolicy.newSessionId()
        val dir = IncomingStore.sessionDir(documents.filesDir, localAppId)
        if (!dir.mkdirs() && !dir.isDirectory) throw IOException("import directory unavailable")
        val dest = File(dir, IncomingFilePolicy.DEFAULT_HTML_NAME)
        try {
            IncomingStore.copyBounded(input, dest)
        } catch (error: Exception) {
            IncomingStore.deleteSession(documents.filesDir, localAppId)
            throw error
        }
        if (!LocalWebHost.isSafeLocalPath(localAppId, IncomingFilePolicy.DEFAULT_HTML_NAME)) {
            IncomingStore.deleteSession(documents.filesDir, localAppId)
            throw IncomingUnsupportedException()
        }
        val startUrl = LocalWebHost.toHttpsUrl(
            LocalWebHost.buildLocalAppUrl(localAppId, IncomingFilePolicy.DEFAULT_HTML_NAME),
        )
        return IncomingMountResult.Html(
            startUrl = startUrl,
            title = title,
            displayPath = displayPath,
            localAppId = localAppId,
            sourceKey = sourceKey,
        )
    }

    private fun mountMarkdown(
        title: String,
        displayPath: String,
        input: java.io.InputStream,
        sourceKey: String?,
    ): IncomingMountResult.Markdown {
        val raw = IncomingStore.readTextBounded(input)
        val localAppId = IncomingFilePolicy.newSessionId()
        val dir = IncomingStore.sessionDir(documents.filesDir, localAppId)
        if (!dir.mkdirs() && !dir.isDirectory) {
            throw IOException("import directory unavailable")
        }
        File(dir, IncomingFilePolicy.DEFAULT_MARKDOWN_NAME).writeText(raw)
        return IncomingMountResult.Markdown(
            title = title,
            displayPath = displayPath,
            content = IncomingMarkdownPolicy.forDisplay(raw),
            localAppId = localAppId,
            sourceKey = sourceKey,
        )
    }

    private suspend fun persistShortcutOnIo(tab: BrowserTab) {
        val kind = when (tab.kind) {
            BrowserTabKind.INCOMING_HTML -> ViewerDocumentKind.HTML
            BrowserTabKind.INCOMING_MARKDOWN -> ViewerDocumentKind.MARKDOWN
            BrowserTabKind.WEB -> throw IOException("not incoming")
        }
        val localAppId = tab.localAppId ?: throw IOException("no session")
        val sourceKey = tab.sourceKey ?: IncomingSourceKey.fromFilesystemPath(tab.displayPath)
        if (sourceKey != null) {
            val existing = webAppDao.findLocalBySourceKey(sourceKey)
            if (existing != null && IncomingSourceKey.localAppMatchesKind(existing.url, kind == ViewerDocumentKind.HTML)) {
                return
            }
        }
        val appId = "app-" + UUID.randomUUID().toString().take(8)
        val destDir = File(File(documents.filesDir, LocalWebHost.LOCAL_APPS_DIR), appId)
        if (!destDir.mkdirs() && !destDir.isDirectory) throw IOException("import directory unavailable")
        val entryName = when (kind) {
            ViewerDocumentKind.HTML -> {
                val source = File(
                    IncomingStore.sessionDir(documents.filesDir, localAppId),
                    IncomingFilePolicy.DEFAULT_HTML_NAME,
                )
                if (!source.isFile) throw IOException("missing html")
                source.copyTo(File(destDir, IncomingFilePolicy.DEFAULT_HTML_NAME), overwrite = true)
                IncomingFilePolicy.DEFAULT_HTML_NAME
            }
            ViewerDocumentKind.MARKDOWN -> {
                val source = File(
                    IncomingStore.sessionDir(documents.filesDir, localAppId),
                    IncomingFilePolicy.DEFAULT_MARKDOWN_NAME,
                )
                if (!source.isFile) throw IOException("missing markdown")
                source.copyTo(File(destDir, IncomingFilePolicy.DEFAULT_MARKDOWN_NAME), overwrite = true)
                IncomingFilePolicy.DEFAULT_MARKDOWN_NAME
            }
        }
        val settings = settingsRepository.settings.first()
        val (page, slot) = if (settings.autoArrangeHome) {
            0 to -1
        } else {
            HomeSlotAllocator.appendSlot(
                apps = webAppDao.observeAll().first(),
                pageCapacity = (settings.gridColumns * settings.gridRows).coerceAtLeast(1),
            )
        }
        val cleanTitle = tab.title.substringBeforeLast('.').ifBlank { tab.title }
        webAppDao.upsert(
            WebAppEntity(
                id = appId,
                title = cleanTitle,
                url = LocalWebHost.buildLocalAppUrl(appId, entryName),
                iconUrl = null,
                desktopMode = false,
                darkMode = false,
                keepAlive = false,
                isFavorite = false,
                homePage = page,
                homeCellIndex = slot,
                folderId = null,
                createdAt = System.currentTimeMillis(),
                isLocal = true,
                externalLinksToBrowser = false,
                textZoomPercent = 100,
                importSourceKey = sourceKey,
            ),
        )
        AppLog.log("incoming", "Incoming document added to home")
    }
}
