package com.webshell.core.webengine

/** Parsed, size-unchecked result returned by the restricted Blob JS task. */
internal data class BlobDownloadPayload(
    val mimeType: String,
    val encoded: String,
)

internal sealed interface BlobDownloadParseResult {
    data class Success(val payload: BlobDownloadPayload) : BlobDownloadParseResult
    data class Failure(val reason: String) : BlobDownloadParseResult
}

/**
 * Parse the JSON-string callback produced by WebView.evaluateJavascript.
 *
 * The wire format is deliberately only two fields after the status marker:
 * `OK:<mime-type>:<base64>`. MIME types do not contain an unquoted colon, so
 * splitting at the first colon after `OK:` keeps the complete Base64 payload
 * intact. Keeping this parser outside ShellWebView makes the delimiter and
 * JavaScript escaping rules unit-testable without constructing a WebView.
 */
internal fun parseBlobMeta(raw: String?): Pair<Long, String>? {
    val result = decodeJavascriptString(raw) ?: return null
    if (!result.startsWith("META:")) return null
    val rest = result.removePrefix("META:")
    val separator = rest.indexOf(':')
    if (separator < 0) return null
    val size = rest.substring(0, separator).toLongOrNull() ?: return null
    if (size < 0L) return null
    val mime = rest.substring(separator + 1).take(96).ifBlank { "application/octet-stream" }
    return size to mime
}

internal fun parseBlobChunk(raw: String?): String? {
    val result = decodeJavascriptString(raw) ?: return null
    if (result.startsWith("ERR:")) return null
    if (!result.startsWith("CHUNK:")) return null
    return result.removePrefix("CHUNK:")
}

internal fun parseBlobEvaluation(raw: String?): BlobDownloadParseResult {
    val result = decodeJavascriptString(raw) ?: return BlobDownloadParseResult.Failure("empty")
    if (!result.startsWith("OK:")) {
        return BlobDownloadParseResult.Failure(
            result.removePrefix("ERR:").take(120).ifBlank { "blob-failed" },
        )
    }
    val separator = result.indexOf(':', startIndex = 3)
    if (separator < 0) return BlobDownloadParseResult.Failure("blob-format")
    val mime = result.substring(3, separator).take(96).ifBlank { "application/octet-stream" }
    val encoded = result.substring(separator + 1)
    return BlobDownloadParseResult.Success(BlobDownloadPayload(mime, encoded))
}

/** Small JSON string unescaper for evaluateJavascript's callback envelope. */
private fun decodeJavascriptString(raw: String?): String? {
    val value = raw?.trim()?.takeUnless { it == "null" } ?: return null
    if (value.length < 2 || value.first() != '"' || value.last() != '"') return value
    val body = value.substring(1, value.lastIndex)
    val out = StringBuilder(body.length)
    var index = 0
    while (index < body.length) {
        val ch = body[index]
        if (ch != '\\' || index == body.lastIndex) {
            out.append(ch)
            index++
            continue
        }
        val escaped = body[index + 1]
        when (escaped) {
            '"', '\\', '/' -> out.append(escaped)
            'b' -> out.append('\b')
            'f' -> out.append('\u000C')
            'n' -> out.append('\n')
            'r' -> out.append('\r')
            't' -> out.append('\t')
            'u' -> {
                val end = index + 6
                if (end <= body.length) {
                    val code = body.substring(index + 2, end).toIntOrNull(16)
                    if (code != null) {
                        out.append(code.toChar())
                        index = end
                        continue
                    }
                }
                // Preserve malformed escapes as data; the later status/parser
                // checks will still reject an invalid payload safely.
                out.append(escaped)
            }
            else -> out.append(escaped)
        }
        index += 2
    }
    return out.toString()
}
