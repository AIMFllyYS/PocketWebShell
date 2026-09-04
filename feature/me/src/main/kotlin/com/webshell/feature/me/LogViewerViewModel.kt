package com.webshell.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.LogEntity
import com.webshell.core.data.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LogViewerUiState(
    /** 已加载的条目（最新在前） */
    val entries: List<LogEntity> = emptyList(),
    val tags: List<String> = emptyList(),
    val tagFilter: String? = null,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    /** 当前过滤条件下的总条数 */
    val totalCount: Int = 0,
)

/**
 * 日志查看页状态中枢：从 Room 分页读取（每页 [PAGE_SIZE] 条），
 * 标签过滤变化时重置分页重查；清空后自动刷新。
 */
@HiltViewModel
class LogViewerViewModel @Inject constructor(
    private val logRepository: LogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LogViewerUiState())
    val state: StateFlow<LogViewerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val tag = _state.value.tagFilter
            val entries = logRepository.page(PAGE_SIZE, 0, tag)
            val total = logRepository.count(tag)
            _state.value = _state.value.copy(
                entries = entries,
                tags = logRepository.tags(),
                totalCount = total,
                hasMore = entries.size < total,
                loadingMore = false,
            )
        }
    }

    /** 滚动到底部时追加下一页；无硬性总上限 */
    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasMore) return
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            val more = logRepository.page(PAGE_SIZE, s.entries.size, s.tagFilter)
            _state.value = _state.value.copy(
                entries = _state.value.entries + more,
                loadingMore = false,
                hasMore = more.size >= PAGE_SIZE,
            )
        }
    }

    fun setTagFilter(tag: String?) {
        if (_state.value.tagFilter == tag) return
        _state.value = _state.value.copy(tagFilter = tag, entries = emptyList(), hasMore = false)
        refresh()
    }

    fun clear() {
        viewModelScope.launch {
            logRepository.clear()
            refresh()
        }
    }

    /** 导出当前过滤条件下的全部条目（不只是已加载页） */
    suspend fun exportText(): String = logRepository.exportAllText(_state.value.tagFilter)

    companion object {
        const val PAGE_SIZE = 30
    }
}
