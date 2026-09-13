package com.webshell.feature.home

import com.webshell.core.data.WebAppEntity

/** 主页网格的一个单元格：普通应用，或聚合了成员的文件夹。 */
data class HomeCell(
    val key: String,
    val app: WebAppEntity,
    val folderMembers: List<WebAppEntity> = emptyList(),
    /** 文件夹名：成员中第一个非空 folderName；null → UI 回落到「文件夹」。 */
    val folderName: String? = null,
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
        val ordered = aggregateCells(apps)
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

    /**
     * 自由摆放（稀疏）分页：每个 cell 保持自己持久的 (homePage, homeCellIndex)，
     * 空槽以 null 占位 —— 拖到哪儿就停在哪儿，绝不自动压实。
     * - homeCellIndex 无效（-1/越界/槽位冲突）按“追加”处理：最后一页首个空槽，末页已满则开新页；
     * - 页数 = 最大占用页 + 1（自动裁掉尾部空页）；
     * - 末页全满时额外补一页全空页，给“添加”入口留槽位（与密集模式一致）。
     */
    fun buildSparse(
        apps: List<WebAppEntity>,
        pageCapacity: Int,
        minPages: Int = 1,
    ): List<List<HomeCell?>> {
        val capacity = pageCapacity.coerceAtLeast(1)
        val cells = aggregateCells(apps)
        val pages = mutableListOf<MutableList<HomeCell?>>()
        fun ensurePage(page: Int) {
            while (pages.size <= page) {
                pages += MutableList<HomeCell?>(capacity) { null }
            }
        }
        fun placeAppend(cell: HomeCell) {
            var target = pages.size - 1
            var slot = pages[target].indexOfFirst { it == null }
            if (slot < 0) {
                ensurePage(target + 1)
                target += 1
                slot = 0
            }
            pages[target][slot] = cell
        }
        val maxPage = cells.maxOfOrNull { it.app.homePage.coerceAtLeast(0) } ?: 0
        ensurePage(maxOf(maxPage, minPages - 1))
        // 先放有明确槽位的（按 页/槽/创建时间 的顺序，冲突时先来先得，后到追加，保证确定性）
        val (placed, appended) = cells.partition { it.app.homeCellIndex in 0 until capacity }
        placed.forEach { cell ->
            val page = cell.app.homePage.coerceAtLeast(0)
            ensurePage(page)
            val slot = cell.app.homeCellIndex
            if (pages[page][slot] == null) pages[page][slot] = cell else placeAppend(cell)
        }
        appended.forEach { placeAppend(it) }
        if (pages.last().all { it != null }) {
            pages += MutableList<HomeCell?>(capacity) { null }
        }
        return pages
    }

    /**
     * 自由摆放落子：把 [draggedKey] 移动到 (toPage, toSlot)。
     * 目标槽为空 → 直接落子；被占用 → 双方交换槽位（文件夹整体交换）。
     * 返回需要落库的实体列表；空列表 = 无操作（原地松手等）。
     */
    fun resolveSlotMove(
        apps: List<WebAppEntity>,
        draggedKey: String,
        toPage: Int,
        toSlot: Int,
        pageCapacity: Int,
    ): List<WebAppEntity> {
        val capacity = pageCapacity.coerceAtLeast(1)
        if (toPage < 0 || toSlot !in 0 until capacity) return emptyList()
        val sparse = buildSparse(apps, capacity)
        val positions = HashMap<String, Pair<Int, Int>>()
        val cellsByKey = HashMap<String, HomeCell>()
        sparse.forEachIndexed { page, slots ->
            slots.forEachIndexed { slot, cell ->
                if (cell != null) {
                    positions[cell.key] = page to slot
                    cellsByKey[cell.key] = cell
                }
            }
        }
        val from = positions[draggedKey] ?: return emptyList()
        if (from == toPage to toSlot) return emptyList()
        val dragged = cellsByKey.getValue(draggedKey)
        val occupant = cellsByKey.values.firstOrNull {
            it.key != draggedKey && positions[it.key] == toPage to toSlot
        }
        val updates = ArrayList<WebAppEntity>()
        val draggedEntities = if (dragged.isFolder) dragged.folderMembers else listOf(dragged.app)
        draggedEntities.mapTo(updates) { it.copy(homePage = toPage, homeCellIndex = toSlot) }
        if (occupant != null) {
            val occupantEntities = if (occupant.isFolder) occupant.folderMembers else listOf(occupant.app)
            occupantEntities.mapTo(updates) {
                it.copy(homePage = from.first, homeCellIndex = from.second)
            }
        }
        return updates
    }

    /**
     * 首屏左缘开新屏落子：被拖 cell 落到新首屏 (0, toSlot)，其余所有实体页号 +1
     * （文件夹成员随 anchor 一起后移，相对槽位不变）。
     * 返回需要落库的实体列表；空列表 = 无操作。
     */
    fun resolvePrependMove(
        apps: List<WebAppEntity>,
        draggedKey: String,
        toSlot: Int,
        pageCapacity: Int,
    ): List<WebAppEntity> {
        val capacity = pageCapacity.coerceAtLeast(1)
        if (toSlot !in 0 until capacity) return emptyList()
        val dragged = aggregateCells(apps).firstOrNull { it.key == draggedKey } ?: return emptyList()
        val draggedIds = (if (dragged.isFolder) dragged.folderMembers else listOf(dragged.app))
            .mapTo(HashSet()) { it.id }
        return apps.map { entity ->
            if (entity.id in draggedIds) {
                entity.copy(homePage = 0, homeCellIndex = toSlot)
            } else {
                entity.copy(homePage = entity.homePage.coerceAtLeast(0) + 1)
            }
        }
    }

    /**
     * 多选组落子：锚点占目标槽，其余按原相对顺序占后续空槽。
     * 组内原槽先释放；溢出到下一页；被挤占的非组成员随后续空槽安置。
     */
    fun resolveGroupMove(
        apps: List<WebAppEntity>,
        groupKeys: Set<String>,
        anchorKey: String,
        toPage: Int,
        toSlot: Int,
        pageCapacity: Int,
    ): List<WebAppEntity> {
        val capacity = pageCapacity.coerceAtLeast(1)
        if (toPage < 0 || toSlot !in 0 until capacity || groupKeys.isEmpty()) return emptyList()
        val sparse = buildSparse(apps, capacity)
        val cellsByKey = HashMap<String, HomeCell>()
        val positions = HashMap<String, Pair<Int, Int>>()
        sparse.forEachIndexed { page, slots ->
            slots.forEachIndexed { slot, cell ->
                if (cell != null) {
                    cellsByKey[cell.key] = cell
                    positions[cell.key] = page to slot
                }
            }
        }
        val group = groupKeys.mapNotNull { cellsByKey[it] }
            .sortedWith(
                compareBy<HomeCell> { positions[it.key]?.first ?: Int.MAX_VALUE }
                    .thenBy { positions[it.key]?.second ?: Int.MAX_VALUE },
            )
        if (group.isEmpty()) return emptyList()
        val anchor = cellsByKey[anchorKey] ?: group.first()
        val occupied = HashSet<Pair<Int, Int>>()
        cellsByKey.values.forEach { cell ->
            if (cell.key !in groupKeys) occupied += positions.getValue(cell.key)
        }
        val occupant = cellsByKey.values.firstOrNull {
            it.key !in groupKeys && positions[it.key] == toPage to toSlot
        }
        val placements = LinkedHashMap<String, Pair<Int, Int>>()
        placements[anchor.key] = toPage to toSlot
        occupied += toPage to toSlot
        var cursor = toPage * capacity + toSlot + 1
        group.forEach { cell ->
            if (cell.key == anchor.key) return@forEach
            while ((cursor / capacity to cursor % capacity) in occupied) cursor++
            val dest = cursor / capacity to cursor % capacity
            placements[cell.key] = dest
            occupied += dest
            cursor++
        }
        if (occupant != null) {
            while ((cursor / capacity to cursor % capacity) in occupied) cursor++
            placements[occupant.key] = cursor / capacity to cursor % capacity
        }
        val updates = ArrayList<WebAppEntity>()
        placements.forEach { (key, dest) ->
            val cell = cellsByKey.getValue(key)
            val entities = if (cell.isFolder) cell.folderMembers else listOf(cell.app)
            entities.mapTo(updates) { it.copy(homePage = dest.first, homeCellIndex = dest.second) }
        }
        return updates
    }

    /**
     * 左侧临时屏组落子：非组成员页号 +1，组落到新首屏。
     * 锚点占 [toSlot]，其余按原相对顺序占后续空槽，溢出到已后移的页。
     */
    fun resolvePrependGroupMove(
        apps: List<WebAppEntity>,
        groupKeys: Set<String>,
        anchorKey: String,
        toSlot: Int,
        pageCapacity: Int,
    ): List<WebAppEntity> {
        val capacity = pageCapacity.coerceAtLeast(1)
        if (toSlot !in 0 until capacity || groupKeys.isEmpty()) return emptyList()
        val cells = aggregateCells(apps)
        val group = cells.filter { it.key in groupKeys }
            .sortedWith(
                compareBy<HomeCell> { it.app.homePage }
                    .thenBy { orderKey(it.app) }
                    .thenBy { it.app.createdAt },
            )
        if (group.isEmpty()) return emptyList()
        val anchor = group.firstOrNull { it.key == anchorKey } ?: group.first()
        val groupEntityIds = HashSet<String>()
        group.forEach { cell ->
            if (cell.isFolder) cell.folderMembers.forEach { groupEntityIds += it.id }
            else groupEntityIds += cell.app.id
        }
        val occupied = HashSet<Pair<Int, Int>>()
        cells.forEach { cell ->
            if (cell.key !in groupKeys) {
                occupied += (cell.app.homePage.coerceAtLeast(0) + 1) to
                    cell.app.homeCellIndex.coerceAtLeast(0)
            }
        }
        val placements = LinkedHashMap<String, Pair<Int, Int>>()
        placements[anchor.key] = 0 to toSlot
        occupied += 0 to toSlot
        var cursor = toSlot + 1
        group.forEach { cell ->
            if (cell.key == anchor.key) return@forEach
            while ((cursor / capacity to cursor % capacity) in occupied) cursor++
            val dest = cursor / capacity to cursor % capacity
            placements[cell.key] = dest
            occupied += dest
            cursor++
        }
        val destById = HashMap<String, Pair<Int, Int>>()
        placements.forEach { (key, dest) ->
            val cell = group.first { it.key == key }
            val members = if (cell.isFolder) cell.folderMembers else listOf(cell.app)
            members.forEach { destById[it.id] = dest }
        }
        return apps.map { entity ->
            val dest = destById[entity.id]
            if (dest != null) {
                entity.copy(homePage = dest.first, homeCellIndex = dest.second)
            } else {
                entity.copy(homePage = entity.homePage.coerceAtLeast(0) + 1)
            }
        }
    }

    /**
     * 自由摆放解散文件夹：首个成员占文件夹原槽位，其余成员从该槽位起顺序占空槽。
     * 返回需要落库的实体列表（folderId 已置空）。
     */
    fun resolveDissolve(
        apps: List<WebAppEntity>,
        folderId: String,
        pageCapacity: Int,
    ): List<WebAppEntity> {
        val capacity = pageCapacity.coerceAtLeast(1)
        val members = apps.filter { it.folderId == folderId }.sortedWith(folderMemberOrder())
        if (members.isEmpty()) return emptyList()
        val sparse = buildSparse(apps, capacity)
        val occupied = HashSet<Pair<Int, Int>>()
        var folderSlot = 0
        sparse.forEachIndexed { page, slots ->
            slots.forEachIndexed { slot, cell ->
                if (cell != null) {
                    occupied += page to slot
                    if (cell.key == "folder-$folderId") folderSlot = page * capacity + slot
                }
            }
        }
        // 文件夹自身槽位释放给首个成员
        occupied -= folderSlot / capacity to folderSlot % capacity
        val updates = ArrayList<WebAppEntity>(members.size)
        var cursor = folderSlot
        members.forEach { member ->
            while ((cursor / capacity to cursor % capacity) in occupied) cursor++
            occupied += cursor / capacity to cursor % capacity
            updates += member.copy(
                folderId = null,
                folderName = null,
                folderCellIndex = null,
                homePage = cursor / capacity,
                homeCellIndex = cursor % capacity,
            )
            cursor++
        }
        return updates
    }

    /** 自由摆放移出文件夹：从文件夹槽位起取第一个空槽。 */
    fun resolveRemoveFromFolder(
        apps: List<WebAppEntity>,
        appId: String,
        pageCapacity: Int,
    ): WebAppEntity? {
        val capacity = pageCapacity.coerceAtLeast(1)
        val app = apps.firstOrNull { it.id == appId } ?: return null
        val folderId = app.folderId ?: return null
        val sparse = buildSparse(apps, capacity)
        val occupied = HashSet<Pair<Int, Int>>()
        var cursor = 0
        sparse.forEachIndexed { page, slots ->
            slots.forEachIndexed { slot, cell ->
                if (cell != null) {
                    occupied += page to slot
                    if (cell.key == "folder-$folderId") cursor = page * capacity + slot
                }
            }
        }
        while ((cursor / capacity to cursor % capacity) in occupied) cursor++
        return app.copy(
            folderId = null,
            folderName = null,
            folderCellIndex = null,
            homePage = cursor / capacity,
            homeCellIndex = cursor % capacity,
        )
    }

    /**
     * 批量删除：文件夹 key 展开为全部成员 id，普通 key 即应用 id。
     * 返回需要从主屏与资源库一并删除的应用 id。
     */
    fun resolveDeleteMany(apps: List<WebAppEntity>, keys: Set<String>): Set<String> {
        if (keys.isEmpty()) return emptySet()
        val ids = HashSet<String>()
        keys.forEach { key ->
            if (key.startsWith("folder-")) {
                val folderId = key.removePrefix("folder-")
                apps.forEach { entity ->
                    if (entity.folderId == folderId) ids += entity.id
                }
            } else {
                if (apps.any { it.id == key }) ids += key
            }
        }
        return ids
    }

    /**
     * 批量移出文件夹：一次算完互不相同的空槽，避免循环调用
     * [resolveRemoveFromFolder] 把多个图标写进同一空槽。
     */
    fun resolveRemoveManyFromFolder(
        apps: List<WebAppEntity>,
        appIds: Collection<String>,
        pageCapacity: Int,
    ): List<WebAppEntity> {
        val capacity = pageCapacity.coerceAtLeast(1)
        val idSet = appIds.toSet()
        val removing = apps.filter { it.id in idSet && it.folderId != null }
            .sortedWith(
                compareBy<WebAppEntity> { it.homePage }
                    .thenBy { it.homeCellIndex }
                    .then(folderMemberOrder()),
            )
        if (removing.isEmpty()) return emptyList()
        val sparse = buildSparse(apps, capacity)
        val occupied = HashSet<Pair<Int, Int>>()
        val folderSlot = HashMap<String, Int>()
        sparse.forEachIndexed { page, slots ->
            slots.forEachIndexed { slot, cell ->
                if (cell != null) {
                    occupied += page to slot
                    if (cell.isFolder) {
                        cell.app.folderId?.let { folderSlot[it] = page * capacity + slot }
                    }
                }
            }
        }
        val updates = ArrayList<WebAppEntity>(removing.size)
        val cursors = HashMap<String, Int>()
        removing.forEach { app ->
            val folderId = app.folderId!!
            var cursor = cursors[folderId] ?: (folderSlot[folderId] ?: 0)
            while ((cursor / capacity to cursor % capacity) in occupied) cursor++
            occupied += cursor / capacity to cursor % capacity
            updates += app.copy(
                folderId = null,
                folderName = null,
                folderCellIndex = null,
                homePage = cursor / capacity,
                homeCellIndex = cursor % capacity,
            )
            cursors[folderId] = cursor + 1
        }
        val removedIds = removing.mapTo(HashSet()) { it.id }
        val affectedFolders = removing.mapNotNullTo(HashSet()) { it.folderId }
        apps.filter { it.folderId in affectedFolders && it.id !in removedIds }
            .groupBy { it.folderId!! }
            .forEach { (_, members) ->
                members.sortedWith(folderMemberOrder()).forEachIndexed { index, entity ->
                    updates += entity.copy(folderCellIndex = index)
                }
            }
        return updates
    }

    /**
     * 批量移入文件夹：目标文件夹成员的 folderCellIndex 重写为稠密 0..n-1，
     * 全部成员共享 [anchorPage]/[anchorSlot]（选中集合的最小页/槽）。
     */
    fun resolveMoveManyToFolder(
        apps: List<WebAppEntity>,
        keys: Set<String>,
        folderId: String,
        anchorPage: Int,
        anchorSlot: Int,
        pageCapacity: Int,
    ): List<WebAppEntity> {
        val capacity = pageCapacity.coerceAtLeast(1)
        if (folderId.isEmpty() || anchorPage < 0 || anchorSlot !in 0 until capacity) return emptyList()
        val moving = keys.mapNotNull { key ->
            if (key.startsWith("folder-")) null else apps.firstOrNull { it.id == key }
        }
        if (moving.isEmpty()) return emptyList()
        val existing = apps.filter { it.folderId == folderId }
            .sortedWith(folderMemberOrder())
        val movingIds = moving.mapTo(HashSet()) { it.id }
        val newcomers = moving
            .filter { it.folderId != folderId }
            .sortedWith(
                compareBy<WebAppEntity> { it.homePage }
                    .thenBy { orderKey(it) }
                    .thenBy { it.createdAt },
            )
        val folderName = existing.firstNotNullOfOrNull { it.folderName }
        val combined = existing + newcomers
        val updates = combined.mapIndexed { index, entity ->
            entity.copy(
                folderId = folderId,
                folderName = folderName ?: entity.folderName,
                folderCellIndex = index,
                homePage = anchorPage,
                homeCellIndex = anchorSlot,
            )
        }.toMutableList()
        val affectedFolders = moving.mapNotNullTo(HashSet()) { it.folderId }
        affectedFolders.remove(folderId)
        apps.filter { it.folderId in affectedFolders && it.id !in movingIds }
            .groupBy { it.folderId!! }
            .forEach { (_, members) ->
                members.sortedWith(folderMemberOrder()).forEachIndexed { index, entity ->
                    updates += entity.copy(folderCellIndex = index)
                }
            }
        return updates
    }

    /**
     * 文件夹内把 fromIndex 成员移动到 toIndex，返回需要落库的成员
     * （folderCellIndex 重写为稠密 0..n-1）。越界/相同索引返回 emptyList。
     * [members] 须已按 folderMemberOrder 排序（与展开视图的全局下标一致）。
     */
    fun resolveFolderMemberMove(
        members: List<WebAppEntity>,
        fromIndex: Int,
        toIndex: Int,
    ): List<WebAppEntity> {
        if (fromIndex == toIndex) return emptyList()
        if (fromIndex !in members.indices || toIndex !in members.indices) return emptyList()
        val reordered = members.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        return reordered.mapIndexed { index, entity -> entity.copy(folderCellIndex = index) }
    }

    /**
     * 纵向滚动模式：把分页网格摊平成一条列表 —— 每页补空槽到满容量后首尾相接。
     * 全局下标与持久化坐标的换算固定为 page = index / capacity、slot = index % capacity，
     * 落子仍走 (homePage, homeCellIndex)，数据模型不变。
     */
    fun flatten(pages: List<List<HomeCell?>>, pageCapacity: Int): List<HomeCell?> {
        val capacity = pageCapacity.coerceAtLeast(1)
        val out = ArrayList<HomeCell?>(pages.size * capacity)
        pages.forEach { page ->
            out.addAll(page.take(capacity))
            repeat((capacity - page.size).coerceAtLeast(0)) { out.add(null) }
        }
        return out
    }

    /**
     * 根级应用 + 文件夹聚合为 cell 列表，按 (页, 槽, 创建时间) 排序。
     * 文件夹成员按 folderMemberOrder 排序；文件夹名取成员中第一个非空 folderName。
     */
    private fun aggregateCells(apps: List<WebAppEntity>): List<HomeCell> {
        val byId = apps.associateBy { it.id }

        val folderGroups = apps.filter { it.folderId != null && byId.containsKey(it.id) }
            .groupBy { it.folderId!! }

        val folderCells = folderGroups.map { (_, members) ->
            val ordered = members.sortedWith(folderMemberOrder())
            val anchor = ordered.minBy { it.homePage * 100_000 + orderKey(it) }
            HomeCell(
                key = "folder-${anchor.folderId}",
                app = anchor,
                folderMembers = ordered,
                folderName = ordered.firstNotNullOfOrNull { it.folderName },
            )
        }

        val rootCells = apps.filter { it.folderId == null }
            .map { HomeCell(key = it.id, app = it) }

        return (rootCells + folderCells).sortedWith(
            compareBy<HomeCell> { it.app.homePage }
                .thenBy { orderKey(it.app) }
                .thenBy { it.app.createdAt },
        )
    }

    /**
     * 文件夹成员排序：folderCellIndex（null 视为排最后）→ createdAt。
     * 成员共享同一 (homePage, homeCellIndex)，网格坐标不参与成员间排序；
     * 历史数据（folderCellIndex 全 null）退化为按创建时间，与旧行为一致。
     */
    internal fun folderMemberOrder(): Comparator<WebAppEntity> =
        compareBy<WebAppEntity> { it.folderCellIndex ?: Int.MAX_VALUE }.thenBy { it.createdAt }

    private fun orderKey(entity: WebAppEntity): Int =
        if (entity.homeCellIndex < 0) Int.MAX_VALUE / 2 else entity.homeCellIndex
}
