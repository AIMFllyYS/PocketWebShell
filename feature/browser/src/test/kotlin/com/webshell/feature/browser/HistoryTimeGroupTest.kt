package com.webshell.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HistoryTimeGroupTest {
    private val now: Long = Calendar.getInstance().run {
        set(2026, Calendar.SEPTEMBER, 14, 15, 30, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
    private val today = startOfLocalDay(now)
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun bucketsFollowLocalCalendar() {
        assertEquals(HistoryTimeGroup.TODAY, historyTimeGroup(today + 3_600_000L, now))
        assertEquals(HistoryTimeGroup.YESTERDAY, historyTimeGroup(today - 1L, now))
        assertEquals(HistoryTimeGroup.TWO_DAYS_AGO, historyTimeGroup(today - day - 1L, now))
        assertEquals(HistoryTimeGroup.THIS_WEEK, historyTimeGroup(today - 3L * day, now))
        assertEquals(HistoryTimeGroup.THIS_MONTH, historyTimeGroup(today - 10L * day, now))
        assertEquals(HistoryTimeGroup.EARLIER, historyTimeGroup(today - 40L * day, now))
    }

    @Test
    fun groupsKeepOrderAndDropEmpty() {
        val pages = listOf(
            page(1, today + 1),
            page(2, today - 2L * day),
            page(3, today - 40L * day),
        )
        val sections = groupHistory(pages, now)
        assertEquals(
            listOf(HistoryTimeGroup.TODAY, HistoryTimeGroup.TWO_DAYS_AGO, HistoryTimeGroup.EARLIER),
            sections.map { it.group },
        )
        assertEquals(listOf(1L), sections[0].items.map { it.id })
        assertEquals(listOf(3L), sections[2].items.map { it.id })
    }

    @Test
    fun recentGroupsStartOpen() {
        assertTrue(historyGroupStartsExpanded(HistoryTimeGroup.TODAY))
        assertTrue(historyGroupStartsExpanded(HistoryTimeGroup.YESTERDAY))
        assertFalse(historyGroupStartsExpanded(HistoryTimeGroup.EARLIER))
    }

    @Test
    fun searchPatternStripsWildcards() {
        assertEquals("%github%", historySearchPattern("  github  "))
        assertEquals("%a%", historySearchPattern("%a_"))
        assertNull(historySearchPattern("   "))
    }

    private fun page(id: Long, visitedAt: Long) = BrowserSavedPage(
        id = id,
        title = "t$id",
        url = "https://example.invalid/$id",
        visitedAt = visitedAt,
    )
}
