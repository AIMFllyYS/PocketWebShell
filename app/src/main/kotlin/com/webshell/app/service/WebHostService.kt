package com.webshell.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.KeepAliveRegistry
import com.webshell.app.MainActivity
import com.webshell.app.R

/**
 * 后台静默前台服务（specialUse 类型）：
 * 持有 WebViewPool 中"保活中"会话的存在感，对抗切后台 ~40s 后的进程冻结
 * —— 这是"切走任务不断线"的核心机制。即便服务被厂商策略杀死，
 * Profile 落盘的 cookie/存储仍保证回来自动恢复登录态（兜底层）。
 */
class WebHostService : Service() {

    companion object {
        val running = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppLog.log("service", "保活服务 onCreate")
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            KeepAliveRegistry.ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                running.value = false
                return START_NOT_STICKY
            }
            else -> startInForeground()
        }
        running.value = true
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                KeepAliveRegistry.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(KeepAliveRegistry.NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, KeepAliveRegistry.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shell)
            .setContentTitle("玄览 后台运行中")
            .setContentText(summaryText())
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun summaryText(): String {
        val count = KeepAliveRegistry.entries.size
        return if (count == 0) {
            "没有正在保活的网页应用"
        } else {
            "正在保活 $count 个网页应用，任务不会中断"
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            KeepAliveRegistry.CHANNEL_ID,
            "后台保活",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保证网页应用切到后台后任务继续运行"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        running.value = false
        AppLog.log("service", "保活服务 onDestroy")
        super.onDestroy()
    }
}
