package com.webshell.feature.me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.webshell.core.designsystem.components.AppCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** 二级页：项目更新日志。 */
@Composable
internal fun UpdateLogPage(onBack: () -> Unit) {
    var shown by rememberSaveable { mutableIntStateOf(UPDATE_PAGE_SIZE) }
    val visible = updateEntries.take(shown)
    DetailPage(title = stringResource(R.string.me_updates), onBack = onBack) {
        visible.forEachIndexed { index, entry ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.version,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            entry.date,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    entry.highlights.forEach { item ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(
                                "•",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(item, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (shown < updateEntries.size) {
            TextButton(
                onClick = { shown = (shown + UPDATE_PAGE_SIZE).coerceAtMost(updateEntries.size) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.me_updates_load_more))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
