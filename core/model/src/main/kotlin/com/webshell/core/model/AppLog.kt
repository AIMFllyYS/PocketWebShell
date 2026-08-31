package com.webshell.core.model

/**
 * 应用内调试日志：固定容量环形缓冲，纯内存、进程存活期内有效。
 * 仅供「我的 → 开发者中心 → 日志」查看，不替代系统 logcat。
 * 记录纪律（AGENTS.md）：禁止写入浏览内容、Cookie、凭证、完整查询串、签名材料。
 */
object AppLog {

    /** 单条日志：时间戳 + 标签 + 消息（标签用于日志页过滤）。 */
    data class Entry(
        val timeMillis: Long,
        val tag: String,
        val message: String,
    )

    const val CAPACITY = 500

    private val buffer = ArrayDeque<Entry>(CAPACITY)
    private val lock = Any()

    /** 追加一条日志；超过容量时淘汰最旧记录。线程安全。 */
    fun log(tag: String, message: String) {
        synchronized(lock) {
            buffer.addLast(Entry(System.currentTimeMillis(), tag, message))
            while (buffer.size > CAPACITY) buffer.removeFirst()
        }
    }

    /** 快照：按时间正序返回。 */
    fun entries(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun clear() = synchronized(lock) { buffer.clear() }

    /** 导出为纯文本（时间 ISO-like + 标签 + 消息），用于复制/分享。 */
    fun exportText(): String = synchronized(lock) {
        buffer.joinToString("\n") { e ->
            "${formatTime(e.timeMillis)}  [${e.tag}]  ${e.message}"
        }
    }

    /** HH:mm:ss.SSS（本地时区），够调试定位用。 */
    fun formatTime(timeMillis: Long): String {
        val local = timeMillis + java.util.TimeZone.getDefault().getOffset(timeMillis)
        val ms = local % 1000
        val totalSeconds = local / 1000
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600 % 24
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, ms)
    }
}
