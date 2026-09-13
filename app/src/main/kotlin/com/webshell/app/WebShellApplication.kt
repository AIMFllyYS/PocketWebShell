package com.webshell.app

import android.app.Application
import android.net.Uri
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.webshell.core.data.AppLogSinkInstaller
import com.webshell.core.data.BrowserOpenTabsRepository
import com.webshell.core.data.DownloadRepository
import com.webshell.core.webengine.DownloadSink
import com.webshell.core.webengine.WebViewPool
import com.webshell.feature.viewer.IncomingStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@HiltAndroidApp
class WebShellApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var sinkInstaller: AppLogSinkInstaller

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var imageHttpClient: OkHttpClient

    @Inject
    lateinit var openTabs: BrowserOpenTabsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 启动时接通 AppLog → Room 持久化与崩溃捕获
        sinkInstaller.start()
        downloadRepository.start()
        // tmp-* 清扫不挡第一帧；孤儿体积大时主线程会明显卡顿。
        appScope.launch {
            val keep = if (this@WebShellApplication::openTabs.isInitialized) {
                runCatching { openTabs.load().mapNotNull { it.localAppId }.toSet() }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
            runCatching { IncomingStore.sweepOrphans(filesDir, keep = keep) }
        }
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

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val builder = ImageLoader.Builder(context)
        if (this::imageHttpClient.isInitialized) {
            builder.components { add(OkHttpNetworkFetcherFactory(imageHttpClient)) }
        }
        return builder.build()
    }
}
