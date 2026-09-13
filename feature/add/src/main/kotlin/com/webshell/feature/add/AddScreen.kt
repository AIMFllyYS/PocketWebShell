package com.webshell.feature.add

import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.LocalTransitionStyle

/** Route owns lifecycle, activity results and one-shot feedback; catalog reuses pure presentation. */
@Composable
fun AddScreen(
    modifier: Modifier = Modifier,
    onCreated: () -> Unit = {},
    viewModel: AddViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCreated by rememberUpdatedState(onCreated)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val permission = HtmlImportStorageAccess.runtimeReadPermission()
        val activity = context as? Activity
        val permanentlyDenied = !granted && permission != null && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        viewModel.openHtmlPicker(readPermanentlyDenied = permanentlyDenied)
    }
    val iconLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::importIcon)
    }
    val htmlDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importLocal(uris)
    }
    var urlText by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    fun requestReadOrOpenPicker() {
        val permission = HtmlImportStorageAccess.runtimeReadPermission()
        if (permission != null && context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        } else {
            viewModel.openHtmlPicker(readPermanentlyDenied = false)
        }
    }

    fun openStorageSettings() {
        val intents = HtmlImportStorageAccess.manageAccessIntent(context.packageName)
        for (intent in intents) {
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }

    fun onGrantStorage(banner: HtmlPickerPermissionBanner) {
        if (banner.showManageRationale || banner.permanentlyDenied) {
            openStorageSettings()
        } else {
            requestReadOrOpenPicker()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPicker()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(viewModel) {
        viewModel.created.collect {
            urlText = ""
            showError = false
            currentOnCreated()
        }
    }
    BackHandler(enabled = state !is AddUiState.Input && state !is AddUiState.PickingLocal) {
        viewModel.reset()
    }
    val lastPicking = remember { mutableStateOf<AddUiState.PickingLocal?>(null) }
    val lastEditor = remember { mutableStateOf<AddUiState.Edit?>(null) }
    (state as? AddUiState.PickingLocal)?.let { lastPicking.value = it }
    (state as? AddUiState.Edit)?.let { lastEditor.value = it }
    val phase = when (state) {
        AddUiState.Input -> "input"
        AddUiState.Loading -> "loading"
        is AddUiState.PickingLocal -> "picking"
        is AddUiState.Edit -> "edit"
    }
    val transitionStyle = LocalTransitionStyle.current
    AnimatedContent(
        targetState = phase,
        transitionSpec = { AppMotion.detailEnterFor(transitionStyle) togetherWith AppMotion.detailExitFor(transitionStyle) },
        label = "add-phase",
        modifier = modifier,
    ) { currentPhase ->
        when (currentPhase) {
            "picking" -> {
                val picking = (state as? AddUiState.PickingLocal) ?: lastPicking.value
                if (picking != null) {
                    HtmlFilePickerScreen(
                        state = picking,
                        onBack = viewModel::onPickerBack,
                        onCancel = viewModel::cancelPicker,
                        onGrantStorage = { picking.permissionBanner?.let(::onGrantStorage) ?: openStorageSettings() },
                        onOpenSystemPicker = {
                            htmlDocumentLauncher.launch(arrayOf("text/html", "application/xhtml+xml", "*/*"))
                        },
                        onOpenRoot = { viewModel.openRoot(it.path, it.kind) },
                        onOpenDir = { viewModel.openDir(it.path) },
                        onImportFile = { viewModel.importPicked(it.path) },
                        onOpenCrumb = viewModel::openCrumb,
                        onQueryChange = viewModel::setPickerQuery,
                    )
                }
            }
            "loading" -> AddLoadingContent()
            "edit" -> {
                val editor = (state as? AddUiState.Edit) ?: lastEditor.value
                if (editor != null) {
                    AddEditorContent(
                        state = editor,
                        onUpdate = viewModel::updateDraft,
                        onSave = viewModel::save,
                        onBack = viewModel::reset,
                        onPickIcon = {
                            iconLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }
            }
            else -> AddInputContent(
                url = urlText,
                showError = showError,
                onUrlChange = { urlText = it; showError = false },
                onContinue = {
                    if (AddUrl.normalize(urlText) != null) viewModel.confirmUrl(urlText) else showError = true
                },
                onImportLocal = ::requestReadOrOpenPicker,
            )
        }
    }
}
