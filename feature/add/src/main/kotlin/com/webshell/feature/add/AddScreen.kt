package com.webshell.feature.add

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.webshell.core.designsystem.components.AppCard
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.designsystem.components.AppSectionHeader
import com.webshell.core.designsystem.components.AppSwitch
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Input → metadata → grouped editor. Persistence and import remain owned by AddViewModel. */
@Composable
fun AddScreen(
    modifier: Modifier = Modifier,
    onCreated: () -> Unit = {},
    viewModel: AddViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnCreated by rememberUpdatedState(onCreated)
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importLocal(uris) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(viewModel) { viewModel.created.collect { currentOnCreated() } }

    when (val current = state) {
        AddUiState.Input -> InputStep(
            modifier = modifier,
            onConfirm = viewModel::confirmUrl,
            onImportLocal = { importLauncher.launch(arrayOf("text/html")) },
        )
        AddUiState.Loading -> LoadingStep(modifier)
        is AddUiState.Edit -> EditStep(
            modifier = modifier,
            draft = current.draft,
            fetchFailed = current.fetchFailed,
            onUpdate = viewModel::updateDraft,
            onSave = viewModel::save,
            onBack = viewModel::reset,
        )
    }
}

@Composable
private fun InputStep(
    modifier: Modifier,
    onConfirm: (String) -> Unit,
    onImportLocal: () -> Unit,
) {
    var urlText by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val valid = remember(urlText) { AddViewModel.normalizeUrl(urlText) != null }
    val confirm = { if (valid) onConfirm(urlText) else showError = true }

    Column(
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.add_title), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.add_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(76.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Public, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
            }
            Text(stringResource(R.string.add_home_title),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            Text(
                stringResource(R.string.add_home_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 16.dp, end = 16.dp),
            )
        }
        AppSectionHeader(stringResource(R.string.add_address_section))
        AppCard(contentPadding = PaddingValues(0.dp)) {
            EditorTextField(
                title = stringResource(R.string.add_address),
                value = urlText,
                onValueChange = { urlText = it; showError = false },
                placeholder = stringResource(R.string.add_address_placeholder),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { confirm() }),
            )
        }
        if (showError) {
            Text(stringResource(R.string.add_address_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Button(
            onClick = confirm,
            enabled = urlText.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
        ) { Text(stringResource(R.string.add_continue), style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(28.dp))
        AppSectionHeader(stringResource(R.string.add_offline_section))
        AppCard(contentPadding = PaddingValues(0.dp)) {
            AppListRow(
                title = stringResource(R.string.add_import),
                subtitle = stringResource(R.string.add_import_hint),
                leadingIcon = Icons.Filled.Description,
                leadingIconBackground = Color(0xFF8E8E93),
                onClick = onImportLocal,
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LoadingStep(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.add_loading), color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun EditStep(
    modifier: Modifier,
    draft: AddDraft,
    fetchFailed: Boolean,
    onUpdate: ((AddDraft) -> AddDraft) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(context.filesDir, "icons").apply { mkdirs() }
                        val dest = File(dir, "icon_${System.currentTimeMillis()}.png")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        } ?: return@runCatching null
                        dest.absolutePath
                    }.getOrNull()
                }
                if (path != null) onUpdate { it.copy(iconUrl = path) }
            }
        }
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        AppNavigationBar(
            title = stringResource(R.string.add_home_title),
            onBack = onBack,
            actions = { TextButton(onClick = onSave) { Text(stringResource(R.string.add_save)) } },
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIconPreview(draft, 76.dp)
                Text(draft.title.ifBlank { stringResource(R.string.add_new_site) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp))
            }
            if (fetchFailed) {
                Text(stringResource(R.string.add_fetch_failed),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 20.dp))
            }
            AppSectionHeader(stringResource(R.string.add_info_section))
            AppCard(contentPadding = PaddingValues(0.dp)) {
                EditorTextField(title = stringResource(R.string.add_name), value = draft.title,
                    onValueChange = { value -> onUpdate { it.copy(title = value) } })
                AppListDivider(hasLeadingIcon = false)
                EditorTextField(
                    title = stringResource(if (draft.isLocal) R.string.add_local_entry else R.string.add_address),
                    value = draft.url,
                    onValueChange = { value -> onUpdate { it.copy(url = value) } },
                    readOnly = draft.isLocal,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
            }
            if (draft.isLocal) {
                Text(stringResource(R.string.add_local_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Spacer(Modifier.height(24.dp))
            AppSectionHeader(stringResource(R.string.add_icon_section))
            AppCard(contentPadding = PaddingValues(0.dp)) {
                AppListRow(
                    title = stringResource(if (draft.iconUrl.startsWith("/"))
                        R.string.add_change_icon else R.string.add_choose_icon),
                    leadingIcon = Icons.Filled.Image,
                    onClick = { pickIcon.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                AppListDivider(hasLeadingIcon = false)
                EditorTextField(title = stringResource(R.string.add_icon_address), value = draft.iconUrl,
                    onValueChange = { value -> onUpdate { it.copy(iconUrl = value) } },
                    placeholder = stringResource(R.string.add_icon_placeholder),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
            }
            Spacer(Modifier.height(24.dp))
            AppSectionHeader(stringResource(R.string.add_browser_section))
            AppCard(contentPadding = PaddingValues(0.dp)) {
                SwitchRow(stringResource(R.string.add_desktop), stringResource(R.string.add_desktop_hint),
                    draft.desktopMode) { value -> onUpdate { it.copy(desktopMode = value) } }
                AppListDivider(hasLeadingIcon = false)
                SwitchRow(stringResource(R.string.add_dark), stringResource(R.string.add_dark_hint),
                    draft.darkMode) { value -> onUpdate { it.copy(darkMode = value) } }
                AppListDivider(hasLeadingIcon = false)
                SwitchRow(stringResource(R.string.add_keep_alive), stringResource(R.string.add_keep_alive_hint),
                    draft.keepAlive) { value -> onUpdate { it.copy(keepAlive = value) } }
                AppListDivider(hasLeadingIcon = false)
                SwitchRow(stringResource(R.string.add_external), stringResource(R.string.add_external_hint),
                    draft.externalLinksToBrowser) { value -> onUpdate { it.copy(externalLinksToBrowser = value) } }
            }
            Spacer(Modifier.height(24.dp))
            AppSectionHeader(stringResource(R.string.add_text_size))
            AppCard {
                Text(stringResource(R.string.add_text_percent, draft.textZoomPercent),
                    style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = draft.textZoomPercent.toFloat(),
                    onValueChange = { value -> onUpdate { it.copy(textZoomPercent = value.toInt()) } },
                    valueRange = 80f..130f,
                    steps = 9,
                )
            }
            Button(onClick = onSave, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp)) {
                Text(stringResource(R.string.add_save_bottom), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Intrinsic-height input avoids Material floating labels and clipped text at large font scales. */
@Composable
private fun EditorTextField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange, readOnly = readOnly, singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).semantics { contentDescription = title },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    AppListRow(title = label, subtitle = description,
        trailing = { AppSwitch(checked = checked, onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = label }) })
}

/** Same rounded-square geometry as a home icon; failed image loads reveal a legible fallback. */
@Composable
private fun AppIconPreview(draft: AddDraft, size: Dp) {
    val parsedColor = remember(draft.themeColor) {
        draft.themeColor?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    }
    val background = parsedColor ?: MaterialTheme.colorScheme.primary
    val foreground = if (background.luminance() > 0.45f) Color.Black else Color.White
    val shape = RoundedCornerShape(26)
    Box(
        modifier = Modifier.size(size).clip(shape).background(background)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (draft.iconUrl.isBlank() || draft.isLocal) {
            Icon(if (draft.isLocal) Icons.Filled.Description else Icons.Filled.Public,
                contentDescription = null, tint = foreground, modifier = Modifier.size(size / 2))
        } else {
            Text(draft.title.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium, color = foreground, maxLines = 1)
        }
        if (draft.iconUrl.isNotBlank()) {
            AsyncImage(model = draft.iconUrl,
                contentDescription = stringResource(R.string.add_icon_description),
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
