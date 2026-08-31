package com.webshell.core.webengine.compose

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.ShellListener
import com.webshell.core.webengine.ShellWebView
import com.webshell.core.webengine.WebViewPool

/**
 * Compose 宿主：从会话池取（或创建）ShellWebView 并装入组合树。
 * - 同一会话在 tab/主页之间往返不重建、不丢状态
 * - insets 自动写入 --ws-safe-* CSS 变量 + IME 避让
 * - 生命周期：ON_PAUSE/ON_RESUME 联动渲染暂停
 */
@Composable
fun ShellWebViewHost(
    sessionId: String,
    configFactory: () -> ShellConfig,
    listener: ShellListener? = null,
    modifier: Modifier = Modifier,
    insetMode: ShellConfig.InsetMode = ShellConfig.InsetMode.PAD,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shell = remember(sessionId) {
        WebViewPool.getOrCreate(context, sessionId) { configFactory() }
    }
    // listener 会随激活标签变化；不能用 remember(sessionId) 固化第一次组合时的闭包。
    SideEffect { shell.listener = listener }

    DisposableEffect(sessionId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> shell.suspendRendering()
                Lifecycle.Event.ON_RESUME -> shell.resumeRendering()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 会话保持在池中（保活/恢复的基础）；仅解除临时 UI 监听器。
            // sessionListener 是持久监听者，生命周期由 ViewModel 层管理，这里不动。
            shell.listener = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { _ ->
                shell.apply {
                    (parent as? ViewGroup)?.removeView(this)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { root ->
                // insets（含 IME）→ CSS 变量；PAD 模式同时做原生 padding 避让
                ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                    val bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                    )
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    shell.updateSafeAreaInsets(bars.top, bars.bottom, bars.left, bars.right, ime)
                    if (insetMode == ShellConfig.InsetMode.PAD) {
                        v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime))
                    } else {
                        v.setPadding(0, 0, 0, 0)
                    }
                    insets
                }
                // 监听器晚于视图 attach 设置时需主动触发一次分发
                ViewCompat.requestApplyInsets(root)
            },
        )
    }
}
