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
