package com.webshell.core.webengine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Refresh resources without deleting shared login/website storage or claiming a DNS flush. */
@Singleton
class WebRefreshCoordinator @Inject constructor(@ApplicationContext private val context: Context) {
    private val lock = Mutex()
    private val pending by lazy { context.getSharedPreferences("pending_web_refresh", Context.MODE_PRIVATE) }

    suspend fun refresh(appId: String): Boolean? = lock.withLock {
        withContext(Dispatchers.Main.immediate) {
            WebViewPool.discardSavedState(appId)
            val shell = WebViewPool.get(appId)
            if (shell == null) {
                pending.edit().putBoolean(appId, true).apply()
                null // Persisted request is consumed on the next actual launch.
            } else shell.forceRefresh()
        }
    }

    fun prepareLaunch(appId: String, shell: ShellWebView) {
        if (pending.getBoolean(appId, false)) {
            pending.edit().remove(appId).apply()
            shell.bypassNextNavigation()
        }
    }
}
