package com.webshell.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryPagingTest {
    @Test
    fun pageSizeIsTwenty() {
        assertEquals(20, HISTORY_PAGE_SIZE)
    }

    @Test
    fun sanitizesHttpIconsAndDropsJunk() {
        assertEquals("https://a.example/favicon.ico", sanitizeHistoryIconUrl("https://a.example/favicon.ico"))
        assertNull(sanitizeHistoryIconUrl("javascript:alert(1)"))
        assertNull(sanitizeHistoryIconUrl("data:image/png;base64,xx"))
        assertNull(sanitizeHistoryIconUrl("  "))
    }
}
