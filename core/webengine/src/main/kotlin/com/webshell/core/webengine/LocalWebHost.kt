package com.webshell.core.webengine

import android.content.Context
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileInputStream
import android.webkit.WebResourceResponse
import java.net.URI
import java.net.URLConnection

/**
 * 本地 HTML 宿主：把打包在 assets 以及用户导入到内部存储的 HTML 以真实 https 源提供，
 * 解决 file:// 的 CORS/ServiceWorker 限制。URL 形如
 * https://appassets.androidplatform.net/assets/<path>（打包资源）
 * https://appassets.androidplatform.net/local/<appId>/<path>（导入的本地应用）
 */
object LocalWebHost {

    const val HOST: String = "appassets.androidplatform.net"
    const val ASSET_PREFIX: String = "/assets/"

    /** 导入的本地应用挂载在域名的 /local/ 前缀下 */
    const val LOCAL_PREFIX: String = "/local/"

    /** 导入文件在内部存储中的根目录名（filesDir/localapps/<appId>/...） */
    const val LOCAL_APPS_DIR: String = "localapps"

    /** 本地导入应用的虚拟 scheme（持久化层使用，渲染时映射到 LOCAL_PREFIX） */
    const val LOCAL_SCHEME: String = "local"

    /**
     * Build a loader for one session. A null [allowedLocalAppId] intentionally
     * means that no imported-local-app directory is readable; this is the safe
     * default for browser/direct sessions. The packaged `/assets/` tree is still
     * handled by AndroidX's canonical asset handler.
     */
    fun createLoader(context: Context, allowedLocalAppId: String? = null): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .addPathHandler(ASSET_PREFIX, WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler(
                LOCAL_PREFIX,
                LocalAppPathHandler(context, allowedLocalAppId),
            )
            .build()

    fun isLocalUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && uri.host.equals(HOST, ignoreCase = true)
            && uri.userInfo == null && uri.port == -1
    }.getOrDefault(false)

    /**
     * Return the imported app id encoded by an AssetLoader URL, but only when
     * the URL has a safe app-relative path. This deliberately uses the encoded
     * path and validates its decoded form so `%2f`, `%5c`, and encoded `..`
     * cannot bypass the boundary check.
     */
    fun localAppIdFromHttpsUrl(url: String): String? = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals(HOST, ignoreCase = true)
        ) return@runCatching null
        val encodedPath = uri.rawPath ?: return@runCatching null
        if (!encodedPath.startsWith(LOCAL_PREFIX)) return@runCatching null
        val clean = encodedPath.removePrefix(LOCAL_PREFIX).trimStart('/')
        val separator = clean.indexOf('/')
        if (separator <= 0 || separator == clean.lastIndex) return@runCatching null
        val appId = clean.substring(0, separator)
        val relative = clean.substring(separator + 1)
        appId.takeIf { isSafeLocalPath(it, relative) }
    }.getOrNull()

    /**
     * Check both the host/path shape and the session's directory capability.
     * `/assets/…` is a packaged resource and is not tied to an imported app;
     * `/local/…` requires an exact app-id match.
     *
     * Cookie sharing must never widen local-file access. [allowedLocalAppId] is
     * a *session* capability that can outlive the page that used it: once the
     * main document navigates to a remote origin, the session must not keep
     * serving `/local/<appId>/…` to whatever that remote page's iframes or
     * `<script src>` tags request. [isMainFrame] and [documentUrl] make that
     * boundary explicit:
     * - a main-frame request (a real navigation, including into the same
     *   imported app) only needs the requested/allowed app-id match;
     * - a subresource request additionally requires the *current top
     *   document* to still resolve to the very same imported app.
     */
    fun isAllowedLocalUrl(
        url: String,
        allowedLocalAppId: String?,
        isMainFrame: Boolean = true,
        documentUrl: String? = null,
    ): Boolean {
        if (!isLocalUrl(url)) return false
        val path = runCatching { URI(url).rawPath.orEmpty() }.getOrDefault("")
        if (path.startsWith(ASSET_PREFIX)) return true
        val requestedAppId = localAppIdFromHttpsUrl(url) ?: return false
        if (allowedLocalAppId == null || requestedAppId != allowedLocalAppId) return false
        if (isMainFrame) return true
        return documentUrl != null && localAppIdFromHttpsUrl(documentUrl) == allowedLocalAppId
    }

    /**
     * 持久化层的本地应用 URL：local://<appId>/index.html
     * 渲染层（M4/M5）需先经 [toHttpsUrl] 映射为 AssetLoader 的 https 地址。
     */
    fun buildLocalAppUrl(appId: String, fileName: String = "index.html"): String {
        require(isSafeLocalPath(appId, fileName)) { "Unsafe local app path" }
        val encodedPath = fileName.trimStart('/').split('/').joinToString("/") { encodePathSegment(it) }
        return "$LOCAL_SCHEME://${encodePathSegment(appId)}/$encodedPath"
    }

    fun isLocalAppUrl(url: String): Boolean =
        url.regionMatches(0, "$LOCAL_SCHEME://", 0, "$LOCAL_SCHEME://".length, ignoreCase = true)

    fun localAppId(url: String): String? {
        if (!isLocalAppUrl(url)) return null
        return url.substring("$LOCAL_SCHEME://".length).substringBefore('/').takeIf { it.isNotBlank() }
    }

    /** local://<appId>/<file> → https://appassets.androidplatform.net/local/<appId>/<file> */
    fun toHttpsUrl(url: String): String {
        if (!isLocalAppUrl(url)) return url
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val appId = uri.host ?: return url
        val rawPath = uri.rawPath ?: return url
        val relative = rawPath.trimStart('/')
        if (!isSafeLocalPath(appId, relative) || relative.isBlank()) return url
        return buildString {
            append("https://$HOST$LOCAL_PREFIX$appId/")
            append(relative)
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }

    /** 导入应用在内部存储中的目录 */
    fun localAppDir(context: Context, appId: String): File =
        File(File(context.filesDir, LOCAL_APPS_DIR), appId)

    /** Pure boundary check shared by tests and the path handler. */
    fun isSafeLocalPath(appId: String, path: String): Boolean {
        if (appId.isBlank() || !isSafeSegment(appId)) return false
        val clean = path.trimStart('/')
        val parts = clean.split('/')
        return parts.isNotEmpty() && parts.none { !isSafeSegment(it) }
    }

    private fun isSafeSegment(segment: String): Boolean {
        if (segment.isEmpty()) return false
        val decoded = runCatching {
            java.net.URLDecoder.decode(segment, Charsets.UTF_8.name())
        }.getOrDefault(segment)
        return decoded != "." && decoded != ".." &&
            decoded.none { it == '/' || it == '\\' || it.isISOControl() }
    }

    private fun encodePathSegment(segment: String): String =
        java.net.URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

    /**
     * Stream a local file without buffering it into a [ByteArray].
     * [File.length] supplies Content-Length; the caller owns [LocalAppFileServe.data].
     */
    internal fun serveLocalAppFile(file: File): LocalAppFileServe? {
        if (!file.isFile) return null
        val mime = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        val encoding = if (
            mime.startsWith("text/") || mime == "application/javascript" || mime == "application/json"
        ) "UTF-8" else null
        val contentType = if (encoding != null) "$mime; charset=$encoding" else mime
        val headers = mapOf(
            "Content-Length" to file.length().toString(),
            "Content-Type" to contentType,
        )
        return runCatching {
            LocalAppFileServe(
                mimeType = mime,
                encoding = encoding,
                statusCode = 200,
                reasonPhrase = "OK",
                headers = headers,
                data = FileInputStream(file),
            )
        }.getOrNull()
    }

    internal data class LocalAppFileServe(
        val mimeType: String,
        val encoding: String?,
        val statusCode: Int,
        val reasonPhrase: String,
        val headers: Map<String, String>,
        val data: java.io.InputStream,
    )

    /**
     * The stock InternalStoragePathHandler protects against `..` escaping its
     * root, but a shared `/local/` root would still allow app-A to address
     * app-B by naming B's first path segment. Resolve the app id first and
     * then enforce the canonical child path stays below that app directory.
     */
    private class LocalAppPathHandler(
        context: Context,
        allowedLocalAppId: String?,
    ) : WebViewAssetLoader.PathHandler {
        private val root = File(context.filesDir, LOCAL_APPS_DIR).canonicalFile
        private val allowedAppId = allowedLocalAppId?.takeIf { isSafeSegment(it) }

        override fun handle(path: String): WebResourceResponse? {
            val clean = path.trimStart('/')
            val appId = clean.substringBefore('/', missingDelimiterValue = "")
            val relative = clean.substringAfter('/', missingDelimiterValue = "")
            // The URL path is shared by every WebView in the process. Directory
            // canonicalisation alone prevents traversal, but would still let
            // app-A name app-B. Require the capability of this session first.
            if (allowedAppId == null || appId != allowedAppId) return null
            if (!isSafeLocalPath(appId, relative) || relative.isBlank()) return null
            val appRoot = File(root, appId).canonicalFile
            if (!isWithin(appRoot, root)) return null
            val target = File(appRoot, relative).canonicalFile
            if (!isWithin(target, appRoot) || !target.isFile) return null
            val serve = serveLocalAppFile(target) ?: return null
            return runCatching {
                WebResourceResponse(
                    serve.mimeType,
                    serve.encoding,
                    serve.statusCode,
                    serve.reasonPhrase,
                    serve.headers,
                    serve.data,
                )
            }.getOrElse {
                runCatching { serve.data.close() }
                null
            }
        }

        private fun isWithin(child: File, parent: File): Boolean {
            val prefix = parent.path + File.separator
            return child.path == parent.path || child.path.startsWith(prefix)
        }

    }
}
