package com.webshell.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.WebAppDao
import com.webshell.core.data.WebAppEntity
import com.webshell.core.data.HomeSettings
import com.webshell.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dao: WebAppDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val apps: StateFlow<List<WebAppEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<HomeSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSettings())

    /** 把 app 移动到 (page, cellIndex)，并同页顺序压实 */
    fun moveApp(appId: String, toPage: Int, toCellIndex: Int) {
        viewModelScope.launch {
            val all = apps.value
            val moved = all.firstOrNull { it.id == appId } ?: return@launch
            val siblings = all.filter {
                it.folderId == null && it.id != appId && it.homePage == toPage
            }.sortedWith(compareBy<WebAppEntity> { it.homeCellIndex }.thenBy { it.createdAt })
                .toMutableList()
            val insertAt = toCellIndex.coerceIn(0, siblings.size)
            siblings.add(insertAt, moved.copy(homePage = toPage))
            val updates = siblings.mapIndexed { index, entity ->
                entity.copy(homePage = toPage, homeCellIndex = index)
            }
            updates.forEach { dao.upsert(it) }
        }
    }

    /**
     * 以“桌面单元格”为单位排序；文件夹的全部成员共享同一个位置。
     * 这避免了直接对 Room 实体列表排序时，一个文件夹被按成员数重复占位。
     */
    fun moveCell(fromKey: String, toKey: String, pageCapacity: Int) {
        if (fromKey == toKey) return
        viewModelScope.launch {
            val cells = HomePages.build(
                apps = apps.value,
                pageCapacity = pageCapacity,
            ).flatten().toMutableList()
            val fromIndex = cells.indexOfFirst { it.key == fromKey }
            val toIndex = cells.indexOfFirst { it.key == toKey }
            if (fromIndex < 0 || toIndex < 0) return@launch
            val moved = cells.removeAt(fromIndex)
            val insertAt = if (fromIndex < toIndex) toIndex - 1 else toIndex
            cells.add(insertAt.coerceIn(0, cells.size), moved)
            persistCellOrder(cells, pageCapacity)
        }
    }

    /** 拖拽普通应用到普通应用/已有文件夹；文件夹自身不再嵌套。 */
    fun createFolder(aKey: String, bKey: String) {
        viewModelScope.launch {
            val all = apps.value
            val cells = HomePages.build(all).flatten()
            val source = cells.firstOrNull { it.key == aKey } ?: return@launch
            val target = cells.firstOrNull { it.key == bKey } ?: return@launch
            if (source.isFolder || source.app.folderId != null) return@launch
            val a = source.app
            val folderId = target.app.folderId
                ?: "f-${a.id.takeLast(4)}${target.app.id.takeLast(4)}"
            dao.upsert(
                a.copy(
                    folderId = folderId,
                    homePage = target.app.homePage,
                    homeCellIndex = target.app.homeCellIndex,
                ),
            )
            if (!target.isFolder) {
                val b = target.app
                dao.upsert(
                    b.copy(
                        folderId = folderId,
                        homePage = b.homePage,
                        homeCellIndex = b.homeCellIndex,
                    ),
                )
            }
        }
    }

    /** 文件夹解散：成员回到原页尾部 */
    fun dissolveFolder(folderId: String) {
        viewModelScope.launch {
            val members = apps.value.filter { it.folderId == folderId }
            val page = members.minOfOrNull { it.homePage } ?: return@launch
            members.forEachIndexed { index, entity ->
                dao.upsert(entity.copy(folderId = null, homePage = page, homeCellIndex = -1 - index))
            }
        }
    }

    /** 从文件夹中移出单个应用 */
    fun removeFromFolder(appId: String) {
        viewModelScope.launch {
            apps.value.firstOrNull { it.id == appId }?.let {
                dao.upsert(it.copy(folderId = null, homeCellIndex = -1))
            }
        }
    }

    fun toggleDesktopMode(appId: String) {
        viewModelScope.launch {
            apps.value.firstOrNull { it.id == appId }?.let {
                dao.upsert(it.copy(desktopMode = !it.desktopMode))
            }
        }
    }

    fun toggleKeepAlive(appId: String) {
        viewModelScope.launch {
            apps.value.firstOrNull { it.id == appId }?.let {
                dao.upsert(it.copy(keepAlive = !it.keepAlive))
            }
        }
    }

    fun delete(appId: String) {
        viewModelScope.launch { dao.deleteById(appId) }
    }

    private suspend fun persistCellOrder(cells: List<HomeCell>, pageCapacity: Int) {
        val updates = buildList {
            cells.forEachIndexed { index, cell ->
                val page = index / pageCapacity.coerceAtLeast(1)
                val cellIndex = index % pageCapacity.coerceAtLeast(1)
                val members = if (cell.isFolder) cell.folderMembers else listOf(cell.app)
                members.forEach { add(it.copy(homePage = page, homeCellIndex = cellIndex)) }
            }
        }
        dao.upsertAll(updates)
    }

    suspend fun appCount(): Int = withContext(kotlinx.coroutines.Dispatchers.IO) {
        apps.value.size
    }
}
