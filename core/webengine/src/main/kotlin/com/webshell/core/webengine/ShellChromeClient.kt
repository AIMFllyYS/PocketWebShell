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

internal class ShellChromeClient(
    private val host: ShellWebView,
    private val generation: Long,
) : WebChromeClient() {

        private fun isCurrent(view: WebView): Boolean =
            generation == host.callbackGeneration && view === host.webView

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (!isCurrent(view)) return
            host.notifyListeners { onProgress(newProgress) }
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            if (!isCurrent(view)) return
            host.notifyListeners { onTitleReceived(title) }
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap) {
            if (!isCurrent(view)) return
            host.notifyListeners { onIconReceived(view.url.orEmpty(), icon) }
        }

        override fun onReceivedTouchIconUrl(view: WebView, url: String, precomposed: Boolean) {
            if (!isCurrent(view) || url.isBlank()) return
            host.notifyListeners { onIconUrl(url); onIconCandidates(view.url.orEmpty(), listOf(url)) }
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
            if (host.listener == null && host.sessionListener == null) return false
            val transport = view.WebViewTransport()
            if (host.config.newWindowPolicy == ShellConfig.NewWindowPolicy.REPLACE_IN_SHELL) {
                // User opted to stay inside this site shell. Same-view navigation
                // has no back stack after we clear it; OAuth/pay popups should
                // keep the default adopt policy.
                transport.setWebView(host.webView)
                host.pendingClearHistory = true
                host.notifyListeners {
                    onNewWindow(
                        NewWindowRequest(
                            sourceSessionId = host.sessionId,
                            sourceUrl = view.url,
                            isUserGesture = isUserGesture,
                            isDialog = isDialog,
                            initialUrl = view.hitTestResult.extra,
                            targetSessionId = host.sessionId,
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
            WebViewPool.protect(host.sessionId, WebViewPool.ProtectionReason.PENDING_WINDOW)
            val target = try {
                runCatching {
                    WebViewPool.getOrCreate(view.context, targetSessionId) {
                        host.config.copy(
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
                WebViewPool.unprotect(host.sessionId, WebViewPool.ProtectionReason.PENDING_WINDOW)
            }
            if (target == null) return false
            transport.setWebView(target.webView)
            val request = NewWindowRequest(
                sourceSessionId = host.sessionId,
                sourceUrl = view.url,
                isUserGesture = isUserGesture,
                isDialog = isDialog,
                initialUrl = view.hitTestResult.extra,
                targetSessionId = targetSessionId,
            )
            // Owner must bind target.sessionListener before Chromium receives
            // the transport; otherwise the first onPageStarted is lost and the
            // adopted tab stays about:blank forever.
            host.notifyListeners { onNewWindow(request) }
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
            chooserView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            if (generation != host.callbackGeneration || chooserView !== host.webView || host.listener == null) {
                filePathCallback.onReceiveValue(null)
                return true
            }
            host.notifyListeners { onFileChooserRequested(fileChooserParams, filePathCallback) }
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            if (generation != host.callbackGeneration || host.listener == null) {
                request.deny()
                return
            }
            host.notifyListeners { onPermissionRequested(request) }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (generation != host.callbackGeneration) return
            host.notifyListeners { onPermissionCanceled(request) }
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: android.webkit.GeolocationPermissions.Callback,
        ) {
            if (generation != host.callbackGeneration) {
                callback.invoke(origin, false, false)
                return
            }
            host.notifyListeners { onGeolocationPrompt(origin) { allow, retain ->
                callback.invoke(origin, allow, retain)
            } }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (generation != host.callbackGeneration) {
                callback.onCustomViewHidden()
                return
            }
            host.customView?.let { host.notifyListeners { onHideCustomView() } }
            host.customView = view
            host.notifyListeners { onShowCustomView(view) { callback.onCustomViewHidden(); host.hideCustomView() } }
        }

        override fun onHideCustomView() {
            if (generation != host.callbackGeneration) return
            host.hideCustomView()
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
            host.listener?.let { ask(it); return true }
            host.armBackgroundDialogFallback({ complete.respond(null) }) { late -> ask(late) }
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
            host.listener?.let { deliver(it, complete); return true }
            host.armBackgroundDialogFallback({ complete.respond(safeDefaultAccepted) }) { late ->
                deliver(late, complete)
            }
            return true
        }
    }

