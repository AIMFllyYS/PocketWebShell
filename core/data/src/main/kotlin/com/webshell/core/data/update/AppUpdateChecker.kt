package com.webshell.core.data.update

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 向 GitHub Releases 查询最新正式版。不落库、不缓存用户数据。
 * `/releases/latest` 会跳过 draft 与 prerelease，与 debug 先行发布规则一致。
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun check(installedVersion: String): AppUpdateResult = withContext(Dispatchers.IO) {
        try {
            if (AppVersion.parse(installedVersion) == null) return@withContext AppUpdateResult.Failed
            decideUpdate(installedVersion, fetchLatest())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppUpdateResult.Failed
        }
    }

    private fun fetchLatest(): GitHubRelease {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "Empty response body" }
            val bytes = readBounded(body.byteStream(), MAX_JSON_BYTES)
            return GitHubReleaseParser.parse(bytes.decodeToString())
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        input.use { stream ->
            val out = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
            val buffer = ByteArray(8 * 1024)
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

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/AIMFllyYS/PocketWebShell/releases/latest"
        const val WEBSITE_URL = "https://xuanlan.1037solo.com/"
        private const val MAX_JSON_BYTES = 256 * 1024
    }
}
