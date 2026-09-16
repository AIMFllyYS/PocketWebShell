package com.webshell.core.data.update

import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class GitHubRelease(
    val versionName: String,
    val tagName: String,
    val notesExcerpt: String,
    val htmlUrl: String,
    val apkUrl: String?,
) {
    val downloadUrl: String get() = apkUrl ?: htmlUrl
}

sealed class AppUpdateResult {
    data class UpToDate(val installed: String, val latest: String) : AppUpdateResult()
    data class Available(val installed: String, val release: GitHubRelease) : AppUpdateResult()
    data object Failed : AppUpdateResult()
}

fun decideUpdate(installed: String, release: GitHubRelease): AppUpdateResult {
    val delta = AppVersion.compareNames(release.versionName, installed) ?: return AppUpdateResult.Failed
    return if (delta > 0) {
        AppUpdateResult.Available(installed, release)
    } else {
        AppUpdateResult.UpToDate(installed, release.versionName)
    }
}

object GitHubReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): GitHubRelease {
        val dto = json.decodeFromString(GitHubLatestReleaseJson.serializer(), raw)
        val versionName = AppVersion.parse(dto.tagName)?.toString()
            ?: error("Unsupported release tag")
        val htmlUrl = httpsGitHubUrl(dto.htmlUrl) ?: error("Invalid release page URL")
        return GitHubRelease(
            versionName = versionName,
            tagName = dto.tagName.trim(),
            notesExcerpt = excerptNotes(dto.body),
            htmlUrl = htmlUrl,
            apkUrl = pickApkUrl(versionName, dto.assets),
        )
    }
}

@Serializable
internal data class GitHubLatestReleaseJson(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GitHubAssetJson> = emptyList(),
)

@Serializable
internal data class GitHubAssetJson(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

internal fun excerptNotes(body: String?, maxChars: Int = 400): String {
    if (body.isNullOrBlank()) return ""
    val text = body.lineSequence()
        .map { line ->
            line.trim()
                .removePrefix("#")
                .trimStart('#', ' ')
                .replace(Regex("^[-*+]\\s+"), "")
                .trim()
        }
        .filter { it.isNotEmpty() }
        .take(8)
        .joinToString("\n")
    if (text.length <= maxChars) return text
    return text.take(maxChars).trimEnd() + "…"
}

internal fun pickApkUrl(versionName: String, assets: List<GitHubAssetJson>): String? {
    val apks = assets.mapNotNull { asset ->
        val name = asset.name
        if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
        if (name.contains("debug", ignoreCase = true)) return@mapNotNull null
        val url = httpsGitHubUrl(asset.browserDownloadUrl) ?: return@mapNotNull null
        name to url
    }
    val expected = "PocketWebShell-v$versionName.apk"
    return apks.firstOrNull { it.first.equals(expected, ignoreCase = true) }?.second
        ?: apks.firstOrNull()?.second
}

internal fun httpsGitHubUrl(raw: String): String? {
    val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() != "https") return null
    if (uri.userInfo != null) return null
    val host = uri.host?.lowercase() ?: return null
    val allowed = host == "github.com" ||
        host.endsWith(".github.com") ||
        host == "githubusercontent.com" ||
        host.endsWith(".githubusercontent.com")
    if (!allowed) return null
    return uri.toASCIIString()
}
