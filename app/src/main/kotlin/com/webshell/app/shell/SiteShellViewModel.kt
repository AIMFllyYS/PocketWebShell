package com.webshell.app.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.BrowserSavedPagesRepository
import com.webshell.core.data.SettingsRepository
import com.webshell.core.data.WebAppLookupRepository
import com.webshell.core.data.metadata.SiteMetadataFetcher
import com.webshell.core.webengine.NewWindowRequest
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.ShellListener
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    ) : SiteShellState
}

data class SiteShellOrbUi(
    val enabled: Boolean = true,
    val x: Float = -1f,
    val y: Float = -1f,
    val parked: Boolean = false,
)

/** One launch resolver; the screen never reads a DAO or manipulates a native WebView. */
@HiltViewModel
class SiteShellViewModel @Inject constructor(
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
        if (!force && request == latestRequest && _state.value is SiteShellState.Ready) return
        latestRequest = request
        openJob?.cancel()
        _state.value = SiteShellState.Loading
        openJob = viewModelScope.launch {
            try {
                val app = if (appId != null) lookup.getById(appId) else lookup.findByUrl(initialUrl)
                if (appId != null && app == null) {
                    _state.value = SiteShellState.Unavailable
                    return@launch
                }
                val config = if (app != null) sessions.openSession(app) else sessions.openDirectSession(initialUrl)
                _state.value = SiteShellState.Ready(
                    config,
                    request,
                    pageUrl = config.startUrl,
                    pageTitle = app?.title.orEmpty(),
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
            update(sessionId) { it.copy(loading = true, progress = 0, pageUrl = url) }
        }
        override fun onTitleReceived(title: String) {
            update(sessionId) { it.copy(pageTitle = title) }
        }
        override fun onProgress(progress: Int) {
            update(sessionId) { it.copy(progress = progress, loading = progress < 100) }
        }
        override fun onPageFinished(url: String) {
            update(sessionId) { it.copy(loading = false, progress = 100, pageUrl = url) }
        }
        override fun onPageError(url: String, errorCode: Int, description: String, insecureHttp: Boolean) {
            update(sessionId) { it.copy(loading = false) }
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
        _state.value = ready.copy(config = ready.config.copy(desktopMode = enabled))
    }
    fun setOrbPlacement(x: Float, y: Float, parked: Boolean) = viewModelScope.launch {
        settingsRepository.setSiteShellOrbPlacement(x, y, parked)
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
    fun openWindow(url: String) {
        (_state.value as? SiteShellState.Ready)?.config?.let { sessions.openWindow(it, url) }
    }
}
