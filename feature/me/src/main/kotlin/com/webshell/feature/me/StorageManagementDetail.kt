package com.webshell.feature.me

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.data.WebAppEntity
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.components.AppContextMenu
import com.webshell.core.designsystem.components.AppContextMenuItem
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppPrimaryButton
import com.webshell.core.designsystem.components.AppSearchField
import com.webshell.core.designsystem.components.AppSettingsSection
import com.webshell.core.designsystem.components.RevealFromPoint
import com.webshell.core.designsystem.components.SiteIcon
import com.webshell.core.designsystem.components.siteIconGlyph
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.designsystem.theme.LocalTransitionStyle
import com.webshell.core.webengine.storage.CookieFacts
import com.webshell.core.webengine.storage.Metric
import com.webshell.core.webengine.storage.SiteStorageStats
import com.webshell.core.webengine.storage.hostOfUrl
import com.webshell.core.webengine.storage.siteKeyOf


@Composable
internal fun StorageDetailPage(
    app: WebAppEntity,
    apps: List<WebAppEntity>,
    stats: SiteStorageStats?,
    state: StorageUiState,
    viewModel: StorageViewModel,
    onBack: () -> Unit,
) {
    var erasePreview by remember { mutableStateOf<EraseSitePreview?>(null) }
    var resetOfflineConfirm by rememberSaveable(app.id) { mutableStateOf(false) }
    val canErase = viewModel.canEraseSite() && !app.isLocal
    val siblings = remember(app.id, apps) {
        val host = hostOfUrl(app.url).orEmpty()
        val key = siteKeyOf(app.id, host, app.isLocal)
        apps.filter { other ->
            other.id != app.id && siteKeyOf(other.id, hostOfUrl(other.url).orEmpty(), other.isLocal) == key
        }
    }

    if (resetOfflineConfirm) AppConfirmDialog(
        title = stringResource(R.string.me_reset_offline),
        text = stringResource(R.string.me_reset_offline_hint),
        confirmText = stringResource(R.string.me_storage_confirm_clear),
        onConfirm = { resetOfflineConfirm = false; viewModel.resetOffline(app.id) },
        onDismiss = { resetOfflineConfirm = false },
    )
    DetailPage(title = app.title, onBack = onBack) {
        if (!app.isLocal) TextButton(
            onClick = { resetOfflineConfirm = true },
            enabled = !state.clearingAll && !state.erasingSite,
        ) { Text(stringResource(R.string.me_reset_offline)) }
        Column(
            Modifier.fillMaxWidth().padding(vertical = AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SiteIcon(
                title = app.title,
                iconUrl = app.iconUrl,
                size = 64.dp,
                glyph = siteIconGlyph(app.isLocal, app.url),
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(app.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                hostOf(app.url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stats?.signedIn == true) {
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    stringResource(R.string.me_storage_signed_in),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        AppSettingsSection(stringResource(R.string.me_storage_usage_section), Modifier.padding(bottom = 24.dp)) {
            Column(Modifier.padding(AppSpacing.lg)) {
                if (stats == null) {
                    Text(
                        stringResource(
                            if (state.scanning) R.string.me_storage_measuring else R.string.me_storage_unmeasurable,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DetailStatRow(
                        stringResource(R.string.me_storage_attributable),
                        if (stats.allMetricsUnavailable()) {
                            stringResource(R.string.me_storage_metric_unavailable)
                        } else {
                            formatStorageBytes(stats.attributableBytes)
                        },
                    )
                    Text(
                        stringResource(R.string.me_storage_attributable_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    DetailStatRow(stringResource(R.string.me_storage_cookie), cookieMetricLabel(stats.cookie))
                    DetailStatRow(
                        stringResource(R.string.me_storage_indexeddb),
                        longMetricLabel(stats.indexedDbBytes),
                    )
                    DetailStatRow(
                        stringResource(R.string.me_storage_quota),
                        longMetricLabel(stats.quotaUsageBytes),
                    )
                    Text(
                        stringResource(R.string.me_storage_quota_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (app.isLocal || stats.localImportBytes is Metric.Measured) {
                        DetailStatRow(
                            stringResource(R.string.me_storage_local_import),
                            longMetricLabel(stats.localImportBytes),
                        )
                    }
                    if (siblings.isNotEmpty()) {
                        Spacer(Modifier.height(AppSpacing.md))
                        Text(
                            stringResource(
                                R.string.me_storage_shared_entries,
                                siblings.joinToString("、") { it.title },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(
                if (canErase) {
                    R.string.me_storage_site_shared_hint_can_erase
                } else {
                    R.string.me_storage_site_shared_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canErase) {
            Spacer(Modifier.height(AppSpacing.lg))
            TextButton(
                onClick = { erasePreview = viewModel.eraseSitePreview(app.id) },
                enabled = !state.clearingAll && !state.erasingSite,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(
                        if (state.erasingSite) R.string.me_storage_erasing else R.string.me_storage_erase_site,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    erasePreview?.let { preview ->
        val others = preview.affectedTitles.filter { it != app.title }
        val text = buildString {
            append(stringResource(R.string.me_storage_erase_site_text, preview.siteDomain))
            if (others.isNotEmpty()) {
                append('\n')
                append(stringResource(R.string.me_storage_erase_site_others, others.joinToString("、")))
            }
            if (preview.willSignOut) {
                append('\n')
                append(stringResource(R.string.me_storage_erase_site_sign_out))
            }
            append('\n')
            append(stringResource(R.string.me_storage_erase_scope_warning))
        }
        AppConfirmDialog(
            title = stringResource(R.string.me_storage_erase_site_title),
            text = text,
            confirmText = stringResource(R.string.me_storage_confirm_erase_site),
            dismissText = stringResource(R.string.me_cancel),
            destructive = true,
            onConfirm = {
                erasePreview = null
                viewModel.eraseSite(app.id)
            },
            onDismiss = { erasePreview = null },
        )
    }
}
