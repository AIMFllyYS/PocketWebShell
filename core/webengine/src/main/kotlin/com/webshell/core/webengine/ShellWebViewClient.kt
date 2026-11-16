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

internal class ShellWebViewClient(
    private val host: ShellWebView,
    private val generation: Long,
) : WebViewClient() {

        private fun isCurrent(view: WebView): Boolean =
            generation == host.callbackGeneration && view === host.webView

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = if (isCurrent(view)) host.routeUrl(request.url.toString(), request.isForMainFrame) else true

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (!isCurrent(view)) return
            host.documentRevision++
            host.lastCommittedUrl = url
            if (host.config.desktopMode && host.config.localAppId == null) {
                // document-start 注入是主路径；onPageStarted 再注入一次作为全
                // WebView 版本兜底（脚本内 __wsBoot 幂等守卫，重复注入零副作用）。
                if (!host.documentStartSupported && !host.desktopFallbackLogged) {
                    host.desktopFallbackLogged = true
                    AppLog.log(
                        "webengine",
                        "DOCUMENT_START_SCRIPT 不可用，桌面 viewport 改写降级为页面回调注入 sessionId=${host.sessionId}",
                    )
                }
                host.webView.evaluateJavascript(
                    WebEngineDefaults.documentStartBootstrap(
                        forceEnableZoom = host.config.forceEnableZoom,
                        localApp = false,
                        desktopMode = true,
                    ),
                    null,
                )
            }
            host.blobs.onMainFrameNavigated(url)
            host.pendingSsl?.let { runCatching { it.cancel() } }
            host.pendingSsl = null
            host.finishPendingCleartext()
            if (host.pendingClearHistory) {
                host.pendingClearHistory = false
                view.post { if (isCurrent(view)) view.clearHistory() }
            }
            AppLog.log("web", "加载 ${host.logHost(url)}")
            host.notifyListeners { onPageStarted(url) }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (!isCurrent(view)) return
            host.notifyListeners { onCanGoBackChanged(view.canGoBack()) }
            host.notifyListeners { onCanGoForwardChanged(view.canGoForward()) }
            host.notifyListeners { onPageUrlChanged(url) }
            super.doUpdateVisitedHistory(view, url, isReload)
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            if (!isCurrent(view)) return
            host.reapplyInsetsIfNeeded()
            host.notifyListeners { onFirstPaint(url) }
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (!isCurrent(view)) return
            host.lastCommittedUrl = url
            if (host.config.desktopMode && host.config.localAppId == null) {
                if (host.documentStartSupported) {
                    // document-start 已注入（__wsBoot 守卫保证幂等）；页面完成后
                    // 强制重断一次 viewport，覆盖站点脚本把 meta 改回去的情况。
                    host.webView.evaluateJavascript("try{window.__wsForceZoom&&window.__wsForceZoom()}catch(e){}", null)
                } else {
                    // 老 WebView 的最终兜底：onPageStarted 注入可能落在旧文档上，
                    // 这里在已提交的新文档上补全量注入（__wsBoot 守卫保证幂等）。
                    host.webView.evaluateJavascript(
                        WebEngineDefaults.documentStartBootstrap(
                            forceEnableZoom = host.config.forceEnableZoom,
                            localApp = false,
                            desktopMode = true,
                        ),
                        null,
                    )
                }
                host.desktopViewportProbe()
            }
            AppLog.log("web", "加载完成 ${host.logHost(url)}")
            // A completed navigation proves that the replacement renderer is
            // stable. The next crash may therefore receive one fresh automatic
            // recovery attempt rather than inheriting an old crash budget.
            host.rendererRecoveryGate.markStable()
            host.rendererRecoveryFailed = false
            host.setRefreshingInternal(false)
            host.restoreDefaultCacheModeIfNeeded()
            host.forcedReload?.complete(true)
            host.notifyListeners { onPageFinished(url) }
            host.requestPageIcons()
            val document = host.documentRevision
            for (delay in listOf(1000L, 3000L)) host.mainHandler.postDelayed({
                if (document == host.documentRevision && host.webView.url == url) host.requestPageIcons()
            }, delay)
            CookieManager.getInstance().flush()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (!isCurrent(view)) return
            if (request.isForMainFrame) {
                host.forcedReload?.complete(false)
                host.restoreDefaultCacheModeIfNeeded()
                AppLog.error(
                    "web",
                    "加载失败 ${host.logHost(request.url?.toString())}: ${error.errorCode} ${error.description}",
                )
                val failedUrl = request.url?.toString().orEmpty()
                val description = error.description?.toString().orEmpty()
                val insecureHttp = description.contains("CLEARTEXT", ignoreCase = true)
                host.notifyListeners { onPageError(failedUrl, error.errorCode, description, insecureHttp) }
                // Complete the navigation state even when Chromium gives us an error page.
                host.notifyListeners { onPageFinished(failedUrl) }
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (!isCurrent(view)) return
            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                host.forcedReload?.complete(false)
                host.restoreDefaultCacheModeIfNeeded()
                val failedUrl = request.url?.toString().orEmpty()
                host.notifyListeners {
                    onPageError(
                        failedUrl,
                        errorResponse.statusCode,
                        errorResponse.reasonPhrase.orEmpty(),
                        false,
                    )
                }
                // Chromium may keep the progress stream open for an HTTP error
                // document. Close the per-session loading state just as we do
                // for onReceivedError; otherwise the toolbar can spin forever.
                host.notifyListeners { onPageFinished(failedUrl) }
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            if (!isCurrent(view)) {
                runCatching { handler.cancel() }
                return
            }
            AppLog.error("web", "SSL 错误 ${host.logHost(view.url)}: ${error.primaryError}")
            // Hold the request only for an explicit, one-shot user decision. A timeout
            // prevents a renderer/network request from waiting forever on a dead dialog.
            host.pendingSsl?.let { runCatching { it.cancel() } }
            host.pendingSsl = handler
            val url = view.url.orEmpty()
            val cancel = {
                if (host.pendingSsl === handler) {
                    host.pendingSsl = null
                    runCatching { handler.cancel() }
                }
            }
            val proceed = {
                if (host.pendingSsl === handler) {
                    host.pendingSsl = null
                    runCatching { handler.proceed() }
                }
            }
            host.notifyListeners { onSslError(url, error.primaryError.toString(), proceed, cancel) }
            host.mainHandler.postDelayed(cancel, ShellWebViewLimits.SSL_DECISION_TIMEOUT_MS)
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
            if (!LocalWebHost.isAllowedLocalUrl(url, host.config.localAppId, request.isForMainFrame, host.lastCommittedUrl)) {
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
            return host.assetLoader.loader.shouldInterceptRequest(request.url)
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: android.webkit.RenderProcessGoneDetail,
        ): Boolean {
            if (!isCurrent(view)) return true
            // 官方建议：不让宿主进程陪葬。Replace only the child WebView so the
            // Compose host and its session listeners remain attached.
            if (!host.recoveryInProgress) {
                if (host.rendererRecoveryGate.tryBeginRecovery()) host.recoverRenderer(view.url)
                else host.failRendererRecovery(view.url)
            }
            return true
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, authHost: String, realm: String?) {
            if (!isCurrent(view)) {
                handler.cancel()
                return
            }
            // Credentials are never collected or cached by the shell. Cancel the
            // request and surface a clear state so the user can use an external
            // browser/password manager if needed.
            handler.cancel()
            host.notifyListeners { onHttpAuthRequested(authHost, realm) { _, _ -> } }
        }

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
            if (!isCurrent(view)) {
                request.cancel()
                return
            }
            request.cancel()
            host.notifyListeners { onClientCertificateRequested(request.host) { } }
        }
    }

