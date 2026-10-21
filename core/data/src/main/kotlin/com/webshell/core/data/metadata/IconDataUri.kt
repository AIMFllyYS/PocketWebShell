package com.webshell.core.data.metadata

import java.io.ByteArrayOutputStream
import java.util.Base64

/** Bounded image data URI decoding. A literal '+' must not become a space. */
object IconDataUri {
    const val MAX_BYTES = 1024 * 1024
    data class Image(val bytes: ByteArray, val extension: String)

    fun decode(value: String): Image? = runCatching {
        require(value.length <= MAX_BYTES * 4)
        val comma = value.indexOf(',')
        require(comma > 0 && value.startsWith("data:", true))
        val header = value.substring(5, comma).lowercase()
        val ext = when (header.substringBefore(';')) {
            "image/svg+xml" -> "svg"
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/x-icon", "image/vnd.microsoft.icon" -> "ico"
            else -> error("Unsupported image")
        }
        val payload = value.substring(comma + 1)
        val raw = ByteArrayOutputStream()
        var i = 0
        while (i < payload.length) {
            if (payload[i] == '%') {
                require(i + 2 < payload.length)
                raw.write(payload.substring(i + 1, i + 3).toInt(16)); i += 3
            } else {
                val cp = payload.codePointAt(i)
                raw.write(String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
                i += Character.charCount(cp)
            }
            require(raw.size() <= MAX_BYTES * 2)
        }
        val bytes = if ("base64" in header.split(';')) Base64.getDecoder().decode(raw.toByteArray()) else raw.toByteArray()
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES)
        Image(bytes, ext)
    }.getOrNull()
}
