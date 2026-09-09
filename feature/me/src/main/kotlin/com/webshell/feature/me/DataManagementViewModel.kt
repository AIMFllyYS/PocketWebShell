package com.webshell.feature.me

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.backup.BackupFormatException
import com.webshell.core.data.backup.BackupRepository
import com.webshell.core.data.backup.BackupType
import com.webshell.core.data.backup.ImportOutcome
import com.webshell.core.data.backup.ImportRejection
import com.webshell.core.model.AppLog
import com.webshell.core.webengine.storage.CacheClearer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DataManagementUiState(
    val exportType: BackupType = BackupType.LAYOUT,
    val importing: Boolean = false,
    val exporting: Boolean = false,
)

/** 一次性事件：Toast / 结果弹窗 / 拉起系统分享。 */
sealed interface DataEvent {
    data class Toast(@StringRes val resId: Int, val arg: String? = null) : DataEvent

    data class ImportSucceeded(
        val importedApps: Int,
        val folderCount: Int,
        val warnings: List<String>,
        val settingsOffered: Map<String, String>?,
    ) : DataEvent

    data class ImportRejected(val reason: ImportRejection) : DataEvent

    data class ImportFailed(val message: String) : DataEvent

    /** downloadsPath 为面向用户展示的下载目录路径；写入公共目录失败时为 null（分享不受影响）。 */
    data class ExportReady(val file: File, val downloadsPath: String?) : DataEvent
}

/**
 * 数据管理页：导出委托 [BackupRepository]（写入 cacheDir/backup/，UI 侧 FileProvider 分享）；
 * 导入先读清单预检，再事务性导入，设置快照由用户确认后单独应用。
 */
@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val clearer: CacheClearer,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DataManagementUiState())
    val state: StateFlow<DataManagementUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DataEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<DataEvent> = _events.asSharedFlow()

    fun setExportType(type: BackupType) {
        _state.value = _state.value.copy(exportType = type)
    }

    fun import(uri: Uri) {
        val s = _state.value
        if (s.importing || s.exporting) return
        _state.value = s.copy(importing = true)
        viewModelScope.launch {
            try {
                // 先读清单预检：文件根本不是备份时直接提示原因，不进入导入流程。
                backupRepository.readManifest(uri).onFailure { e ->
                    AppLog.warn(TAG, "清单预检失败：${e.message}")
                    _events.tryEmit(DataEvent.Toast(rejectionText(mapReadFailure(e))))
                    return@launch
                }
                when (val outcome = backupRepository.import(uri, clearer.multiProfileSupported())) {
                    is ImportOutcome.Success -> _events.tryEmit(
                        DataEvent.ImportSucceeded(
                            importedApps = outcome.importedApps,
                            folderCount = outcome.folderCount,
                            warnings = outcome.warnings,
                            settingsOffered = outcome.settingsOffered,
                        ),
                    )
                    is ImportOutcome.Rejected -> _events.tryEmit(DataEvent.ImportRejected(outcome.reason))
                    is ImportOutcome.Failure -> _events.tryEmit(DataEvent.ImportFailed(outcome.message))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLog.error(TAG, "导入异常：${e.message}")
                _events.tryEmit(DataEvent.ImportFailed(e.message ?: "导入失败"))
            } finally {
                _state.value = _state.value.copy(importing = false)
            }
        }
    }

    fun applySettings(settings: Map<String, String>) {
        viewModelScope.launch {
            try {
                backupRepository.applyImportedSettings(settings)
                _events.tryEmit(DataEvent.Toast(R.string.me_data_settings_applied))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLog.error(TAG, "应用导入设置失败：${e.message}")
            }
        }
    }

    fun export() {
        val s = _state.value
        if (s.exporting || s.importing) return
        _state.value = s.copy(exporting = true)
        viewModelScope.launch {
            try {
                backupRepository.export(_state.value.exportType)
                    .onSuccess { file ->
                        val downloadsPath = saveToDownloads(file)
                        _events.tryEmit(DataEvent.ExportReady(file, downloadsPath))
                    }
                    .onFailure { e ->
                        _events.tryEmit(
                            DataEvent.Toast(R.string.me_data_export_failed, e.message ?: "unknown"),
                        )
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                _state.value = _state.value.copy(exporting = false)
            }
        }
    }

    /**
     * 导出副本写入公共下载目录（MediaStore Downloads，API 29+ 无需权限），
     * 返回展示用相对路径；失败仅记录日志并返回 null，不影响后续分享。
     */
    private suspend fun saveToDownloads(source: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOADS_RELATIVE_PATH)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert 返回空 Uri")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("无法打开下载目录输出流")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            DOWNLOADS_RELATIVE_PATH + source.name
        }.onFailure { e -> AppLog.warn(TAG, "导出副本写入下载目录失败：${e.message}") }
            .getOrNull()
    }

    private fun mapReadFailure(e: Throwable): ImportRejection = when ((e as? BackupFormatException)?.reason) {
        BackupFormatException.Reason.NOT_BACKUP -> ImportRejection.NotBackupFile
        BackupFormatException.Reason.UNSUPPORTED_VERSION -> ImportRejection.UnsupportedVersion
        else -> ImportRejection.Corrupted
    }

    private companion object {
        const val TAG = "backup"
        const val DOWNLOADS_RELATIVE_PATH = "Download/PocketWebShell/"
    }
}

/** 拒绝原因 → 文案资源；导入拒绝弹窗与清单预检 Toast 共用同一映射。 */
@StringRes
internal fun rejectionText(reason: ImportRejection): Int = when (reason) {
    ImportRejection.NotBackupFile -> R.string.me_data_reject_not_backup
    ImportRejection.UnsupportedVersion -> R.string.me_data_reject_version
    ImportRejection.Corrupted -> R.string.me_data_reject_corrupted
    ImportRejection.LayoutIncompatible -> R.string.me_data_reject_layout
    is ImportRejection.PositionConflict -> R.string.me_data_reject_conflict
    ImportRejection.StructureInvalid -> R.string.me_data_reject_structure
}
