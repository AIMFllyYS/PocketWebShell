package com.webshell.core.webengine.storage

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import com.webshell.core.model.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 共享 Default Profile 上的按站证据探测。Cookie / 配额必须在主线程询问 WebView；
 * IndexedDB 目录反查是纯函数，可在 IO 线程调用。
 */
@Singleton
class SiteStorageProbe @Inject constructor(
    @Suppress("UNUSED_PARAMETER") @ApplicationContext context: Context,
) {

    fun capabilities(): SiteProbeCapabilities = SiteProbeCapabilities(
        deleteForSite = featureSupported(WebViewFeature.DELETE_BROWSING_DATA),
        cookieInfo = featureSupported(WebViewFeature.GET_COOKIE_INFO),
        httpCacheQuota = featureSupported(WebViewFeature.HTTP_CACHE_MANAGER),
    )

    /**
     * 对 [url] 的 http 与 https 各查一次（Secure cookie 只匹配 https）。
     * 日志只记 host / 条数 / 字节，不记 Cookie 名或值。
     */
    suspend fun cookieEvidence(url: String): Metric<CookieFacts> = withContext(Dispatchers.Main.immediate) {
        val host = hostOfUrl(url)
        if (host.isNullOrBlank()) return@withContext Metric.Unavailable
        val facts = runCatching { collectCookieFacts(cookieProbeUrls(url, host)) }
            .onFailure { AppLog.warn(TAG, "Cookie 探测失败: $host") }
            .getOrNull()
            ?: return@withContext Metric.Unavailable
        AppLog.log(TAG, "Cookie 探测: host=$host count=${facts.count} bytes=${facts.serializedBytes}")
        if (facts.count == 0) Metric.Absent else Metric.Measured(facts, exact = false)
    }

    /**
     * 主线程 `WebStorage.getOrigins`，约 3s 超时返回 null（没问到）。
     * 空 Map 表示问到了、确实没有配额型存储。按 `URI(origin).host` 聚合（分区存储同 origin 多行）。
     */
    suspend fun quotaUsageByHost(): Map<String, Long>? = withContext(Dispatchers.Main.immediate) {
        withTimeoutOrNull(QUOTA_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    WebStorage.getInstance().getOrigins { raw ->
                        if (cont.isActive) {
                            cont.resume(aggregateQuotaByHost(raw))
                        }
                    }
                }.onFailure {
                    AppLog.warn(TAG, "配额探测失败：${it.javaClass.simpleName}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private fun collectCookieFacts(urls: List<String>): CookieFacts {
        val manager = CookieManager.getInstance()
        val useInfo = capabilities().cookieInfo
        val seen = LinkedHashMap<String, Int>()
        for (probeUrl in urls) {
            if (useInfo) {
                val lines = CookieManagerCompat.getCookieInfo(manager, probeUrl)
                for (line in lines) {
                    val name = line.substringBefore('=').trim()
                    if (name.isEmpty()) continue
                    val domain = setCookieAttribute(line, "domain").orEmpty()
                    val path = setCookieAttribute(line, "path").orEmpty()
                    seen.putIfAbsent("$name|$domain|$path", line.length)
                }
            } else {
                val header = manager.getCookie(probeUrl) ?: continue
                for (part in header.split(';')) {
                    val pair = part.trim()
                    if (pair.isEmpty()) continue
                    val name = pair.substringBefore('=')
                    if (name.isEmpty()) continue
                    seen.putIfAbsent(name, pair.length)
                }
            }
        }
        return CookieFacts(count = seen.size, serializedBytes = seen.values.sumOf { it.toLong() })
    }

    private companion object {
        const val TAG = "storage"
        const val QUOTA_TIMEOUT_MS = 3_000L
    }
}

private val INDEXED_DB_SUFFIXES = listOf(".indexeddb.leveldb", ".indexeddb.blob")
private val INDEXED_DB_SCHEMES = setOf("http", "https", "file")

/**
 * 解析 Default/IndexedDB 目录名并按 host 汇总字节。
 * 支持 `https_example.com_0.indexeddb.leveldb`、`.blob`、带端口、host 含下划线；畸形名跳过。
 */
fun indexedDbBytesByHost(profileDir: File): Map<String, Long> {
    val root = File(profileDir, "IndexedDB")
    if (!root.isDirectory) return emptyMap()
    val children = runCatching { root.listFiles() }.getOrNull() ?: return emptyMap()
    val out = LinkedHashMap<String, Long>()
    for (child in children) {
        val host = parseIndexedDbDirHost(child.name) ?: continue
        val bytes = if (child.isDirectory) {
            walkFiles(child)?.totalBytes() ?: 0L
        } else {
            child.length()
        }
        out[host] = (out[host] ?: 0L) + bytes
    }
    return out
}

internal fun parseIndexedDbDirHost(name: String): String? {
    var stem = name
    for (suffix in INDEXED_DB_SUFFIXES) {
        if (stem.endsWith(suffix)) {
            stem = stem.removeSuffix(suffix)
            break
        }
    }
    val parts = stem.split('_')
    if (parts.size < 3) return null
    val scheme = parts.first().lowercase()
    if (scheme !in INDEXED_DB_SCHEMES) return null
    if (parts.last().toIntOrNull() == null) return null
    val middle = parts.drop(1).dropLast(1)
    if (middle.isEmpty()) return null
    val hostParts = if (middle.size >= 2 && middle.last().toIntOrNull() != null) {
        middle.dropLast(1)
    } else {
        middle
    }
    if (hostParts.isEmpty()) return null
    val host = hostParts.joinToString("_").lowercase()
    return host.takeIf { it.isNotBlank() }
}

internal fun cookieProbeUrls(url: String, host: String): List<String> {
    val out = LinkedHashSet<String>()
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        out += url
    }
    out += "https://$host/"
    out += "http://$host/"
    return out.toList()
}

internal fun aggregateQuotaByHost(raw: Map<*, *>?): Map<String, Long> {
    val out = LinkedHashMap<String, Long>()
    if (raw == null) return out
    for (value in raw.values) {
        val origin = value as? WebStorage.Origin ?: continue
        val host = runCatching { URI(origin.origin).host }.getOrNull()?.lowercase() ?: continue
        if (host.isBlank()) continue
        out[host] = (out[host] ?: 0L) + origin.usage
    }
    return out
}

private fun setCookieAttribute(line: String, name: String): String? {
    val prefix = "$name="
    return line.split(';').asSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
        ?.substring(prefix.length)
}

private fun featureSupported(name: String): Boolean =
    runCatching { WebViewFeature.isFeatureSupported(name) }.getOrDefault(false)
