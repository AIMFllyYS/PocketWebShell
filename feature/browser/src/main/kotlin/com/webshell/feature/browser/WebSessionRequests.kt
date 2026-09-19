package com.webshell.feature.browser

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.content.Intent
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.components.AppFormField
import com.webshell.core.webengine.CleartextGate
import com.webshell.core.webengine.ShellListener
import com.webshell.core.webengine.NewWindowRequest
import com.webshell.core.webengine.WebViewPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Platform objects are scoped to the current visible session, never to saved UI state. */
@Stable
class WebSessionRequests internal constructor() {
    internal var sessionId: String? = null
    internal var permission by mutableStateOf<PermissionRequest?>(null)
    internal var sslError by mutableStateOf<String?>(null)
    internal var fileCallback by mutableStateOf<ValueCallback<Array<Uri>>?>(null)
    internal var available = false
    lateinit var listener: ShellListener
        internal set
    internal var allowPermission: () -> Unit = {}
    internal var captureConsent by mutableStateOf<FileCapturePrompt?>(null)
    internal var captureOutputUri: Uri? = null
    internal var allowCapture: () -> Unit = {}
    internal var geolocation: ((Boolean, Boolean) -> Unit)? = null
    internal var geolocationOrigin by mutableStateOf<String?>(null)
    internal var allowGeolocation: () -> Unit = {}
    var fullScreenView by mutableStateOf<View?>(null)
        internal set
    var exitFullScreen: () -> Unit = {}
    internal var sslCancel: () -> Unit = {}
    internal var cleartextHost by mutableStateOf<String?>(null)
    internal var cleartextProceed: () -> Unit = {}
    internal var cleartextCancel: () -> Unit = {}
    internal var jsDialog by mutableStateOf<PendingJsDialog?>(null)
    val busy: Boolean get() = permission != null || sslError != null || fileCallback != null || captureConsent != null || geolocationOrigin != null || fullScreenView != null || jsDialog != null || cleartextHost != null

    internal fun denyPermission() {
        permission?.deny()
        permission = null
        sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.PENDING_PERMISSION) }
    }

    /** Deny only the pending geolocation prompt; leaves any unrelated pending request untouched. */
    internal fun denyGeolocation() {
        geolocation?.invoke(false, false)
        geolocation = null
        geolocationOrigin = null
        sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.PENDING_PERMISSION) }
        allowGeolocation = {}
    }

    /**
     * Cancel every pending platform request at once — tab switch/leave, not a
     * single dialog's own dismiss button. A geolocation prompt's own "deny"
     * must call [denyGeolocation] instead, or it would also silently
     * cancel an unrelated pending JS dialog/file chooser/SSL decision.
     */
    internal fun failFileChooser() {
        captureConsent = null
        captureOutputUri = null
        allowCapture = {}
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.PENDING_FILE) }
    }

    internal fun denyCapture() {
        failFileChooser()
    }

    internal fun cancelPending() {
        failFileChooser()
        denyPermission()
        sslError = null
        sslCancel()
        sslCancel = {}
        dismissCleartext()
        denyGeolocation()
        completeJsDialog(accepted = false)
        if (fullScreenView != null) exitFullScreen()
    }

    internal fun confirmCleartext() {
        val proceed = cleartextProceed
        cleartextHost = null
        cleartextProceed = {}
        cleartextCancel = {}
        proceed()
    }

    internal fun dismissCleartext() {
        val cancel = cleartextCancel
        cleartextHost = null
        cleartextProceed = {}
        cleartextCancel = {}
        cancel()
    }

    internal fun completeJsDialog(accepted: Boolean, promptValue: String? = null) {
        val current = jsDialog ?: return
        jsDialog = null
        current.complete(accepted, promptValue)
    }

    internal fun presentJsDialog(dialog: PendingJsDialog) {
        if (!available) {
            dialog.complete(accepted = dialog is PendingJsDialog.Alert, promptValue = null)
            return
        }
        jsDialog?.complete(accepted = false, promptValue = null)
        jsDialog = dialog
    }

    internal fun clear() {
        available = false
        cancelPending()
    }
}

/** Activity results can outlive a tab switch; route each result to its launch-time owner. */
internal data class FileCapturePrompt(
    val video: Boolean,
    val params: WebChromeClient.FileChooserParams,
)

private class PlatformLaunchOwners {
    var file: WebSessionRequests? = null
    var permission: WebSessionRequests? = null
    var location: WebSessionRequests? = null
    var capture: WebSessionRequests? = null
}

@Composable
fun rememberWebSessionRequests(
    sessionId: String?, visible: Boolean,
    onNewWindow: (NewWindowRequest) -> Unit, onMessage: (String) -> Unit,
): WebSessionRequests {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val requests = remember(sessionId) { WebSessionRequests() }
    val owners = remember { PlatformLaunchOwners() }
    val newWindow = rememberUpdatedState(onNewWindow)
    val message = rememberUpdatedState(onMessage)
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val owner = owners.file
        owners.file = null
        val captureUri = owner?.captureOutputUri
        owner?.captureOutputUri = null
        val uris = when {
            owner == null || !owner.available -> null
            captureUri != null -> if (result.resultCode == Activity.RESULT_OK) arrayOf(captureUri) else null
            else -> WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        }
        owner?.fileCallback?.onReceiveValue(uris)
        owner?.fileCallback = null
        owner?.sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.PENDING_FILE) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val owner = owners.permission
        owners.permission = null
        owner?.permission?.let { request ->
            val granted = request.resources.filter { resource ->
                permissionForWebResource(resource)?.let { permission ->
                    grants[permission] == true ||
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                } == true
            }
            if (owner.available && granted.isNotEmpty()) request.grant(granted.toTypedArray()) else request.deny()
        }
        owner?.permission = null
        owner?.sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.PENDING_PERMISSION) }
    }
    val capturePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val owner = owners.capture
        owners.capture = null
        val prompt = owner?.captureConsent
        owner?.captureConsent = null
        if (owner == null || !owner.available || prompt == null) {
            owner?.failFileChooser()
            return@rememberLauncherForActivityResult
        }
        val cameraOk = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (cameraOk) {
            startCaptureOrPicker(scope, context, owner, owners, fileLauncher, prompt)
        } else {
            launchFilePicker(owner, owners, fileLauncher, prompt.params)
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val owner = owners.location
        owners.location = null
        val callback = owner?.geolocation
        owner?.geolocation = null
        val allowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        callback?.invoke(if (owner?.available == true) allowed else false, false)
        owner?.geolocationOrigin = null
        owner?.sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.PENDING_PERMISSION) }
    }
    SideEffect {
        requests.sessionId = sessionId
        requests.available = visible && sessionId != null
        requests.allowPermission = {
            val request = requests.permission
            if (request != null && requests.available) {
                val permissions = androidPermissionsForWebResources(request.resources)
                val grantable = grantableWebResources(request.resources)
                if (permissions.isEmpty() || grantable.isEmpty() || owners.permission != null) {
                    requests.denyPermission()
                } else if (permissions.all { permission ->
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    }
                ) {
                    request.grant(grantable.toTypedArray())
                    requests.permission = null
                    requests.sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.PENDING_PERMISSION) }
                } else {
                    owners.permission = requests
                    runCatching { permissionLauncher.launch(permissions.toTypedArray()) }.onFailure {
                        owners.permission = null
                        requests.denyPermission()
                    }
                }
            } else requests.denyPermission()
        }
        requests.allowCapture = {
            val prompt = requests.captureConsent
            if (prompt == null || !requests.available || owners.capture != null) {
                requests.denyCapture()
            } else {
                val permissions = WebFileCapture.runtimePermissions(true, prompt.params.acceptTypes)
                val missing = permissions.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    requests.captureConsent = null
                    startCaptureOrPicker(scope, context, requests, owners, fileLauncher, prompt)
                } else {
                    owners.capture = requests
                    runCatching { capturePermissionLauncher.launch(missing.toTypedArray()) }.onFailure {
                        owners.capture = null
                        requests.denyCapture()
                    }
                }
            }
        }
        requests.allowGeolocation = {
            val callback = requests.geolocation
            if (callback == null || !requests.available || owners.location != null) {
                callback?.invoke(false, false)
                requests.geolocation = null
                requests.geolocationOrigin = null
                requests.sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.PENDING_PERMISSION) }
            } else {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    requests.geolocation = null
                    requests.geolocationOrigin = null
                    requests.sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.PENDING_PERMISSION) }
                    callback(true, false)
                } else {
                    owners.location = requests
                    runCatching {
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }.onFailure {
                        owners.location = null
                        requests.geolocation = null
                        requests.geolocationOrigin = null
                        requests.sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.PENDING_PERMISSION) }
                        callback(false, false)
                    }
                }
            }
        }
    }
    DisposableEffect(requests, visible) {
        onDispose {
            if (owners.file === requests) owners.file = null
            if (owners.permission === requests) owners.permission = null
            if (owners.location === requests) owners.location = null
            if (owners.capture === requests) owners.capture = null
            requests.clear()
        }
    }
    requests.listener = remember(requests) {
        object : ShellListener {
            override fun onPageStarted(url: String) { requests.cancelPending() }
            override fun onNewWindow(request: NewWindowRequest) { if (requests.available) newWindow.value(request) }
            override fun onFileChooserRequested(
                params: WebChromeClient.FileChooserParams, callback: ValueCallback<Array<Uri>>,
            ) {
                if (!requests.available || owners.file != null) { callback.onReceiveValue(null); return }
                requests.fileCallback?.onReceiveValue(null)
                requests.fileCallback = callback
                owners.file = requests
                requests.sessionId?.let { WebViewPool.protect(it, WebViewPool.ProtectionReason.PENDING_FILE) }
                if (params.isCaptureEnabled) {
                    requests.captureConsent = FileCapturePrompt(
                        video = WebFileCapture.isVideoAccept(params.acceptTypes),
                        params = params,
                    )
                } else {
                    launchFilePicker(requests, owners, fileLauncher, params)
                }
            }
            override fun onPermissionRequested(request: PermissionRequest) {
                if (!requests.available || !shouldPromptWebPermission(request.resources)) {
                    request.deny()
                    return
                }
                requests.denyPermission()
                requests.permission = request
                requests.sessionId?.let { WebViewPool.protect(it, WebViewPool.ProtectionReason.PENDING_PERMISSION) }
            }
            override fun onPermissionCanceled(request: PermissionRequest) {
                if (requests.permission === request) {
                    requests.permission = null
                    requests.sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.PENDING_PERMISSION) }
                }
            }
            override fun onGeolocationPrompt(origin: String, callback: (Boolean, Boolean) -> Unit) {
                if (!requests.available || requests.geolocation != null || !origin.startsWith("https://", ignoreCase = true)) {
                    callback(false, false); return
                }
                requests.geolocation = callback
                requests.geolocationOrigin = origin
                requests.sessionId?.let { WebViewPool.protect(it, WebViewPool.ProtectionReason.PENDING_PERMISSION) }
            }
            override fun onSslError(url: String, error: String, proceed: () -> Unit) {
                if (requests.available) requests.sslError = error
            }
            override fun onSslError(url: String, error: String, proceed: () -> Unit, cancel: () -> Unit) {
                if (requests.available) { requests.sslError = error; requests.sslCancel = cancel } else cancel()
            }
            override fun onCleartextPrompt(url: String, proceed: () -> Unit, cancel: () -> Unit) {
                if (!requests.available) { cancel(); return }
                requests.cleartextHost = CleartextGate.hostOf(url) ?: url
                requests.cleartextProceed = proceed
                requests.cleartextCancel = cancel
            }
            override fun onPageError(url: String, errorCode: Int, description: String, insecureHttp: Boolean) {
                if (requests.available) message.value(
                    if (insecureHttp) context.getString(R.string.browser_http_failed)
                    else context.getString(R.string.browser_page_loading_error),
                )
            }
            override fun onHttpAuthRequested(host: String, realm: String?, respond: (String?, String?) -> Unit) {
                if (requests.available) message.value(context.getString(R.string.browser_http_auth_unsupported, host))
            }
            override fun onClientCertificateRequested(host: String, respond: (Boolean) -> Unit) {
                if (requests.available) message.value(context.getString(R.string.browser_client_cert_unsupported, host))
            }
            override fun onShowCustomView(view: View, exit: () -> Unit) {
                requests.fullScreenView = view
                requests.exitFullScreen = exit
                requests.sessionId?.let { WebViewPool.protect(it, WebViewPool.ProtectionReason.FULLSCREEN) }
            }
            override fun onHideCustomView() {
                requests.fullScreenView = null
                requests.exitFullScreen = {}
                requests.sessionId?.let { WebViewPool.unprotect(it, WebViewPool.ProtectionReason.FULLSCREEN) }
            }
            override fun onExternalLaunchFailed(url: String) {
                if (requests.available) message.value(context.getString(R.string.browser_external_failed))
            }
            override fun onJsAlert(url: String, message: String, confirm: () -> Unit) {
                requests.presentJsDialog(PendingJsDialog.Alert(url, message, confirm))
            }
            override fun onJsConfirm(url: String, message: String, respond: (Boolean) -> Unit) {
                requests.presentJsDialog(PendingJsDialog.Confirm(url, message, respond))
            }
            override fun onJsPrompt(
                url: String, message: String, defaultValue: String, respond: (String?) -> Unit,
            ) {
                requests.presentJsDialog(PendingJsDialog.Prompt(url, message, defaultValue, respond))
            }
            override fun onJsBeforeUnload(url: String, message: String, respond: (Boolean) -> Unit) {
                requests.presentJsDialog(PendingJsDialog.BeforeUnload(url, message, respond))
            }
        }
    }
    return requests
}

internal sealed class PendingJsDialog {
    abstract val url: String
    abstract val message: String
    abstract fun complete(accepted: Boolean, promptValue: String?)

    data class Alert(
        override val url: String,
        override val message: String,
        val confirm: () -> Unit,
    ) : PendingJsDialog() {
        override fun complete(accepted: Boolean, promptValue: String?) { confirm() }
    }

    data class Confirm(
        override val url: String,
        override val message: String,
        val respond: (Boolean) -> Unit,
    ) : PendingJsDialog() {
        override fun complete(accepted: Boolean, promptValue: String?) { respond(accepted) }
    }

    data class Prompt(
        override val url: String,
        override val message: String,
        val defaultValue: String,
        val respond: (String?) -> Unit,
    ) : PendingJsDialog() {
        override fun complete(accepted: Boolean, promptValue: String?) {
            respond(if (accepted) promptValue.orEmpty() else null)
        }
    }

    data class BeforeUnload(
        override val url: String,
        override val message: String,
        val respond: (Boolean) -> Unit,
    ) : PendingJsDialog() {
        override fun complete(accepted: Boolean, promptValue: String?) { respond(accepted) }
    }
}

/** WebKit resource strings are not Android runtime permission names. Unknown resources deny. */
internal fun permissionForWebResource(resource: String): String? = when (resource) {
    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
    else -> null
}

internal fun grantableWebResources(resources: Array<String>): List<String> =
    resources.filter { permissionForWebResource(it) != null }

internal fun androidPermissionsForWebResources(resources: Array<String>): List<String> =
    resources.mapNotNull(::permissionForWebResource).distinct()

internal fun shouldPromptWebPermission(resources: Array<String>): Boolean =
    grantableWebResources(resources).isNotEmpty()

private fun launchFilePicker(
    owner: WebSessionRequests,
    owners: PlatformLaunchOwners,
    fileLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    params: WebChromeClient.FileChooserParams,
) {
    runCatching {
        val intent = params.createIntent().apply {
            if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
        fileLauncher.launch(intent)
    }.onFailure {
        owners.file = null
        owner.failFileChooser()
    }
}

private fun startCaptureOrPicker(
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    owner: WebSessionRequests,
    owners: PlatformLaunchOwners,
    fileLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    prompt: FileCapturePrompt,
) {
    scope.launch {
        val prepared = withContext(Dispatchers.IO) {
            WebFileCapture.intentOrNull(context, prompt.video)
        }
        if (prepared != null) {
            owner.captureOutputUri = prepared.second
            runCatching { fileLauncher.launch(prepared.first) }.onFailure {
                owner.captureOutputUri = null
                launchFilePicker(owner, owners, fileLauncher, prompt.params)
            }
        } else {
            launchFilePicker(owner, owners, fileLauncher, prompt.params)
        }
    }
}

@Composable
fun WebSessionDialogs(
    requests: WebSessionRequests,
    onRetry: () -> Unit,
    onLeave: () -> Unit,
    onCleartextContinued: () -> Unit = {},
    onCleartextExit: () -> Unit = {},
) {
    requests.permission?.let { request ->
        val capabilities = request.resources.mapNotNull { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> stringResource(R.string.browser_camera)
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> stringResource(R.string.browser_microphone)
                else -> null
            }
        }.joinToString(" / ")
        AppConfirmDialog(
            title = stringResource(R.string.browser_permission_title),
            text = stringResource(R.string.browser_permission_message, request.origin.host.orEmpty(), capabilities),
            confirmText = stringResource(R.string.browser_allow), dismissText = stringResource(R.string.browser_deny),
            onConfirm = requests.allowPermission, onDismiss = requests::denyPermission,
        )
    }
    requests.captureConsent?.let { prompt ->
        val capabilities = buildString {
            append(stringResource(R.string.browser_camera))
            if (prompt.video) {
                append(" / ")
                append(stringResource(R.string.browser_microphone))
            }
        }
        AppConfirmDialog(
            title = stringResource(R.string.browser_permission_title),
            text = stringResource(R.string.browser_capture_permission_message, capabilities),
            confirmText = stringResource(R.string.browser_allow),
            dismissText = stringResource(R.string.browser_deny),
            onConfirm = requests.allowCapture,
            onDismiss = requests::denyCapture,
        )
    }
    requests.geolocationOrigin?.let { origin ->
        AppConfirmDialog(
            title = stringResource(R.string.browser_location_title),
            text = stringResource(R.string.browser_location_message, origin),
            confirmText = stringResource(R.string.browser_allow),
            dismissText = stringResource(R.string.browser_deny),
            onConfirm = requests.allowGeolocation,
            onDismiss = requests::denyGeolocation,
        )
    }
    requests.sslError?.let { error ->
        BrowserCertificateDialog(
            error,
            onRetry = { requests.sslError = null; requests.sslCancel(); requests.sslCancel = {}; onRetry() },
            onDismiss = { requests.sslError = null; requests.sslCancel(); requests.sslCancel = {} },
        )
    }
    requests.cleartextHost?.let { host ->
        AppConfirmDialog(
            title = stringResource(R.string.browser_cleartext_title),
            text = stringResource(R.string.browser_cleartext_message, host),
            confirmText = stringResource(R.string.browser_cleartext_continue),
            dismissText = stringResource(R.string.browser_cleartext_exit),
            onConfirm = { requests.confirmCleartext(); onCleartextContinued() },
            onDismiss = { requests.dismissCleartext(); onCleartextExit() },
        )
    }
    requests.jsDialog?.let { dialog ->
        JsDialogPrompt(dialog, onComplete = requests::completeJsDialog)
    }
}

@Composable
private fun JsDialogPrompt(
    dialog: PendingJsDialog,
    onComplete: (accepted: Boolean, promptValue: String?) -> Unit,
) {
    val host = remember(dialog.url) {
        runCatching { dialog.url.toUri().host }.getOrNull().orEmpty()
    }
    val title = when (dialog) {
        is PendingJsDialog.BeforeUnload -> stringResource(R.string.browser_js_leave_title)
        // Which site is asking matters at least as much for a prompt as it
        // does for an alert/confirm — show the host here too, same as the
        // other dialog kinds, and only fall back to the generic wording when
        // the host itself is unknown.
        is PendingJsDialog.Prompt -> host.ifBlank { stringResource(R.string.browser_js_prompt_title) }
        else -> host.ifBlank { stringResource(R.string.browser_js_alert_title) }
    }
    val text = when {
        dialog is PendingJsDialog.BeforeUnload && dialog.message.isBlank() ->
            stringResource(R.string.browser_js_leave_message)
        dialog is PendingJsDialog.Alert && host.isNotBlank() && dialog.message.isNotBlank() ->
            dialog.message
        dialog is PendingJsDialog.Alert && dialog.message.isBlank() ->
            host.ifBlank { stringResource(R.string.browser_js_alert_title) }
        else -> dialog.message
    }
    val confirmText = when (dialog) {
        is PendingJsDialog.BeforeUnload -> stringResource(R.string.browser_leave)
        else -> stringResource(R.string.browser_js_ok)
    }
    val dismissText = when (dialog) {
        is PendingJsDialog.Alert -> null
        is PendingJsDialog.BeforeUnload -> stringResource(R.string.browser_js_stay)
        else -> stringResource(R.string.browser_cancel)
    }
    var promptValue by remember(dialog) {
        mutableStateOf((dialog as? PendingJsDialog.Prompt)?.defaultValue.orEmpty())
    }
    AppConfirmDialog(
        title = title,
        text = text,
        confirmText = confirmText,
        dismissText = dismissText,
        destructive = dialog is PendingJsDialog.BeforeUnload,
        onConfirm = {
            onComplete(true, if (dialog is PendingJsDialog.Prompt) promptValue else null)
        },
        onDismiss = {
            onComplete(dialog is PendingJsDialog.Alert, if (dialog is PendingJsDialog.Prompt) promptValue else null)
        },
        content = if (dialog is PendingJsDialog.Prompt) {
            {
                AppFormField(
                    value = promptValue,
                    onValueChange = { promptValue = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            null
        },
    )
}

/** Native custom-view host for HTML5 full-screen video. */
@Composable
fun WebSessionFullScreen(requests: WebSessionRequests) {
    val view = requests.fullScreenView ?: return
    val activity = LocalContext.current as? Activity
    androidx.compose.runtime.DisposableEffect(view) {
        val decor = activity?.window?.decorView
        val previous = decor?.systemUiVisibility ?: 0
        decor?.systemUiVisibility = previous or 0x00000400 or 0x00000002 or 0x00001000
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { decor?.systemUiVisibility = previous }
    }
    AndroidView(
        factory = { context -> FrameLayout(context).apply { addView(view, FrameLayout.LayoutParams(-1, -1)) } },
        update = { host ->
            if (view.parent !== host) {
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                host.removeAllViews()
                host.addView(view, FrameLayout.LayoutParams(-1, -1))
            }
        },
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    )
}

@Composable
internal fun BrowserCertificateDialog(error: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    AppConfirmDialog(
        title = stringResource(R.string.browser_ssl_title),
        text = stringResource(R.string.browser_ssl_message, error),
        confirmText = stringResource(R.string.browser_ssl_retry),
        dismissText = stringResource(R.string.browser_cancel),
        onConfirm = onRetry,
        onDismiss = onDismiss,
    )
}
