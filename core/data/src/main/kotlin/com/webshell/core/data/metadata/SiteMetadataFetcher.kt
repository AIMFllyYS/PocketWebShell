package com.webshell.core.data.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.InetAddress

/** 抓取到的站点元数据 */
data class SiteMetadata(
    val title: String,
    val iconUrl: String?,
    val themeColor: String?,
    val finalUrl: String,
)

/**
 * 单个站点的元数据抓取器：HTML → 标题 / 主题色 / 图标。
 * 纯 JVM（OkHttp + jsoup + org.json），可注入 OkHttpClient 便于测试与全局复用。
 * 位于 core/data：feature/add（新增）与 feature/home（强制刷新）共用。
 */
class SiteMetadataFetcher @javax.inject.Inject constructor(
    private val client: OkHttpClient,
) {
    private val noRedirectClient: OkHttpClient by lazy {
        client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

    suspend fun fetch(url: String): Result<SiteMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val (doc, finalUrl) = getDocument(url)
            val title = doc.title().trim()
                .ifBlank { doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty() }
                .ifBlank { hostLabel(finalUrl) }
            val themeColor = doc.selectFirst("meta[name=theme-color]")?.attr("content")?.trim()
                ?.takeIf { it.isNotEmpty() }
            val iconUrl = pickBestIcon(finalUrl, doc) ?: fallbackIconUrl(finalUrl)
            SiteMetadata(
                title = title,
                iconUrl = iconUrl,
                themeColor = themeColor,
                finalUrl = finalUrl,
            )
        }
    }

    private fun getDocument(url: String): Pair<Document, String> {
        var current = validatePublicHttpUrl(url)
        var redirects = 0
        while (true) {
            val response = noRedirectClient.newCall(request(current)).execute()
            response.use { resp ->
                if (resp.isRedirect) {
                    check(redirects++ < MAX_REDIRECTS) { "Too many redirects" }
                    val location = resp.header("Location") ?: error("Redirect without location")
                    current = validatePublicHttpUrl(URI(current).resolve(location).toString())
                    return@use
                }
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                val type = resp.header("Content-Type").orEmpty().lowercase()
                check(type.isBlank() || type.contains("text/html") || type.contains("application/xhtml+xml")) {
                    "Non-HTML response"
                }
                val body = checkNotNull(resp.body) { "Empty response body" }
                val bytes = readBounded(body.byteStream(), MAX_HTML_BYTES)
                return Jsoup.parse(bytes.inputStream(), null, current) to current
            }
        }
    }

    private fun request(url: String): Request =
        Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/*;q=0.8,*/*;q=0.5")
            .build()

    private fun hostLabel(url: String): String =
        runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url

    // ===== 图标挑选（页面声明 → 常用站点备份 → Google s2 标签页图标）=====

    /**
     * 从已解析 HTML 按优先级列出图标地址（尚未做公网校验）。
     * 顺序：apple-touch / 较大的 raster link → manifest → 普通 icon → /favicon.ico。
     */
    fun rankDocumentIcons(pageUrl: String, doc: Document): List<String> {
        val ranked = ArrayList<Pair<Int, String>>()
        fun add(url: String, score: Int) {
            if ((url.startsWith("http://", true) || url.startsWith("https://", true)) &&
                ranked.none { it.second == url }
            ) {
                ranked += score to url
            }
        }
        for ((href, sizes) in linkCandidates(doc, "apple-touch-icon", pageUrl) +
            linkCandidates(doc, "apple-touch-icon-precomposed", pageUrl)
        ) {
            add(href, 400 + parseIconSize(sizes) + formatBonus(href))
        }
        for ((href, sizes) in linkCandidates(doc, "icon", pageUrl)) {
            add(href, 200 + parseIconSize(sizes) + formatBonus(href))
        }
        add(resolveUrl(pageUrl, DEFAULT_FAVICON_PATH), 50)
        wellKnownIconUrl(pageUrl)?.let { add(it, 80) }
        return ranked.sortedByDescending { it.first }.map { it.second }
    }

    private fun pickBestIcon(finalUrl: String, doc: Document): String? {
        val declared = rankDocumentIcons(finalUrl, doc).toMutableList()
        linkCandidates(doc, "manifest", finalUrl).firstOrNull()?.let { (manifestUrl, _) ->
            manifestIconCandidates(manifestUrl)?.let { icons ->
                chooseBestManifestIcon(icons)?.let { src ->
                    declared.add(0, resolveUrl(finalUrl, src))
                }
            }
        }
        declared.forEach { candidate ->
            sanitizeIconUrl(candidate)?.let { return it }
        }
        return fallbackIconUrl(finalUrl)
    }

    /** HTML 抓不到或地址不可用时，用 Google 公开的标签页图标接口（返回 PNG）。 */
    fun fallbackIconUrl(pageUrl: String): String? {
        val host = runCatching { URI(pageUrl.trim()).host }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        wellKnownIconUrl(pageUrl)?.let { known ->
            sanitizeIconUrl(known)?.let { return it }
        }
        return sanitizeIconUrl("https://www.google.com/s2/favicons?domain=$host&sz=128")
    }

    /** 展示层兜底：不访问 DNS，只拼公开图标地址。 */
    fun displayFallbackIconUrl(pageUrl: String): String? {
        wellKnownIconUrl(pageUrl)?.let { return it }
        val host = runCatching { URI(pageUrl.trim()).host }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        return "https://www.google.com/s2/favicons?domain=$host&sz=128"
    }

    /** 仅接受 http(s) 图标地址；data:/blob: 等伪 URL 一律视为无图标 */
    private fun sanitizeIconUrl(url: String): String? =
        url.takeIf {
            (it.startsWith("http://") || it.startsWith("https://")) &&
                runCatching { validatePublicHttpUrl(it) }.isSuccess
        }

    /** rel 关键字匹配（rel 属性是多值空格分隔列表，如 "shortcut icon"） */
    private fun linkCandidates(doc: Document, relKeyword: String, baseUrl: String): List<Pair<String, String>> =
        doc.select("link[rel]").mapNotNull { el ->
            val rels = el.attr("rel").lowercase().split(Regex("\\s+"))
            if (relKeyword !in rels) return@mapNotNull null
            val href = el.attr("abs:href").ifBlank { resolveUrl(baseUrl, el.attr("href")) }
            href.takeIf { it.isNotEmpty() }?.let { it to el.attr("sizes") }
        }

    /** 拉取 web manifest 并把 icons[] 转为 map 列表；任何失败都安静降级为 null */
    private fun manifestIconCandidates(manifestUrl: String): List<Map<String, String>>? = runCatching {
        val response = noRedirectClient.newCall(request(validatePublicHttpUrl(manifestUrl))).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return null
            val type = resp.header("Content-Type").orEmpty().lowercase()
            if (type.isNotBlank() && !type.contains("json")) return null
            val body = resp.body
            val icons = JSONObject(String(readBounded(body.byteStream(), MAX_MANIFEST_BYTES), Charsets.UTF_8))
                .optJSONArray("icons") ?: return null
            iconsToCandidates(icons)
        }
    }.getOrNull()

    private fun iconsToCandidates(icons: JSONArray): List<Map<String, String>> =
        (0 until icons.length())
            .mapNotNull { icons.optJSONObject(it) }
            .map { obj ->
                mapOf(
                    "src" to obj.optString("src"),
                    "sizes" to obj.optString("sizes"),
                    "purpose" to obj.optString("purpose"),
                )
            }
            .filter { it.getValue("src").isNotBlank() }

    /**
     * 纯函数：从 manifest icons[]（map 列表）里挑最合适的图标，返回 src 原值（未相对解析）。
     * 规则：purpose 缺省/含 any/maskable 才可用；有效尺寸 ≥ [MIN_ICON_SIDE]px 优先进入候选；
     * 同组内优先 512px（声明 512 的排最前），再按最大边长取最大。
     * sizes 缺省（未知尺寸）保留为兜底候选，排序时按 0 处理。
     */
    fun chooseBestManifestIcon(icons: List<Map<String, String>>?): String? {
        if (icons.isNullOrEmpty()) return null
        data class Candidate(val src: String, val anyPurpose: Boolean, val maxSide: Int)

        val candidates = icons.mapNotNull { icon ->
            val src = icon["src"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val purpose = icon["purpose"]?.lowercase().orEmpty()
            // purpose 缺省视为 any；maskable 有安全区但仍可做应用图标
            val anyPurpose = purpose.isEmpty() || "any" in purpose || "maskable" in purpose
            Candidate(src, anyPurpose, parseIconSize(icon["sizes"].orEmpty()))
        }
        return candidates.asSequence()
            .filter { it.anyPurpose }
            .filter { it.maxSide >= MIN_ICON_SIDE || it.maxSide == 0 }
            .maxWithOrNull(compareBy({ it.maxSide == PREFERRED_SIDE }, { it.maxSide }))
            ?.src
    }

    /** "48x48 96x96" → 96；"any" 或空 → 0（未知） */
    fun parseIconSize(sizes: String): Int =
        sizes.trim().split(Regex("\\s+"))
            .mapNotNull { token ->
                val parts = token.lowercase().split("x")
                if (parts.size == 2) parts[0].toIntOrNull() else null
            }
            .maxOrNull() ?: 0

    private fun resolveUrl(base: String, spec: String): String = try {
        URI(base).resolve(spec.replace(" ", "%20")).toString()
    } catch (_: Exception) {
        spec
    }

    /** Public for deterministic unit tests and for callers that preflight icon URLs. */
    fun validatePublicHttpUrl(raw: String): String {
        val value = raw.trim()
        val uri = URI(value)
        check(uri.scheme?.lowercase() in setOf("http", "https")) { "Unsupported URL scheme" }
        check(uri.host.isNullOrBlank().not() && uri.userInfo == null && uri.port in -1..65535) { "Invalid URL" }
        check(!value.any { it.isISOControl() || it.isWhitespace() }) { "Invalid URL characters" }
        val addresses = runCatching { InetAddress.getAllByName(uri.host) }.getOrElse { emptyArray() }
        check(addresses.isNotEmpty()) { "Unresolvable host" }
        check(addresses.none { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress }) {
            "Private address blocked"
        }
        return uri.toASCIIString()
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        input.use { stream ->
            val out = java.io.ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                check(total <= maxBytes) { "Response body too large" }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private companion object {
        const val DEFAULT_FAVICON_PATH = "/favicon.ico"
        const val MIN_ICON_SIDE = 129
        const val PREFERRED_SIDE = 512
        const val MAX_HTML_BYTES = 2 * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 512 * 1024
        const val MAX_REDIRECTS = 5

        val WELL_KNOWN_ICONS = mapOf(
            "github.com" to "https://github.com/fluidicon.png",
            "wikipedia.org" to "https://www.wikipedia.org/static/apple-touch/wikipedia.png",
            "youtube.com" to "https://www.youtube.com/s/desktop/f82dea74/img/favicon_144x144.png",
            "google.com" to "https://www.google.com/images/branding/product/2x/googleg_96dp.png",
            "bilibili.com" to "https://www.bilibili.com/favicon.ico",
        )

        fun wellKnownIconUrl(pageUrl: String): String? {
            val host = runCatching { URI(pageUrl.trim()).host }.getOrNull()
                ?.lowercase()?.removePrefix("www.") ?: return null
            WELL_KNOWN_ICONS[host]?.let { return it }
            return WELL_KNOWN_ICONS.entries.firstOrNull { host.endsWith(".${it.key}") }?.value
        }

        fun formatBonus(url: String): Int {
            val path = url.substringAfterLast('/').substringBefore('?').lowercase()
            return when {
                path.endsWith(".png") || path.endsWith(".webp") ||
                    path.endsWith(".jpg") || path.endsWith(".jpeg") -> 80
                path.endsWith(".ico") -> 10
                path.endsWith(".svg") -> -20
                else -> 0
            }
        }
    }
}
