package com.webshell.feature.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.HomeSlotAllocator
import com.webshell.core.data.SettingsRepository
import com.webshell.core.data.WebAppDao
import com.webshell.core.data.WebAppEntity
import com.webshell.core.webengine.LocalWebHost
import com.webshell.feature.add.metadata.SiteMetadata
import com.webshell.feature.add.metadata.SiteMetadataFetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 第二步（编辑属性）的草稿状态；fetch 失败时也用它做纯手动录入 */
data class AddDraft(
    val appId: String = "",
    val url: String = "",
    val title: String = "",
    val iconUrl: String = "",
    val themeColor: String? = null,
    val desktopMode: Boolean = false,
    val darkMode: Boolean = false,
    val keepAlive: Boolean = true,
    val externalLinksToBrowser: Boolean = false,
    val textZoomPercent: Int = 100,
    val isLocal: Boolean = false,
)

sealed interface AddUiState {
    data object Input : AddUiState

    /** 元数据抓取中 */
    data object Loading : AddUiState

    /** 编辑属性（fetch 成功或降级手动） */
    data class Edit(val draft: AddDraft, val fetchFailed: Boolean = false) : AddUiState
}

@HiltViewModel
class AddViewModel @Inject constructor(
    private val fetcher: SiteMetadataFetcher,
    private val importer: LocalAppImporter,
    private val dao: WebAppDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AddUiState>(AddUiState.Input)
    val state: StateFlow<AddUiState> = _state.asStateFlow()

    private val _created = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val created: SharedFlow<Unit> = _created.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 确认网址：抓取元数据后进入编辑；失败则降级为手动录入（通用地球图标） */
    fun confirmUrl(rawUrl: String) {
        val normalized = normalizeUrl(rawUrl)
        if (normalized == null) {
            _state.value = AddUiState.Input
            return
        }
        _state.value = AddUiState.Loading
        viewModelScope.launch {
            val metadata: SiteMetadata? = fetcher.fetch(normalized).getOrNull()
            when {
                metadata == null -> {
                    _state.value = AddUiState.Edit(
                        draft = AddDraft(appId = newAppId(), url = normalized, title = hostLabel(normalized)),
                        fetchFailed = true,
                    )
                }
                else -> {
                    _state.value = AddUiState.Edit(
                        draft = AddDraft(
                            appId = newAppId(),
                            url = metadata.finalUrl,
                            title = metadata.title,
                            iconUrl = metadata.iconUrl.orEmpty(),
                            themeColor = metadata.themeColor,
                        ),
                    )
                }
            }
        }
    }

    /** 本地 HTML 导入：拷贝文件后直接进入编辑（标题先取文件名） */
    fun importLocal(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        _state.value = AddUiState.Loading
        viewModelScope.launch {
            val appId = newAppId()
            val result = importer.import(appId, uris)
            result.onSuccess { entryUrl ->
                val entryName = java.net.URLDecoder.decode(
                    entryUrl.substringAfterLast('/'),
                    Charsets.UTF_8,
                )
                _state.value = AddUiState.Edit(
                    draft = AddDraft(
                        appId = appId,
                        url = entryUrl,
                        title = entryName.substringBeforeLast('.').ifBlank { "本地应用" },
                        isLocal = true,
                    ),
                )
            }.onFailure { e ->
                _state.value = AddUiState.Input
                _messages.tryEmit("导入失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun updateDraft(transform: (AddDraft) -> AddDraft) {
        val current = _state.value
        if (current is AddUiState.Edit) {
            _state.value = current.copy(draft = transform(current.draft))
        }
    }

    /** 保存到主页数据库 */
    fun save() {
        val current = _state.value as? AddUiState.Edit ?: return
        viewModelScope.launch {
            val d = current.draft
            val settings = settingsRepository.settings.first()
            // 自由摆放：直接落在最后一页的首个空槽；自动整理：-1 追加末尾后压实。
            val (homePage, homeCellIndex) = if (settings.autoArrangeHome) {
                0 to -1
            } else {
                HomeSlotAllocator.appendSlot(
                    apps = dao.observeAll().first(),
                    pageCapacity = (settings.gridColumns * settings.gridRows).coerceAtLeast(1),
                )
            }
            dao.upsert(
                WebAppEntity(
                    id = d.appId.ifBlank { newAppId() },
                    title = d.title.trim().ifBlank { hostLabel(d.url) },
                    url = d.url,
                    iconUrl = d.iconUrl.trim().takeIf {
                        // 远端 favicon 或用户上传的本地图片（应用私有目录绝对路径）
                        it.startsWith("http://") || it.startsWith("https://") || it.startsWith("/")
                    },
                    desktopMode = d.desktopMode,
                    darkMode = d.darkMode,
                    keepAlive = d.keepAlive,
                    isFavorite = false,
                    homePage = homePage,
                    homeCellIndex = homeCellIndex,
                    folderId = null,
                    createdAt = System.currentTimeMillis(),
                    isLocal = d.isLocal,
                    externalLinksToBrowser = d.externalLinksToBrowser,
                    textZoomPercent = d.textZoomPercent,
                ),
            )
            _messages.tryEmit("已添加到主页")
            _created.tryEmit(Unit)
            _state.value = AddUiState.Input
        }
    }

    fun reset() {
        _state.value = AddUiState.Input
    }

    private fun newAppId(): String = "app-${UUID.randomUUID().toString().take(8)}"

    companion object {
        /** 补全 scheme；仅接受 http/https */
        fun normalizeUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = when {
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> "https://$trimmed"
            }
            return try {
                val uri = java.net.URI(withScheme)
                if (uri.host.isNullOrBlank()) null else withScheme
            } catch (_: Exception) {
                null
            }
        }

        fun hostLabel(url: String): String = try {
            java.net.URI(url).host?.removePrefix("www.") ?: url
        } catch (_: Exception) {
            url
        }
    }
}
