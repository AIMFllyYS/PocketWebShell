package com.webshell.core.data

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.svg.SvgDecoder
import coil3.toBitmap
import com.webshell.core.data.metadata.IconDataUri
import com.webshell.core.data.metadata.SiteMetadataFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request

/** Stores validated automatic icons as immutable PNG files, shared by every entry point. */
@Singleton
class SiteIconRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val metadata: SiteMetadataFetcher,
    private val apps: WebAppDao,
    private val history: HistoryDao,
    private val bookmarks: BookmarkDao,
) {
    private val latest = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    fun latest(pageUrl: String): String? = latest.value[pageUrl]
    fun observe(pageUrl: String) = latest.map { it[pageUrl] }.distinctUntilChanged()

    private val loader by lazy { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
    private val iconClient by lazy { client.newBuilder().followRedirects(false).followSslRedirects(false)
        .callTimeout(4, java.util.concurrent.TimeUnit.SECONDS).build() }
    private val sourcePreferences by lazy { context.getSharedPreferences("icon_sources", Context.MODE_PRIVATE) }

    fun markCustom(appId: String, custom: Boolean = true) {
        sourcePreferences.edit().putBoolean(appId, custom).apply()
    }

    fun canReplace(app: WebAppEntity): Boolean {
        if (sourcePreferences.getBoolean(app.id, false)) return false
        val path = app.iconUrl
        return path.isNullOrBlank() || path.startsWith("http", true) ||
            File(path).name.startsWith("auto_") || File(path).name.startsWith("extracted-favicon") ||
            (app.isLocal && path.startsWith(File(context.filesDir, "localapps/${app.id}").absolutePath + "/"))
    }

    suspend fun save(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
        val scale = minOf(1f, 512f / maxOf(bitmap.width, bitmap.height))
        val image = if (scale < 1) Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        val dir = File(context.filesDir, "icons").apply { mkdirs() }
        val target = File(dir, "pending_${UUID.randomUUID()}.png")
        try {
            target.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(target.readBytes())
                .joinToString("") { "%02x".format(it) }
            val stored = File(dir, "auto_$digest.png")
            if (stored.exists()) target.delete() else check(target.renameTo(stored))
            stored.absolutePath
        } catch (e: Exception) { target.delete(); null }
        finally { if (image !== bitmap) image.recycle() }
    }

    suspend fun resolve(candidates: List<String>): String? = kotlinx.coroutines.withTimeoutOrNull(15_000) { withContext(Dispatchers.IO) {
        for (candidate in candidates.distinct().take(16)) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            var pending: File? = null
            try {
                val bytes = if (candidate.startsWith("data:", true)) {
                    IconDataUri.decode(candidate)?.bytes ?: continue
                } else {
                    var url = candidate
                    var body: ByteArray? = null
                    for (hop in 0..5) {
                        metadata.validatePublicHttpUrl(url)
                        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
                        iconClient.newCall(request).execute().use { response ->
                                if (response.isRedirect) {
                                    url = java.net.URI(url).resolve(response.header("Location") ?: error("Missing redirect")).toString()
                                } else {
                                    check(response.isSuccessful)
                                    body = response.body.byteStream().use { input ->
                                        val out = java.io.ByteArrayOutputStream()
                                        val buffer = ByteArray(8192)
                                        while (out.size() <= IconDataUri.MAX_BYTES) {
                                            val n = input.read(buffer)
                                            if (n < 0) break
                                            out.write(buffer, 0, n)
                                        }
                                        out.toByteArray()
                                    }
                                }
                            }
                        if (body != null) break
                    }
                    body?.takeIf { it.isNotEmpty() && it.size <= IconDataUri.MAX_BYTES } ?: continue
                }
                // Decode the bytes, not the server's possibly misleading MIME/rel type.
                val temporary = File.createTempFile("icon-", ".image", context.cacheDir)
                pending = temporary
                temporary.writeBytes(bytes)
                val result = loader.execute(ImageRequest.Builder(context).data(temporary).size(256).build())
                if (result is SuccessResult) save(result.image.toBitmap())?.let { return@withContext it }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Try the next declared candidate. */ }
            finally { pending?.delete() }
        }
        null
    } }

    suspend fun publish(pageUrl: String, path: String, appId: String? = null) {
        latest.update { it + (pageUrl to path) }
        history.updateIcon(pageUrl, path)
        bookmarks.updateIcon(pageUrl, path)
        val app = appId?.let { apps.getById(it) } ?: apps.getByUrl(pageUrl)
        if (app != null && iconBelongsToApp(app, pageUrl) && canReplace(app)) apps.updateAutomaticIcon(app.id, app.iconUrl, path)
    }
}

private fun sameOrigin(a: String, b: String): Boolean = runCatching {
    fun key(s: String): Triple<String?, String?, Int> {
        val u = java.net.URI(s)
        return Triple(u.scheme?.lowercase(), u.host?.lowercase(), if (u.port == -1) { if (u.scheme == "https") 443 else 80 } else u.port)
    }
    key(a) == key(b)
}.getOrDefault(false)

private fun iconBelongsToApp(app: WebAppEntity, pageUrl: String): Boolean {
    if (!app.isLocal) return sameOrigin(app.url, pageUrl)
    return runCatching {
        val persisted = java.net.URI(app.url)
        val rendered = java.net.URI(pageUrl)
        persisted.scheme == "local" && rendered.scheme == "https" &&
            rendered.host == "appassets.androidplatform.net" && rendered.port in setOf(-1, 443) &&
            rendered.path.startsWith("/local/${persisted.host}/")
    }.getOrDefault(false)
}
