package com.webshell.core.model

enum class DownloadStatus {
    Queued,
    Running,
    Success,
    Failed,
}

/** Immutable download row. Progress fields are ephemeral; persistence never stores a URL or Cookie. */
data class DownloadItem(
    val id: Long,
    val displayName: String,
    val status: DownloadStatus,
    val documentUri: String? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
) {
    val progress: Float?
        get() = if (totalBytes > 0L) {
            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

    val relativePath: String
        get() = "Download/PocketWebShell/$displayName"

    val inProgress: Boolean
        get() = status == DownloadStatus.Queued || status == DownloadStatus.Running
}
