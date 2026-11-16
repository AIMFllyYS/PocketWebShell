package com.webshell.core.webengine

import java.net.URI
import java.util.Locale

/**
 * Pure navigation and log decisions for [ShellWebView].
 *
 * ShellWebView itself needs Android, so the choices that must be unit-tested
 * (which opaque documents are consumed, what may be written to the log, and
 * how a desktop-probe callback is reduced to numbers) live here.
 */
internal object ShellNavigation {
    const val ERROR_OPAQUE_MAIN_FRAME = -97
    const val OPAQUE_MAIN_FRAME_DESCRIPTION = "已拦截不受支持的主框架地址"
    const val PROBE_FAIL = "probe-fail"

    private val SAFE_SCHEMES = setOf("http", "https", "blob", "data", "about", "local")
    private val NUMBER_TOKEN = Regex("""-?(?:0|[1-9]\d*)(?:\.\d+)?""")
    private const val PROBE_FIELD_COUNT = 16
    private const val MAX_PROBE_CALLBACK_CHARS = 1024
    private const val MAX_PROBE_NUMBER = 1_000_000_000.0
    private const val PROBE_LINE_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789=.,- "

    /**
     * Host only. With no host, a short scheme token from [SAFE_SCHEMES], or
     * "(no host)". Never the URL remainder — `data:` and `blob:` payloads
     * must not reach the log.
     */
    fun logHost(url: String?): String {
        if (url.isNullOrBlank()) return "(无 URL)"
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return "(no host)"
        safeHost(uri.host)?.let { return it }
        return safeScheme(uri.scheme) ?: "(no host)"
    }

    /** Main-frame `data:` and `blob:` documents are consumed. Subresources are not. */
    fun consumesOpaqueMainFrame(route: UrlRoute, isMainFrame: Boolean): Boolean =
        isMainFrame && (route == UrlRoute.DATA || route == UrlRoute.BLOB)

    /**
     * Desktop-probe callback → fixed numeric fields only.
     *
     * Field order from the page script: iw, cw, ow, rc, dpr, sw, vv, vs,
     * viewport-meta count, six media-query ints, boot. Anything else,
     * including meta text, userAgentData, raw JSON, or an exception message,
     * becomes [PROBE_FAIL].
     */
    fun desktopProbeLog(raw: String?): String {
        if (raw == null || raw.length > MAX_PROBE_CALLBACK_CHARS) return PROBE_FAIL
        return runCatching { formatProbe(raw) }.getOrDefault(PROBE_FAIL)
    }

    private fun formatProbe(raw: String): String {
        val text = unwrapJsResult(raw) ?: return PROBE_FAIL
        if (text == PROBE_FAIL || text.startsWith("$PROBE_FAIL:")) return PROBE_FAIL
        val body = text.trim().let { value ->
            if (value.startsWith("[") && value.endsWith("]") && value.length >= 2) {
                value.substring(1, value.lastIndex)
            } else {
                value
            }
        }
        val parts = body.split(',')
        if (parts.size != PROBE_FIELD_COUNT) return PROBE_FAIL
        val values = parts.map { part ->
            val token = part.trim()
            if (!NUMBER_TOKEN.matches(token)) return PROBE_FAIL
            val number = token.toDoubleOrNull() ?: return PROBE_FAIL
            if (!number.isFinite() || number !in -MAX_PROBE_NUMBER..MAX_PROBE_NUMBER) return PROBE_FAIL
            number
        }
        for (index in 8 until PROBE_FIELD_COUNT) {
            if (!isWhole(values[index])) return PROBE_FAIL
        }
        val line = buildString {
            append("iw=${fmt(values[0])}")
            append(" cw=${fmt(values[1])}")
            append(" ow=${fmt(values[2])}")
            append(" rc=${fmt(values[3])}")
            append(" dpr=${fmt(values[4])}")
            append(" sw=${fmt(values[5])}")
            append(" vv=${fmt(values[6])}")
            append(" vs=${fmt(values[7])}")
            append(" metas=${values[8].toInt()}")
            append(" mq=")
            append((9..14).joinToString(",") { values[it].toInt().toString() })
            append(" boot=${values[15].toInt()}")
        }
        if (line.any { it !in PROBE_LINE_CHARS }) return PROBE_FAIL
        return line
    }

    private fun unwrapJsResult(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.length < 2 || trimmed.first() != '"' || trimmed.last() != '"') return trimmed
        val body = trimmed.substring(1, trimmed.lastIndex)
        val out = StringBuilder(body.length)
        var index = 0
        while (index < body.length) {
            val ch = body[index]
            if (ch != '\\') {
                out.append(ch)
                index++
                continue
            }
            if (index + 1 >= body.length) return null
            when (val escaped = body[index + 1]) {
                '"', '\\', '/' -> out.append(escaped)
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (index + 6 > body.length) return null
                    val code = body.substring(index + 2, index + 6).toIntOrNull(16) ?: return null
                    out.append(code.toChar())
                    index += 6
                    continue
                }
                else -> return null
            }
            index += 2
        }
        return out.toString()
    }

    private fun safeHost(host: String?): String? {
        val value = host?.takeIf { it.isNotBlank() } ?: return null
        if (value.length > 253) return null
        if (value.any { it.isISOControl() || it.isWhitespace() || it in "/?#@,\\" }) return null
        return value
    }

    private fun safeScheme(scheme: String?): String? {
        val token = scheme?.lowercase(Locale.ROOT) ?: return null
        if (token.length !in 2..8 || token.any { it !in 'a'..'z' }) return null
        return token.takeIf { it in SAFE_SCHEMES }
    }

    private fun isWhole(value: Double): Boolean = value == value.toLong().toDouble()

    private fun fmt(value: Double): String {
        val asLong = value.toLong()
        if (value == asLong.toDouble()) return asLong.toString()
        return String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
    }
}
