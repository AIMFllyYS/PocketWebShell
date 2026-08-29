package com.webshell.feature.add

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

/**
 * 添加页：网址 → 应用。两步流程：
 * 1. 输入网址（或导入本地 HTML）→ 抓取站点元数据；
 * 2. 编辑属性（图标/标题/桌面模式/深色/保活/外链/缩放）→ 保存到主页。
 */
@Composable
fun AddScreen(
    modifier: Modifier = Modifier,
    onCreated: () -> Unit = {},
    viewModel: AddViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importLocal(uris) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.created.collect { onCreated() }
    }

    when (val s = state) {
        AddUiState.Input -> InputStep(
            modifier = modifier,
            onConfirm = viewModel::confirmUrl,
            onImportLocal = {
                importLauncher.launch(arrayOf("text/html"))
            },
        )

        AddUiState.Loading -> LoadingStep(modifier)

        is AddUiState.Edit -> EditStep(
            modifier = modifier,
            draft = s.draft,
            fetchFailed = s.fetchFailed,
            onUpdate = viewModel::updateDraft,
            onSave = viewModel::save,
            onBack = viewModel::reset,
        )
    }
}

// ===== 第一步：输入网址 =====

@Composable
private fun InputStep(
    modifier: Modifier,
    onConfirm: (String) -> Unit,
    onImportLocal: () -> Unit,
) {
    var urlText by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    val valid = AddViewModel.normalizeUrl(urlText) != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Public,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("把网址做成应用", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "输入一个网址，为它定制图标与属性，\n添加后像原生应用一样出现在主页",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = urlText,
            onValueChange = {
                urlText = it
                showError = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("网址") },
            placeholder = { Text("例如：github.com") },
            singleLine = true,
            isError = showError,
            supportingText = if (showError) {
                { Text("请输入有效的网址", color = MaterialTheme.colorScheme.error) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (valid) onConfirm(urlText) else showError = true
            }),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (valid) onConfirm(urlText) else showError = true
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = urlText.isNotBlank(),
        ) {
            Text("确认")
        }
        Spacer(Modifier.height(32.dp))
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Text(
            "没有网址？也可以导入本地网页",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onImportLocal, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Description, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("导入本地 HTML")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "支持选择一个或多个 .html 文件，导入后离线可用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

// ===== 加载中 =====

@Composable
private fun LoadingStep(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("正在获取站点信息…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ===== 第二步：编辑属性 =====

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("编辑属性") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (fetchFailed) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "无法获取站点信息，请手动填写标题（稍后可再改图标）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // 图标预览 + 图标 URL
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconPreview(draft, 72.dp)
                Spacer(Modifier.size(16.dp))
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { value -> onUpdate { it.copy(title = value) } },
                    label = { Text("应用名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = draft.iconUrl,
                onValueChange = { value -> onUpdate { it.copy(iconUrl = value) } },
                label = { Text("图标地址（可选）") },
                placeholder = { Text("https://…/icon.png") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = draft.url,
                onValueChange = { value -> onUpdate { it.copy(url = value) } },
                label = { Text(if (draft.isLocal) "本地入口文件" else "网址") },
                singleLine = true,
                enabled = !draft.isLocal,
                readOnly = draft.isLocal,
                supportingText = if (draft.isLocal) {
                    { Text("导入的本地网页已存入应用目录，离线可用") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            SwitchRow("桌面模式", "以电脑版网页渲染", draft.desktopMode) { v ->
                onUpdate { it.copy(desktopMode = v) }
            }
            SwitchRow("深色适配", "网页未适配时自动反色", draft.darkMode) { v ->
                onUpdate { it.copy(darkMode = v) }
            }
            SwitchRow("后台保活", "切到后台后静默保持运行（核心特性）", draft.keepAlive) { v ->
                onUpdate { it.copy(keepAlive = v) }
            }
            SwitchRow("外链在系统浏览器打开", "站外链接交给浏览器处理", draft.externalLinksToBrowser) { v ->
                onUpdate { it.copy(externalLinksToBrowser = v) }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "文字大小 ${draft.textZoomPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Slider(
                value = draft.textZoomPercent.toFloat(),
                onValueChange = { value ->
                    onUpdate { it.copy(textZoomPercent = value.toInt()) }
                },
                valueRange = 80f..130f,
                steps = 9, // 5% 一档：80…130
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("保存", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 图标预览：有地址用 AsyncImage，加载失败/为空时回退为主题色圆 + 首字母（或地球） */
@Composable
private fun AppIconPreview(draft: AddDraft, size: androidx.compose.ui.unit.Dp) {
    val themeColor = draft.themeColor?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // 回退层：网络图加载失败时自然露出
        Box(
            modifier = Modifier
                .size(size)
                .background(themeColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (draft.iconUrl.isBlank() || draft.isLocal) {
                Icon(
                    imageVector = if (draft.isLocal) Icons.Filled.Description else Icons.Filled.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(size / 2),
                )
            } else {
                Text(
                    text = (draft.title.trim().firstOrNull()?.uppercase() ?: "?"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        if (draft.iconUrl.isNotBlank() && !draft.isLocal) {
            AsyncImage(
                model = draft.iconUrl,
                contentDescription = "应用图标",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}
