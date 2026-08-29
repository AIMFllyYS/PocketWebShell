package com.webshell.feature.home

import com.webshell.core.data.WebAppEntity

/** 主页网格的一个单元格：普通应用，或聚合了成员的文件夹。 */
data class HomeCell(
    val key: String,
    val app: WebAppEntity,
    val folderMembers: List<WebAppEntity> = emptyList(),
) {
    val isFolder: Boolean get() = folderMembers.isNotEmpty()
}

object HomePages {

    /**
     * 把数据库里的应用列表推导为分页网格。
     * - folderId == null 的应用按 (homePage, homeCellIndex, createdAt) 排序
     * - homeCellIndex == -1 视为"追加"（排在同页末尾，按创建时间）
     * - folderId 相同的应用聚合为一个文件夹单元格，位置取成员的最小 (page, index)
     */
    fun build(
        apps: List<WebAppEntity>,
        minPages: Int = 1,
        pageCapacity: Int = Int.MAX_VALUE,
    ): List<List<HomeCell>> {
        val byId = apps.associateBy { it.id }

        val folderGroups = apps.filter { it.folderId != null && byId.containsKey(it.id) }
            .groupBy { it.folderId!! }

        val folderCells = folderGroups.map { (_, members) ->
            val ordered = members.sortedWith(entityOrder())
            val anchor = ordered.minBy { it.homePage * 100_000 + orderKey(it) }
            HomeCell(
                key = "folder-${anchor.folderId}",
                app = anchor,
                folderMembers = ordered,
            )
        }

        val rootCells = apps.filter { it.folderId == null }
            .map { HomeCell(key = it.id, app = it) }

        val ordered = (rootCells + folderCells).sortedWith(
            compareBy<HomeCell> { it.app.homePage }
                .thenBy { orderKey(it.app) }
                .thenBy { it.app.createdAt },
        )
        val capacity = pageCapacity.coerceAtLeast(1)
        val pages = if (capacity == Int.MAX_VALUE) {
            ordered.groupBy { it.app.homePage }
                .let { map ->
                    val maxPage = (map.keys.maxOrNull() ?: 0).coerceAtLeast(minPages - 1)
                    (0..maxPage).map { page -> map[page].orEmpty() }
                }
        } else {
            ordered.chunked(capacity).ifEmpty { listOf(emptyList()) }.toMutableList().apply {
                // 最后一屏始终给“添加”入口留一个格子，避免满屏时纵向溢出。
                if (lastOrNull()?.size == capacity) add(emptyList())
                while (size < minPages) add(emptyList())
            }
        }
        return pages
    }

    private fun entityOrder(): Comparator<WebAppEntity> =
        compareBy<WebAppEntity> { it.homePage }.thenBy { orderKey(it) }.thenBy { it.createdAt }

    private fun orderKey(entity: WebAppEntity): Int =
        if (entity.homeCellIndex < 0) Int.MAX_VALUE / 2 else entity.homeCellIndex
}
