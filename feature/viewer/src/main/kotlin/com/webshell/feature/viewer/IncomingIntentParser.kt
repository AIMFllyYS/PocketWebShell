package com.webshell.feature.viewer

import android.content.Intent
import android.net.Uri
import android.os.Build

data class IncomingOpenCandidate(
    val uriString: String,
    val mimeType: String?,
    val token: Long,
)

/**
 * Extracts a single content/file URI from VIEW / SEND / SEND_MULTIPLE.
 * Ignores EXTRA_TEXT and the app's internal EXTRA_URL launch extra.
 */
object IncomingIntentParser {
    const val EXTRA_CONSUMED: String = "com.webshell.feature.viewer.consumed"

    fun from(intent: Intent?, token: Long = System.nanoTime()): IncomingOpenCandidate? {
        if (intent == null || intent.getBooleanExtra(EXTRA_CONSUMED, false)) return null
        if (intent.action == Intent.ACTION_MAIN) return null
        return parse(
            action = intent.action,
            data = intent.data?.toString(),
            type = intent.type,
            extraStream = extraStreamUri(intent),
            extraStreams = extraStreamUris(intent),
            clipData = clipUri(intent),
            token = token,
        )
    }

    fun markConsumed(intent: Intent?) {
        intent?.putExtra(EXTRA_CONSUMED, true)
    }

    fun parse(
        action: String?,
        data: String?,
        type: String?,
        extraStream: String? = null,
        extraStreams: List<String> = emptyList(),
        clipData: String? = null,
        token: Long = 0L,
    ): IncomingOpenCandidate? {
        if (action == Intent.ACTION_MAIN) return null
        val uri = firstSupportedUri(data, extraStream, extraStreams, clipData) ?: return null
        return IncomingOpenCandidate(uriString = uri, mimeType = IncomingFilePolicy.mimeOf(type), token = token)
    }

    fun isSupportedUri(raw: String?): Boolean {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.any { it.isISOControl() }) return false
        val scheme = schemeOf(value) ?: return false
        return scheme == "content" || scheme == "file"
    }

    internal fun schemeOf(raw: String): String? {
        val idx = raw.indexOf("://")
        if (idx <= 0) return null
        return raw.substring(0, idx).lowercase()
    }

    private fun firstSupportedUri(
        data: String?,
        extraStream: String?,
        extraStreams: List<String>,
        clipData: String?,
    ): String? = sequenceOf(data, extraStream)
        .plus(extraStreams.asSequence())
        .plus(clipData)
        .mapNotNull { it?.trim()?.takeIf(::isSupportedUri) }
        .firstOrNull()

    private fun extraStreamUri(intent: Intent): String? {
        val uri = parcelableExtra(intent, Intent.EXTRA_STREAM) ?: return null
        return uri.toString()
    }

    private fun extraStreamUris(intent: Intent): List<String> {
        val extras = intent.clipData
        val fromList = parcelableArrayListExtra(intent, Intent.EXTRA_STREAM)
        val values = buildList {
            fromList?.forEach { add(it.toString()) }
            extras?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.toString()?.let(::add)
                }
            }
        }
        return values
    }

    private fun clipUri(intent: Intent): String? {
        val clip = intent.clipData ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).uri?.toString()
    }

    private fun parcelableExtra(intent: Intent, key: String): Uri? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(key, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(key) as? Uri
    }

    private fun parcelableArrayListExtra(intent: Intent, key: String): List<Uri>? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(key, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(key)
        }
}
