package com.webshell.app.download

import android.Manifest
import android.os.Build

/**
 * Storage permission actually required to help open public downloads.
 * Mixed APKs/PDFs cannot use READ_MEDIA_*; All-files access is not required
 * because DownloadManager / SAF content URIs plus ACTION_VIEW_DOWNLOADS work.
 */
object DownloadStorageAccess {
    const val LEGACY_READ_MAX_SDK = 32

    fun runtimeReadPermission(sdkInt: Int = Build.VERSION.SDK_INT): String? =
        if (sdkInt in 29..LEGACY_READ_MAX_SDK) Manifest.permission.READ_EXTERNAL_STORAGE else null

    fun needsLegacyRead(target: DownloadOpenTarget?): Boolean =
        target == null ||
            target == DownloadOpenTarget.Folder ||
            target == DownloadOpenTarget.File
}
