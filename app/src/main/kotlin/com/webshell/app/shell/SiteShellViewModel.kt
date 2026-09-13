package com.webshell.app.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.webshell.core.data.BrowserSavedPagesRepository
import com.webshell.core.data.SettingsRepository
import com.webshell.core.data.WebAppEntity
import com.webshell.core.data.WebAppLookupRepository
import com.webshell.core.data.metadata.SiteMetadataFetcher
import com.webshell.core.model.LocalAppUrls
import com.webshell.core.webengine.KeepAliveRegistry
import com.webshell.core.webengine.LocalWebHost
import com.webshell.core.webengine.NewWindowRequest
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.ShellListener
import com.webshell.core.webengine.WebEngineDefaults
import com.webshell.core.webengine.WebViewPool
import com.webshell.core.webengine.resolveForceEnableZoom
import com.webshell.feature.viewer.IncomingMarkdownPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SiteShellState {
    data object Loading : SiteShellState
    data object Unavailable : SiteShellState
    data class Ready(
        val config: ShellConfig,
        val request: Pair<String, String?>,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val loading: Boolean = true,
        val progress: Int = 0,
        val pageUrl: String = "",
        val pageTitle: String = "",
        val loadError: SiteShellLoadError? = null,
    ) : SiteShellState
    data class Markdown(
        val title: String,
        val content: String,
        val request: Pair<String, String?>,
    ) : SiteShellState
}

enum class SiteShellLoadError { RENDERER_GONE }

data class SiteShellOrbUi(
    val enabled: Boolean = true,
    val x: Float = -1f,
    val y: Float = -1f,
    val parked: Boolean = false,
)

/** One launch resolver; the screen never reads a DAO or manipulates a native WebView. */
@HiltViewModel
class SiteShellViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lookup: WebAppLookupRepository,
    private val sessions: ShellSessionController,
    private val settingsRepository: SettingsRepository,
    private val savedPages: BrowserSavedPagesRepository,
    private val metadataFetcher: SiteMetadataFetcher,
) : ViewModel() {
    /**
     * null = the persisted setting has not been read yet (DataStore's first
     * emission is async). A synthetic `false` default here would win a race
     * against [ShellSessionController.openSession]'s already-correct,
     * synchronously-baked [ShellConfig.pullToRefresh] the moment
     * [ShellWebViewHost] reconfigures on its very first frame — silently
     * turning an enabled pull-to-refresh back off. Callers must only apply
     * this value once it is non-null.
     */
    val pullToRefreshEnabled: StateFlow<Boolean?> = settingsRepository.settings
        .map { it.pullToRefreshEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    /**
     * null until DataStore emits, same race as [pullToRefreshEnabled].
     * Callers must keep the session-baked [ShellConfig.forceEnableZoom]
     * until this value is non-null.
     */
    val forceEnableZoomEnabled: StateFlow<Boolean?> = settingsRepository.settings
        .map { it.forceEnableZoomEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val siteShellOrb: StateFlow<SiteShellOrbUi> = settingsRepository.settings
        .map {
            SiteShellOrbUi(
                enabled = it.siteShellOrbEnabled,
                x = it.siteShellOrbX,
                y = it.siteShellOrbY,
                parked = it.siteShellOrbParked,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SiteShellOrbUi())
    val bookmarkedUrls: StateFlow<Set<String>> = savedPages.observeBookmarks()
        .map { rows -> rows.map { it.url }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private val _state = MutableStateFlow<SiteShellState>(SiteShellState.Loading)
    val state: StateFlow<SiteShellState> = _state.asStateFlow()
    private var openJob: Job? = null
    private var latestRequest: Pair<String, String?>? = null
    /** Survives the UI listener being torn down while a popup is handed to Browse. */
    var adoptWindow: ((sessionId: String, initialUrl: String?) -> Unit)? = null

    fun open(initialUrl: String, appId: String?, force: Boolean = false) {
        val request = initialUrl to appId
        val current = _state.value
        val sameOpen = when (current) {
            is SiteShellState.Ready -> current.request == request
            is SiteShellState.Markdown -> current.request == request
            else -> false
        }
        if (!force && sameOpen) return
        latestRequest = request
        openJob?.cancel()
        val pooled = appId?.let { WebViewPool.get(it) }
        val pooledUrl = pooled?.currentUrl()
        if (!force &&
            pooled != null &&
            !pooledUrl.isNullOrBlank() &&
            !pooledUrl.equals("about:blank", ignoreCase = true)
        ) {
            _state.value = SiteShellState.Ready(
                config = pooled.config,
                request = request,
                pageUrl = pooledUrl,
                pageTitle = KeepAliveRegistry.entries.firstOrNull { it.sessionId == appId }?.title.orEmpty(),
                loading = false,
                progress = 100,
                canGoBack = pooled.canGoBack(),
                canGoForward = pooled.canGoForward(),
            )
            openJob = viewModelScope.launch {
                try {
                    val app = lookup.getById(appId)
                    val config = if (app != null) sessions.openSession(app) else sessions.openDirectSession(initialUrl)
                    val ready = _state.value as? SiteShellState.Ready ?: return@launch
                    if (ready.request != request) return@launch
                    _state.value = ready.copy(
                        config = config,
                        pageTitle = app?.title?.takeIf { it.isNotBlank() } ?: ready.pageTitle,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (_state.value is SiteShellState.Ready &&
                        (_state.value as SiteShellState.Ready).request == request
                    ) {
                        return@launch
                    }
                    _state.value = SiteShellState.Unavailable
                }
            }
            return
        }
        // Re-opening the same live session keeps the last frame. Switching
        // sites must not keep the previous Ready chrome while the next
        // lookup is in flight — that flashes the wrong page / error mask.
        if (!sameOpen || force) _state.value = SiteShellState.Loading
        openJob = viewModelScope.launch {
            try {
                val app = if (appId != null) lookup.getById(appId) else lookup.findByUrl(initialUrl)
                if (appId != null && app == null) {
                    _state.value = SiteShellState.Unavailable
                    return@launch
                }
                if (app != null && isLocalMarkdown(app)) {
                    val markdown = withContext(Dispatchers.IO) { readLocalMarkdown(app) }
                    if (markdown == null) {
                        _state.value = SiteShellState.Unavailable
                        return@launch
                    }
                    _state.value = SiteShellState.Markdown(
                        title = app.title,
                        content = IncomingMarkdownPolicy.forDisplay(markdown),
                        request = request,
                    )
                    return@launch
                }
                val config = if (app != null) sessions.openSession(app) else sessions.openDirectSession(initialUrl)
                _state.value = SiteShellState.Ready(
                    config,
                    request,
                    pageUrl = config.startUrl,
                    pageTitle = app?.title.orEmpty(),
                    loading = true,
                    progress = 0,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = SiteShellState.Unavailable
            }
        }
    }

    fun retryOpen() { latestRequest?.let { open(it.first, it.second, force = true) } }
    fun cancelPendingOpen() { openJob?.cancel() }

    fun onReady(config: ShellConfig) {
        val ready = _state.value as? SiteShellState.Ready ?: return
        if (ready.config.sessionId != config.sessionId) return
        _state.value = ready.copy(
            canGoBack = sessions.ensureLoaded(config),
            canGoForward = config.sessionId?.let(sessions::canGoForward) == true,
        )
    }

    fun listenerFor(sessionId: String): ShellListener = object : ShellListener {
        private var rendererGone = false
        private fun update(sessionId: String, transform: (SiteShellState.Ready) -> SiteShellState.Ready) {
            val ready = _state.value as? SiteShellState.Ready ?: return
            if (ready.config.sessionId == sessionId) _state.value = transform(ready)
        }
        override fun onCanGoBackChanged(canGoBack: Boolean) {
            update(sessionId) { it.copy(canGoBack = canGoBack) }
        }
        override fun onCanGoForwardChanged(canGoForward: Boolean) {
            update(sessionId) { it.copy(canGoForward = canGoForward) }
        }
        override fun onPageStarted(url: String) {
            if (!url.equals("about:blank", ignoreCase = true)) rendererGone = false
            update(sessionId) { it.applyPageStarted(url) }
        }
        override fun onTitleReceived(title: String) {
            update(sessionId) { it.copy(pageTitle = title) }
        }
        override fun onProgress(progress: Int) {
            update(sessionId) { it.copy(progress = progress, loading = progress < 100) }
        }
        override fun onFirstPaint(url: String) {
            update(sessionId) { it.applyFirstPaint(url) }
        }
        override fun onPageFinished(url: String) {
            update(sessionId) { it.copy(loading = false, progress = 100, pageUrl = url) }
            sessions.tryRegisterDeferredKeepAlive(sessionId, url, rendererGone)
        }
        override fun onPageError(url: String, errorCode: Int, description: String, insecureHttp: Boolean) {
            if (errorCode == WebEngineDefaults.ERROR_RENDERER_GONE) rendererGone = true
            update(sessionId) { it.applyPageError(errorCode) }
        }
        override fun onRenderProcessRecovered() {
            rendererGone = false
            update(sessionId) { it.applyRendererRecovered() }
        }
        override fun onRenderProcessRecoveryFailed() {
            rendererGone = true
            update(sessionId) { it.applyRendererRecoveryFailed() }
        }
        override fun onNewWindow(request: NewWindowRequest) {
            if (request.targetSessionId != request.sourceSessionId) {
                adoptWindow?.invoke(request.targetSessionId, request.initialUrl)
            }
        }
    }

    fun goBack(): Boolean = (_state.value as? SiteShellState.Ready)?.config?.sessionId
        ?.let(sessions::goBack) == true

    fun reload() { (_state.value as? SiteShellState.Ready)?.config?.sessionId?.let(sessions::reload) }
    fun stopLoading() {
        (_state.value as? SiteShellState.Ready)?.config?.sessionId?.let(sessions::stopLoading)
    }
    fun goForward(): Boolean = (_state.value as? SiteShellState.Ready)?.config?.sessionId
        ?.let(sessions::goForward) == true
    fun setDesktopMode(enabled: Boolean) {
        val ready = _state.value as? SiteShellState.Ready ?: return
        val sessionId = ready.config.sessionId ?: return
        sessions.setDesktopMode(sessionId, enabled)
        _state.value = ready.copy(
            config = ready.config.copy(
                desktopMode = enabled,
                forceEnableZoom = resolveForceEnableZoom(
                    desktopMode = enabled,
                    localApp = ready.config.localAppId != null,
                    userEnabled = forceEnableZoomEnabled.value ?: false,
                ),
            ),
        )
        val appId = ready.request.second
        if (!appId.isNullOrBlank() &&
            !appId.startsWith("direct-") &&
            !appId.startsWith("browser-")
        ) {
            viewModelScope.launch { lookup.updateDesktopMode(appId, enabled) }
        }
    }
    fun setOrbPlacement(x: Float, y: Float, parked: Boolean) = viewModelScope.launch {
        settingsRepository.setSiteShellOrbPlacement(x, y, parked)
    }

    fun hideOrb() = viewModelScope.launch {
        settingsRepository.setSiteShellOrbEnabled(false)
    }

    fun toggleBookmark(): Boolean {
        val ready = _state.value as? SiteShellState.Ready ?: return false
        val url = ready.pageUrl
        if (url.isBlank() || url == "about:blank") return false
        viewModelScope.launch {
            savedPages.toggleBookmark(
                url,
                ready.pageTitle.ifBlank { url },
                metadataFetcher.displayFallbackIconUrl(url),
            )
        }
        return true
    }

    private fun isLocalMarkdown(app: WebAppEntity): Boolean =
        app.isLocal && LocalAppUrls.isMarkdown(app.url)

    private fun readLocalMarkdown(app: WebAppEntity): String? {
        val uri = runCatching { URI(app.url.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals(LocalWebHost.LOCAL_SCHEME, ignoreCase = true)) return null
        if (uri.host != app.id) return null
        val relative = uri.path.orEmpty().trimStart('/')
        if (relative.isBlank() || relative.contains("..") || '/' in relative || '\\' in relative) return null
        val file = File(File(File(context.filesDir, LocalWebHost.LOCAL_APPS_DIR), app.id), relative)
        if (!file.isFile || file.length() > 20L * 1024 * 1024) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }
}

internal fun SiteShellState.Ready.applyFirstPaint(url: String): SiteShellState.Ready {
    if (url.equals("about:blank", ignoreCase = true)) return this
    return copy(
        progress = progress.coerceAtLeast(90),
        pageUrl = url.ifBlank { pageUrl },
    )
}

internal fun SiteShellState.Ready.applyPageStarted(url: String): SiteShellState.Ready = copy(
    loading = !url.equals("about:blank", ignoreCase = true),
    progress = 0,
    pageUrl = url,
    loadError = if (url.equals("about:blank", ignoreCase = true)) loadError else null,
)

internal fun SiteShellState.Ready.applyPageError(errorCode: Int): SiteShellState.Ready = copy(
    loading = false,
    loadError = if (errorCode == WebEngineDefaults.ERROR_RENDERER_GONE) {
        SiteShellLoadError.RENDERER_GONE
    } else {
        loadError
    },
)

internal fun SiteShellState.Ready.applyRendererRecovered(): SiteShellState.Ready =
    copy(loadError = null)

internal fun SiteShellState.Ready.applyRendererRecoveryFailed(): SiteShellState.Ready =
    copy(loading = false, loadError = SiteShellLoadError.RENDERER_GONE)
