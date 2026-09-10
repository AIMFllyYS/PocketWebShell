package com.webshell.core.webengine

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
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
    private val rendererRecoveryGate = RendererRecoveryGate()
    /** Monotonically invalidates callbacks belonging to a replaced WebView. */
    private var callbackGeneration = 0L
    private var customView: View? = null
    private var blobRequestToken = 0L
    private var activeBlobToken: Long? = null

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
            builtInZoomControls = false
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
            WebSettingsCompat.setBackForwardCacheEnabled(webView.settings, true)
        }

        CookieManager.getInstance().setAcceptCookie(true)
        // 显式双向设置：true/false 都调用，避免跨实例状态泄漏（CookieManager 进程级共享）
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, config.thirdPartyCookies)
    }

    fun setDesktopMode(enabled: Boolean) {
        config = config.copy(desktopMode = enabled)
        webView.settings.userAgentString =
            if (enabled) WebEngineDefaults.DESKTOP_USER_AGENT else WebEngineDefaults.MOBILE_USER_AGENT
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            runCatching {
                WebSettingsCompat.setUserAgentMetadata(
                    webView.settings,
                    androidx.webkit.UserAgentMetadata.Builder()
                        .setPlatform(if (enabled) "Windows" else "Android")
                        .setPlatformVersion(if (enabled) "10.0.0" else Build.VERSION.RELEASE ?: "")
                        .setArchitecture(if (enabled) "x86" else "")
                        .setModel(if (enabled) "" else Build.MODEL ?: "")
                        .setMobile(!enabled)
                        .build(),
                )
            }
        }
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
        if (merged == old) return
        config = merged
        val needsReload = old.desktopMode != config.desktopMode ||
            old.textZoomPercent != config.textZoomPercent ||
            old.algorithmicDark != config.algorithmicDark ||
            old.thirdPartyCookies != config.thirdPartyCookies ||
            old.autoplayMedia != config.autoplayMedia
        configureBaseSettings()
        applyPullToRefresh()
        if (needsReload && !webView.url.isNullOrBlank() && webView.url != "about:blank") reload()
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
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            "(function(){" +
                "window.__wsBoot={t:Date.now()};" +
                "if(!document.getElementById('ws-safe-style')){" +
                "var s=document.createElement('style');s.id='ws-safe-style';" +
                "s.textContent=':root{--ws-safe-top:0px;--ws-safe-bottom:0px;" +
                "--ws-safe-left:0px;--ws-safe-right:0px;--ws-ime-height:0px;}';" +
                "document.documentElement.appendChild(s);}" +
                "})();",
            setOf("*"),
        )
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
            cancelActiveBlob("cancelled")
            blobRequestToken++
            pendingSsl?.let { runCatching { it.cancel() } }
            pendingSsl = null
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
            if (!LocalWebHost.isAllowedLocalUrl(url, config.localAppId, request.isForMainFrame, view.url)) {
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
            // Every entry point (browser tab, saved-site shell, direct link)
            // opens a real second pooled session here, never the source's own
            // WebView. Reusing the current view would let a popup silently
            // replace the page the user is still looking at (phishing risk)
            // and breaks the OAuth opener/redirect-back contract. The target
            // always shares the default profile (product-wide single login).
            val transport = view.WebViewTransport()
            val targetSessionId = "browser-window-${UUID.randomUUID().toString().take(12)}"
            // Protect the source from pool eviction while the target is being
            // allocated: getOrCreate() may itself need to evict an entry, and
            // the source must not be the one chosen while it is mid-callback.
            WebViewPool.protect(sessionId, WebViewPool.ProtectionReason.PENDING_WINDOW)
            val target = try {
                runCatching {
                    WebViewPool.getOrCreate(view.context, targetSessionId) {
                        config.copy(sessionId = targetSessionId, profileId = null, startUrl = "about:blank", localAppId = null)
                    }
                }.getOrNull()
            } finally {
                WebViewPool.unprotect(sessionId, WebViewPool.ProtectionReason.PENDING_WINDOW)
            }
            if (target == null) return false
            transport.setWebView(target.webView)
            val delivered = runCatching {
                resultMsg.obj = transport
                resultMsg.sendToTarget()
            }.isSuccess
            if (!delivered) {
                WebViewPool.destroyAndForget(targetSessionId)
                return false
            }
            val request = NewWindowRequest(
                sourceSessionId = sessionId,
                sourceUrl = view.url,
                isUserGesture = isUserGesture,
                isDialog = isDialog,
                initialUrl = view.hitTestResult.extra,
                targetSessionId = targetSessionId,
            )
            notifyListeners { onNewWindow(request) }
            return true
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            if (generation != callbackGeneration || webView !== this@ShellWebView.webView) {
                filePathCallback.onReceiveValue(null)
                return true
            }
            notifyListeners { onFileChooserRequested(fileChooserParams, filePathCallback) }
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            if (generation != callbackGeneration) {
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
            !LocalWebHost.isAllowedLocalUrl(url, config.localAppId, isMainFrame, webView.url)
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
                if (config.externalLinkPolicy == ShellConfig.ExternalLinkPolicy.OPEN_IN_BROWSER &&
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

    private fun recoverRenderer(url: String?) {
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
        pendingRecoveryUrl = url
        notifyListeners { onPageError(url.orEmpty(), ERROR_RENDERER_GONE, "网页渲染进程已重启", false) }
        val state = Bundle()
        val saved = runCatching { webView.saveState(state); state }.getOrNull()
        runCatching { hideCustomView() }
        val replacement = runCatching { replaceWebView() }.getOrNull()
        if (replacement == null) {
            failRendererRecovery(url)
            return
        }
        val configured = runCatching {
            applyProfile()
            configureBaseSettings()
            applyChromeClients()
            applyPullToRefresh()
            applyFindListener()
            injectBootstrapOnce()
        }.isSuccess
        if (!configured) {
            failRendererRecovery(url)
            return
        }
        val hasSavedState = saved != null && saved.size() > 0
        val restored = if (hasSavedState) {
            runCatching { webView.restoreState(saved); true }.getOrDefault(false)
        } else false
        val restoreUrl = url?.takeIf { it.isNotBlank() && it != "about:blank" }
        // A saved back/forward list will navigate itself; only fall back to a
        // direct URL when Chromium could not serialize the old state.
        if (!restored && (webView.url.isNullOrBlank() || webView.url == "about:blank")) {
            val loaded = restoreUrl?.let { runCatching { webView.loadUrl(it) }.isSuccess } ?: true
            if (!loaded) {
                failRendererRecovery(url)
                return
            }
        }
        pendingRecoveryUrl = null
        recoveryInProgress = false
        WebViewPool.markLifecycle(sessionId, SessionLifecycleState.ACTIVE)
        notifyListeners { onRenderProcessRecovered() }
    }

    private fun failRendererRecovery(url: String?) {
        if (recoveryInProgress) recoveryInProgress = false
        rendererRecoveryFailed = true
        pendingRecoveryUrl = null
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
        runCatching {
            val fileName = DownloadPolicy.safeFileName(URLUtil.guessFileName(url, contentDisposition, mimeType))
            val request = DownloadManager.Request(url.toUri())
                .setMimeType(mimeType)
                .setTitle(fileName)
                .setDescription("玄览 下载")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                .addRequestHeader("User-Agent", userAgent)
            DownloadPolicy.requestHeaders(
                url = url,
                userAgent = userAgent,
                referer = webView.url,
                cookie = CookieManager.getInstance().getCookie(url),
            ).forEach { (name, value) -> request.addRequestHeader(name, value) }
            context.getSystemService(DownloadManager::class.java).enqueue(request)
            notifyListeners { onDownloadStarted(fileName) }
        }.onFailure {
            notifyListeners { onDownloadFailed(it.message ?: "download-failed") }
        }
    }

    /**
     * Read a blob only from the current document, with a hard encoded and decoded
     * size limit. No JavaScript bridge is exposed to the origin.
     */
    private fun downloadBlob(url: String) {
        val token = ++blobRequestToken
        val generation = callbackGeneration
        beginBlobDownload(token)
        val script = """
            (async function(){
              try {
                const b = await fetch(${org.json.JSONObject.quote(url)}).then(r => r.blob());
                if (b.size > ${MAX_BLOB_BYTES}) return 'ERR:too-large';
                const a = new Uint8Array(await b.arrayBuffer());
                let s=''; for(let i=0;i<a.length;i+=0x8000) s += String.fromCharCode(...a.subarray(i,i+0x8000));
                return 'OK:' + b.type + ':' + btoa(s);
              } catch(e) { return 'ERR:' + (e && e.message ? e.message : 'blob-failed'); }
            })()
        """.trimIndent()
        runCatching {
            webView.evaluateJavascript(script) { raw ->
                if (token != blobRequestToken || generation != callbackGeneration) {
                    // Superseded by a newer request/renderer — that transition
                    // is expected to have already unprotected this token via
                    // cancelActiveBlob. Do not blindly call endBlobDownload()
                    // here: activeBlobToken may by now belong to a *different*,
                    // still in-flight download, and clearing it would drop that
                    // one's PENDING_DOWNLOAD guard out from under it. Only
                    // reconcile if this stale token is, despite the invariant
                    // above, still the one on record.
                    if (activeBlobToken == token) endBlobDownload()
                    return@evaluateJavascript
                }
                try {
                    completeBlobDownload(raw)
                } finally {
                    endBlobDownload()
                }
            }
        }.onFailure {
            if (activeBlobToken == token) endBlobDownload()
            notifyListeners { onDownloadFailed(it.message ?: "blob-failed") }
        }
    }

    private fun completeBlobDownload(raw: String?) {
            val parsed = parseBlobEvaluation(raw)
            val payload = when (parsed) {
                is BlobDownloadParseResult.Success -> parsed.payload
                is BlobDownloadParseResult.Failure -> {
                    notifyListeners { onDownloadFailed(parsed.reason) }
                    return
                }
            }
            val mime = payload.mimeType
            val encoded = payload.encoded
            if (encoded.length > MAX_BLOB_BASE64_CHARS) {
                notifyListeners { onDownloadFailed("blob-too-large") }
                return
            }
            val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
            if (bytes == null || bytes.size > MAX_BLOB_BYTES) {
                notifyListeners { onDownloadFailed("blob-too-large") }
                return
            }
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
            runCatching {
                out.parentFile?.mkdirs()
                FileOutputStream(out).use { it.write(bytes) }
                // A FileProvider URI is mandatory on API 24+; falling back to
                // Uri.fromFile would trigger FileUriExposedException and could
                // leak a private cache path to another application.
                val shareUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    out,
                )
                notifyListeners { onDownloadFinished(fileName, shareUri) }
            }.onFailure {
                runCatching { out.delete() }
                notifyListeners { onDownloadFailed(it.message ?: "blob-write-failed") }
            }
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
        if (activeBlobToken == null) return
        endBlobDownload()
        notifyListeners { onDownloadFailed(reason) }
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

    /** 直接加载 URL（供 onNewWindow 等场景复用当前会话） */
    fun load(url: String) {
        if (retryRendererIfNeeded()) return
        rendererRecoveryGate.resetForExplicitNavigation()
        webView.loadUrl(url)
    }

    /** Already-validated user navigation that must retain a saved site's external-link policy. */
    fun loadFollowingLinkPolicy(url: String) {
        if (!routeUrl(url)) {
            if (retryRendererIfNeeded()) return
            rendererRecoveryGate.resetForExplicitNavigation()
            webView.loadUrl(url)
        }
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

    /** Recreate a child WebView after the automatic recovery budget is spent.
     * Android marks the crashed WebView unusable; a plain reload would leave a
     * permanent blank surface, so the user-facing retry must replace it too. */
    private fun retryRendererIfNeeded(): Boolean {
        if (!rendererRecoveryFailed) return false
        rendererRecoveryGate.resetForExplicitNavigation()
        rendererRecoveryFailed = false
        recoverRenderer(webView.url)
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
        const val MAX_BLOB_BYTES = 512 * 1024
        const val MAX_BLOB_BASE64_CHARS = MAX_BLOB_BYTES * 4 / 3 + 8
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
