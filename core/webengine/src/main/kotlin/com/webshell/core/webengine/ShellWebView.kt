package com.webshell.core.webengine

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
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
    private inline fun notifyListeners(block: ShellListener.() -> Unit) {
        sessionListener?.block()
        listener?.block()
    }

    private val assetLoader: WebViewAssetLoaderHolder =
        WebViewAssetLoaderHolder(context, config.localAppId)

    private var savedStateBundle: Bundle? = null

    internal var pendingRecoveryUrl: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSsl: SslErrorHandler? = null
    /** Safe-default resolver for a JS dialog awaiting a visible listener; see [deliverJsResult]. */
    private var pendingJsDialogFallback: (() -> Unit)? = null
    private var recoveryInProgress = false
    private var rendererRecoveryFailed = false
    /** True after a crash replacement that has not yet been asked to load again. */
    private var rendererReplacedSinceCrash = false
    private val rendererRecoveryGate = RendererRecoveryGate()
    /**
     * Last main-document URL observed on the UI thread. [shouldInterceptRequest]
     * runs off-thread and must not read [WebView.getUrl].
     */
    @Volatile
    private var lastCommittedUrl: String? = null
    /** Monotonically invalidates callbacks belonging to a replaced WebView. */
    private var callbackGeneration = 0L
    private var customView: View? = null
    private var blobRequestToken = 0L
    private var activeBlobToken: Long? = null
    private var blobOut: FileOutputStream? = null
    private var blobFile: File? = null
    private var blobOffset = 0
    private var blobTotal = 0
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
    /** DOCUMENT_START_SCRIPT support is process-constant; cache it for the fallback path. */
    private val documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    /** Log the legacy-WebView fallback injection at most once per session. */
    private var desktopFallbackLogged = false
    /** REPLACE_IN_SHELL just handed Chromium this same WebView; drop the opener history. */
    private var pendingClearHistory = false

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
            // UA 字符串只覆盖 User-Agent 头；Sec-CH-UA-* Client Hints 来自 metadata。
            // brand 列表必须与 UA 字符串版本对齐，否则按 UA-CH 判定的站点仍会收到移动信号。
            runCatching {
                WebSettingsCompat.setUserAgentMetadata(
                    webView.settings,
                    androidx.webkit.UserAgentMetadata.Builder()
                        .setBrandVersionList(
                            listOf(
                                androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                                    .setBrand("Chromium")
                                    .setMajorVersion(WebEngineDefaults.UA_MAJOR_VERSION)
                                    .setFullVersion(WebEngineDefaults.UA_FULL_VERSION)
                                    .build(),
                                androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                                    .setBrand("Not_A Brand")
                                    .setMajorVersion("99")
                                    .build(),
                            ),
                        )
                        .setPlatform(if (enabled) "Windows" else "Android")
                        .setPlatformVersion(if (enabled) "10.0.0" else Build.VERSION.RELEASE ?: "")
                        .setArchitecture(if (enabled) "x86" else "")
                        .setModel(if (enabled) "" else Build.MODEL ?: "")
                        .setMobile(!enabled)
                        .build(),
                )
            }.onFailure { e ->
                AppLog.warn("webengine", "UA-CH metadata 设置失败 desktop=$enabled : ${e.message}")
            }
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
        webView.webViewClient = ShellClient(generation)
        webView.webChromeClient = ShellChromeClient(generation)
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

    private inner class ShellClient(private val generation: Long) : WebViewClient() {

        private fun isCurrent(view: WebView): Boolean =
            generation == callbackGeneration && view === webView

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = if (isCurrent(view)) routeUrl(request.url.toString(), request.isForMainFrame) else true

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (!isCurrent(view)) return
            restoreDefaultCacheModeIfNeeded()
            lastCommittedUrl = url
            if (config.desktopMode && config.localAppId == null) {
                // document-start 注入是主路径；onPageStarted 再注入一次作为全
                // WebView 版本兜底（脚本内 __wsBoot 幂等守卫，重复注入零副作用）。
                if (!documentStartSupported && !desktopFallbackLogged) {
                    desktopFallbackLogged = true
                    AppLog.log(
                        "webengine",
                        "DOCUMENT_START_SCRIPT 不可用，桌面 viewport 改写降级为页面回调注入 sessionId=$sessionId",
                    )
                }
                webView.evaluateJavascript(
                    WebEngineDefaults.documentStartBootstrap(
                        forceEnableZoom = config.forceEnableZoom,
                        localApp = false,
                        desktopMode = true,
                    ),
                    null,
                )
            }
            if (!url.startsWith("blob:", ignoreCase = true) &&
                !url.startsWith("data:", ignoreCase = true)
            ) {
                if (activeBlobToken != null) {
                    AppLog.log("download", "页内下载因导航取消 host=${logHost(url)}")
                }
                cancelActiveBlob("cancelled")
                blobRequestToken++
            }
            pendingSsl?.let { runCatching { it.cancel() } }
            pendingSsl = null
            if (pendingClearHistory) {
                pendingClearHistory = false
                view.post { if (isCurrent(view)) view.clearHistory() }
            }
            AppLog.log("web", "加载 ${logHost(url)}")
            notifyListeners { onPageStarted(url) }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (!isCurrent(view)) return
            notifyListeners { onCanGoBackChanged(view.canGoBack()) }
            notifyListeners { onCanGoForwardChanged(view.canGoForward()) }
            super.doUpdateVisitedHistory(view, url, isReload)
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            if (!isCurrent(view)) return
            reapplyInsetsIfNeeded()
            notifyListeners { onFirstPaint(url) }
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (!isCurrent(view)) return
            lastCommittedUrl = url
            if (config.desktopMode && config.localAppId == null && !documentStartSupported) {
                // 老 WebView 的最终兜底：onPageStarted 注入可能落在旧文档上，
                // 这里在已提交的新文档上再补一次（__wsBoot 守卫保证幂等）。
                webView.evaluateJavascript(
                    WebEngineDefaults.documentStartBootstrap(
                        forceEnableZoom = config.forceEnableZoom,
                        localApp = false,
                        desktopMode = true,
                    ),
                    null,
                )
            }
            AppLog.log("web", "加载完成 ${logHost(url)}")
            // A completed navigation proves that the replacement renderer is
            // stable. The next crash may therefore receive one fresh automatic
            // recovery attempt rather than inheriting an old crash budget.
            rendererRecoveryGate.markStable()
            rendererRecoveryFailed = false
            setRefreshingInternal(false)
            notifyListeners { onPageFinished(url) }
            CookieManager.getInstance().flush()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (!isCurrent(view)) return
            if (request.isForMainFrame) {
                AppLog.error(
                    "web",
                    "加载失败 ${logHost(request.url?.toString())}: ${error.errorCode} ${error.description}",
                )
                val failedUrl = request.url?.toString().orEmpty()
                val insecureHttp = failedUrl.startsWith("http://", ignoreCase = true)
                notifyListeners { onPageError(failedUrl, error.errorCode, error.description?.toString().orEmpty(), insecureHttp) }
                // Complete the navigation state even when Chromium gives us an error page.
                notifyListeners { onPageFinished(failedUrl) }
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (!isCurrent(view)) return
            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                val failedUrl = request.url?.toString().orEmpty()
                notifyListeners {
                    onPageError(
                        failedUrl,
                        errorResponse.statusCode,
                        errorResponse.reasonPhrase.orEmpty(),
                        failedUrl.startsWith("http://", ignoreCase = true),
                    )
                }
                // Chromium may keep the progress stream open for an HTTP error
                // document. Close the per-session loading state just as we do
                // for onReceivedError; otherwise the toolbar can spin forever.
                notifyListeners { onPageFinished(failedUrl) }
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            if (!isCurrent(view)) {
                runCatching { handler.cancel() }
                return
            }
            AppLog.error("web", "SSL 错误 ${logHost(view.url)}: ${error.primaryError}")
            // Hold the request only for an explicit, one-shot user decision. A timeout
            // prevents a renderer/network request from waiting forever on a dead dialog.
            pendingSsl?.let { runCatching { it.cancel() } }
            pendingSsl = handler
            val url = view.url.orEmpty()
            val cancel = {
                if (pendingSsl === handler) {
                    pendingSsl = null
                    runCatching { handler.cancel() }
                }
            }
            val proceed = {
                if (pendingSsl === handler) {
                    pendingSsl = null
                    runCatching { handler.proceed() }
                }
            }
            notifyListeners { onSslError(url, error.primaryError.toString(), proceed, cancel) }
            mainHandler.postDelayed(cancel, SSL_DECISION_TIMEOUT_MS)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (!isCurrent(view)) return null
            val url = request.url.toString()
            if (!LocalWebHost.isLocalUrl(url)) return null
            // The AssetLoader host is shared by all sessions. Only the session
            // that owns an imported app may resolve its /local/<appId>/ tree,
            // and only while the top document itself is still on that app's
            // pages (a subresource fetched after the main frame left for a
            // remote origin must not keep reading local files).
            if (!LocalWebHost.isAllowedLocalUrl(url, config.localAppId, request.isForMainFrame, lastCommittedUrl)) {
                // Do not let a denied appassets URL fall through to a real
                // network request. Return an empty local 403 response instead.
                return WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    403,
                    "Forbidden",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0)),
                )
            }
            return assetLoader.loader.shouldInterceptRequest(request.url)
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: android.webkit.RenderProcessGoneDetail,
        ): Boolean {
            if (!isCurrent(view)) return true
            // 官方建议：不让宿主进程陪葬。Replace only the child WebView so the
            // Compose host and its session listeners remain attached.
            if (!recoveryInProgress) {
                if (rendererRecoveryGate.tryBeginRecovery()) recoverRenderer(view.url)
                else failRendererRecovery(view.url)
            }
            return true
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String?) {
            if (!isCurrent(view)) {
                handler.cancel()
                return
            }
            // Credentials are never collected or cached by the shell. Cancel the
            // request and surface a clear state so the user can use an external
            // browser/password manager if needed.
            handler.cancel()
            notifyListeners { onHttpAuthRequested(host, realm) { _, _ -> } }
        }

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
            if (!isCurrent(view)) {
                request.cancel()
                return
            }
            request.cancel()
            notifyListeners { onClientCertificateRequested(request.host) { } }
        }
    }

    private inner class ShellChromeClient(private val generation: Long) : WebChromeClient() {

        private fun isCurrent(view: WebView): Boolean =
            generation == callbackGeneration && view === webView

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (!isCurrent(view)) return
            notifyListeners { onProgress(newProgress) }
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            if (!isCurrent(view)) return
            notifyListeners { onTitleReceived(title) }
        }

        override fun onReceivedTouchIconUrl(view: WebView, url: String, precomposed: Boolean) {
            if (!isCurrent(view) || url.isBlank()) return
            notifyListeners { onIconUrl(url) }
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message,
        ): Boolean {
            if (!isCurrent(view)) return false
            // A target WebView is a real pooled session, not a disposable probe.
            // If no owner is attached there is nobody who can adopt/close it, so
            // reject the request before allocating a renderer.
            if (listener == null && sessionListener == null) return false
            val transport = view.WebViewTransport()
            if (config.newWindowPolicy == ShellConfig.NewWindowPolicy.REPLACE_IN_SHELL) {
                // User opted to stay inside this site shell. Same-view navigation
                // has no back stack after we clear it; OAuth/pay popups should
                // keep the default adopt policy.
                transport.setWebView(webView)
                pendingClearHistory = true
                notifyListeners {
                    onNewWindow(
                        NewWindowRequest(
                            sourceSessionId = sessionId,
                            sourceUrl = view.url,
                            isUserGesture = isUserGesture,
                            isDialog = isDialog,
                            initialUrl = view.hitTestResult.extra,
                            targetSessionId = sessionId,
                        ),
                    )
                }
                return runCatching {
                    resultMsg.obj = transport
                    resultMsg.sendToTarget()
                    true
                }.getOrDefault(false)
            }
            // Every other entry point opens a real second pooled session here,
            // never the source's own WebView. Reusing the current view would
            // let a popup silently replace the page (phishing risk) and breaks
            // the OAuth opener/redirect-back contract. The target always shares
            // the default profile (product-wide single login).
            val targetSessionId = "browser-${UUID.randomUUID().toString().take(12)}"
            // Protect the source from pool eviction while the target is being
            // allocated: getOrCreate() may itself need to evict an entry, and
            // the source must not be the one chosen while it is mid-callback.
            WebViewPool.protect(sessionId, WebViewPool.ProtectionReason.PENDING_WINDOW)
            val target = try {
                runCatching {
                    WebViewPool.getOrCreate(view.context, targetSessionId) {
                        config.copy(
                            sessionId = targetSessionId,
                            profileId = null,
                            startUrl = "about:blank",
                            localAppId = null,
                            // A popup handed to Browse must use in-app tab rules, not the
                            // source site's "open foreign hosts in the system browser" switch.
                            externalLinkPolicy = ShellConfig.ExternalLinkPolicy.OPEN_IN_SAME,
                        )
                    }
                }.getOrNull()
            } finally {
                WebViewPool.unprotect(sessionId, WebViewPool.ProtectionReason.PENDING_WINDOW)
            }
            if (target == null) return false
            transport.setWebView(target.webView)
            val request = NewWindowRequest(
                sourceSessionId = sessionId,
                sourceUrl = view.url,
                isUserGesture = isUserGesture,
                isDialog = isDialog,
                initialUrl = view.hitTestResult.extra,
                targetSessionId = targetSessionId,
            )
            // Owner must bind target.sessionListener before Chromium receives
            // the transport; otherwise the first onPageStarted is lost and the
            // adopted tab stays about:blank forever.
            notifyListeners { onNewWindow(request) }
            val delivered = runCatching {
                resultMsg.obj = transport
                resultMsg.sendToTarget()
            }.isSuccess
            if (!delivered) {
                WebViewPool.destroyAndForget(targetSessionId)
                return false
            }
            return true
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            if (generation != callbackGeneration || webView !== this@ShellWebView.webView || listener == null) {
                filePathCallback.onReceiveValue(null)
                return true
            }
            notifyListeners { onFileChooserRequested(fileChooserParams, filePathCallback) }
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            if (generation != callbackGeneration || listener == null) {
                request.deny()
                return
            }
            notifyListeners { onPermissionRequested(request) }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (generation != callbackGeneration) return
            notifyListeners { onPermissionCanceled(request) }
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: android.webkit.GeolocationPermissions.Callback,
        ) {
            if (generation != callbackGeneration) {
                callback.invoke(origin, false, false)
                return
            }
            notifyListeners { onGeolocationPrompt(origin) { allow, retain ->
                callback.invoke(origin, allow, retain)
            } }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (generation != callbackGeneration) {
                callback.onCustomViewHidden()
                return
            }
            customView?.let { notifyListeners { onHideCustomView() } }
            customView = view
            notifyListeners { onShowCustomView(view) { callback.onCustomViewHidden(); hideCustomView() } }
        }

        override fun onHideCustomView() {
            if (generation != callbackGeneration) return
            hideCustomView()
        }

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
            // An alert has no "decline" concept; the only safe unattended
            // default is to let the page's script continue.
            return deliverJsResult(view, result, safeDefaultAccepted = true) { target, complete ->
                target.onJsAlert(url, JsDialogText.sanitize(message)) { complete.confirm() }
            }
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            return deliverJsResult(view, result, safeDefaultAccepted = false) { target, complete ->
                target.onJsConfirm(url, JsDialogText.sanitize(message), complete::respond)
            }
        }

        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean {
            return deliverJsResult(view, result, safeDefaultAccepted = false) { target, complete ->
                target.onJsBeforeUnload(url, JsDialogText.sanitize(message), complete::respond)
            }
        }

        override fun onJsPrompt(
            view: WebView,
            url: String,
            message: String,
            defaultValue: String,
            result: JsPromptResult,
        ): Boolean {
            if (!isCurrent(view)) {
                result.cancel()
                return true
            }
            val complete = OneShotJsPromptResult(result)
            fun ask(target: ShellListener) {
                target.onJsPrompt(
                    url,
                    JsDialogText.sanitize(message),
                    JsDialogText.sanitize(defaultValue),
                    complete::respond,
                )
            }
            // JS dialogs are an interactive decision, never a bookkeeping
            // callback: only the currently visible host (listener) may
            // answer one. sessionListener exists purely to track per-session
            // title/URL/progress and must never silently inherit this.
            listener?.let { ask(it); return true }
            armBackgroundDialogFallback({ complete.respond(null) }) { late -> ask(late) }
            return true
        }

        private fun deliverJsResult(
            view: WebView,
            result: JsResult,
            safeDefaultAccepted: Boolean,
            deliver: (ShellListener, OneShotJsResult) -> Unit,
        ): Boolean {
            if (!isCurrent(view)) {
                result.cancel()
                return true
            }
            val complete = OneShotJsResult(result)
            listener?.let { deliver(it, complete); return true }
            armBackgroundDialogFallback({ complete.respond(safeDefaultAccepted) }) { late ->
                deliver(late, complete)
            }
            return true
        }
    }

    /**
     * A JS dialog fired while nobody is watching (a background browser tab,
     * or a host mid tab-switch that has not attached [listener] yet). Give it
     * a short grace window — re-checking [listener] once — before applying
     * [fallback]. This is a real decision on the engine's behalf, made
     * explicit here, not an accidental fall-through to a listener interface's
     * default method.
     */
    private fun armBackgroundDialogFallback(fallback: () -> Unit, deliverToLate: (ShellListener) -> Unit) {
        pendingJsDialogFallback?.invoke()
        val resolve = {
            pendingJsDialogFallback = null
            val late = listener
            if (late != null) deliverToLate(late) else fallback()
        }
        pendingJsDialogFallback = fallback
        mainHandler.postDelayed(resolve, JS_DIALOG_BACKGROUND_TIMEOUT_MS)
    }

    // ---------------------------------------------------------------- routing

    /**
     * 日志只记录 host（隐私纪律：不落完整 URL/查询串）；
     * 解析失败时截断原串到 64 字符内兜底。
     */
    private fun logHost(url: String?): String {
        if (url.isNullOrBlank()) return "(无 URL)"
        val host = runCatching { Uri.parse(url).host?.takeIf { it.isNotBlank() } }.getOrNull()
        return host ?: url.take(64)
    }

    /** @return true 表示本引擎已处理（不交给 WebView 加载） */
    private fun routeUrl(url: String, isMainFrame: Boolean = true): Boolean {
        if (LocalWebHost.isLocalUrl(url) &&
            !LocalWebHost.isAllowedLocalUrl(url, config.localAppId, isMainFrame, lastCommittedUrl)
        ) {
            // A foreign /local/<appId>/ navigation must not fall through to
            // Chromium's network stack. The same check is applied to
            // subresources in shouldInterceptRequest above.
            notifyListeners {
                onPageError(url, ERROR_LOCAL_RESOURCE_FORBIDDEN, "本地应用资源不属于当前会话", false)
            }
            return true
        }
        val decision = UrlRouter.classify(url)
        val uri = runCatching { url.toUri() }.getOrNull()
        return when (decision.route) {
            UrlRoute.WEB -> {
                if (isMainFrame && DownloadPolicy.looksLikeFileDownload(url)) {
                    AppLog.log(
                        "download",
                        "页内文件链接按下载处理 host=${DownloadPolicy.logHost(url)} name=${DownloadPolicy.safeFileName(URLUtil.guessFileName(url, null, null))}",
                    )
                    handleDownload(url, webView.settings.userAgentString.orEmpty(), null, null)
                    true
                } else if (config.externalLinkPolicy == ShellConfig.ExternalLinkPolicy.OPEN_IN_BROWSER &&
                    !LocalWebHost.isLocalUrl(url) && isForeignHost(url)
                ) {
                    launchExternal(url)
                    true
                } else false
            }
            UrlRoute.ABOUT_BLANK, UrlRoute.DATA, UrlRoute.BLOB -> false
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
            check(intent.resolveActivity(context.packageManager) != null) { "No handler" }
            dispatchActivity(intent)
        }.onFailure { notifyListeners { onExternalLaunchFailed(url) } }
        return true
    }

    /** WebViewPool deliberately stores application-context views; add the
     * required task flag only for that context and preserve Activity semantics
     * for hosts that pass an Activity context directly. */
    private fun dispatchActivity(intent: Intent) {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun hideCustomView() {
        if (customView != null) {
            customView = null
            notifyListeners { onHideCustomView() }
        }
    }

    private fun recoverRenderer(url: String?, userInitiated: Boolean = false) {
        if (recoveryInProgress) return
        recoveryInProgress = true
        rendererRecoveryFailed = false
        // The crashed renderer's JsResult (if any) is no longer resolvable
        // against a live web contents; resolve it with the safe default now
        // instead of leaving it dangling until the grace timer fires.
        pendingJsDialogFallback?.let { runCatching { it() } }
        pendingJsDialogFallback = null
        cancelActiveBlob("cancelled")
        blobRequestToken++
        WebViewPool.markLifecycle(sessionId, SessionLifecycleState.RECOVERING)
        val restoreUrl = RendererRecoveryPolicy.retryLoadUrl(url, pendingRecoveryUrl, config.startUrl)
        pendingRecoveryUrl = restoreUrl.takeIf { it.isNotBlank() }
        notifyListeners { onPageError(url.orEmpty(), ERROR_RENDERER_GONE, "网页渲染进程已重启", false) }
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
        WebViewPool.markLifecycle(sessionId, SessionLifecycleState.ACTIVE)
        notifyListeners { onRenderProcessRecovered() }
    }

    private fun failRendererRecovery(url: String?) {
        if (recoveryInProgress) recoveryInProgress = false
        rendererRecoveryFailed = true
        if (pendingRecoveryUrl.isNullOrBlank() || pendingRecoveryUrl.equals("about:blank", ignoreCase = true)) {
            pendingRecoveryUrl = url?.takeIf { it.isNotBlank() && !it.equals("about:blank", ignoreCase = true) }
        }
        WebViewPool.markLifecycle(sessionId, SessionLifecycleState.BACKGROUND)
        notifyListeners {
            onPageError(
                url.orEmpty(),
                ERROR_RENDERER_GONE,
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
            downloadBlob(url)
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

    /**
     * Read a blob from the current document in Binder-safe chunks. No JavaScript
     * bridge is exposed to the origin — each slice comes back through evaluateJavascript.
     */
    private fun downloadBlob(url: String) {
        val token = ++blobRequestToken
        val generation = callbackGeneration
        beginBlobDownload(token)
        val quoted = org.json.JSONObject.quote(url)
        val script = """
            (async function(){
              try {
                const b = await fetch($quoted).then(r => r.blob());
                if (b.size > $MAX_BLOB_BYTES) return 'ERR:too-large';
                window.__wsDl = window.__wsDl || {};
                window.__wsDl['$token'] = b;
                return 'META:' + b.size + ':' + (b.type || '');
              } catch(e) { return 'ERR:' + (e && e.message ? e.message : 'blob-failed'); }
            })()
        """.trimIndent()
        runCatching {
            webView.evaluateJavascript(script) { raw ->
                if (!blobStillActive(token, generation)) return@evaluateJavascript
                val meta = parseBlobMeta(raw)
                if (meta == null) {
                    val parsed = parseBlobEvaluation(raw)
                    abortBlob(token, (parsed as? BlobDownloadParseResult.Failure)?.reason ?: "blob-failed")
                    return@evaluateJavascript
                }
                val (size, mime) = meta
                if (size > MAX_BLOB_BYTES) {
                    abortBlob(token, "blob-too-large")
                    return@evaluateJavascript
                }
                AppLog.log("download", "页内 blob 分块 size=$size")
                if (!openBlobFile(mime)) {
                    abortBlob(token, "blob-write-failed")
                    return@evaluateJavascript
                }
                blobTotal = size.toInt()
                if (size == 0L) {
                    finishBlob(token)
                    return@evaluateJavascript
                }
                requestBlobChunk(token, generation)
            }
        }.onFailure {
            abortBlob(token, it.message ?: "blob-failed")
        }
    }

    private fun requestBlobChunk(token: Long, generation: Long) {
        if (!blobStillActive(token, generation)) return
        val script = """
            (async function(){
              try {
                const b = window.__wsDl && window.__wsDl['$token'];
                if (!b) return 'ERR:gone';
                const slice = b.slice($blobOffset, ${blobOffset + BLOB_CHUNK_BYTES});
                const a = new Uint8Array(await slice.arrayBuffer());
                let s=''; for(let i=0;i<a.length;i+=0x8000) s += String.fromCharCode(...a.subarray(i,i+0x8000));
                return 'CHUNK:' + btoa(s);
              } catch(e) { return 'ERR:' + (e && e.message ? e.message : 'blob-failed'); }
            })()
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            if (!blobStillActive(token, generation)) return@evaluateJavascript
            val encoded = parseBlobChunk(raw)
            if (encoded == null) {
                abortBlob(token, decodeBlobError(raw))
                return@evaluateJavascript
            }
            if (encoded.length > MAX_BLOB_BASE64_CHARS) {
                abortBlob(token, "blob-too-large")
                return@evaluateJavascript
            }
            val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
            if (bytes == null) {
                abortBlob(token, "blob-failed")
                return@evaluateJavascript
            }
            runCatching { blobOut?.write(bytes) }.onFailure {
                abortBlob(token, "blob-write-failed")
                return@evaluateJavascript
            }
            blobOffset += bytes.size
            if (blobOffset >= blobTotal) finishBlob(token) else requestBlobChunk(token, generation)
        }
    }

    private fun openBlobFile(mime: String): Boolean {
        val extension = when {
            mime.contains("pdf") -> ".pdf"
            mime.contains("json") -> ".json"
            mime.contains("text/") -> ".txt"
            mime.contains("zip") -> ".zip"
            mime.contains("png") -> ".png"
            mime.contains("jpeg") -> ".jpg"
            else -> ".bin"
        }
        val fileName = "download-${System.currentTimeMillis()}$extension"
        val out = File(context.cacheDir, "downloads/$fileName")
        return runCatching {
            out.parentFile?.mkdirs()
            blobFile = out
            blobOut = FileOutputStream(out)
            blobOffset = 0
            true
        }.getOrDefault(false)
    }

    private fun finishBlob(token: Long) {
        val out = blobFile
        runCatching { blobOut?.close() }
        blobOut = null
        blobFile = null
        blobOffset = 0
        blobTotal = 0
        cleanupBlobJs(token)
        endBlobDownload()
        if (out == null) {
            notifyListeners { onDownloadFailed("blob-write-failed") }
            return
        }
        runCatching {
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                out,
            )
            WebViewPool.downloadSink?.completeBlob(out.name, shareUri)
            notifyListeners { onDownloadFinished(out.name, shareUri) }
        }.onFailure {
            runCatching { out.delete() }
            notifyListeners { onDownloadFailed(it.message ?: "blob-write-failed") }
        }
    }

    private fun abortBlob(token: Long, reason: String) {
        if (activeBlobToken != token && activeBlobToken != null) return
        AppLog.warn("download", "页内下载失败 reason=${reason.take(80)}")
        runCatching { blobOut?.close() }
        blobOut = null
        blobFile?.let { runCatching { it.delete() } }
        blobFile = null
        blobOffset = 0
        blobTotal = 0
        cleanupBlobJs(token)
        if (activeBlobToken == token || activeBlobToken == null) {
            if (activeBlobToken == token) endBlobDownload()
            notifyListeners { onDownloadFailed(reason) }
        }
    }

    private fun cleanupBlobJs(token: Long) {
        webView.evaluateJavascript(
            "(function(){try{if(window.__wsDl)delete window.__wsDl['$token'];}catch(e){}})()",
            null,
        )
    }

    private fun blobStillActive(token: Long, generation: Long): Boolean {
        if (token == blobRequestToken && generation == callbackGeneration && activeBlobToken == token) {
            return true
        }
        if (activeBlobToken == token) endBlobDownload()
        return false
    }

    private fun decodeBlobError(raw: String?): String {
        val parsed = parseBlobEvaluation(raw)
        return (parsed as? BlobDownloadParseResult.Failure)?.reason ?: "blob-failed"
    }

    private fun beginBlobDownload(token: Long) {
        activeBlobToken = token
        WebViewPool.protect(sessionId, WebViewPool.ProtectionReason.PENDING_DOWNLOAD)
    }

    private fun endBlobDownload() {
        activeBlobToken = null
        WebViewPool.unprotect(sessionId, WebViewPool.ProtectionReason.PENDING_DOWNLOAD)
    }

    private fun cancelActiveBlob(reason: String) {
        val token = activeBlobToken ?: return
        abortBlob(token, reason)
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

    /** 宿主把系统 insets 写进页面 CSS 变量（WebView 中 env(safe-area-inset-*) 恒为 0） */
    fun updateSafeAreaInsets(top: Int, bottom: Int, left: Int, right: Int, imeHeight: Int = 0) {
        val js = "(function(){if(!document.documentElement)return;" +
            "document.documentElement.style.setProperty('--ws-safe-top','${top}px');" +
            "document.documentElement.style.setProperty('--ws-safe-bottom','${bottom}px');" +
            "document.documentElement.style.setProperty('--ws-safe-left','${left}px');" +
            "document.documentElement.style.setProperty('--ws-safe-right','${right}px');" +
            "document.documentElement.style.setProperty('--ws-ime-height','${imeHeight}px');})();"
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
        restoreDefaultCacheModeIfNeeded()
        pendingRecoveryUrl?.let { recovery ->
            webView.loadUrl(recovery)
            pendingRecoveryUrl = null
        } ?: run { webView.loadUrl(url) }
    }

    fun reloadWithStateRestore() {
        if (retryRendererIfNeeded()) return
        rendererRecoveryGate.resetForExplicitNavigation()
        webView.reload()
    }

    fun currentUrl(): String? = webView.url

    fun isAwaitingExplicitRendererRetry(): Boolean = rendererRecoveryFailed

    /** 直接加载 URL（供 onNewWindow 等场景复用当前会话） */
    fun load(url: String) {
        if (retryRendererIfNeeded()) return
        rendererRecoveryGate.resetForExplicitNavigation()
        restoreDefaultCacheModeIfNeeded()
        webView.loadUrl(url)
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
        webView.reload()
    }

    /**
     * 绕过 HTTP 缓存的 reload：桌面/移动切换必须让服务器按新 UA 重新决策主文档，
     * 普通 reload 可能直接复用缓存中的旧版本页面。主文档请求发出后，
     * [restoreDefaultCacheModeIfNeeded] 在下一次 onPageStarted 恢复默认缓存策略。
     */
    fun reloadBypassingCache() {
        if (retryRendererIfNeeded()) return
        rendererRecoveryGate.resetForExplicitNavigation()
        cacheBypassReloadPending = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.reload()
    }

    private fun restoreDefaultCacheModeIfNeeded() {
        if (cacheBypassReloadPending) {
            cacheBypassReloadPending = false
            webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
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
        blobRequestToken++
        if (activeBlobToken != null) endBlobDownload()
        pendingSsl?.let { runCatching { it.cancel() } }
        pendingSsl = null
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

    private companion object {
        // evaluateJavascript returns through a Binder transaction; keep the
        // payload comfortably below the platform transaction limit.
        const val MAX_BLOB_BYTES = 32 * 1024 * 1024
        const val BLOB_CHUNK_BYTES = 192 * 1024
        const val MAX_BLOB_BASE64_CHARS = BLOB_CHUNK_BYTES * 4 / 3 + 8
        const val SSL_DECISION_TIMEOUT_MS = 15_000L
        // Short: this only bridges a tab-switch/host-attach race, not a real
        // user decision window. A background session must not keep a page's
        // script blocked noticeably longer than an attached host would.
        const val JS_DIALOG_BACKGROUND_TIMEOUT_MS = 400L
        const val ERROR_LOCAL_RESOURCE_FORBIDDEN = -98
        const val ERROR_RENDERER_GONE = -99
    }
}

private class OneShotJsResult(private val result: JsResult) {
    private var done = false

    fun confirm() = complete(true)

    fun respond(accepted: Boolean) = complete(accepted)

    private fun complete(accepted: Boolean) {
        if (done) return
        done = true
        runCatching { if (accepted) result.confirm() else result.cancel() }
    }
}

private class OneShotJsPromptResult(private val result: JsPromptResult) {
    private var done = false

    fun respond(value: String?) {
        if (done) return
        done = true
        runCatching {
            if (value != null) result.confirm(value) else result.cancel()
        }
    }
}
