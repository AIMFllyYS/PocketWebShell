package com.webshell.core.webengine

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.CookieManager
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.webshell.core.model.AppLog
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * "原生感"网页壳引擎：零浏览器 UI、下拉刷新容器、SPA 返回、外链路由、
 * 下载/上传/权限、共享默认 Profile、渲染进程自愈。
 * 设计约束：必须在主线程创建与使用（WebView 要求）。
 * 对外暴露 [view]（装入布局）与 [webView]（WebView 能力）。
 */
@SuppressLint("SetJavaScriptEnabled")
class ShellWebView internal constructor(
    context: Context,
    @Volatile var config: ShellConfig,
) : SwipeRefreshWebView(context) {

    val sessionId: String = config.sessionId ?: "anon-${System.nanoTime()}"

    var listener: ShellListener? = null

    /** Identity of the current Compose host; an outgoing host cannot detach its successor. */
    internal var uiHostOwner: Any? = null

    /**
     * 持久监听者：随会话存活，不随 Compose 组合摘除（ShellWebViewHost 出组合只清 listener）。
     * 由 ViewModel 层注册/注销，承载按会话归属的状态写回（标题/URL/进度/返回栈）。
     */
    var sessionListener: ShellListener? = null

    /** 事件分发：先持久 sessionListener，后临时 UI listener */
    internal fun notifyListeners(block: ShellListener.() -> Unit) {
        sessionListener?.block()
        listener?.block()
    }

    internal val assetLoader: WebViewAssetLoaderHolder =
        WebViewAssetLoaderHolder(context, config.localAppId)

    private var savedStateBundle: Bundle? = null

    internal var pendingRecoveryUrl: String? = null
    internal val mainHandler = Handler(Looper.getMainLooper())
    internal var pendingSsl: SslErrorHandler? = null
    private var pendingCleartextCancel: (() -> Unit)? = null
    /** Safe-default resolver for a JS dialog awaiting a visible listener; see [deliverJsResult]. */
    private var pendingJsDialogFallback: (() -> Unit)? = null
    internal var recoveryInProgress = false
    internal var rendererRecoveryFailed = false
    /** True after a crash replacement that has not yet been asked to load again. */
    private var rendererReplacedSinceCrash = false
    internal val rendererRecoveryGate = RendererRecoveryGate()
    /**
     * Last main-document URL observed on the UI thread. [shouldInterceptRequest]
     * runs off-thread and must not read [WebView.getUrl].
     */
    @Volatile
    internal var lastCommittedUrl: String? = null
    /** Monotonically invalidates callbacks belonging to a replaced WebView. */
    internal var callbackGeneration = 0L
    internal var customView: View? = null
    private var documentStartScript: ScriptHandler? = null
    private var injectedBootstrapKey: Triple<Boolean, Boolean, Boolean>? = null
    /** setDesktopMode already flipped the flag; reconfigure must still reload once. */
    private var pendingDesktopReload = false
    /**
     * A desktop/mobile switch reloads with the HTTP cache bypassed so the server
     * re-evaluates the new UA; the default cache mode is restored on the next
     * page start (or when an explicit navigation supersedes the reload).
     */
    private var cacheBypassReloadPending = false
    internal var forcedReload: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    suspend fun forceRefresh(): Boolean {
        val completion = kotlinx.coroutines.CompletableDeferred<Boolean>()
        forcedReload?.complete(false)
        forcedReload = completion
        return try {
            reloadBypassingCache()
            kotlinx.coroutines.withTimeoutOrNull(20_000) { completion.await() } ?: false
        } finally {
            if (forcedReload === completion) { forcedReload = null; restoreDefaultCacheModeIfNeeded() }
        }
    }

    fun bypassNextNavigation() {
        cacheBypassReloadPending = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        val generation = documentRevision
        mainHandler.postDelayed({ if (documentRevision <= generation + 1) restoreDefaultCacheModeIfNeeded() }, 25_000)
    }
    /** DOCUMENT_START_SCRIPT support is process-constant; cache it for the fallback path. */
    internal val documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    /** Log the legacy-WebView fallback injection at most once per session. */
    internal var desktopFallbackLogged = false
    /** UA-CH 不受支持只记一次（老/定制 WebView 上身份链只有 UA 字符串）。 */
    private var uaChUnsupportedLogged = false
    /** REPLACE_IN_SHELL just handed Chromium this same WebView; drop the opener history. */
    internal var pendingClearHistory = false

    internal val blobs = ShellBlobTransfer(this)

    init {
        // Profile 必须先于任何 settings 触碰完成切换，失败仅降级回默认共享 Profile。
        applyProfile()
        configureBaseSettings()
        applyChromeClients()
        applyPullToRefresh()
        applyFindListener()
        injectBootstrapOnce()
    }

    // ---------------------------------------------------------------- settings

    private fun configureBaseSettings() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true) // target=_blank 走 onCreateWindow
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = !config.autoplayMedia
            setAllowFileAccess(false)
            // No JavaScript bridge exposes content:// URIs to the page; file
            // upload/download go through the system picker and
            // DownloadManager respectively, not WebView-mediated content
            // access. Keep this surface closed rather than the platform
            // default (true).
            setAllowContentAccess(false)
            textZoom = config.textZoomPercent
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.overScrollMode = OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        setDesktopMode(config.desktopMode)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, config.algorithmicDark)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
            WebSettingsCompat.setBackForwardCacheEnabled(webView.settings, config.localAppId == null)
        }

        CookieManager.getInstance().setAcceptCookie(true)
        // 显式双向设置：true/false 都调用，避免跨实例状态泄漏（CookieManager 进程级共享）
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, config.thirdPartyCookies)
    }

    fun setDesktopMode(enabled: Boolean) {
        val changed = config.desktopMode != enabled
        config = config.copy(desktopMode = enabled)
        webView.settings.userAgentString =
            if (enabled) WebEngineDefaults.DESKTOP_USER_AGENT else WebEngineDefaults.MOBILE_USER_AGENT
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            // UA 字符串只覆盖 User-Agent 头；Sec-CH-UA-* 与 navigator.userAgentData
            // 来自 metadata。构造逻辑在 WebEngineDefaults.userAgentMetadata（纯 JVM、
            // 有单元测试）：brand 三字段缺一整套都构造不出来，绝不能在这里内联。
            runCatching {
                var metadata = WebEngineDefaults.userAgentMetadata(
                    desktop = enabled,
                    mobilePlatformVersion = Build.VERSION.RELEASE ?: "",
                    mobileModel = Build.MODEL ?: "",
                )
                if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA_FORM_FACTORS)) {
                    // Chrome 140+ 桌面模式声明 Sec-CH-UA-Form-Factors: "Desktop"；
                    // 读取该信号的现代站点缺了它会把我们识别回手机。
                    metadata = androidx.webkit.UserAgentMetadata.Builder(metadata)
                        .setFormFactors(
                            listOf(
                                if (enabled) androidx.webkit.UserAgentMetadata.FORM_FACTOR_DESKTOP
                                else androidx.webkit.UserAgentMetadata.FORM_FACTOR_MOBILE,
                            ),
                        )
                        .build()
                }
                WebSettingsCompat.setUserAgentMetadata(webView.settings, metadata)
            }.onSuccess {
                // 回读校验：构造成功不等于内核接受，把生效的身份写进日志（不记页面内容）。
                val applied = runCatching {
                    WebSettingsCompat.getUserAgentMetadata(webView.settings)
                }.getOrNull()
                AppLog.log(
                    "webengine",
                    "UA-CH 已设置 desktop=$enabled platform=${applied?.platform} " +
                        "mobile=${applied?.isMobile} brands=${applied?.brandVersionList?.size}",
                )
            }.onFailure { e ->
                // 历史上这里静默失败过三个版本；失败必须显眼。
                AppLog.error("webengine", "UA-CH metadata 设置失败 desktop=$enabled : ${e.message}")
            }
        } else if (!uaChUnsupportedLogged) {
            // 老内核/定制 WebView（如 HwWebview 114）没有 UA-CH：navigator.userAgentData
            // 不存在，身份链只能靠 UA 字符串。记一条日志，读探针 uad:"none" 时不用再猜。
            uaChUnsupportedLogged = true
            AppLog.log("webengine", "UA-CH 不受当前 WebView 支持，桌面/移动身份仅靠 UA 字符串")
        }
        applyDesktopScale()
        injectBootstrapOnce()
        if (changed) pendingDesktopReload = true
        AppLog.log("webengine", "桌面模式 sessionId=$sessionId enabled=$enabled changed=$changed")
    }

    /**
     * Match Chrome "Request Desktop Site": CSS layouts at 980px, then overview
     * shrinks that layout to the current view width so pinch-zoom still works.
     */
    private fun applyDesktopScale() {
        val desktopLayout = config.desktopMode && config.localAppId == null
        if (!desktopLayout) {
            webView.setInitialScale(0)
            return
        }
        val width = webView.width
        if (width > 0) {
            webView.setInitialScale(WebEngineDefaults.desktopInitialScalePercent(width))
            return
        }
        val observer = webView.viewTreeObserver
        if (!observer.isAlive) return
        observer.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val live = webView.viewTreeObserver
                if (live.isAlive) live.removeOnGlobalLayoutListener(this)
                if (config.desktopMode && config.localAppId == null && webView.width > 0) {
                    webView.setInitialScale(WebEngineDefaults.desktopInitialScalePercent(webView.width))
                }
            }
        })
    }

    /**
     * Re-apply persisted site settings to an already pooled WebView.
     * [ShellWebViewHost]'s `SideEffect` calls this on *every* recomposition —
     * including ones driven purely by a progress/title callback that never
     * touches settings — so a structural no-op must be a cheap, side-effect-free
     * early return rather than re-running [configureBaseSettings] (which
     * touches `WebSettings`/`CookieManager`) and [applyPullToRefresh] every frame.
     */
    fun reconfigure(newConfig: ShellConfig) {
        if (newConfig.sessionId != null && newConfig.sessionId != sessionId) return
        val old = config
        val merged = old.mergedWith(newConfig)
        if (merged == old && !pendingDesktopReload) return
        config = merged
        val desktopChanged = pendingDesktopReload || old.desktopMode != config.desktopMode
        val needsReload = desktopChanged ||
            old.textZoomPercent != config.textZoomPercent ||
            old.algorithmicDark != config.algorithmicDark ||
            old.thirdPartyCookies != config.thirdPartyCookies ||
            old.autoplayMedia != config.autoplayMedia ||
            old.forceEnableZoom != config.forceEnableZoom
        pendingDesktopReload = false
        configureBaseSettings()
        applyPullToRefresh()
        injectBootstrapOnce()
        if (needsReload) {
            val url = webView.url
            val canReload = !url.isNullOrBlank() && url != "about:blank"
            AppLog.log(
                "webengine",
                "reconfigure 刷新 sessionId=$sessionId desktopChanged=$desktopChanged bypassCache=$desktopChanged reload=$canReload",
            )
            // 桌面切换必须让服务器看到新 UA：绕过 HTTP 缓存重新请求主文档。
            if (canReload) {
                if (desktopChanged) reloadBypassingCache() else reload()
            }
        }
    }

    private fun applyProfile() {
        // profileId 为 null 时使用 WebView 默认共享 Profile（不做任何切换）
        val profileId = config.profileId ?: return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        runCatching {
            androidx.webkit.ProfileStore.getInstance().getOrCreateProfile(profileId)
            WebViewCompat.setProfile(webView, profileId)
        }.onSuccess {
            AppLog.log("webengine", "已应用独立 Profile profileId=$profileId")
        }.onFailure { e ->
            AppLog.warn("webengine", "设置独立 Profile 失败 profileId=$profileId : ${e.message}")
        }
    }

    private fun injectBootstrapOnce() {
        if (!documentStartSupported) return
        val enableZoom = config.forceEnableZoom
        val desktop = config.desktopMode
        val local = config.localAppId != null
        val key = Triple(enableZoom, desktop, local)
        if (documentStartScript != null && injectedBootstrapKey == key) return
        runCatching { documentStartScript?.remove() }
        documentStartScript = WebViewCompat.addDocumentStartJavaScript(
            webView,
            WebEngineDefaults.documentStartBootstrap(enableZoom, local, desktop),
            setOf("*"),
        )
        injectedBootstrapKey = key
    }

    // ---------------------------------------------------------------- clients

    private fun applyChromeClients() {
        val generation = ++callbackGeneration
        webView.webViewClient = ShellWebViewClient(this, generation)
        webView.webChromeClient = ShellChromeClient(this, generation)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (generation == callbackGeneration) {
                handleDownload(url, userAgent, contentDisposition, mimeType)
            }
        }
    }

    private fun applyPullToRefresh() {
        setPullToRefreshEnabled(config.pullToRefresh && !config.desktopMode)
        onUserRefresh = { reload() }
    }

    /**
     * A JS dialog fired while nobody is watching (a background browser tab,
     * or a host mid tab-switch that has not attached [listener] yet). Give it
     * a short grace window — re-checking [listener] once — before applying
     * [fallback]. This is a real decision on the engine's behalf, made
     * explicit here, not an accidental fall-through to a listener interface's
     * default method.
     */
    internal fun armBackgroundDialogFallback(fallback: () -> Unit, deliverToLate: (ShellListener) -> Unit) {
        pendingJsDialogFallback?.invoke()
        val resolve = {
            pendingJsDialogFallback = null
            val late = listener
            if (late != null) deliverToLate(late) else fallback()
        }
        pendingJsDialogFallback = fallback
        mainHandler.postDelayed(resolve, ShellWebViewLimits.JS_DIALOG_BACKGROUND_TIMEOUT_MS)
    }

    // ---------------------------------------------------------------- routing

    /**
     * 日志只记录 host。没有 host 时只记录短的安全 scheme，不保留 URL 其余部分。
     * data: / blob: 载荷不能进入日志。
     */
    internal fun logHost(url: String?): String = ShellNavigation.logHost(url)

    /** @return true 表示本引擎已处理（不交给 WebView 加载） */
    internal fun routeUrl(url: String, isMainFrame: Boolean = true): Boolean {
        if (LocalWebHost.isLocalUrl(url) &&
            !LocalWebHost.isAllowedLocalUrl(url, config.localAppId, isMainFrame, lastCommittedUrl)
        ) {
            // A foreign /local/<appId>/ navigation must not fall through to
            // Chromium's network stack. The same check is applied to
            // subresources in shouldInterceptRequest above.
            notifyListeners {
                onPageError(url, ShellWebViewLimits.ERROR_LOCAL_RESOURCE_FORBIDDEN, "本地应用资源不属于当前会话", false)
            }
            return true
        }
        val decision = UrlRouter.classify(url)
        val uri = runCatching { url.toUri() }.getOrNull()
        return when (decision.route) {
            UrlRoute.WEB -> {
                if (isMainFrame && interceptFileDownload(url)) {
                    true
                } else if (config.externalLinkPolicy == ShellConfig.ExternalLinkPolicy.OPEN_IN_BROWSER &&
                    !LocalWebHost.isLocalUrl(url) && isForeignHost(url)
                ) {
                    launchExternal(url)
                    true
                } else if (isMainFrame && maybePromptCleartext(url) { webView.loadUrl(url) }) {
                    true
                } else false
            }
            UrlRoute.ABOUT_BLANK -> false
            // Main-frame data: and blob: documents are not loaded. Subresource
            // data: and blob: (images, media) still return false so WebView can
            // fetch them. Blob downloads go through ShellBlobTransfer, not here.
            UrlRoute.DATA, UrlRoute.BLOB -> {
                if (!ShellNavigation.consumesOpaqueMainFrame(decision.route, isMainFrame)) {
                    false
                } else {
                    val label = logHost(url)
                    AppLog.log("web", "${ShellNavigation.OPAQUE_MAIN_FRAME_DESCRIPTION} scheme=$label")
                    notifyListeners {
                        onPageError(
                            label,
                            ShellNavigation.ERROR_OPAQUE_MAIN_FRAME,
                            ShellNavigation.OPAQUE_MAIN_FRAME_DESCRIPTION,
                            false,
                        )
                    }
                    true
                }
            }
            UrlRoute.JAVASCRIPT -> true
            UrlRoute.EXTERNAL_INTENT -> if (uri?.scheme.equals("intent", ignoreCase = true)) {
                launchIntentUri(url)
            } else { launchExternal(url); true }
            UrlRoute.BLOCKED, UrlRoute.UNKNOWN -> {
                notifyListeners { onExternalLaunchFailed(url) }
                true
            }
        }
    }

    private fun isForeignHost(url: String): Boolean {
        val current = this.webView.url?.toUri()?.host ?: return false
        val target = url.toUri().host ?: return false
        return current != target
    }

    private fun launchExternal(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            check(intent.resolveActivity(context.packageManager) != null) { "No handler" }
            dispatchActivity(intent)
        }.onFailure { notifyListeners { onExternalLaunchFailed(url) } }
    }

    private fun launchIntentUri(url: String): Boolean {
        runCatching {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            // Do not allow an embedded page to select an arbitrary explicit
            // component or package. Only dispatch intents that the system can
            // resolve through the normal chooser/handler path.
            check(intent.component == null && intent.selector == null && intent.`package` == null) {
                "Explicit target blocked"
            }
            // URI grants and clip data are not part of a normal deep link.
            // String extras stay. A nested Intent extra is refused the same
            // way as an explicit component, and extras are not cleared wholesale.
            intent.removeFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            intent.clipData = null
            check(!extrasContainNestedIntent(intent)) { "Nested intent blocked" }
            check(intent.resolveActivity(context.packageManager) != null) { "No handler" }
            dispatchActivity(intent)
        }.onFailure { notifyListeners { onExternalLaunchFailed(url) } }
        return true
    }

    /** True when an extra, or an extra nested inside a Bundle or collection, is an [Intent]. */
    private fun extrasContainNestedIntent(intent: Intent): Boolean =
        try {
            containsNestedIntent(intent.extras)
        } catch (_: Exception) {
            true
        }

    private fun containsNestedIntent(
        value: Any?,
        depth: Int = 0,
        seen: MutableSet<Int> = mutableSetOf(),
    ): Boolean {
        if (value == null) return false
        if (value is Intent) return true
        if (depth >= 8) {
            return value is Bundle || value is Array<*> || value is Iterable<*> || value is Map<*, *>
        }
        return when (value) {
            is Bundle -> {
                val id = System.identityHashCode(value)
                if (!seen.add(id)) return true
                try {
                    for (key in value.keySet()) {
                        val child = try {
                            @Suppress("DEPRECATION") // Typed getters cannot see an unknown nested Intent.
                            value.get(key)
                        } catch (_: Exception) {
                            return true
                        }
                        if (containsNestedIntent(child, depth + 1, seen)) return true
                    }
                    false
                } finally {
                    seen.remove(id)
                }
            }
            is Array<*> -> value.any { containsNestedIntent(it, depth + 1, seen) }
            is Iterable<*> -> value.any { containsNestedIntent(it, depth + 1, seen) }
            is Map<*, *> -> value.values.any { containsNestedIntent(it, depth + 1, seen) }
            else -> false
        }
    }

    /** WebViewPool deliberately stores application-context views; add the
     * required task flag only for that context and preserve Activity semantics
     * for hosts that pass an Activity context directly. */
    private fun dispatchActivity(intent: Intent) {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    internal fun hideCustomView() {
        if (customView != null) {
            customView = null
            notifyListeners { onHideCustomView() }
        }
    }

    internal fun recoverRenderer(url: String?, userInitiated: Boolean = false) {
        if (recoveryInProgress) return
        recoveryInProgress = true
        rendererRecoveryFailed = false
        // The crashed renderer's JsResult (if any) is no longer resolvable
        // against a live web contents; resolve it with the safe default now
        // instead of leaving it dangling until the grace timer fires.
        pendingJsDialogFallback?.let { runCatching { it() } }
        pendingJsDialogFallback = null
        blobs.invalidate("cancelled")
        val restoreUrl = RendererRecoveryPolicy.retryLoadUrl(url, pendingRecoveryUrl, config.startUrl)
        pendingRecoveryUrl = restoreUrl.takeIf { it.isNotBlank() }
        notifyListeners { onPageError(url.orEmpty(), ShellWebViewLimits.ERROR_RENDERER_GONE, "网页渲染进程已重启", false) }
        val state = Bundle()
        val saved = if (userInitiated || RendererRecoveryPolicy.shouldAutoReloadDocument(config.localAppId)) {
            runCatching { webView.saveState(state); state }.getOrNull()
        } else null
        runCatching { hideCustomView() }
        val replacement = runCatching { replaceWebView() }.getOrNull()
        if (replacement == null) {
            failRendererRecovery(restoreUrl)
            return
        }
        lastCommittedUrl = null
        rendererReplacedSinceCrash = true
        val configured = runCatching {
            applyProfile()
            configureBaseSettings()
            applyChromeClients()
            applyPullToRefresh()
            applyFindListener()
            documentStartScript = null
            injectedBootstrapKey = null
            injectBootstrapOnce()
        }.isSuccess
        if (!configured) {
            failRendererRecovery(restoreUrl)
            return
        }
        if (!userInitiated && !RendererRecoveryPolicy.shouldAutoReloadDocument(config.localAppId)) {
            // Local imports often crash again on the same document. Leave the
            // replacement on about:blank until the user explicitly retries.
            failRendererRecovery(restoreUrl)
            return
        }
        val hasSavedState = saved != null && saved.size() > 0
        val restored = if (!userInitiated && hasSavedState) {
            runCatching { webView.restoreState(saved); true }.getOrDefault(false)
        } else false
        // A saved back/forward list will navigate itself; only fall back to a
        // direct URL when Chromium could not serialize the old state.
        if (!restored && (webView.url.isNullOrBlank() || webView.url == "about:blank")) {
            val loaded = restoreUrl.takeIf { it.isNotBlank() }
                ?.let { runCatching { webView.loadUrl(it) }.isSuccess } ?: true
            if (!loaded) {
                failRendererRecovery(restoreUrl)
                return
            }
        }
        pendingRecoveryUrl = null
        rendererReplacedSinceCrash = false
        recoveryInProgress = false
        notifyListeners { onRenderProcessRecovered() }
    }

    internal fun failRendererRecovery(url: String?) {
        if (recoveryInProgress) recoveryInProgress = false
        rendererRecoveryFailed = true
        if (pendingRecoveryUrl.isNullOrBlank() || pendingRecoveryUrl.equals("about:blank", ignoreCase = true)) {
            pendingRecoveryUrl = url?.takeIf { it.isNotBlank() && !it.equals("about:blank", ignoreCase = true) }
        }
        notifyListeners {
            onPageError(
                url.orEmpty(),
                ShellWebViewLimits.ERROR_RENDERER_GONE,
                "网页渲染进程连续崩溃，已停止自动重试",
                false,
            )
        }
        notifyListeners { onRenderProcessRecoveryFailed() }
    }

    private fun handleDownload(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        if (url.startsWith("blob:")) {
            notifyListeners { onDownloadStarted("(blob)") }
            blobs.download(url)
            return
        }
        val fileName = DownloadPolicy.safeFileName(URLUtil.guessFileName(url, contentDisposition, mimeType))
        if (!DownloadPolicy.canEnqueue(url)) {
            AppLog.warn("download", "拒绝下载 host=${DownloadPolicy.logHost(url)} name=$fileName")
            notifyListeners { onDownloadFailed("unsupported-download") }
            return
        }
        AppLog.log("download", "开始下载 host=${DownloadPolicy.logHost(url)} name=$fileName")
        val downloadId = WebViewPool.downloadSink?.startHttp(
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            userAgent = userAgent,
            referer = webView.url,
            cookie = CookieManager.getInstance().getCookie(url),
        )
        if (downloadId == null) {
            notifyListeners { onDownloadFailed("download-failed") }
        } else {
            notifyListeners { onDownloadStarted(fileName) }
        }
    }

    // ---------------------------------------------------------------- find in page

    /** 页面内查找结果回调（主线程）；activeMatchOrdinal 从 0 开始 */
    var onFindResult: ((activeMatchOrdinal: Int, numberOfMatches: Int) -> Unit)? = null

    private fun applyFindListener() {
        val generation = callbackGeneration
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            if (generation == callbackGeneration) {
                onFindResult?.invoke(activeMatchOrdinal, numberOfMatches)
            }
        }
    }

    /** 开始/更新页内查找；空串等效清除 */
    fun findInPage(query: String) {
        webView.findAllAsync(query)
    }

    /** 跳到上/下一个匹配项 */
    fun findNext(forward: Boolean) {
        webView.findNext(forward)
    }

    /** 清除查找高亮 */
    fun clearFindMatches() {
        runCatching { webView.clearMatches() }
    }

    // ---------------------------------------------------------------- insets

    /** 最近一次 insets 注入脚本；换文档（新页面加载）后需重放 */
    private var lastInsetsJs: String? = null

    /** Host-owned remaining insets, converted from physical pixels to the document's CSS pixels. */
    fun updateSafeAreaInsets(top: Int, bottom: Int, left: Int, right: Int, imeHeight: Int = 0) {
        val js = "(function(){if(!document.documentElement)return;" +
            "var ratio=(window.devicePixelRatio||1)*((window.visualViewport&&window.visualViewport.scale)||1);" +
            "function set(k,v){document.documentElement.style.setProperty(k,(v/ratio)+'px');}" +
            "set('--ws-safe-top',$top);set('--ws-safe-bottom',$bottom);" +
            "set('--ws-safe-left',$left);set('--ws-safe-right',$right);set('--ws-ime-height',$imeHeight);})();"
        if (lastInsetsJs == js) return
        lastInsetsJs = js
        post { webView.evaluateJavascript(js, null) }
    }

    /** 新文档就绪时重放 insets（页面级 CSS 变量不跨文档持久） */
    internal fun reapplyInsetsIfNeeded() {
        lastInsetsJs?.let { js -> post { webView.evaluateJavascript(js, null) } }
    }

    // ---------------------------------------------------------------- state

    /** 会话快照（含返回栈）；由池淘汰/宿主 onStop/保活服务调用 */
    fun saveSessionState(): Bundle? {
        val bundle = Bundle()
        val saved = runCatching {
            WebViewCompat.saveState(webView, bundle, 4 * 1024 * 1024, true)
            true
        }.getOrElse {
            // A renderer-gone WebView may reject both the WebKit and platform
            // snapshot APIs. Do not let that exception escape into pool eviction
            // or release; the URL is still retained by the per-session UI state.
            runCatching { webView.saveState(bundle); true }.getOrDefault(false)
        }
        if (!saved) return null
        runCatching { CookieManager.getInstance().flush() }
        savedStateBundle = bundle
        return bundle
    }

    fun restoreSessionState(bundle: Bundle?) {
        bundle?.let { runCatching { webView.restoreState(it) } }
    }

    /** 首次加载：若存在渲染恢复 URL 则用之，否则加载 startUrl */
    fun loadWithStateRestore(url: String) {
        if (retryRendererIfNeeded()) return
        rendererRecoveryGate.resetForExplicitNavigation()
        val target = pendingRecoveryUrl?.also { pendingRecoveryUrl = null } ?: url
        loadUrlOrPrompt(target)
    }

    fun reloadWithStateRestore() {
        if (retryRendererIfNeeded()) return
        rendererRecoveryGate.resetForExplicitNavigation()
        reload()
    }

    fun currentUrl(): String? = webView.url

    fun isAwaitingExplicitRendererRetry(): Boolean = rendererRecoveryFailed

    /** 直接加载 URL（供 onNewWindow 等场景复用当前会话） */
    fun load(url: String) {
        if (retryRendererIfNeeded()) return
        rendererRecoveryGate.resetForExplicitNavigation()
        restoreDefaultCacheModeIfNeeded()
        loadUrlOrPrompt(url)
    }

    fun goBack(): Boolean = if (webView.canGoBack()) {
        rendererRecoveryGate.resetForExplicitNavigation()
        webView.goBack(); true
    } else false

    fun canGoBack(): Boolean = webView.canGoBack()

    fun goForward(): Boolean = if (webView.canGoForward()) {
        rendererRecoveryGate.resetForExplicitNavigation()
        webView.goForward(); true
    } else false

    fun canGoForward(): Boolean = webView.canGoForward()

    fun reload() {
        if (retryRendererIfNeeded()) return
        rendererRecoveryGate.resetForExplicitNavigation()
        val url = webView.url
        if (url != null && maybePromptCleartext(url) { webView.reload() }) return
        webView.reload()
    }

    /**
     * 绕过 HTTP 缓存的 reload：桌面/移动切换必须让服务器按新 UA 重新决策主文档，
     * 普通 reload 可能直接复用缓存中的旧版本页面。整个导航期间保持绕缓存，
     * 完成、失败、停止或超时后恢复默认策略；不删除 Cookie 或网站存储。
     */
    internal var documentRevision = 0L

    fun requestPageIcons() {
        val revision = documentRevision
        val url = webView.url ?: return
        webView.evaluateJavascript(PageIconDeclarations.script) { raw ->
            if (revision != documentRevision || webView.url != url) return@evaluateJavascript
            val candidates = PageIconDeclarations.parse(raw)
            if (candidates.isNotEmpty()) notifyListeners { onIconCandidates(url, candidates) }
        }
    }

    fun reloadBypassingCache() {
        if (retryRendererIfNeeded()) return
        rendererRecoveryGate.resetForExplicitNavigation()
        val go = {
            cacheBypassReloadPending = true
            webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            val generation = documentRevision
            mainHandler.postDelayed({ if (documentRevision <= generation + 1) restoreDefaultCacheModeIfNeeded() }, 25_000)
            webView.reload()
        }
        val url = webView.url
        if (url != null && maybePromptCleartext(url, go)) return
        go()
    }

    private fun loadUrlOrPrompt(url: String) {
        // loadUrl() does not call shouldOverrideUrlLoading. File URLs (GitHub
        // APK, etc.) must enqueue here or Chromium may try to render the bytes.
        if (interceptFileDownload(url)) return
        if (maybePromptCleartext(url) { webView.loadUrl(url) }) return
        webView.loadUrl(url)
    }

    /** True when the URL was taken as a DownloadManager enqueue, not a document. */
    private fun interceptFileDownload(url: String): Boolean {
        if (!DownloadPolicy.looksLikeFileDownload(url)) return false
        AppLog.log(
            "download",
            "文件地址按下载处理 host=${DownloadPolicy.logHost(url)} name=${DownloadPolicy.safeFileName(URLUtil.guessFileName(url, null, null))}",
        )
        handleDownload(url, webView.settings.userAgentString.orEmpty(), null, null)
        return true
    }

    /** @return true if navigation was deferred for a visible confirm. */
    private fun maybePromptCleartext(url: String, onAllowed: () -> Unit): Boolean {
        if (!CleartextGate.requiresPrompt(sessionId, url)) return false
        requestCleartextConsent(url, onAllowed)
        return true
    }

    private fun requestCleartextConsent(url: String, onAllowed: () -> Unit) {
        val host = CleartextGate.hostOf(url)
        if (host == null) {
            onAllowed()
            return
        }
        finishPendingCleartext()
        val target = listener
        if (target == null) {
            AppLog.log("web", "明文导航等待可见宿主 host=$host")
            mainHandler.post {
                if (listener == null) {
                    AppLog.log("web", "明文导航无可见宿主，已取消 host=$host")
                } else {
                    requestCleartextConsent(url, onAllowed)
                }
            }
            return
        }
        var finished = false
        val cancel = {
            if (!finished) {
                finished = true
                pendingCleartextCancel = null
                WebViewPool.unprotect(sessionId, WebViewPool.ProtectionReason.PENDING_CLEARTEXT)
            }
        }
        val proceed = {
            if (!finished) {
                finished = true
                pendingCleartextCancel = null
                CleartextGate.allow(sessionId, host)
                WebViewPool.unprotect(sessionId, WebViewPool.ProtectionReason.PENDING_CLEARTEXT)
                onAllowed()
            }
        }
        pendingCleartextCancel = cancel
        WebViewPool.protect(sessionId, WebViewPool.ProtectionReason.PENDING_CLEARTEXT)
        AppLog.log("web", "明文导航待确认 host=$host")
        target.onCleartextPrompt(url, proceed, cancel)
    }

    internal fun finishPendingCleartext() {
        pendingCleartextCancel?.invoke()
        pendingCleartextCancel = null
    }

    internal fun restoreDefaultCacheModeIfNeeded() {
        if (cacheBypassReloadPending) {
            cacheBypassReloadPending = false
            webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        }
    }

    /**
     * 桌面模式加载完成后的运行时探针（我的 → 开发者选项 → 日志查看）。
     * 只记录固定数值：iw、cw、ow、rc、dpr、sw、vv、vs、mq 六个整数、boot，
     * 以及 viewport meta 的条数 metas。不记录 meta 原文、userAgentData、
     * 原始 JSON 或异常信息；探针失败是固定标记。
     * 断点高于布局宽时不命中属正常。
     */
    internal fun desktopViewportProbe() {
        webView.evaluateJavascript(DESKTOP_VIEWPORT_PROBE_JS) { raw ->
            AppLog.log(
                "webengine",
                "桌面探针 sessionId=$sessionId ${ShellNavigation.desktopProbeLog(raw)}",
            )
        }
    }

    /** Recreate a child WebView after the automatic recovery budget is spent.
     * Android marks the crashed WebView unusable; a plain reload would leave a
     * permanent blank surface, so the user-facing retry must replace it too. */
    private fun retryRendererIfNeeded(): Boolean {
        if (!rendererRecoveryFailed) return false
        rendererRecoveryGate.resetForExplicitNavigation()
        rendererRecoveryFailed = false
        val retryUrl = RendererRecoveryPolicy.retryLoadUrl(pendingRecoveryUrl, webView.url, config.startUrl)
        if (rendererReplacedSinceCrash) {
            rendererReplacedSinceCrash = false
            pendingRecoveryUrl = null
            val loaded = retryUrl.takeIf { it.isNotBlank() }
                ?.let { runCatching { webView.loadUrl(it) }.isSuccess } ?: true
            if (!loaded) failRendererRecovery(retryUrl)
            return true
        }
        recoverRenderer(retryUrl, userInitiated = true)
        return true
    }

    fun stopLoading() {
        forcedReload?.complete(false)
        restoreDefaultCacheModeIfNeeded()
        runCatching { webView.stopLoading() }
    }

    fun captureThumbnail(maxWidth: Int = 360, maxHeight: Int = 640): Bitmap? {
        if (webView.width == 0 || webView.height == 0) return null
        val scale = minOf(1f, maxWidth.toFloat() / webView.width, maxHeight.toFloat() / webView.height)
        val bmp = Bitmap.createBitmap(
            (webView.width * scale).toInt().coerceAtLeast(1),
            (webView.height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        webView.draw(canvas)
        return bmp
    }

    fun release() {
        documentRevision++
        forcedReload?.complete(false)
        restoreDefaultCacheModeIfNeeded()
        listener = null
        sessionListener = null
        callbackGeneration++
        rendererRecoveryFailed = false
        rendererReplacedSinceCrash = false
        lastCommittedUrl = null
        // A JsResult must always get a definitive answer, even if the host is
        // torn down mid-grace-window; resolve it before wiping the handler.
        pendingJsDialogFallback?.let { runCatching { it() } }
        pendingJsDialogFallback = null
        mainHandler.removeCallbacksAndMessages(null)
        blobs.detach()
        pendingSsl?.let { runCatching { it.cancel() } }
        pendingSsl = null
        finishPendingCleartext()
        hideCustomView()
        saveSessionState()
        runCatching {
            (parent as? ViewGroup)?.removeView(this)
            webView.destroy()
        }
    }

    /**
     * 多标签切换：把视图从窗口摘除但实例保留在池中（JS/网络/返回栈不中断）。
     * 与 [release] 的区别：不销毁 WebView、不暂停渲染——池中的会话保持"活着"。
     */
    fun detachForReuse() {
        runCatching { (parent as? ViewGroup)?.removeView(this) }
    }

    fun suspendRendering() {
        webView.onPause()
    }

    fun resumeRendering() {
        webView.onResume()
    }


}

/**
 * Numeric desktop probe. Order is the contract of [ShellNavigation.desktopProbeLog]:
 * iw, cw, ow, rc, dpr, sw, vv, vs, viewport-meta count, six media-query ints, boot.
 * Meta content and userAgentData are not read. Failures return a fixed token.
 */
private val DESKTOP_VIEWPORT_PROBE_JS =
    "(function(){try{" +
        "var metas=0;var ms=document.getElementsByTagName('meta');" +
        "for(var i=0;i<ms.length;i++){" +
        "if((ms[i].getAttribute('name')||'').toLowerCase()==='viewport')metas++;}" +
        "function mq(q){try{return matchMedia(q).matches?1:0}catch(e){return -1}}" +
        "function n(v){var x=+v;return isFinite(x)?x:0}" +
        "return [" +
        "n(window.innerWidth),n(document.documentElement.clientWidth)," +
        "n(window.outerWidth)," +
        "n(Math.round(document.documentElement.getBoundingClientRect().width))," +
        "n(window.devicePixelRatio),n(window.screen.width)," +
        "n(window.visualViewport?Math.round(window.visualViewport.width):0)," +
        "n(window.visualViewport?+window.visualViewport.scale.toFixed(2):0)," +
        "metas|0," +
        "mq('(min-width:768px)'),mq('(min-width:980px)'),mq('(min-width:1024px)')," +
        "mq('(min-width:1280px)'),mq('(pointer:coarse)'),mq('(hover:hover)')," +
        "n(window.__wsBoot?window.__wsBoot.v:0)" +
        "].join(',');}catch(e){return '${ShellNavigation.PROBE_FAIL}';}})()"
