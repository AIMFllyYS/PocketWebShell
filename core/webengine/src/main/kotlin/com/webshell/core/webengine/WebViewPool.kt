package com.webshell.core.webengine

import android.content.Context
import android.os.Bundle

/**
 * 会话级 WebView 池：同一 sessionId 复用同一个 ShellWebView 实例。
 * 这是"切 tab / 切主页不丢状态"与"后台保活"的基础——视图从窗口摘除后实例仍在池中，
 * 由前台服务（M5）或本池继续持有，JS 定时器/网络不中断。
 *
 * 池语义：
 * - access-order LinkedHashMap 实现真 LRU，get/getOrCreate 命中即刷新访问序；
 * - [activeSessionId] 指向的激活会话受保护，永不淘汰；若池中只剩受保护会话
 *   导致无法淘汰，允许暂时超过 [maxLive]（直接创建返回，不死循环）；
 * - 淘汰与 [destroy] 前把会话快照存入 [savedStates]，getOrCreate 重建时自动恢复；
 * - [destroyAndForget] 用于"用户彻底关闭标签"：不存快照且清除已有快照。
 */
object WebViewPool {

    enum class ProtectionReason {
        ACTIVE, KEEP_ALIVE, PENDING_FILE, PENDING_PERMISSION, PENDING_DOWNLOAD, PENDING_WINDOW, FULLSCREEN, AUTH, PENDING_CLEARTEXT
    }

    private val pool = LinkedHashMap<String, ShellWebView>(16, 0.75f, true)

    /** 淘汰下来的会话快照（含返回栈）；会话重建时恢复并移除 */
    private val savedStates = HashMap<String, Bundle>()
    private val protection = HashMap<String, MutableSet<ProtectionReason>>()
    private val lifecycle = HashMap<String, SessionLifecycleState>()

    @Volatile
    var maxLive: Int = 6

    /**
     * Process-wide download sink. Installed from Application because
     * [ShellWebView] is not created by Hilt.
     */
    @Volatile
    var downloadSink: DownloadSink? = null

    /** 正在展示的激活会话 ID，淘汰时跳过；null 表示无保护对象 */
    @Volatile
    var activeSessionId: String? = null

    /** 会话被淘汰时的通知（主线程）；宿主可观测 renderer 暂时离线，但不应删除 UI 状态。 */
    var onSessionEvicted: ((sessionId: String) -> Unit)? = null

    fun getOrCreate(context: Context, sessionId: String, config: (ShellWebView?) -> ShellConfig): ShellWebView {
        val existing = pool[sessionId]
        if (existing != null) return existing
        evictIfNeeded()
        val created = ShellWebView(context.applicationContext, config = config(existing))
        pool[sessionId] = created
        lifecycle[sessionId] = SessionLifecycleState.CREATED
        // 有淘汰期快照则恢复返回栈，并移除该条目
        savedStates.remove(sessionId)?.let { created.restoreSessionState(it) }
        return created
    }

    fun protect(sessionId: String, reason: ProtectionReason) {
        synchronized(protection) { protection.getOrPut(sessionId) { mutableSetOf() }.add(reason) }
    }

    fun unprotect(sessionId: String, reason: ProtectionReason) {
        synchronized(protection) {
            protection[sessionId]?.let { reasons ->
                reasons.remove(reason)
                if (reasons.isEmpty()) protection.remove(sessionId)
            }
        }
    }

    fun protectionReasons(sessionId: String): Set<ProtectionReason> =
        synchronized(protection) { protection[sessionId].orEmpty().toSet() }

    fun isProtected(sessionId: String): Boolean =
        sessionId == activeSessionId || protectionReasons(sessionId).isNotEmpty()

    /** 命中即刷新 LRU 访问序（access-order map 的 get 自动完成） */
    fun get(sessionId: String): ShellWebView? = pool[sessionId]

    fun liveSessions(): List<String> = pool.keys.toList()

    fun suspendSession(sessionId: String) {
        pool[sessionId]?.suspendRendering()
        if (pool.containsKey(sessionId)) lifecycle[sessionId] = SessionLifecycleState.BACKGROUND
    }

    fun resumeSession(sessionId: String) {
        pool[sessionId]?.resumeRendering()
        if (pool.containsKey(sessionId)) lifecycle[sessionId] = SessionLifecycleState.ACTIVE
    }

    fun lifecycleState(sessionId: String): SessionLifecycleState =
        lifecycle[sessionId] ?: if (savedStates.containsKey(sessionId)) SessionLifecycleState.EVICTED else SessionLifecycleState.CLOSED

    internal fun markLifecycle(sessionId: String, state: SessionLifecycleState) {
        if (pool.containsKey(sessionId)) lifecycle[sessionId] = state
    }

    /** Used by the explicit "clear all browser data" action after all live sessions are closed. */
    fun clearSavedStates() {
        savedStates.clear()
        lifecycle.entries.removeIf { it.value == SessionLifecycleState.EVICTED }
    }

    /** 销毁实例释放 WebView；会话快照保留在池中（Profile 数据保留在磁盘），重建时恢复 */
    fun destroy(sessionId: String) {
        val shell = pool.remove(sessionId) ?: return
        shell.saveSessionState()?.let { savedStates[sessionId] = it }
        shell.release()
        synchronized(protection) { protection.remove(sessionId) }
        lifecycle[sessionId] = SessionLifecycleState.CLOSED
        CleartextGate.forgetSession(sessionId)
    }

    /** 用户彻底关闭标签：销毁实例，不存快照且移除已有快照 */
    fun destroyAndForget(sessionId: String) {
        savedStates.remove(sessionId)
        pool.remove(sessionId)?.release()
        synchronized(protection) { protection.remove(sessionId) }
        lifecycle[sessionId] = SessionLifecycleState.CLOSED
        CleartextGate.forgetSession(sessionId)
    }

    /**
     * 把会话视图从窗口摘除但不销毁：多标签切换时调用，
     * ShellWebViewHost 会在激活时重新 attach 同一实例。
     */
    fun detach(sessionId: String) {
        pool[sessionId]?.detachForReuse()
    }

    /** Drop every unprotected live session except [keepSessionId] so a heavy local app can load. */
    fun evictUnprotectedExcept(keepSessionId: String) {
        val victims = pool.keys.filter { it != keepSessionId && !isProtected(it) }
        victims.forEach(::evict)
    }

    private fun evictIfNeeded() {
        while (pool.size >= maxLive) {
            // access-order 下首位即最久未访问；跳过受保护的激活会话
            val eldest = pool.entries.firstOrNull { !isProtected(it.key) } ?: break
            evict(eldest.key)
        }
    }

    private fun evict(sessionId: String) {
        val shell = pool.remove(sessionId) ?: return
        shell.saveSessionState()?.let { savedStates[sessionId] = it }
        shell.release()
        synchronized(protection) { protection.remove(sessionId) }
        lifecycle[sessionId] = SessionLifecycleState.EVICTED
        onSessionEvicted?.invoke(sessionId)
    }
}
