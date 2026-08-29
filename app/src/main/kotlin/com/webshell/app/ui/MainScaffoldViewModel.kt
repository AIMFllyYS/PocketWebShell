package com.webshell.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.app.shell.ShellSessionController
import com.webshell.core.data.WebAppDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** MainScaffold 的装配层：主页图标 → 会话控制器（保活登记 + 沉浸式打开）。 */
@HiltViewModel
class MainScaffoldViewModel @Inject constructor(
    private val sessionController: ShellSessionController,
    private val webAppDao: WebAppDao,
) : ViewModel() {

    var launchedUrl: String? = null
        private set

    /** 主页点击图标：按实体打开会话（保活登记由控制器完成） */
    fun launchApp(appId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val app = webAppDao.getById(appId) ?: return@launch
            sessionController.openSession(app)
            launchedUrl = app.url
            onReady(app.url)
        }
    }

    /** 冷启动带 url 的外部唤起：若 url 对应某个已创建应用且开了保活，登记会话 */
    fun registerKeepAliveFor(url: String) {
        viewModelScope.launch {
            val apps = webAppDao.observeAll().first()
            val match = apps.firstOrNull { it.url == url } ?: return@launch
            if (match.keepAlive) sessionController.openSession(match)
        }
    }

    fun setKeepAliveServiceEnabled(enabled: Boolean) {
        sessionController.setServiceEnabled(enabled)
    }
}
