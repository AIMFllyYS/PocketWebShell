package com.webshell.app.shell

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewFeature
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.ShellListener
import com.webshell.core.webengine.WebViewPool
import com.webshell.core.webengine.compose.ShellWebViewHost
import java.util.UUID

/**
 * 单会话沉浸壳入口：主页打开应用 / `--es url` 外部直达时使用。
 * 浏览 tab 的多标签浏览已由 feature/browser 的 BrowserScreen 承担。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(initialUrl: String? = null, immersive: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionPrefix = "browse"

    var urlInput by remember { mutableStateOf(initialUrl.orEmpty()) }
    var activeUrl by remember { mutableStateOf(initialUrl) }
    var sessionKey by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }

    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var permissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var sslWarning by remember { mutableStateOf<Pair<String, String>?>(null) }
    var sslProceedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        fileChooserCallback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
        )
        fileChooserCallback = null
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionRequest?.let { request ->
            val allGranted = request.resources.all { res ->
                grants[res] == true ||
                    ContextCompat.checkSelfPermission(context, res) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) request.grant(request.resources) else request.deny()
        }
        permissionRequest = null
    }

    val listener = remember {
        object : ShellListener {
            override fun onPageStarted(url: String) {
                loading = true
                activeUrl = url
                urlInput = url
            }

            override fun onProgress(value: Int) {
                progress = value
                loading = value < 100
            }

            override fun onPageFinished(url: String) {
                loading = false
            }

            override fun onTitleReceived(title: String) {
                pageTitle = title
            }

            override fun onCanGoBackChanged(value: Boolean) {
                canGoBack = value
            }

            override fun onNewWindow(url: String) {
                sessionKey = null
                activeUrl = url
                urlInput = url
            }

            override fun onDownloadStarted(fileName: String) {
                toast = "开始下载：$fileName"
            }

            override fun onFileChooserRequested(
                params: WebChromeClient.FileChooserParams,
                callback: ValueCallback<Array<Uri>>,
            ) {
                fileChooserCallback = callback
                runCatching { fileChooserLauncher.launch(params.createIntent()) }
                    .onFailure { callback.onReceiveValue(null); fileChooserCallback = null }
            }

            override fun onPermissionRequested(request: PermissionRequest) {
                permissionRequest = request
                runtimePermissionLauncher.launch(request.resources)
            }

            override fun onSslError(url: String, error: String, proceed: () -> Unit) {
                sslWarning = url to error
                sslProceedAction = proceed
            }

            override fun onRenderProcessRecovered() {
                toast = "页面渲染进程已恢复"
            }

            override fun onExternalLaunchFailed(url: String) {
                toast = "无法打开：$url"
            }
        }
    }

    // 返回键：会话内可返回则消费，否则交给 tab 切换
    BackHandler(enabled = sessionKey != null && canGoBack) {
        WebViewPool.get(sessionKey!!)?.let { shell ->
            runCatching { shellBack(shell) }
        }
    }

    fun openUrl(raw: String) {
        val normalized = normalizeUrl(raw)
        if (normalized.isEmpty()) return
        if (sessionKey == null) sessionKey = "$sessionPrefix-${UUID.randomUUID()}"
        activeUrl = normalized
        urlInput = normalized
        val shell = WebViewPool.getOrCreate(context, sessionKey!!) {
            ShellConfig(sessionId = sessionKey, startUrl = normalized)
        }
        val current = shell.currentUrl()
        if (current == null || current == "about:blank") {
            shell.loadWithStateRestore(normalized)
        }
    }

    // 初始 URL（从主页打开应用时传入）
    androidx.compose.runtime.LaunchedEffect(initialUrl) {
        if (initialUrl != null && sessionKey == null) openUrl(initialUrl)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!immersive) {
            TopAppBar(
                title = {
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        placeholder = { Text("输入网址") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            sessionKey?.let { key -> WebViewPool.get(key)?.let { shellBack(it) } }
                        },
                        enabled = canGoBack,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(
                        onClick = { sessionKey?.let { key ->
                            WebViewPool.get(key)?.reloadWithStateRestore()
                        } },
                    ) { Icon(Icons.Filled.Refresh, "刷新") }
                    TextButton(onClick = { openUrl(urlInput) }) { Text("前往") }
                },
            )
            if (loading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            sessionKey?.let { key ->
                ShellWebViewHost(
                    sessionId = key,
                    configFactory = {
                        ShellConfig(sessionId = key, startUrl = activeUrl ?: "about:blank")
                    },
                    listener = listener,
                )
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "输入网址开始浏览",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            toast?.let { message ->
                Text(
                    message,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                androidx.compose.runtime.LaunchedEffect(message) {
                    kotlinx.coroutines.delay(2500)
                    toast = null
                }
            }
        }
    }

    if (sslWarning != null) {
        AlertDialog(
            onDismissRequest = { sslWarning = null; sslProceedAction = null },
            title = { Text("证书警告") },
            text = { Text("网站证书校验失败（${sslWarning!!.second}），连接不安全。是否仍然继续？") },
            confirmButton = {
                TextButton(onClick = {
                    sslProceedAction?.invoke()
                    sslWarning = null
                    sslProceedAction = null
                }) { Text("仍要继续") }
            },
            dismissButton = {
                TextButton(onClick = { sslWarning = null; sslProceedAction = null }) {
                    Text("离开") }
            },
        )
    }
}

private fun shellBack(shell: com.webshell.core.webengine.ShellWebView) {
    runCatching { shell.webView.goBack() }
}

internal fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
        else -> trimmed
    }
}
