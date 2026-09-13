package com.webshell.app.download

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.DownloadRepository
import com.webshell.core.data.SettingsRepository
import com.webshell.core.model.DownloadItem
import com.webshell.core.model.DownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DownloadOpenTarget {
    Folder,
    SystemDownloads,
    File,
    Share,
}

data class DownloadOpenCommand(
    val documentUri: Uri?,
    val fileUri: Uri?,
    val displayName: String,
    val target: DownloadOpenTarget? = null,
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _historyOpen = MutableStateFlow(false)
    val historyOpen: StateFlow<Boolean> = _historyOpen.asStateFlow()

    val records: StateFlow<List<DownloadItem>> = repository.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _menuOpen = MutableStateFlow(false)
    val menuOpen: StateFlow<Boolean> = _menuOpen.asStateFlow()

    private val _openFailed = MutableStateFlow(false)
    val openFailed: StateFlow<Boolean> = _openFailed.asStateFlow()

    private val _openCommands = MutableSharedFlow<DownloadOpenCommand>(extraBufferCapacity = 4)
    val openCommands = _openCommands.asSharedFlow()
    private var lastOpen: DownloadOpenCommand? = null

    private val _capsuleParked = MutableStateFlow(false)
    val capsuleParked: StateFlow<Boolean> = _capsuleParked.asStateFlow()

    private val _capsuleY = MutableStateFlow(DownloadCapsuleGeometry.DEFAULT_Y)
    val capsuleY: StateFlow<Float> = _capsuleY.asStateFlow()

    val capsuleEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.downloadCapsuleEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val capsuleItem: StateFlow<DownloadItem?> = repository.visibleItems
        .map { items ->
            items.lastOrNull { it.status == DownloadStatus.Running || it.status == DownloadStatus.Queued }
                ?: items.lastOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onCapsuleTap() {
        _openFailed.value = false
        _menuOpen.value = true
    }

    fun dismissMenu() {
        _menuOpen.value = false
    }

    fun setCapsulePlacement(parked: Boolean, y: Float) {
        _capsuleParked.value = parked
        _capsuleY.value = DownloadCapsuleGeometry.normalizeY(y)
    }

    fun hideOrb() {
        _menuOpen.value = false
        viewModelScope.launch {
            settingsRepository.setDownloadCapsuleEnabled(false)
        }
    }

    fun openFolder() = emitOpen(DownloadOpenTarget.Folder)

    fun openSystemDownloads() = emitOpen(DownloadOpenTarget.SystemDownloads)

    fun openFile() = emitOpen(DownloadOpenTarget.File)

    fun shareFile() = emitOpen(DownloadOpenTarget.Share)

    fun openDestination() {
        val item = capsuleItem.value ?: return
        emit(item, target = null)
    }

    fun markOpenFailed() {
        _openFailed.value = true
        _menuOpen.value = true
    }

    fun dismissCapsule() {
        val id = capsuleItem.value?.id ?: return
        _menuOpen.value = false
        repository.dismiss(id)
    }

    fun showHistory() {
        _menuOpen.value = false
        _historyOpen.value = true
    }

    fun dismissHistory() {
        _historyOpen.value = false
    }

    fun removeRecord(id: Long) {
        if (capsuleItem.value?.id == id) {
            _menuOpen.value = false
        }
        repository.remove(id)
    }

    fun openRecord(item: DownloadItem) {
        emit(item, target = null)
    }

    private fun emitOpen(target: DownloadOpenTarget) {
        val item = capsuleItem.value ?: return
        _menuOpen.value = false
        emit(item, target)
    }

    fun lastOpenCommand(): DownloadOpenCommand? = lastOpen

    private fun emit(item: DownloadItem, target: DownloadOpenTarget?) {
        val documentUri = item.documentUri?.toUriOrNull()
        val fileUri = repository.downloadedFileUri(item.id) ?: documentUri
        val command = DownloadOpenCommand(documentUri, fileUri, item.displayName, target)
        lastOpen = command
        _openFailed.value = false
        _openCommands.tryEmit(command)
    }
}

private fun String.toUriOrNull(): Uri? = runCatching { toUri() }.getOrNull()
