package com.webshell.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.app.shell.ShellSessionController
import com.webshell.core.data.WebAppLookupRepository
import com.webshell.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** MainScaffold 的装配层：主页图标 → 会话控制器（保活登记 + 沉浸式打开）。 */
@HiltViewModel
class MainScaffoldViewModel @Inject constructor(
    private val sessionController: ShellSessionController,
    private val webApps: WebAppLookupRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val browserPreferences = settingsRepository.settings
        .map { BrowserHostPreferences(it.browserAutoCollapse, it.browserOrbX, it.browserOrbY, it.pullToRefreshEnabled) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowserHostPreferences())

    fun setBrowserOrbPosition(x: Float, y: Float) {
        viewModelScope.launch { settingsRepository.setBrowserOrbPosition(x, y) }
    }

    /** Keep the saved identity: the site shell must display this app's configured session. */
    fun launchApp(appId: String, onReady: (String, String) -> Unit) {
        viewModelScope.launch {
            val app = webApps.getById(appId) ?: return@launch
            onReady(app.url, app.id)
        }
    }

    fun setKeepAliveServiceEnabled(enabled: Boolean) {
        sessionController.setServiceEnabled(enabled)
    }

    /**
     * "结束后台会话" is a lifecycle action (stop this site's renderer/keep-alive,
     * stop the foreground service if nothing else needs it), never a login
     * action. It must never touch shared cookies/site storage — that is
     * exclusively the "清除全部网站数据" flow in Storage settings.
     */
    fun closeSessions(sessionIds: List<String>) {
        sessionIds.forEach(sessionController::closeSession)
    }
}

data class BrowserHostPreferences(
    val autoCollapse: Boolean = true,
    val orbX: Float = -1f,
    val orbY: Float = -1f,
    val pullToRefresh: Boolean = false,
)
