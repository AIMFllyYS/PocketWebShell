package com.webshell.app.shell

import android.content.Context
import android.content.Intent
import android.os.Build
import com.webshell.app.service.WebHostService
import com.webshell.core.data.SITE_SHELL_NEW_WINDOW_ADOPT
import com.webshell.core.data.SITE_SHELL_NEW_WINDOW_REPLACE
import com.webshell.core.data.SettingsRepository
import com.webshell.core.data.WebAppEntity
import com.webshell.core.data.normalizeSiteShellNewWindowPolicy
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.KeepAliveRegistry
import com.webshell.core.webengine.LocalWebHost
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.resolveForceEnableZoom
import com.webshell.core.webengine.WebViewPool
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Single-site session owner: saved-site config, native operations and optional foreground service.
 * Browser tabs have their own owner. Neither foreground services nor this pool promise uninterrupted
 * execution; Android memory, power policy and the website remain authoritative.
 */
@Singleton
class ShellSessionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val deferredLocalKeepAlive = LinkedHashMap<String, Pair<String, String>>()

    fun configFor(
        app: WebAppEntity,
        pullToRefresh: Boolean = false,
        forceEnableZoomUser: Boolean = false,
        globalNewWindowPolicy: String = SITE_SHELL_NEW_WINDOW_ADOPT,
    ): ShellConfig =
        requireNotNull(configuredSiteShell(app, pullToRefresh, forceEnableZoomUser, globalNewWindowPolicy)) {
            "Invalid saved-site launch configuration"
        }

    /** Resolve/create only; first navigation starts after the host installs its owned listeners. */
    suspend fun openSession(app: WebAppEntity): ShellConfig {
        val settings = settingsRepository.settings.first()
        val config = configFor(
            app,
            settings.pullToRefreshEnabled,
            settings.forceEnableZoomEnabled,
            settings.siteShellNewWindowPolicy,
        )
        createSession(config)
        if (app.keepAlive && settings.keepAliveServiceEnabled) {
            if (config.localAppId != null) {
                // Do not start FGS on the same beat as the first local WebGL peak.
                deferredLocalKeepAlive[app.id] = app.title to app.url
            } else {
                KeepAliveRegistry.register(app.id, app.title, app.url)
                runCatching { ensureServiceRunning() }.onFailure {
                    KeepAliveRegistry.unregister(app.id)
                    AppLog.error("session", "Foreground service unavailable; foreground browsing remains available")
                }
            }
        } else {
            deferredLocalKeepAlive.remove(app.id)
            KeepAliveRegistry.unregister(app.id)
            if (KeepAliveRegistry.entries.isEmpty()) stopService()
        }
        return config
    }

    suspend fun openDirectSession(url: String): ShellConfig {
        val validated = requireNotNull(validatedExternalSiteUrl(url)) { "Invalid direct-site URL" }
        val settings = settingsRepository.settings.first()
        val config = ShellConfig(
            sessionId = "direct-${UUID.nameUUIDFromBytes(validated.toByteArray(Charsets.UTF_8))}",
            startUrl = validated,
            // A direct link is still this same single-user browsing session;
            // it must honor the user's pull-to-refresh preference exactly
            // like a saved-site or browser-tab session does.
            pullToRefresh = settings.pullToRefreshEnabled,
            forceEnableZoom = resolveForceEnableZoom(
                desktopMode = false,
                localApp = false,
                userEnabled = settings.forceEnableZoomEnabled,
            ),
            externalLinkPolicy = ShellConfig.ExternalLinkPolicy.OPEN_IN_SAME,
            newWindowPolicy = resolveNewWindowPolicy(null, settings.siteShellNewWindowPolicy),
        )
        createSession(config)
        return config
    }

    private fun createSession(config: ShellConfig) {
        val id = requireNotNull(config.sessionId)
        WebViewPool.activeSessionId = id
        WebViewPool.getOrCreate(context, id) { config }
    }

    fun ensureLoaded(config: ShellConfig): Boolean {
        val id = config.sessionId ?: return false
        val shell = WebViewPool.get(id) ?: return false
        val current = shell.currentUrl()
        val needsFirstNavigation = current.isNullOrBlank() || current.equals("about:blank", ignoreCase = true)
        // Only clear siblings before the first real navigation of a heavy local
        // document. Re-entering an already-rendered local page must not thrash
        // the browser tab pool on every host attach.
        if (config.localAppId != null && needsFirstNavigation) {
            WebViewPool.evictUnprotectedExcept(id)
        }
        if (shell.isAwaitingExplicitRendererRetry()) return shell.canGoBack()
        if (needsFirstNavigation) {
            shell.loadWithStateRestore(config.startUrl)
        } else {
            tryRegisterDeferredKeepAlive(id, current, rendererGone = false)
        }
        return shell.canGoBack()
    }

    fun tryRegisterDeferredKeepAlive(sessionId: String, pageUrl: String, rendererGone: Boolean) {
        if (rendererGone || pageUrl.isBlank() || pageUrl.equals("about:blank", ignoreCase = true)) return
        val pending = deferredLocalKeepAlive.remove(sessionId) ?: return
        KeepAliveRegistry.register(sessionId, pending.first, pending.second)
        runCatching { ensureServiceRunning() }.onFailure {
            KeepAliveRegistry.unregister(sessionId)
            AppLog.error("session", "Foreground service unavailable; foreground browsing remains available")
        }
    }

    fun canGoBack(sessionId: String): Boolean = WebViewPool.get(sessionId)?.canGoBack() == true
    fun canGoForward(sessionId: String): Boolean = WebViewPool.get(sessionId)?.canGoForward() == true
    fun goBack(sessionId: String): Boolean = WebViewPool.get(sessionId)?.goBack() == true
    fun goForward(sessionId: String): Boolean = WebViewPool.get(sessionId)?.goForward() == true
    fun reload(sessionId: String) { WebViewPool.get(sessionId)?.reload() }
    fun stopLoading(sessionId: String) { WebViewPool.get(sessionId)?.stopLoading() }
    fun setDesktopMode(sessionId: String, enabled: Boolean) {
        WebViewPool.get(sessionId)?.setDesktopMode(enabled)
    }

    fun closeSession(sessionId: String) {
        deferredLocalKeepAlive.remove(sessionId)
        KeepAliveRegistry.unregister(sessionId)
        WebViewPool.suspendSession(sessionId)
        if (KeepAliveRegistry.entries.isEmpty()) stopService()
    }

    fun ensureServiceRunning() {
        val intent = Intent(context, WebHostService::class.java).setAction(KeepAliveRegistry.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
    }

    fun stopService() {
        // Do not start a background service just to ask it to stop.
        context.stopService(Intent(context, WebHostService::class.java))
    }

    fun setServiceEnabled(enabled: Boolean) {
        if (enabled && KeepAliveRegistry.entries.isNotEmpty()) runCatching { ensureServiceRunning() }
            .onFailure { AppLog.error("session", "Foreground service start rejected by platform") }
        else if (!enabled) stopService()
    }
}

/** Pure launch mapping, including the legacy stored webpage zoom (independent of app font scale). */
internal fun resolveNewWindowPolicy(
    perApp: String?,
    global: String,
): ShellConfig.NewWindowPolicy =
    if (normalizeSiteShellNewWindowPolicy(perApp ?: global) == SITE_SHELL_NEW_WINDOW_REPLACE) {
        ShellConfig.NewWindowPolicy.REPLACE_IN_SHELL
    } else {
        ShellConfig.NewWindowPolicy.ADOPT_IN_BROWSER
    }

internal fun configuredSiteShell(
    app: WebAppEntity,
    pullToRefresh: Boolean = false,
    forceEnableZoomUser: Boolean = false,
    globalNewWindowPolicy: String = SITE_SHELL_NEW_WINDOW_ADOPT,
): ShellConfig? {
    val uri = runCatching { URI(app.url.trim()) }.getOrNull() ?: return null
    val local = uri.scheme.equals(LocalWebHost.LOCAL_SCHEME, ignoreCase = true)
    val renderUrl = if (local) {
        if (uri.host != app.id || uri.userInfo != null || uri.port != -1 || !safeLocalPath(uri.path)) return null
        LocalWebHost.toHttpsUrl(uri.toASCIIString())
    } else validatedExternalSiteUrl(app.url) ?: return null
    return ShellConfig(
        // This release is intentionally single-user: saved-site launches share the
        // browser's default WebView profile so a login made in one tab/entry is
        // available everywhere, just like a normal browser.
        sessionId = app.id, profileId = null, startUrl = renderUrl,
        localAppId = app.id.takeIf { local },
        desktopMode = app.desktopMode, algorithmicDark = app.darkMode,
        textZoomPercent = app.textZoomPercent, thirdPartyCookies = true, pullToRefresh = pullToRefresh,
        forceEnableZoom = resolveForceEnableZoom(
            desktopMode = app.desktopMode,
            localApp = local,
            userEnabled = forceEnableZoomUser,
        ),
        // Only the explicit per-site switch controls where an off-site link
        // opens. The home-screen star (isFavorite) is presentation only and
        // must never change navigation/session behavior — this app is a
        // single-user shell, not a per-site sandbox.
        externalLinkPolicy = if (app.externalLinksToBrowser) {
            ShellConfig.ExternalLinkPolicy.OPEN_IN_BROWSER
        } else ShellConfig.ExternalLinkPolicy.OPEN_IN_SAME,
        newWindowPolicy = resolveNewWindowPolicy(app.siteShellNewWindowPolicy, globalNewWindowPolicy),
    )
}

/** No file/content/javascript/data/intent schemes, credentials, control characters or private import URLs. */
internal fun validatedExternalSiteUrl(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty() || value.any { it.isISOControl() || it.isWhitespace() }) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null ||
        uri.port !in -1..65535) return null
    if (uri.host.equals(LocalWebHost.HOST, ignoreCase = true) && uri.path.orEmpty().startsWith(LocalWebHost.LOCAL_PREFIX)) return null
    return uri.toASCIIString()
}

private fun safeLocalPath(path: String?): Boolean =
    !path.isNullOrBlank() && path.startsWith('/') && '\\' !in path && path.none { it.isISOControl() } &&
        path.split('/').none { it == "." || it == ".." }

