package com.webshell.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppSettingsSection
import com.webshell.core.designsystem.theme.LocalOverlayClearance
import com.webshell.core.webengine.KeepAliveRegistry

@Composable
internal fun MeHome(
    state: MeUiState,
    onRequestStop: (String) -> Unit,
    onOpenSection: (MeSection) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 16.dp + LocalOverlayClearance.current,
        ),
    ) {
        item(key = "settings-header") {
            Text(
                stringResource(R.string.me_settings),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.me_settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )
        }
        item(key = "running-sessions") {
            AppSettingsSection(stringResource(R.string.me_protection_section), Modifier.padding(bottom = 24.dp)) {
                val sessions = state.runningSessions
                if (sessions.isEmpty()) {
                    SessionEmptyRow()
                } else {
                    val visible = sessions.take(3)
                    val overflow = sessions.size - visible.size
                    visible.forEachIndexed { index, session ->
                        SessionRow(session = session, onStop = { onRequestStop(session.sessionId) })
                        if (index < visible.lastIndex || overflow > 0) AppListDivider()
                    }
                    if (overflow > 0) {
                        AppListRow(
                            title = stringResource(R.string.me_sessions_all),
                            subtitle = stringResource(R.string.me_sessions_count, sessions.size),
                            leadingIcon = Icons.Rounded.PlayCircle,
                            leadingIconBackground = Color(0xFF34C759),
                            onClick = { onOpenSection(MeSection.SESSIONS) },
                            trailing = { SettingsChevron() },
                        )
                    }
                }
                AppListDivider()
                SettingsMenuEntry(Icons.Rounded.BatterySaver, stringResource(R.string.me_background), Color(0xFF34C759)) { onOpenSection(MeSection.BACKGROUND) }
            }
        }
        item(key = "display-settings") {
            AppSettingsSection(stringResource(R.string.me_display_section), Modifier.padding(bottom = 24.dp)) {
                SettingsMenuEntry(Icons.Rounded.Palette, stringResource(R.string.me_appearance), Color(0xFFAF52DE)) { onOpenSection(MeSection.APPEARANCE) }
                AppListDivider()
                SettingsMenuEntry(Icons.Rounded.GridView, stringResource(R.string.me_layout), Color(0xFF007AFF)) { onOpenSection(MeSection.LAYOUT) }
            }
        }
        item(key = "feature-settings") {
            AppSettingsSection(stringResource(R.string.me_features_section), Modifier.padding(bottom = 24.dp)) {
                SettingsMenuEntry(Icons.Rounded.TouchApp, stringResource(R.string.me_features), Color(0xFF5AC8FA)) { onOpenSection(MeSection.FEATURES) }
                AppListDivider()
                SettingsMenuEntry(Icons.Rounded.Public, stringResource(R.string.me_engine), Color(0xFF007AFF)) { onOpenSection(MeSection.ENGINE) }
            }
        }
        item(key = "data-storage-settings") {
            AppSettingsSection(stringResource(R.string.me_data_storage_section), Modifier.padding(bottom = 24.dp)) {
                SettingsMenuEntry(Icons.Rounded.Storage, stringResource(R.string.me_storage_title), Color(0xFFFF9500)) { onOpenSection(MeSection.STORAGE) }
                AppListDivider()
                SettingsMenuEntry(Icons.Rounded.ImportExport, stringResource(R.string.me_data_title), Color(0xFF007AFF)) { onOpenSection(MeSection.DATA) }
            }
        }
        item(key = "about-settings") {
            AppSettingsSection(stringResource(R.string.me_about_section)) {
                SettingsMenuEntry(
                    icon = Icons.Rounded.SystemUpdate,
                    title = stringResource(R.string.me_check_update),
                    color = Color(0xFF007AFF),
                    trailingText = updateStatusText(state.updateCheck),
                    showChevron = false,
                    onClick = onCheckUpdate,
                )
                AppListDivider()
                SettingsMenuEntry(Icons.Rounded.NewReleases, stringResource(R.string.me_updates), Color(0xFF8E8E93)) { onOpenSection(MeSection.UPDATE_LOG) }
                AppListDivider()
                SettingsMenuEntry(Icons.Rounded.DeveloperMode, stringResource(R.string.me_developer), Color(0xFF8E8E93)) { onOpenSection(MeSection.DEVELOPER) }
            }
        }
    }
}

@Composable
private fun updateStatusText(state: UpdateCheckState): String? = when (state) {
    UpdateCheckState.Idle -> null
    UpdateCheckState.Checking -> stringResource(R.string.me_check_update_checking)
    UpdateCheckState.UpToDate -> stringResource(R.string.me_check_update_latest)
    is UpdateCheckState.Available -> stringResource(R.string.me_check_update_available)
    UpdateCheckState.Failed -> stringResource(R.string.me_check_update_failed)
}

@Composable
private fun SettingsMenuEntry(
    icon: ImageVector,
    title: String,
    color: Color,
    onClick: () -> Unit,
    trailingText: String? = null,
    showChevron: Boolean = true,
) {
    AppListRow(
        title = title,
        leadingIcon = icon,
        leadingIconBackground = color,
        onClick = onClick,
        trailing = when {
            trailingText != null -> {
                {
                    Text(
                        trailingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            showChevron -> {
                { SettingsChevron() }
            }
            else -> null
        },
    )
}

@Composable
internal fun SessionRow(
    session: KeepAliveRegistry.Entry,
    onStop: () -> Unit,
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    AppListRow(
        title = session.title,
        subtitle = session.url,
        titleMaxLines = 1,
        subtitleMaxLines = 1,
        leadingIcon = if (selecting) {
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked
        } else Icons.Rounded.PlayCircle,
        leadingIconBackground = if (selecting) null else Color(0xFF34C759),
        leadingIconTint = if (selecting) {
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        } else null,
        onClick = if (selecting) onToggle else null,
        trailing = if (selecting) null else {
            {
                TextButton(onClick = onStop) {
                    Text(stringResource(R.string.me_session_end), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@Composable
internal fun SessionEmptyRow() {
    AppListRow(
        title = stringResource(R.string.me_sessions_empty),
        subtitle = stringResource(R.string.me_sessions_empty_hint),
        leadingIcon = Icons.Rounded.PlayCircle,
        leadingIconBackground = Color(0xFF34C759),
        titleMaxLines = 1,
        subtitleMaxLines = 2,
    )
}

@Composable
internal fun SettingsChevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
