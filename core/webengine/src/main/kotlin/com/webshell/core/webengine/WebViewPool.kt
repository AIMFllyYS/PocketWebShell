package com.webshell.core.webengine

import android.content.Context

/**
 * 会话级 WebView 池：同一 sessionId 复用同一个 ShellWebView 实例。
 * 这是"切 tab / 切主页不丢状态"与"后台保活"的基础——视图从窗口摘除后实例仍在池中，
 * 由前台服务（M5）或本池继续持有，JS 定时器/网络不中断。
 */
object WebViewPool {

    private val pool = LinkedHashMap<String, ShellWebView>()

    @Volatile
    var maxLive: Int = 6

    fun getOrCreate(context: Context, sessionId: String, config: (ShellWebView?) -> ShellConfig): ShellWebView {
        val existing = pool[sessionId]
        if (existing != null) return existing
        evictIfNeeded()
        val created = ShellWebView(context.applicationContext, config = config(existing))
        pool[sessionId] = created
        return created
    }

    fun get(sessionId: String): ShellWebView? = pool[sessionId]

    fun liveSessions(): List<String> = pool.keys.toList()

    fun suspendSession(sessionId: String) {
        pool[sessionId]?.suspendRendering()
    }

    fun resumeSession(sessionId: String) {
        pool[sessionId]?.resumeRendering()
    }

    /** 销毁会话并释放 WebView（Profile 数据保留在磁盘） */
    fun destroy(sessionId: String) {
        pool.remove(sessionId)?.release()
    }

    /**
     * 把会话视图从窗口摘除但不销毁：多标签切换时调用，
     * ShellWebViewHost 会在激活时重新 attach 同一实例。
     */
    fun detach(sessionId: String) {
        pool[sessionId]?.detachForReuse()
    }

    private fun evictIfNeeded() {
        while (pool.size >= maxLive) {
            val eldest = pool.entries.firstOrNull() ?: break
            eldest.value.saveSessionState()
            eldest.value.release()
            pool.remove(eldest.key)
        }
    }
}
