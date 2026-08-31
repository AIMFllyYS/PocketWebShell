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

    // ---------- 自由摆放（稀疏网格） ----------

    @Test
    fun `sparse build keeps gaps and does not compact`() {
        val pages = HomePages.buildSparse(
            apps = listOf(
                app("a", cell = 0),
                app("b", cell = 1),
                app("c", cell = 11),
            ),
            pageCapacity = 12,
        )
        assertEquals(1, pages.size)
        assertEquals(12, pages[0].size)
        assertEquals("a", pages[0][0]?.key)
        assertEquals("b", pages[0][1]?.key)
        assertEquals("c", pages[0][11]?.key)
        // 中间空槽保持为空，绝不自动压实
        assertEquals((2..10).toList(), (0 until 12).filter { pages[0][it] == null })
    }

    @Test
    fun `sparse build appends invalid index to first empty slot of last page`() {
        val pages = HomePages.buildSparse(
            apps = listOf(
                app("a", cell = 0),
                app("b", cell = 3),
                app("new", cell = -1, createdAt = 5),
            ),
            pageCapacity = 4,
        )
        assertEquals(1, pages.size)
        // 追加到末页首个空槽（slot 1），而不是填补更早的洞之外的位置
        assertEquals(listOf("a", "new", null, "b"), pages[0].map { it?.key })
    }

    @Test
    fun `sparse build adds trailing empty page when last page is full`() {
        val pages = HomePages.buildSparse(
            apps = (0 until 4).map { app("app-$it", cell = it) },
            pageCapacity = 4,
        )
        assertEquals(2, pages.size)
        assertEquals(0, pages[1].count { it != null })
    }

    @Test
    fun `sparse build spills overflow page from explicit page index`() {
        val pages = HomePages.buildSparse(
            apps = listOf(
                app("a", page = 0, cell = 0),
                app("b", page = 2, cell = 3),
            ),
            pageCapacity = 4,
        )
        // 中间空页保留，页数 = 最大占用页 + 1
        assertEquals(3, pages.size)
        assertEquals("b", pages[2][3]?.key)
    }

    @Test
    fun `slot move to empty slot stays there`() {
        val updates = HomePages.resolveSlotMove(
            apps = listOf(app("a", cell = 0), app("b", cell = 1), app("c", cell = 2)),
            draggedKey = "b",
            toPage = 0,
            toSlot = 11,
            pageCapacity = 12,
        )
        assertEquals(1, updates.size)
        assertEquals("b", updates[0].id)
        assertEquals(0, updates[0].homePage)
        assertEquals(11, updates[0].homeCellIndex)
    }

    @Test
    fun `slot move onto occupied slot swaps both sides`() {
        val updates = HomePages.resolveSlotMove(
            apps = listOf(app("a", cell = 0), app("b", cell = 1), app("c", cell = 6)),
            draggedKey = "b",
            toPage = 0,
            toSlot = 6,
            pageCapacity = 12,
        ).associateBy { it.id }
        assertEquals(6, updates.getValue("b").homeCellIndex)
        // 被占用方换到拖拽方的原槽位
        assertEquals(1, updates.getValue("c").homeCellIndex)
        assertEquals(2, updates.size)
    }

    @Test
    fun `slot move onto same slot is a no-op`() {
        val updates = HomePages.resolveSlotMove(
            apps = listOf(app("a", cell = 0), app("b", cell = 5)),
            draggedKey = "b",
            toPage = 0,
            toSlot = 5,
            pageCapacity = 12,
        )
        assertEquals(0, updates.size)
    }

    @Test
    fun `slot move swaps folder with app as a whole`() {
        val updates = HomePages.resolveSlotMove(
            apps = listOf(
                app("a", cell = 0),
                app("x", cell = 4, folder = "f1"),
                app("y", cell = 4, folder = "f1"),
            ),
            draggedKey = "folder-f1",
            toPage = 0,
            toSlot = 0,
            pageCapacity = 12,
        ).associateBy { it.id }
        // 文件夹全体成员落到目标槽，占用方 a 换到文件夹原槽位
        assertEquals(0, updates.getValue("x").homeCellIndex)
        assertEquals(0, updates.getValue("y").homeCellIndex)
        assertEquals(4, updates.getValue("a").homeCellIndex)
        assertEquals(3, updates.size)
    }

    @Test
    fun `dissolve places members from folder slot into following empty slots`() {
        val updates = HomePages.resolveDissolve(
            apps = listOf(
                app("a", cell = 0),
                app("x", cell = 5, folder = "f1"),
                app("y", cell = 5, folder = "f1"),
                app("b", cell = 6),
            ),
            folderId = "f1",
            pageCapacity = 12,
        ).associateBy { it.id }
        // 首个成员占文件夹原槽位 5；次成员跳过占用的 6，落到 7
        assertEquals(null, updates.getValue("x").folderId)
        assertEquals(5, updates.getValue("x").homeCellIndex)
        assertEquals(7, updates.getValue("y").homeCellIndex)
        assertEquals(2, updates.size)
    }

    @Test
    fun `remove from folder takes first empty slot after folder`() {
        val updated = HomePages.resolveRemoveFromFolder(
            apps = listOf(
                app("a", cell = 0),
                app("x", cell = 5, folder = "f1"),
                app("y", cell = 5, folder = "f1"),
            ),
            appId = "y",
            pageCapacity = 12,
        )!!
        assertEquals(null, updated.folderId)
        assertEquals(0, updated.homePage)
        assertEquals(6, updated.homeCellIndex)
    }
}
