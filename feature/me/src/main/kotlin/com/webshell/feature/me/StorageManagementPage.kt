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


/** 存储管理：概览（已统计占用/可清理/分段条）+ 站点列表（搜索/排序）+ 站点详情子页。 */
@Composable
internal fun StorageManagementPage(
    onBack: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var detailAppId by rememberSaveable { mutableStateOf<String?>(null) }
    val listScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    LaunchedEffect(viewModel) {
        viewModel.scan(forceRefresh = true)
        viewModel.toasts.collect { toast ->
            val text = toast.arg?.let { context.getString(toast.resId, it) } ?: context.getString(toast.resId)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.scan(forceRefresh = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = detailAppId != null) { detailAppId = null }

    val statsById = state.overview?.sites?.associateBy { it.appId }.orEmpty()
    val detailApp = apps.firstOrNull { it.id == detailAppId }
    LaunchedEffect(apps, detailAppId) {
        if (detailAppId != null && detailApp == null && apps.isNotEmpty()) detailAppId = null
    }

    val transitionStyle = LocalTransitionStyle.current
    AnimatedContent(
        targetState = detailAppId != null,
        transitionSpec = { AppMotion.detailEnterFor(transitionStyle) togetherWith AppMotion.detailExitFor(transitionStyle) },
        label = "storage-detail",
    ) { showDetail ->
        if (showDetail) {
            detailApp?.let { app ->
                StorageDetailPage(
                    app = app,
                    apps = apps,
                    stats = statsById[app.id],
                    state = state,
                    viewModel = viewModel,
                    onBack = { detailAppId = null },
                )
            }
        } else {
            StorageListPage(
                state = state,
                apps = apps,
                statsById = statsById,
                listScrollState = listScrollState,
                viewModel = viewModel,
                onOpenSite = { detailAppId = it },
                onBack = onBack,
            )
        }
    }
}
