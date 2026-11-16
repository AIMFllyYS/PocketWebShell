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

internal class ShellBlobTransfer(private val host: ShellWebView) {
    private var blobRequestToken = 0L
    private var activeBlobToken: Long? = null
    private var blobOut: java.io.FileOutputStream? = null
    private var blobFile: java.io.File? = null
    private var blobOffset = 0
    private var blobTotal = 0

    /** Renderer replacement: abort the in-flight blob, then invalidate its callback token. */
    fun invalidate(reason: String) {
        cancelActiveBlob(reason)
        blobRequestToken++
    }

    /** Session release: drop protection without treating the transfer as a user-visible failure. */
    fun detach() {
        blobRequestToken++
        if (activeBlobToken != null) endBlobDownload()
    }

    fun onMainFrameNavigated(url: String) {
        if (url.startsWith("blob:", ignoreCase = true) || url.startsWith("data:", ignoreCase = true)) return
        if (activeBlobToken != null) {
            com.webshell.core.model.AppLog.log("download", "页内下载因导航取消 host=${host.logHost(url)}")
        }
        cancelActiveBlob("cancelled")
        blobRequestToken++
    }

    /**
     * Read a blob from the current document in Binder-safe chunks. No JavaScript
     * bridge is exposed to the origin — each slice comes back through evaluateJavascript.
     */
    fun download(url: String) {
        val token = ++blobRequestToken
        val generation = host.callbackGeneration
        beginBlobDownload(token)
        val quoted = org.json.JSONObject.quote(url)
        val script = """
            (async function(){
              try {
                const b = await fetch($quoted).then(r => r.blob());
                if (b.size > ${ShellWebViewLimits.MAX_BLOB_BYTES}) return 'ERR:too-large';
                window.__wsDl = window.__wsDl || {};
                window.__wsDl['$token'] = b;
                return 'META:' + b.size + ':' + (b.type || '');
              } catch(e) { return 'ERR:' + (e && e.message ? e.message : 'blob-failed'); }
            })()
        """.trimIndent()
        runCatching {
            host.webView.evaluateJavascript(script) { raw ->
                if (!blobStillActive(token, generation)) return@evaluateJavascript
                val meta = parseBlobMeta(raw)
                if (meta == null) {
                    val parsed = parseBlobEvaluation(raw)
                    abortBlob(token, (parsed as? BlobDownloadParseResult.Failure)?.reason ?: "blob-failed")
                    return@evaluateJavascript
                }
                val (size, mime) = meta
                if (size > ShellWebViewLimits.MAX_BLOB_BYTES) {
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
                const slice = b.slice($blobOffset, ${blobOffset + ShellWebViewLimits.BLOB_CHUNK_BYTES});
                const a = new Uint8Array(await slice.arrayBuffer());
                let s=''; for(let i=0;i<a.length;i+=0x8000) s += String.fromCharCode(...a.subarray(i,i+0x8000));
                return 'CHUNK:' + btoa(s);
              } catch(e) { return 'ERR:' + (e && e.message ? e.message : 'blob-failed'); }
            })()
        """.trimIndent()
        host.webView.evaluateJavascript(script) { raw ->
            if (!blobStillActive(token, generation)) return@evaluateJavascript
            val encoded = parseBlobChunk(raw)
            if (encoded == null) {
                abortBlob(token, decodeBlobError(raw))
                return@evaluateJavascript
            }
            if (encoded.length > ShellWebViewLimits.MAX_BLOB_BASE64_CHARS) {
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
        val out = File(host.context.cacheDir, "downloads/$fileName")
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
            host.notifyListeners { onDownloadFailed("blob-write-failed") }
            return
        }
        runCatching {
            val shareUri = FileProvider.getUriForFile(
                host.context,
                "${host.context.packageName}.fileprovider",
                out,
            )
            WebViewPool.downloadSink?.completeBlob(out.name, shareUri)
            host.notifyListeners { onDownloadFinished(out.name, shareUri) }
        }.onFailure {
            runCatching { out.delete() }
            host.notifyListeners { onDownloadFailed(it.message ?: "blob-write-failed") }
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
            host.notifyListeners { onDownloadFailed(reason) }
        }
    }

    private fun cleanupBlobJs(token: Long) {
        host.webView.evaluateJavascript(
            "(function(){try{if(window.__wsDl)delete window.__wsDl['$token'];}catch(e){}})()",
            null,
        )
    }

    private fun blobStillActive(token: Long, generation: Long): Boolean {
        if (token == blobRequestToken && generation == host.callbackGeneration && activeBlobToken == token) {
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
        WebViewPool.protect(host.sessionId, WebViewPool.ProtectionReason.PENDING_DOWNLOAD)
    }

    private fun endBlobDownload() {
        activeBlobToken = null
        WebViewPool.unprotect(host.sessionId, WebViewPool.ProtectionReason.PENDING_DOWNLOAD)
    }

    private fun cancelActiveBlob(reason: String) {
        val token = activeBlobToken ?: return
        abortBlob(token, reason)
    }

}
