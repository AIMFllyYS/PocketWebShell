package com.webshell.app.download

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.DownloadRepository
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

data class DownloadOpenCommand(
    val documentUri: Uri?,
    val fileUri: Uri?,
    val displayName: String,
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repository: DownloadRepository,
) : ViewModel() {
    private val _historyOpen = MutableStateFlow(false)
    val historyOpen: StateFlow<Boolean> = _historyOpen.asStateFlow()

    val records: StateFlow<List<DownloadItem>> = repository.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _cardOpen = MutableStateFlow(false)
    val cardOpen: StateFlow<Boolean> = _cardOpen.asStateFlow()

    private val _openFailed = MutableStateFlow(false)
    val openFailed: StateFlow<Boolean> = _openFailed.asStateFlow()

    private val _openCommands = MutableSharedFlow<DownloadOpenCommand>(extraBufferCapacity = 1)
    val openCommands = _openCommands.asSharedFlow()

    val capsuleItem: StateFlow<DownloadItem?> = repository.visibleItems
        .map { items ->
            items.lastOrNull { it.status == DownloadStatus.Running || it.status == DownloadStatus.Queued }
                ?: items.lastOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onCapsuleTap() {
        val item = capsuleItem.value ?: return
        if (item.status == DownloadStatus.Success || item.status == DownloadStatus.Failed) {
            _openFailed.value = false
            _cardOpen.value = true
        }
    }

    fun dismissCard() {
        _cardOpen.value = false
    }

    fun openDestination() {
        val item = capsuleItem.value ?: return
        val documentUri = item.documentUri?.toUriOrNull()
        val fileUri = repository.downloadedFileUri(item.id) ?: documentUri
        _openCommands.tryEmit(DownloadOpenCommand(documentUri, fileUri, item.displayName))
    }

    fun markOpenFailed() {
        _openFailed.value = true
    }

    fun dismissCapsule() {
        val id = capsuleItem.value?.id ?: return
        _cardOpen.value = false
        repository.dismiss(id)
    }

    fun showHistory() {
        _historyOpen.value = true
    }

    fun dismissHistory() {
        _historyOpen.value = false
    }

    fun removeRecord(id: Long) {
        if (capsuleItem.value?.id == id) {
            _cardOpen.value = false
        }
        repository.remove(id)
    }

    fun openRecord(item: DownloadItem) {
        val documentUri = item.documentUri?.toUriOrNull()
        val fileUri = repository.downloadedFileUri(item.id) ?: documentUri
        _openCommands.tryEmit(DownloadOpenCommand(documentUri, fileUri, item.displayName))
    }
}

private fun String.toUriOrNull(): Uri? = runCatching { toUri() }.getOrNull()
