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
