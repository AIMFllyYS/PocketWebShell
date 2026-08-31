package com.webshell.app.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.webshell.core.data.WebAppEntity
import com.webshell.core.data.SettingsRepository
import com.webshell.core.webengine.KeepAliveRegistry
import com.webshell.core.webengine.LocalWebHost
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.WebViewPool
import com.webshell.app.service.WebHostService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 网页应用会话的统一入口：主页点击图标 / 浏览器新建会话都走这里。
 * 负责：会话配置（含 local:// 重写与新列映射）、保活登记、前台服务启停。
 */
@Singleton
class ShellSessionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    fun configFor(app: WebAppEntity): ShellConfig {
        val renderUrl = if (LocalWebHost.isLocalAppUrl(app.url)) {
            LocalWebHost.toHttpsUrl(app.url)
        } else {
            app.url
        }
        return ShellConfig(
            sessionId = app.id,
            profileId = app.id, // 网页应用壳保持独立 Profile 隔离（站点间互不串号）
            startUrl = renderUrl,
            desktopMode = app.desktopMode,
            algorithmicDark = app.darkMode,
            textZoomPercent = app.textZoomPercent,
            thirdPartyCookies = true,
            pullToRefresh = true,
            externalLinkPolicy =
            if (app.externalLinksToBrowser || app.isFavorite) {
                ShellConfig.ExternalLinkPolicy.OPEN_IN_BROWSER
            } else {
                ShellConfig.ExternalLinkPolicy.OPEN_IN_SAME
            },
        )
    }

    /** 打开一个网页应用会话（主页图标点击）：建会话 + 保活登记 + 前台服务 */
    suspend fun openSession(app: WebAppEntity) {
        val config = configFor(app)
        val shell = WebViewPool.getOrCreate(context, app.id) { config }
        if (shell.currentUrl() == null || shell.currentUrl() == "about:blank") {
            shell.loadWithStateRestore(config.startUrl)
        }
        if (app.keepAlive && settingsRepository.settings.first().keepAliveServiceEnabled) {
            KeepAliveRegistry.register(app.id, app.title, app.url)
            ensureServiceRunning()
        }
    }

    /** 沉浸式启动（MainActivity 重新进入并带 url extra） */
    suspend fun launchImmersive(activityContext: Context, app: WebAppEntity) {
        openSession(app)
        activityContext.startActivity(
            Intent(activityContext, com.webshell.app.MainActivity::class.java).apply {
                putExtra(com.webshell.app.MainActivity.EXTRA_URL, app.url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    /** 会话被用户关闭/删除 */
    fun closeSession(sessionId: String) {
        KeepAliveRegistry.unregister(sessionId)
        WebViewPool.suspendSession(sessionId)
        if (KeepAliveRegistry.entries.isEmpty()) stopService()
    }

    fun ensureServiceRunning() {
        val intent = Intent(context, WebHostService::class.java)
            .setAction(KeepAliveRegistry.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService() {
        context.startService(
            Intent(context, WebHostService::class.java)
                .setAction(KeepAliveRegistry.ACTION_STOP),
        )
    }

    /** 全局开关只控制前台服务；会话实例仍保留，重新开启时可以继续接管。 */
    fun setServiceEnabled(enabled: Boolean) {
        if (enabled) {
            if (KeepAliveRegistry.entries.isNotEmpty()) ensureServiceRunning()
        } else {
            stopService()
        }
    }

    // -------------------------------------------------- 电池白名单（可靠性向导）

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 系统白名单对话框（非 Play 分发场景合法使用） */
    fun requestBatteryWhitelist(activityContext: Context) {
        val pm = activityContext.getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(activityContext.packageName)) return
        runCatching {
            activityContext.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${activityContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
