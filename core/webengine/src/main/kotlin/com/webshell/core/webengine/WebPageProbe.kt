package com.webshell.core.webengine

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One bounded renderer for an explicit URL-add/refresh request, never one per launcher cell. */
@Singleton
class WebPageProbe @Inject constructor(@ApplicationContext private val context: Context) {
    private val gate = kotlinx.coroutines.sync.Mutex()
    suspend fun <T> withDataLock(block: suspend () -> T): T {
        gate.lock()
        return try { block() } finally { gate.unlock() }
    }
    data class Page(val url: String, val title: String, val icon: Bitmap?, val candidates: List<String>, val offlineReset: Boolean? = null)

    suspend fun fetch(url: String, resetOffline: Boolean = false, beforeLoad: () -> Unit = {}): Page? = withDataLock {
        try { withContext(Dispatchers.Main.immediate) {
            if (UrlRouter.classify(url).route != UrlRoute.WEB) return@withContext null
            beforeLoad()
            val view = WebView(context)
            var finished = false
            var failed = false
            var icon: Bitmap? = null
            var candidates = emptyList<String>()
            try {
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                view.settings.allowFileAccess = false
                view.settings.allowContentAccess = false
                view.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                view.settings.javaScriptCanOpenWindowsAutomatically = false
                view.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                view.webChromeClient = object : WebChromeClient() {
                    override fun onReceivedIcon(view: WebView, bitmap: Bitmap) { icon = bitmap }
                }
                view.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        UrlRouter.classify(request.url.toString()).route != UrlRoute.WEB
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { finished = false; failed = false; icon = null }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                        if (request.isForMainFrame) { failed = true; finished = true }
                    }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                        if (request.isForMainFrame && response.statusCode >= 400) { failed = true; finished = true }
                    }
                    override fun onPageFinished(view: WebView, url: String) { finished = true }
                }
                if (resetOffline) {
                    // An inert same-origin document prevents site scripts from recreating caches
                    // while deletion is running. No remote page or Android bridge is needed.
                    view.loadDataWithBaseURL(url, "<html><head></head><body></body></html>", "text/html", "UTF-8", url)
                } else view.loadUrl(url)
                withTimeoutOrNull(15_000) {
                    while (!finished) delay(100)
                    if (failed) return@withTimeoutOrNull null
                    delay(600) // Allow the native favicon and initial client-rendered head to settle.
                    var inspected = false
                    view.evaluateJavascript(PageIconDeclarations.script) {
                        candidates = PageIconDeclarations.parse(it); inspected = true
                    }
                    while (!inspected) delay(25)
                    val reset = if (resetOffline && sameWebOrigin(url, view.url.orEmpty())) {
                        val key = "__wsReset" + java.util.UUID.randomUUID().toString().replace("-", "")
                        // Cache deletion decides the result; it is the part we can verify. Service-worker
                        // cleanup stays best-effort: this inert document is same-origin and secure, yet
                        // Chromium rejects getRegistrations() with InvalidStateError because the document
                        // is not a real navigation target. Awaiting it in the same Promise.all rejected the
                        // whole chain, so a successful cache purge reported failure and made the caller
                        // (StorageViewModel.resetOffline) show a false "cleanup failed" toast.
                        view.evaluateJavascript(
                            "window['$key']='pending';(function(){var k='$key';function d(v){window[k]=v;}" +
                                "try{caches.keys().then(function(ks){return Promise.all(ks.map(function(c){return caches.delete(c);}));})" +
                                ".then(function(){return caches.keys();}).then(function(left){" +
                                "try{navigator.serviceWorker.getRegistrations().then(function(rs){return Promise.all(rs.map(function(r){return r.unregister();}));},function(){});}catch(e){}" +
                                "d(left&&left.length===0?'done':'failed');},function(){d('failed');});" +
                                "}catch(e){d('failed');}})();void 0",
                            null,
                        )
                        var state = "pending"
                        withTimeoutOrNull(5000) {
                            while (state == "pending") {
                                view.evaluateJavascript("window['$key']") { if (it == "\"done\"") state = "done" else if (it == "\"failed\"") state = "failed" }
                                delay(100)
                            }
                        }
                        state == "done"
                    } else if (resetOffline) false else null
                    Page(view.url ?: url, view.title.orEmpty(), icon ?: view.favicon, candidates, reset)
                }
            } finally {
                view.stopLoading(); view.webChromeClient = null
                view.webViewClient = WebViewClient(); view.destroy()
            }
        } } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
}

internal object PageIconDeclarations {
    const val script = "JSON.stringify(Array.from(document.querySelectorAll('link[rel]')).filter(e=>e.rel.split(/\\s+/).some(r=>['icon','apple-touch-icon','apple-touch-icon-precomposed'].includes(r.toLowerCase()))).map(e=>e.href).filter(h=>h.length<=350000).slice(0,16))"
    fun parse(raw: String): List<String> = runCatching {
        val text = org.json.JSONTokener(raw).nextValue() as? String ?: return@runCatching emptyList<String>()
        val array = org.json.JSONArray(text)
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
}

/** DNS names are compared as origins, never as suffixes or raw URL prefixes. */
fun sameWebOrigin(a: String, b: String): Boolean = runCatching {
    fun origin(raw: String): Triple<String, String, Int> {
        val u = java.net.URI(raw)
        val scheme = u.scheme.lowercase()
        require(scheme == "https" || scheme == "http")
        return Triple(scheme, u.host.lowercase(), if (u.port != -1) u.port else if (scheme == "https") 443 else 80)
    }
    origin(a) == origin(b)
}.getOrDefault(false)
