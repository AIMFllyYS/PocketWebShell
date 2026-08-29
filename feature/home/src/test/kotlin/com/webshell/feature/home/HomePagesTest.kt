package com.webshell.feature.home

import com.webshell.core.data.WebAppEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePagesTest {

    private fun app(
        id: String,
        page: Int = 0,
        cell: Int = -1,
        folder: String? = null,
        createdAt: Long = 0L,
    ) = WebAppEntity(
        id = id,
        title = id,
        url = "https://example.com/$id",
        iconUrl = null,
        desktopMode = false,
        darkMode = false,
        keepAlive = false,
        isFavorite = false,
        homePage = page,
        homeCellIndex = cell,
        folderId = folder,
        createdAt = createdAt,
    )

    @Test
    fun `empty list yields one empty page`() {
        val pages = HomePages.build(emptyList())
        assertEquals(1, pages.size)
        assertEquals(0, pages[0].size)
    }

    @Test
    fun `cells sorted by index then creation`() {
        val pages = HomePages.build(
            listOf(
                app("b", cell = 1, createdAt = 1),
                app("a", cell = 0, createdAt = 2),
                app("c", cell = -1, createdAt = 1),
            ),
        )
        assertEquals(listOf("a", "b", "c"), pages[0].map { it.key })
    }

    @Test
    fun `folder members aggregate to anchor cell`() {
        val pages = HomePages.build(
            listOf(
                app("a", cell = 0),
                app("x", cell = 2, folder = "f1"),
                app("y", cell = 5, folder = "f1"),
            ),
        )
        assertEquals(2, pages[0].size)
        val folder = pages[0].first { it.isFolder }
        assertEquals(listOf("x", "y"), folder.folderMembers.map { it.id })
        // 锚点取成员最小 cell → 文件夹排在 cell=2 位置，即 a(0) 之后
        assertEquals(listOf("a", "folder-f1"), pages[0].map { it.key })
    }

    @Test
    fun `large desktop is split into fixed capacity pages`() {
        val pages = HomePages.build(
            apps = (0 until 9).map { app("app-$it", cell = it) },
            pageCapacity = 4,
        )
        assertEquals(listOf(4, 4, 1), pages.map { it.size })
        assertEquals((0 until 9).map { "app-$it" }, pages.flatten().map { it.key })
    }

    @Test
    fun `full last page gets an empty page for add tile`() {
        val pages = HomePages.build(
            apps = (0 until 8).map { app("app-$it", cell = it) },
            pageCapacity = 4,
        )
        assertEquals(listOf(4, 4, 0), pages.map { it.size })
    }
}
