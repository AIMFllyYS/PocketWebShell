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
