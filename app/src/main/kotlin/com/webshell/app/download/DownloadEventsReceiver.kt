package com.webshell.app.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.webshell.core.data.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DownloadEventsReceiver : BroadcastReceiver() {
    @Inject
    lateinit var repository: DownloadRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != -1L) repository.onSystemComplete(id)
            }
            DownloadManager.ACTION_NOTIFICATION_CLICKED -> {
                val ids = intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS)
                val id = ids?.firstOrNull() ?: return
                val item = repository.item(id)
                val documentUri = item?.documentUri?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                val fileUri = repository.downloadedFileUri(id) ?: documentUri
                val title = context.getString(com.webshell.app.R.string.download_share_title)
                DownloadIntents.launch(context.applicationContext, documentUri, fileUri, title)
            }
        }
    }
}
