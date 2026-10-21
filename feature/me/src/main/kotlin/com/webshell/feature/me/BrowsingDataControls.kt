package com.webshell.feature.me

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.components.AppSettingsSection
import com.webshell.core.designsystem.components.AppToggleRow

/** One reviewed selection, with the actual shared-data consequences visible before deletion. */
@Composable
internal fun BrowsingDataControls(viewModel: StorageViewModel, busy: Boolean) {
    var cache by rememberSaveable { mutableStateOf(true) }
    var cookies by rememberSaveable { mutableStateOf(false) }
    var data by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    AppSettingsSection(stringResource(R.string.me_browsing_data)) {
        AppToggleRow(stringResource(R.string.me_clear_resource_cache), checked = cache, onCheckedChange = { cache = it })
        AppToggleRow(stringResource(R.string.me_clear_cookies), checked = cookies, onCheckedChange = { cookies = it })
        AppToggleRow(stringResource(R.string.me_clear_site_storage), checked = data, onCheckedChange = { data = it })
        AppToggleRow(stringResource(R.string.me_clear_visit_history), checked = history, onCheckedChange = { history = it })
        TextButton(onClick = { confirm = true }, enabled = !busy && (cache || cookies || data || history)) {
            Text(stringResource(R.string.me_clear_selected))
        }
        Text(stringResource(R.string.me_dns_capability), modifier = Modifier.padding(16.dp))
    }
    if (confirm) AppConfirmDialog(
        title = stringResource(R.string.me_clear_selected),
        text = stringResource(R.string.me_clear_selected_hint),
        confirmText = stringResource(R.string.me_storage_confirm_clear),
        onConfirm = { confirm = false; viewModel.clearSelected(cache, cookies, data, history) },
        onDismiss = { confirm = false },
    )
}
