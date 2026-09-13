package com.webshell.feature.browser

import java.util.Calendar

enum class HistoryTimeGroup {
    TODAY,
    YESTERDAY,
    TWO_DAYS_AGO,
    THIS_WEEK,
    THIS_MONTH,
    EARLIER,
}

data class HistorySection(
    val group: HistoryTimeGroup,
    val items: List<BrowserSavedPage>,
)

fun startOfLocalDay(now: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = now
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun historyTimeGroup(visitedAt: Long, now: Long): HistoryTimeGroup {
    val today = startOfLocalDay(now)
    val day = 24L * 60L * 60L * 1000L
    return when {
        visitedAt >= today -> HistoryTimeGroup.TODAY
        visitedAt >= today - day -> HistoryTimeGroup.YESTERDAY
        visitedAt >= today - 2L * day -> HistoryTimeGroup.TWO_DAYS_AGO
        visitedAt >= today - 7L * day -> HistoryTimeGroup.THIS_WEEK
        visitedAt >= today - 30L * day -> HistoryTimeGroup.THIS_MONTH
        else -> HistoryTimeGroup.EARLIER
    }
}

fun groupHistory(pages: List<BrowserSavedPage>, now: Long): List<HistorySection> {
    if (pages.isEmpty()) return emptyList()
    val buckets = LinkedHashMap<HistoryTimeGroup, MutableList<BrowserSavedPage>>()
    HistoryTimeGroup.entries.forEach { buckets[it] = mutableListOf() }
    pages.forEach { page ->
        buckets.getValue(historyTimeGroup(page.visitedAt, now)).add(page)
    }
    return HistoryTimeGroup.entries.mapNotNull { group ->
        val items = buckets.getValue(group)
        if (items.isEmpty()) null else HistorySection(group, items)
    }
}

fun historySearchPattern(raw: String): String? {
    val cleaned = raw.trim().replace("%", "").replace("_", "").take(80)
    if (cleaned.isEmpty()) return null
    return "%$cleaned%"
}

fun historyGroupStartsExpanded(group: HistoryTimeGroup): Boolean = when (group) {
    HistoryTimeGroup.TODAY, HistoryTimeGroup.YESTERDAY, HistoryTimeGroup.TWO_DAYS_AGO -> true
    HistoryTimeGroup.THIS_WEEK, HistoryTimeGroup.THIS_MONTH, HistoryTimeGroup.EARLIER -> false
}
