package com.webshell.feature.browser

import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tab
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.core.data.HistoryEntity
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.ShellListener
import com.webshell.core.webengine.WebViewPool
import com.webshell.core.webengine.compose.ShellWebViewHost

/**
 * 多标签浏览器（M2）：
 * - Compose 只承载浏览器 Chrome（地址栏/标签墙/查找栏/菜单）；
 * - 内容区 = ShellWebViewHost(sessionId = "browser-<tabId>")：同一 tabId 永远复用
 *   池中的同一个 ShellWebView 实例，切 tab 只换 activeTabId，不销毁会话；
 * - 切走前在点击处理器里同步截缩略图（captureThumbnail 是同步绘制，必须在
 *   视图仍 attached 时执行）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onOpenUrl: (String) -> Unit = {},
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val findState by viewModel.findState.collectAsStateWithLifecycle()
    val bookmarkedUrls by viewModel.bookmarkedUrls.collectAsStateWithLifecycle()
    val desktopModes by viewModel.desktopModes.collectAsStateWithLifecycle()

    var urlInput by rememberSaveable { mutableStateOf("") }
    /** 地址栏是否处于焦点编辑中：编辑中不被页面回调覆盖输入 */
    var editing by rememberSaveable { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var permissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var sslWarning by remember { mutableStateOf<Pair<String, String>?>(null) }
    var sslProceedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

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

    val activeSessionId = activeTabId?.let { "browser-$it" }
    val activeTab = tabs.firstOrNull { it.tabId == activeTabId }

    // 进度/加载/返回前进/标题/URL 全部从激活 tab 的 per-tab 字段派生，切 tab 天然不串台
    val progress = activeTab?.progress ?: 0
    val loading = activeTab?.loading ?: false
    val canGoBack = activeTab?.canGoBack == true
    val canGoForward = activeTab?.canGoForward == true
    val pageTitle = activeTab?.title.orEmpty()

    // 切 tab 时地址栏从 activeTab.url 重置（无论是否在编辑）
    LaunchedEffect(activeTabId) {
        editing = false
        urlInput = activeTab?.url.orEmpty().stripScheme()
    }

    // 页面导航时地址栏跟随 tab URL；编辑中（焦点在地址栏）不覆盖用户输入
    LaunchedEffect(activeTab?.url) {
        if (!editing) urlInput = activeTab?.url.orEmpty().stripScheme()
    }

    // 切 tab：暂停切走会话的渲染/媒体，恢复新会话并标记激活保护；
    // 切回时主动向 shell 查询刷新 per-tab 导航状态（后台期间的尾部回调可能已错过）
    var previousSessionId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeSessionId) {
        previousSessionId?.takeIf { it != activeSessionId }?.let { WebViewPool.suspendSession(it) }
        val sid = activeSessionId
        val tabId = activeTabId
        if (sid != null && tabId != null) {
            WebViewPool.resumeSession(sid)
            WebViewPool.activeSessionId = sid
            WebViewPool.get(sid)?.let { shell ->
                shell.currentUrl()?.let { viewModel.updateTabMeta(tabId, url = it) }
                viewModel.updateTabNav(
                    tabId,
                    canGoBack = shell.canGoBack(),
                    canGoForward = shell.canGoForward(),
                )
            }
        }
        previousSessionId = activeSessionId
    }

    // 新会话首装：池中刚创建（currentUrl 为空/about:blank）时加载 tab 携带的起始 URL。
    // 已有导航历史的会话不做任何事——切回旧 tab 不能重载。
    LaunchedEffect(activeSessionId, activeTab?.url) {
        val sessionId = activeSessionId ?: return@LaunchedEffect
        val url = activeTab?.url ?: return@LaunchedEffect
        if (url.isBlank() || url == "about:blank") return@LaunchedEffect
        val shell = WebViewPool.get(sessionId) ?: return@LaunchedEffect
        val current = shell.currentUrl()
        if (current == null || current == "about:blank") {
            shell.loadWithStateRestore(url)
        }
    }

    // 查找结果回调：ShellWebView.onFindResult 是按会话的 var，随激活会话换绑
    DisposableEffect(activeSessionId) {
        val shell = activeSessionId?.let { WebViewPool.get(it) }
        shell?.onFindResult = { active, total ->
            viewModel.onFindResult(active, total)
        }
        onDispose { shell?.onFindResult = null }
    }

    // UI 临时监听器：只承载对话框/toast/文件选择/权限/新窗口等 UI 交互。
    // 状态写回类回调（标题/URL/进度/返回栈/历史）由 ViewModel 的 sessionListener
    // 按各自 tabId 持久承担，这里不再重复写，避免双写与串台。
    val listener = remember {
        object : ShellListener {
            override fun onNewWindow(url: String) {
                // target=_blank / window.open：后台开新标签并前置，会话由池接管
                viewModel.createTab(url, activate = true)
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
                    .onFailure {
                        callback.onReceiveValue(null)
                        fileChooserCallback = null
                    }
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

    fun captureAndStore(tabId: String) {
        runCatching {
            WebViewPool.get("browser-$tabId")?.captureThumbnail()?.let { bmp ->
                viewModel.updateTabThumbnail(tabId, bmp)
            }
        }
    }

    fun switchTo(tabId: String) {
        val current = activeTabId
        if (current != null && current != tabId) {
            captureAndStore(current)
            WebViewPool.detach("browser-$current")
        }
        viewModel.activateTab(tabId)
        showTabSwitcher = false
    }

    fun newTab(startUrl: String = "about:blank") {
        viewModel.createTab(startUrl = startUrl, activate = true)
    }

    // 返回键分层：标签墙 > 查找栏 > 页面历史 > 弹出激活标签（最后一个则回到空态）
    BackHandler(enabled = true) {
        when {
            showTabSwitcher -> showTabSwitcher = false
            findState.visible -> viewModel.hideFindBar()
            activeSessionId != null && canGoBack -> {
                WebViewPool.get(activeSessionId)?.goBack()
            }
            activeTabId != null -> {
                activeTabId?.let { tabId ->
                    captureAndStore(tabId)
                    viewModel.closeTab(tabId)
                }
            }
            else -> Unit // 空态：无标签可退，交给外层导航
        }
    }

    val currentUrl = activeTab?.url.orEmpty()
    val isBookmarked = currentUrl in bookmarkedUrls
    val desktopOn = activeSessionId != null && desktopModes[activeSessionId] == true

    Column(modifier = Modifier.fillMaxSize()) {
        BrowserTopBar(
            urlInput = urlInput,
            onUrlInputChanged = { urlInput = it },
            onEditingChanged = { editing = it },
            onGo = {
                val normalized = normalizeUrl(urlInput)
                if (normalized.isNotEmpty()) {
                    onOpenUrl(normalized)
                    val sid = activeSessionId
                    if (sid != null) {
                        WebViewPool.get(sid)?.loadWithStateRestore(normalized)
                    } else {
                        // 空态直接输入网址：建第一个标签并由 LaunchedEffect 装载
                        newTab(startUrl = normalized)
                    }
                }
            },
            canGoBack = canGoBack,
            onBack = { activeSessionId?.let { WebViewPool.get(it)?.goBack() } },
            canGoForward = canGoForward,
            onForward = { activeSessionId?.let { WebViewPool.get(it)?.goForward() } },
            loading = loading,
            onRefresh = { activeSessionId?.let { WebViewPool.get(it)?.reload() } },
            onStop = { activeSessionId?.let { WebViewPool.get(it)?.stopLoading() } },
            tabCount = tabs.size,
            onTabSwitcher = { showTabSwitcher = true },
            onNewTab = { newTab() },
            bookmarked = isBookmarked,
            onBookmark = {
                if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
                    viewModel.toggleBookmark(currentUrl, pageTitle.ifBlank { currentUrl })
                    toast = if (isBookmarked) "已取消收藏" else "已收藏"
                }
            },
            onFind = { viewModel.showFindBar() },
            menuExpanded = menuExpanded,
            onMenuToggle = { menuExpanded = it },
            desktopMode = desktopOn,
            onDesktopMode = {
                menuExpanded = false
                activeSessionId?.let { sessionId ->
                    viewModel.setDesktopMode(sessionId, !desktopOn)
                    toast = if (!desktopOn) "桌面版：开" else "桌面版：关"
                }
            },
            onHistory = {
                menuExpanded = false
                showHistory = true
            },
            onBookmarks = {
                menuExpanded = false
                showBookmarks = true
            },
            onCloseAllTabs = {
                menuExpanded = false
                showTabSwitcher = false
                viewModel.closeAllTabs()
            },
            progress = progress,
        )

        if (findState.visible) {
            FindBar(
                query = findState.query,
                active = findState.active,
                total = findState.total,
                onQueryChanged = { viewModel.updateFindQuery(it) },
                onPrevious = { viewModel.findNext(false) },
                onNext = { viewModel.findNext(true) },
                onClose = { viewModel.hideFindBar() },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (activeTab != null && activeSessionId != null) {
                ShellWebViewHost(
                    sessionId = activeSessionId,
                    configFactory = {
                        ShellConfig(
                            sessionId = activeSessionId,
                            startUrl = activeTab.url,
                            desktopMode = desktopModes[activeSessionId] == true,
                        )
                    },
                    listener = listener,
                )
                // 持久监听者随会话存活（出组合不摘除）：后台 tab 的标题/URL/进度
                // 回调由 ViewModel 按 tabId 写回 per-tab 状态；closeTab 时统一清除
                SideEffect {
                    WebViewPool.get(activeSessionId)?.sessionListener =
                        viewModel.listenerFor(activeSessionId)
                }
            } else {
                EmptyTabsPrompt(
                    modifier = Modifier.fillMaxSize(),
                    onNewTab = { newTab() },
                )
            }

            toast?.let { message ->
                Text(
                    message,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(2500)
                    toast = null
                }
            }
        }
    }

    // ---------------------------------------------------------------- overlays

    if (showTabSwitcher) {
        TabSwitcherSheet(
            tabs = tabs,
            activeTabId = activeTabId,
            onActivate = { switchTo(it) },
            onClose = { tabId -> viewModel.closeTab(tabId) },
            onNewTab = {
                showTabSwitcher = false
                newTab()
            },
            onCloseAll = {
                viewModel.closeAllTabs()
                showTabSwitcher = false
            },
            onDismiss = { showTabSwitcher = false },
        )
    }

    if (showHistory) {
        HistorySheet(
            viewModel = viewModel,
            onOpen = { entry ->
                showHistory = false
                val normalized = normalizeUrl(entry.url)
                val sid = activeSessionId
                if (sid != null) {
                    WebViewPool.get(sid)?.loadWithStateRestore(normalized)
                } else {
                    newTab(startUrl = normalized)
                }
            },
            onDismiss = { showHistory = false },
        )
    }

    if (showBookmarks) {
        BookmarksSheet(
            viewModel = viewModel,
            onOpen = { url ->
                showBookmarks = false
                val normalized = normalizeUrl(url)
                val sid = activeSessionId
                if (sid != null) {
                    WebViewPool.get(sid)?.loadWithStateRestore(normalized)
                } else {
                    newTab(startUrl = normalized)
                }
            },
            onDismiss = { showBookmarks = false },
        )
    }

    sslWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { sslWarning = null; sslProceedAction = null },
            title = { Text("证书警告") },
            text = { Text("网站证书校验失败（${warning.second}），连接不安全。是否仍然继续？") },
            confirmButton = {
                TextButton(onClick = {
                    sslProceedAction?.invoke()
                    sslWarning = null
                    sslProceedAction = null
                }) { Text("仍要继续") }
            },
            dismissButton = {
                TextButton(onClick = { sslWarning = null; sslProceedAction = null }) {
                    Text("离开")
                }
            },
        )
    }
}

private fun String.stripScheme(): String =
    removePrefix("https://").removePrefix("http://")

/** 补全 scheme；含空格视为搜索词原样返回（M2 仅做 URL 跳转） */
internal fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
        else -> trimmed
    }
}

@Composable
private fun BrowserTopBar(
    urlInput: String,
    onUrlInputChanged: (String) -> Unit,
    onEditingChanged: (Boolean) -> Unit,
    onGo: () -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
    canGoForward: Boolean,
    onForward: () -> Unit,
    loading: Boolean,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    tabCount: Int,
    onTabSwitcher: () -> Unit,
    onNewTab: () -> Unit,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onFind: () -> Unit,
    menuExpanded: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    desktopMode: Boolean,
    onDesktopMode: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onCloseAllTabs: () -> Unit,
    progress: Int,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            // 单行紧凑顶栏：地址栏与标签按钮统一 46dp 高，操作入口收进右侧菜单
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = onForward,
                    enabled = canGoForward,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "前进",
                        modifier = Modifier.size(22.dp),
                    )
                }
                // 自绘地址栏：BasicTextField + 胶囊容器，文本垂直居中，
                // 不用 M3 OutlinedTextField（其固定内边距会在 46dp 高度下把文字截半）
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(23.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp),
                    ) {
                        BasicTextField(
                            value = urlInput,
                            onValueChange = onUrlInputChanged,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { onEditingChanged(it.isFocused) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { onGo() }),
                            decorationBox = { innerTextField ->
                                if (urlInput.isEmpty()) {
                                    Text(
                                        "输入网址或搜索",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                innerTextField()
                            },
                        )
                        IconButton(onClick = if (loading) onStop else onRefresh) {
                            Icon(
                                if (loading) Icons.Filled.Stop else Icons.Filled.Refresh,
                                contentDescription = if (loading) "停止" else "刷新",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Surface(
                    onClick = onTabSwitcher,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(46.dp),
                    shape = RoundedCornerShape(23.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) { TabCountBadge(tabCount) }
                }
                Box {
                    IconButton(onClick = { onMenuToggle(!menuExpanded) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "菜单",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuToggle(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (bookmarked) "取消收藏" else "收藏本页") },
                            leadingIcon = {
                                Icon(
                                    if (bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = null,
                                    tint = if (bookmarked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = { onMenuToggle(false); onBookmark() },
                        )
                        DropdownMenuItem(
                            text = { Text("页内查找") },
                            leadingIcon = { Icon(Icons.Filled.FindInPage, contentDescription = null) },
                            onClick = { onMenuToggle(false); onFind() },
                        )
                        DropdownMenuItem(
                            text = { Text("新标签页") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = { onMenuToggle(false); onNewTab() },
                        )
                        DropdownMenuItem(
                            text = { Text("桌面版") },
                            leadingIcon = {
                                Icon(Icons.Filled.DesktopWindows, contentDescription = null)
                            },
                            trailingIcon = {
                                if (desktopMode) Icon(Icons.Filled.Check, contentDescription = "已开启")
                            },
                            onClick = onDesktopMode,
                        )
                        DropdownMenuItem(
                            text = { Text("历史") },
                            leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                            onClick = onHistory,
                        )
                        DropdownMenuItem(
                            text = { Text("收藏夹") },
                            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                            onClick = onBookmarks,
                        )
                        DropdownMenuItem(
                            text = { Text("关闭全部标签") },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                            onClick = onCloseAllTabs,
                        )
                    }
                }
            }
            if (loading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 标签计数徽标（标签墙入口） */
@Composable
private fun TabCountBadge(tabCount: Int) {
    Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Tab, contentDescription = "标签页")
        Text(
            text = tabCount.coerceAtMost(99).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun FindBar(
    query: String,
    active: Int,
    total: Int,
    onQueryChanged: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("在页面中查找") },
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (total > 0) "${active + 1}/$total" else "0/0",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onPrevious, enabled = total > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一个")
            }
            IconButton(onClick = onNext, enabled = total > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一个")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭查找")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabSwitcherSheet(
    tabs: List<BrowserTab>,
    activeTabId: String?,
    onActivate: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "标签页（${tabs.size}）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNewTab) { Text("新标签页") }
            TextButton(onClick = onCloseAll) { Text("关闭全部") }
        }
        if (tabs.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无标签页", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tabs, key = { it.tabId }) { tab ->
                    TabCard(
                        tab = tab,
                        active = tab.tabId == activeTabId,
                        onClick = { onActivate(tab.tabId) },
                        onClose = { onClose(tab.tabId) },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TabCard(
    tab: BrowserTab,
    active: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val borderColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val thumb = tab.thumbnail
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Filled.Public,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭标签",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(3.dp),
            )
        }
        Column(Modifier.padding(8.dp)) {
            Text(
                tab.title.ifBlank { "新标签页" },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tab.url.stripScheme(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    viewModel: BrowserViewModel,
    onOpen: (HistoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val history by viewModel.history.collectAsStateWithLifecycle(initialValue = emptyList())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "历史",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.clearHistory() }) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "清空历史")
            }
        }
        if (history.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无历史记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            ) {
                items(history, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(entry) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                entry.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(
    viewModel: BrowserViewModel,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle(initialValue = emptyList())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "收藏夹",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        if (bookmarks.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(bookmark.url) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                bookmark.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                bookmark.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { viewModel.removeBookmark(bookmark.url) }) {
                            Icon(Icons.Filled.Close, contentDescription = "删除收藏")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyTabsPrompt(
    modifier: Modifier = Modifier,
    onNewTab: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Public,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("暂无标签页", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "输入网址或新建标签页开始浏览",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onNewTab) { Text("新建标签页") }
    }
}
