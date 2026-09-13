package com.webshell.feature.viewer

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.HomeSlotAllocator
import com.webshell.core.data.SettingsRepository
import com.webshell.core.data.WebAppDao
import com.webshell.core.data.WebAppEntity
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.LocalWebHost
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.ShellListener
import com.webshell.core.webengine.WebEngineDefaults
import com.webshell.core.webengine.WebViewPool
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ViewerFailure { UNSUPPORTED, TOO_LARGE, UNREADABLE }

sealed interface ViewerState {
    data object Idle : ViewerState
    data object Loading : ViewerState
    data class Failed(val reason: ViewerFailure) : ViewerState
    data class Html(
        val config: ShellConfig,
        val title: String,
        val displayPath: String,
        val loading: Boolean = true,
        val progress: Int = 0,
        val rendererGone: Boolean = false,
        val askAddToHome: Boolean = false,
    ) : ViewerState
    data class Markdown(
        val title: String,
        val content: String,
        val displayPath: String,
        val askAddToHome: Boolean = false,
    ) : ViewerState
}

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val documents: IncomingDocuments,
    private val webAppDao: WebAppDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ViewerState>(ViewerState.Idle)
    val state: StateFlow<ViewerState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()
    private var openJob: Job? = null
    private var openedToken: Long? = null
    private var activeSessionId: String? = null
    private var markdownText: String? = null
    private var documentTitle: String = ""
    private var documentKind: ViewerDocumentKind? = null

    fun open(candidate: IncomingOpenCandidate) {
        if (openedToken == candidate.token && _state.value !is ViewerState.Idle) return
        openedToken = candidate.token
        openJob?.cancel()
        releaseSession()
        markdownText = null
        documentTitle = ""
        documentKind = null
        _state.value = ViewerState.Loading
        openJob = viewModelScope.launch {
            try {
                _state.value = withContext(Dispatchers.IO) { mount(candidate) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IncomingTooLargeException) {
                _state.value = ViewerState.Failed(ViewerFailure.TOO_LARGE)
            } catch (_: IncomingUnsupportedException) {
                _state.value = ViewerState.Failed(ViewerFailure.UNSUPPORTED)
            } catch (_: Exception) {
                _state.value = ViewerState.Failed(ViewerFailure.UNREADABLE)
            }
        }
    }

    fun release() {
        openJob?.cancel()
        openedToken = null
        markdownText = null
        documentTitle = ""
        documentKind = null
        releaseSession()
        _state.value = ViewerState.Idle
    }

    fun offerAddToHome() {
        when (val current = _state.value) {
            is ViewerState.Html -> if (!current.askAddToHome) _state.value = current.copy(askAddToHome = true)
            is ViewerState.Markdown -> if (!current.askAddToHome) _state.value = current.copy(askAddToHome = true)
            else -> Unit
        }
    }

    fun dismissAddToHome() {
        when (val current = _state.value) {
            is ViewerState.Html -> _state.value = current.copy(askAddToHome = false)
            is ViewerState.Markdown -> _state.value = current.copy(askAddToHome = false)
            else -> Unit
        }
    }

    fun addToHome() {
        val kind = documentKind ?: return
        val title = documentTitle
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { persistShortcut(kind, title) }.isSuccess
            }
            dismissAddToHome()
            if (ok) {
                AppLog.log("viewer", "Incoming document added to home")
                _messages.tryEmit(R.string.viewer_added_home)
            } else {
                AppLog.warn("viewer", "Incoming document home add failed")
                _messages.tryEmit(R.string.viewer_add_home_failed)
            }
        }
    }

    fun rejectAdoptedWindow(sessionId: String) {
        if (sessionId != activeSessionId) WebViewPool.destroyAndForget(sessionId)
    }

    fun onHtmlReady(config: ShellConfig) {
        val ready = _state.value as? ViewerState.Html ?: return
        if (ready.config.sessionId != config.sessionId) return
        val sessionId = config.sessionId ?: return
        val shell = WebViewPool.get(sessionId) ?: return
        val current = shell.currentUrl()
        val needsFirstNavigation = current.isNullOrBlank() || current.equals("about:blank", ignoreCase = true)
        if (needsFirstNavigation) {
            WebViewPool.evictUnprotectedExcept(sessionId)
        }
        if (shell.isAwaitingExplicitRendererRetry()) return
        if (needsFirstNavigation) {
            shell.loadWithStateRestore(config.startUrl)
        }
    }

    fun htmlListener(sessionId: String): ShellListener = object : ShellListener {
        private fun update(transform: (ViewerState.Html) -> ViewerState.Html) {
            val html = _state.value as? ViewerState.Html ?: return
            if (html.config.sessionId == sessionId) _state.value = transform(html)
        }

        override fun onPageStarted(url: String) {
            if (!url.equals("about:blank", ignoreCase = true)) {
                update { it.copy(loading = true, progress = 0, rendererGone = false) }
            }
        }

        override fun onProgress(progress: Int) {
            update { it.copy(progress = progress, loading = progress < 100) }
        }

        override fun onFirstPaint(url: String) {
            if (url.equals("about:blank", ignoreCase = true)) return
            update { it.copy(progress = it.progress.coerceAtLeast(90)) }
        }

        override fun onPageFinished(url: String) {
            update { it.copy(loading = false, progress = 100) }
            if (!url.equals("about:blank", ignoreCase = true)) offerAddToHome()
        }

        override fun onTitleReceived(title: String) {
            val cleaned = title.trim()
            if (cleaned.isNotEmpty() && !cleaned.equals("about:blank", ignoreCase = true)) {
                update { it.copy(title = cleaned) }
            }
        }

        override fun onPageError(url: String, errorCode: Int, description: String, insecureHttp: Boolean) {
            if (errorCode == WebEngineDefaults.ERROR_RENDERER_GONE) {
                update { it.copy(loading = false, rendererGone = true) }
            }
        }

        override fun onRenderProcessRecovered() {
            update { it.copy(rendererGone = false) }
        }

        override fun onRenderProcessRecoveryFailed() {
            update { it.copy(loading = false, rendererGone = true) }
        }
    }

    fun reloadHtml() {
        val html = _state.value as? ViewerState.Html ?: return
        val sessionId = html.config.sessionId ?: return
        WebViewPool.get(sessionId)?.reload()
        _state.value = html.copy(rendererGone = false, loading = true)
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }

    private fun releaseSession() {
        val sessionId = activeSessionId ?: return
        activeSessionId = null
        WebViewPool.destroyAndForget(sessionId)
        IncomingStore.deleteSession(documents.filesDir, sessionId)
    }

    private fun mount(candidate: IncomingOpenCandidate): ViewerState {
        val uri = runCatching { candidate.uriString.toUri() }.getOrNull()
            ?: throw IncomingUnsupportedException()
        if (!IncomingIntentParser.isSupportedUri(candidate.uriString)) throw IncomingUnsupportedException()
        documents.tryPersistRead(uri)
        val displayName = documents.queryDisplayName(uri)
        val displayPath = IncomingPathResolver.resolve(documents.appContext, uri, displayName)
        val stream = documents.openStream(uri) ?: throw IOException("unreadable")
        return stream.use { input ->
            val header = ByteArray(512)
            val read = input.read(header).coerceAtLeast(0)
            val prefix = header.copyOf(read)
            val kind = IncomingFilePolicy.resolveKind(displayName, candidate.mimeType, prefix)
                ?: throw IncomingUnsupportedException()
            val title = IncomingFilePolicy.sanitizeFileName(
                displayName ?: IncomingFilePolicy.defaultName(kind),
                kind,
            )
            documentTitle = title
            documentKind = kind
            val chained = IncomingStore.prependedStream(prefix, input)
            when (kind) {
                ViewerDocumentKind.HTML -> mountHtml(title, displayPath, chained)
                ViewerDocumentKind.MARKDOWN -> {
                    val text = IncomingStore.readTextBounded(chained)
                    markdownText = text
                    ViewerState.Markdown(
                        title = title,
                        content = IncomingMarkdownPolicy.forDisplay(text),
                        displayPath = displayPath,
                    )
                }
            }
        }
    }

    private fun mountHtml(title: String, displayPath: String, input: java.io.InputStream): ViewerState.Html {
        val sessionId = IncomingFilePolicy.newSessionId()
        activeSessionId = sessionId
        val dir = IncomingStore.sessionDir(documents.filesDir, sessionId)
        if (!dir.mkdirs() && !dir.isDirectory) {
            activeSessionId = null
            throw IOException("import directory unavailable")
        }
        val dest = File(dir, IncomingFilePolicy.DEFAULT_HTML_NAME)
        try {
            IncomingStore.copyBounded(input, dest)
        } catch (error: Exception) {
            IncomingStore.deleteSession(documents.filesDir, sessionId)
            activeSessionId = null
            throw error
        }
        if (!LocalWebHost.isSafeLocalPath(sessionId, IncomingFilePolicy.DEFAULT_HTML_NAME)) {
            IncomingStore.deleteSession(documents.filesDir, sessionId)
            throw IncomingUnsupportedException()
        }
        val startUrl = LocalWebHost.toHttpsUrl(
            LocalWebHost.buildLocalAppUrl(sessionId, IncomingFilePolicy.DEFAULT_HTML_NAME),
        )
        activeSessionId = sessionId
        return ViewerState.Html(
            config = ShellConfig(
                sessionId = sessionId,
                profileId = null,
                startUrl = startUrl,
                localAppId = sessionId,
                pullToRefresh = false,
                forceEnableZoom = false,
                externalLinkPolicy = ShellConfig.ExternalLinkPolicy.OPEN_IN_SAME,
            ),
            title = title,
            displayPath = displayPath,
        )
    }

    private suspend fun persistShortcut(kind: ViewerDocumentKind, title: String) {
        val appId = "app-" + UUID.randomUUID().toString().take(8)
        val destDir = File(File(documents.filesDir, LocalWebHost.LOCAL_APPS_DIR), appId)
        if (!destDir.mkdirs() && !destDir.isDirectory) throw IOException("import directory unavailable")
        val entryName = when (kind) {
            ViewerDocumentKind.HTML -> {
                val sessionId = activeSessionId ?: throw IOException("no session")
                val source = File(
                    IncomingStore.sessionDir(documents.filesDir, sessionId),
                    IncomingFilePolicy.DEFAULT_HTML_NAME,
                )
                if (!source.isFile) throw IOException("missing html")
                val dest = File(destDir, IncomingFilePolicy.DEFAULT_HTML_NAME)
                source.copyTo(dest, overwrite = true)
                IncomingFilePolicy.DEFAULT_HTML_NAME
            }
            ViewerDocumentKind.MARKDOWN -> {
                val text = markdownText ?: throw IOException("missing markdown")
                val dest = File(destDir, IncomingFilePolicy.DEFAULT_MARKDOWN_NAME)
                dest.writeText(text)
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
        val cleanTitle = title.substringBeforeLast('.').ifBlank { title }
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
            ),
        )
    }

}
