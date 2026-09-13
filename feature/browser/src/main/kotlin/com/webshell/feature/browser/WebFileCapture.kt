package com.webshell.feature.browser

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Builds a camera/video capture intent for <input capture>. Does not grant the page a camera object. */
internal object WebFileCapture {
    const val CACHE_DIR = "captures"

    fun isVideoAccept(acceptTypes: Array<String>?): Boolean =
        acceptTypes.orEmpty().any { it.contains("video", ignoreCase = true) }

    fun runtimePermissions(captureEnabled: Boolean, acceptTypes: Array<String>?): List<String> {
        if (!captureEnabled) return emptyList()
        return buildList {
            add(Manifest.permission.CAMERA)
            if (isVideoAccept(acceptTypes)) add(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun intentOrNull(context: Context, video: Boolean): Pair<Intent, Uri>? {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = runCatching {
            File.createTempFile("capture_", if (video) ".mp4" else ".jpg", dir)
        }.getOrNull() ?: return null
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return null
        val intent = Intent(
            if (video) MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE,
        ).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("capture", uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return intent to uri
    }
}
