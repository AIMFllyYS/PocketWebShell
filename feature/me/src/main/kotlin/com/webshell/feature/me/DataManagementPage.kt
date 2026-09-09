package com.webshell.feature.me

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.data.backup.BackupType
import com.webshell.core.data.backup.ImportRejection
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppPrimaryButton
import com.webshell.core.designsystem.components.AppSelectionRow
import com.webshell.core.designsystem.components.AppSettingsSection
import com.webshell.core.designsystem.theme.AppSpacing
import java.io.File

/** 数据管理：.pws 备份的导入（预检+事务导入+设置快照确认）与三种粒度的导出（系统分享）。 */
@Composable
internal fun DataManagementPage(
    onBack: () -> Unit,
    viewModel: DataManagementViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var importSuccess by remember { mutableStateOf<DataEvent.ImportSucceeded?>(null) }
    var settingsOffer by remember { mutableStateOf<Map<String, String>?>(null) }
    var importRejected by remember { mutableStateOf<ImportRejection?>(null) }
    var importFailed by remember { mutableStateOf<String?>(null) }
    var showExportConfirm by remember { mutableStateOf(false) }
    var exportSuccess by remember { mutableStateOf<DataEvent.ExportReady?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    fun shareExport(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.me_data_share_title)))
    }

    fun tryShareExport(file: File) {
        runCatching { shareExport(file) }
            .onFailure {
                Toast.makeText(context, R.string.me_data_share_failed, Toast.LENGTH_SHORT).show()
            }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DataEvent.Toast -> {
                    val text = event.arg?.let { context.getString(event.resId, it) }
                        ?: context.getString(event.resId)
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
                is DataEvent.ImportSucceeded -> importSuccess = event
                is DataEvent.ImportRejected -> importRejected = event.reason
                is DataEvent.ImportFailed -> importFailed = event.message
                is DataEvent.ExportReady -> {
                    tryShareExport(event.file)
                    exportSuccess = event
                }
            }
        }
    }

    DetailPage(title = stringResource(R.string.me_data_title), onBack = onBack) {
        AppSettingsSection(stringResource(R.string.me_data_import_section), Modifier.padding(bottom = 24.dp)) {
            Column(Modifier.padding(AppSpacing.lg)) {
                Text(
                    stringResource(R.string.me_data_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppSpacing.md))
                AppPrimaryButton(
                    text = stringResource(
                        if (state.importing) R.string.me_data_importing else R.string.me_data_import_pick,
                    ),
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/octet-stream", "application/zip", "application/x-zip-compressed"),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.importing && !state.exporting,
                    loading = state.importing,
                )
            }
        }
        AppSettingsSection(stringResource(R.string.me_data_export_section), Modifier.padding(bottom = 16.dp)) {
            val choices = listOf(
                Triple(BackupType.URLS, R.string.me_data_export_urls, R.string.me_data_export_urls_hint),
                Triple(BackupType.LAYOUT, R.string.me_data_export_layout, R.string.me_data_export_layout_hint),
                Triple(BackupType.FULL, R.string.me_data_export_full, R.string.me_data_export_full_hint),
            )
            choices.forEachIndexed { index, (type, title, hint) ->
                AppSelectionRow(
                    title = stringResource(title),
                    selected = state.exportType == type,
                    onClick = { viewModel.setExportType(type) },
                    subtitle = stringResource(hint),
                )
                if (index < choices.lastIndex) AppListDivider(false)
            }
        }
        AppPrimaryButton(
            text = stringResource(
                if (state.exporting) R.string.me_data_exporting else R.string.me_data_export_action,
            ),
            onClick = { showExportConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.exporting && !state.importing,
            loading = state.exporting,
        )
        if (state.exportType == BackupType.FULL) {
            Text(
                stringResource(R.string.me_data_export_full_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.sm),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    importSuccess?.let { result ->
        val summary = stringResource(R.string.me_data_import_done, result.importedApps) +
            if (result.folderCount > 0) {
                "、" + stringResource(R.string.me_data_import_done_folders, result.folderCount)
            } else {
                ""
            }
        val text = if (result.warnings.isEmpty()) {
            summary
        } else {
            summary + "\n\n" + result.warnings.joinToString("\n")
        }
        AppConfirmDialog(
            title = stringResource(R.string.me_data_import_done_title),
            text = text,
            confirmText = stringResource(R.string.me_ok),
            dismissText = stringResource(R.string.me_cancel),
            onConfirm = {
                importSuccess = null
                result.settingsOffered?.let { settingsOffer = it }
            },
            onDismiss = {
                importSuccess = null
                result.settingsOffered?.let { settingsOffer = it }
            },
        )
    }

    settingsOffer?.let { offered ->
        AppConfirmDialog(
            title = stringResource(R.string.me_data_import_settings_title),
            text = stringResource(R.string.me_data_import_settings_text),
            confirmText = stringResource(R.string.me_data_import_settings_apply),
            dismissText = stringResource(R.string.me_cancel),
            onConfirm = { settingsOffer = null; viewModel.applySettings(offered) },
            onDismiss = { settingsOffer = null },
        )
    }

    importRejected?.let { reason ->
        val text = if (reason is ImportRejection.PositionConflict) {
            stringResource(rejectionText(reason), reason.detail)
        } else {
            stringResource(rejectionText(reason))
        }
        AppConfirmDialog(
            title = stringResource(R.string.me_data_import_failed_title),
            text = text,
            confirmText = stringResource(R.string.me_ok),
            dismissText = stringResource(R.string.me_cancel),
            onConfirm = { importRejected = null },
            onDismiss = { importRejected = null },
        )
    }

    importFailed?.let { message ->
        AppConfirmDialog(
            title = stringResource(R.string.me_data_import_failed_title),
            text = message,
            confirmText = stringResource(R.string.me_ok),
            dismissText = stringResource(R.string.me_cancel),
            onConfirm = { importFailed = null },
            onDismiss = { importFailed = null },
        )
    }

    if (showExportConfirm) {
        AppConfirmDialog(
            title = stringResource(R.string.me_data_export_confirm_title),
            text = stringResource(R.string.me_data_export_confirm_text, exportTypeName(state.exportType)),
            confirmText = stringResource(R.string.me_data_export_confirm_yes),
            dismissText = stringResource(R.string.me_data_export_confirm_no),
            onConfirm = { showExportConfirm = false; viewModel.export() },
            onDismiss = { showExportConfirm = false },
        )
    }

    exportSuccess?.let { ready ->
        val pathLine = ready.downloadsPath?.let { path ->
            stringResource(R.string.me_data_export_done_saved_downloads, path)
        } ?: stringResource(R.string.me_data_export_done_saved_private, ready.file.absolutePath)
        AppConfirmDialog(
            title = stringResource(R.string.me_data_export_done_title),
            text = stringResource(
                R.string.me_data_export_done_file,
                ready.file.name,
                formatStorageBytes(ready.file.length()),
            ) + "\n" + pathLine + "\n\n" + stringResource(R.string.me_data_export_done_hint),
            confirmText = stringResource(R.string.me_data_export_share_again),
            dismissText = stringResource(R.string.me_done),
            onConfirm = {
                exportSuccess = null
                tryShareExport(ready.file)
            },
            onDismiss = { exportSuccess = null },
        )
    }
}

@Composable
private fun exportTypeName(type: BackupType): String = stringResource(
    when (type) {
        BackupType.URLS -> R.string.me_data_export_urls
        BackupType.LAYOUT -> R.string.me_data_export_layout
        BackupType.FULL -> R.string.me_data_export_full
        BackupType.FOLDER -> R.string.me_data_export_layout
    },
)
