package com.webshell.core.webengine.compose

import android.view.ViewGroup
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.ShellListener
import com.webshell.core.webengine.WebViewPool
import com.webshell.core.webengine.remainingWebTopInset
import com.webshell.core.model.WebTopInsetMode

/**
 * The sole native-view lifecycle boundary. A session retains its WebView/history in the pool,
 * while UI callbacks and visible-session protection belong to this host.
 * Parents using Compose safeDrawing/imePadding opt into [parentHandlesInsets] to avoid double
 * padding. Full-screen site shells keep the existing native PAD/CSS_ONLY behavior by default.
 */
@Composable
fun ShellWebViewHost(
    sessionId: String,
    configFactory: () -> ShellConfig,
    listener: ShellListener? = null,
    modifier: Modifier = Modifier,
    insetMode: ShellConfig.InsetMode? = null,
    parentHandlesInsets: Boolean = false,
    parentHandlesTopInset: Boolean = parentHandlesInsets,
    isVisible: Boolean = true,
    sessionListener: ShellListener? = null,
    onFindResult: ((Int, Int) -> Unit)? = null,
    onReady: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shell = remember(sessionId) { WebViewPool.getOrCreate(context, sessionId) { configFactory() } }
    val ownership = remember(shell) { Any() }
    val visible = rememberUpdatedState(isVisible)
    val latestReady = rememberUpdatedState(onReady)
    val currentConfig = configFactory()
    val actualInsetMode = insetMode ?: currentConfig.insetMode
    val topMode = currentConfig.topInsetMode

    SideEffect {
        shell.uiHostOwner = ownership
        // The pooled instance outlives this composition. Re-apply the latest
        // settings whenever the host observes a changed config (UA, zoom,
        // darkening, cookie policy, autoplay, refresh and inset behavior).
        shell.reconfigure(currentConfig)
        shell.listener = if (isVisible) listener else null
        shell.onFindResult = if (isVisible) onFindResult else null
        if (sessionListener != null) shell.sessionListener = sessionListener
    }
    LaunchedEffect(shell) { latestReady.value?.invoke() }

    DisposableEffect(shell, lifecycleOwner, isVisible) {
        if (isVisible) {
            WebViewPool.activeSessionId = sessionId
            WebViewPool.protect(sessionId, WebViewPool.ProtectionReason.ACTIVE)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) shell.resumeRendering()
        } else {
            shell.suspendRendering()
            if (WebViewPool.activeSessionId == sessionId) WebViewPool.activeSessionId = null
            WebViewPool.unprotect(sessionId, WebViewPool.ProtectionReason.ACTIVE)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (shell.uiHostOwner == ownership) {
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> shell.suspendRendering()
                    Lifecycle.Event.ON_RESUME -> if (visible.value) shell.resumeRendering()
                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (shell.uiHostOwner == ownership) {
                shell.listener = null
                shell.onFindResult = null
                shell.suspendRendering()
                WebViewPool.unprotect(sessionId, WebViewPool.ProtectionReason.ACTIVE)
                shell.uiHostOwner = null
                if (WebViewPool.activeSessionId == sessionId) WebViewPool.activeSessionId = null
            }
        }
    }

    DisposableEffect(shell, parentHandlesInsets, parentHandlesTopInset, actualInsetMode, topMode) {
        var lastInsets: WindowInsetsCompat? = null
        fun applyInsets(insets: WindowInsetsCompat) {
            if (shell.uiHostOwner !== ownership) return
            val rootInsets = ViewCompat.getRootWindowInsets(shell) ?: insets
            val topTypes = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            val safeTop = if (topMode == WebTopInsetMode.AVOID) {
                rootInsets.getInsetsIgnoringVisibility(topTypes).top
            } else rootInsets.getInsets(topTypes).top
            val hostTop = if (ViewCompat.isLaidOut(shell) && shell.isAttachedToWindow) {
                val hostLocation = IntArray(2)
                shell.getLocationInWindow(hostLocation)
                hostLocation[1]
            } else null
            val remainingTop = remainingWebTopInset(topMode, safeTop, hostTop, parentHandlesTopInset)
            val nativeTop = if (actualInsetMode == ShellConfig.InsetMode.PAD || topMode == WebTopInsetMode.AVOID) {
                remainingTop
            } else 0
            val bars = if (parentHandlesInsets) androidx.core.graphics.Insets.NONE else insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val ime = if (parentHandlesInsets) 0 else insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            // Native padding owns the top in PAD mode. A page must not receive it again in CSS.
            shell.updateSafeAreaInsets(remainingTop - nativeTop, bars.bottom, bars.left, bars.right, ime)
            val left = if (actualInsetMode == ShellConfig.InsetMode.PAD) bars.left else 0
            val right = if (actualInsetMode == ShellConfig.InsetMode.PAD) bars.right else 0
            val bottom = if (actualInsetMode == ShellConfig.InsetMode.PAD) maxOf(bars.bottom, ime) else 0
            if (shell.paddingLeft != left || shell.paddingTop != nativeTop || shell.paddingRight != right || shell.paddingBottom != bottom) {
                shell.setPadding(left, nativeTop, right, bottom)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(shell) { _, insets ->
            lastInsets = insets
            applyInsets(insets)
            // Padding already owns this edge, including when the user deliberately disables it.
            // Keep real side/bottom cutouts and IME; only remove the duplicate top information.
            insets.withoutWebTopInset()
        }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            lastInsets?.let(::applyInsets)
        }
        shell.addOnLayoutChangeListener(layoutListener)
        ViewCompat.requestApplyInsets(shell)
        onDispose {
            shell.removeOnLayoutChangeListener(layoutListener)
            if (shell.uiHostOwner == ownership || shell.uiHostOwner == null) {
                ViewCompat.setOnApplyWindowInsetsListener(shell, null)
            }
        }
    }

    // AndroidView.factory is not re-run by a changed remember(sessionId). Keying the native
    // host is essential: otherwise switching tabs can leave the previous WebView on screen.
    key(shell) {
        Box(modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    shell.apply {
                        (parent as? ViewGroup)?.removeView(this)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
            )
        }
    }
}
