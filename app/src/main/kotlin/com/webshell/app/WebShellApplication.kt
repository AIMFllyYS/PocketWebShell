package com.webshell.app

import android.app.Application
import android.net.Uri
import com.webshell.core.data.AppLogSinkInstaller
import com.webshell.core.data.DownloadRepository
import com.webshell.core.webengine.DownloadSink
import com.webshell.core.webengine.WebViewPool
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WebShellApplication : Application() {

    @Inject
    lateinit var sinkInstaller: AppLogSinkInstaller

    @Inject
    lateinit var downloadRepository: DownloadRepository

    override fun onCreate() {
        super.onCreate()
        // 启动时接通 AppLog → Room 持久化与崩溃捕获
        sinkInstaller.start()
        downloadRepository.start()
        WebViewPool.downloadSink = object : DownloadSink {
            override fun startHttp(
                url: String,
                fileName: String,
                mimeType: String?,
                userAgent: String?,
                referer: String?,
                cookie: String?,
            ): Long? = downloadRepository.startHttp(url, fileName, mimeType, userAgent, referer, cookie)

            override fun completeBlob(fileName: String, uri: Uri) {
                downloadRepository.completeBlob(fileName, uri)
            }
        }
    }
}
