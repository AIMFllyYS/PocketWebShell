package com.webshell.feature.home

import com.webshell.core.data.WebAppEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AllAppsIndexTest {

    private fun app(
        title: String,
        createdAt: Long = 0L,
    ) = WebAppEntity(
        id = title,
        title = title,
        url = "https://example.com/$title",
        iconUrl = null,
        desktopMode = false,
        darkMode = false,
        keepAlive = false,
        isFavorite = false,
        homePage = 0,
        homeCellIndex = 0,
        folderId = null,
        createdAt = createdAt,
    )

    @Test
    fun `ascii titles group by uppercase first letter`() {
        assertEquals("A", AllAppsIndex.sectionKey("apple"))
        assertEquals("B", AllAppsIndex.sectionKey("Banana"))
        assertEquals("G", AllAppsIndex.sectionKey("GitHub"))
    }

    @Test
    fun `chinese titles group by pinyin first letter`() {
        assertEquals("B", AllAppsIndex.sectionKey("百度"))
        assertEquals("Z", AllAppsIndex.sectionKey("知乎"))
        assertEquals("W", AllAppsIndex.sectionKey("微博"))
    }

    @Test
    fun `digits and symbols fall into other section`() {
        assertEquals("#", AllAppsIndex.sectionKey("123导航"))
        assertEquals("#", AllAppsIndex.sectionKey("!bang"))
        assertEquals("#", AllAppsIndex.sectionKey(""))
        assertEquals("#", AllAppsIndex.sectionKey("  "))
    }

    @Test
    fun `sections sort A to Z with other last`() {
        val sections = AllAppsIndex.buildSections(
            listOf(app("知乎"), app("123"), app("百度"), app("Apple")),
        )
        assertEquals(listOf("A", "B", "Z", "#"), sections.map { it.letter })
    }

    @Test
    fun `apps within a section sort by pinyin-aware title order`() {
        // 同为 Z 区：支付宝(zhifubao) 应排在 中国(zhongguo) 之前（拼音序而非码位序）
        val sections = AllAppsIndex.buildSections(
            listOf(app("中国"), app("支付宝"), app("知乎")),
        )
        assertEquals(listOf("Z"), sections.map { it.letter })
        assertEquals(
            listOf("支付宝", "知乎", "中国"),
            sections.single().apps.map { it.title },
        )
    }

    @Test
    fun `flatten interleaves headers and records section first indices`() {
        val sections = AllAppsIndex.buildSections(
            listOf(app("Banana"), app("Apple"), app("123")),
        )
        val flat = AllAppsIndex.flatten(sections)
        assertEquals(
            listOf("H:A", "Apple", "H:B", "Banana", "H:#", "123"),
            flat.items.map {
                when (it) {
                    is AllAppsIndex.Item.Header -> "H:${it.letter}"
                    is AllAppsIndex.Item.Entry -> it.app.title
                }
            },
        )
        assertEquals(mapOf("A" to 0, "B" to 2, "#" to 4), flat.sectionFirstIndex)
    }
}
