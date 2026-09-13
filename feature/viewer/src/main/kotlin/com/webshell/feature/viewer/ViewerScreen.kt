package com.webshell.feature.viewer

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.m3.Markdown
import com.webshell.core.designsystem.components.AppConfirmDialog
import com.webshell.core.designsystem.components.AppNavigationBar
import com.webshell.core.webengine.compose.ShellWebViewHost
import com.webshell.feature.browser.WebSessionDialogs
import com.webshell.feature.browser.WebSessionEmptyState
import com.webshell.feature.browser.WebSessionFullScreen
import com.webshell.feature.browser.rememberWebSessionRequests

@Composable
fun ViewerScreen(
    candidate: IncomingOpenCandidate,
    onLeave: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(candidate.token) { viewModel.open(candidate) }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show() }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.release() }
    }
    val leave = {
        viewModel.release()
        onLeave()
    }
    BackHandler(onBack = leave)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        when (val current = state) {
            ViewerState.Idle, ViewerState.Loading -> {
                AppNavigationBar(title = stringResource(R.string.viewer_opening), onBack = leave)
                WebSessionEmptyState(
                    title = stringResource(R.string.viewer_opening),
                    description = stringResource(R.string.viewer_opening_hint),
                    actionLabel = stringResource(R.string.viewer_opening),
                    onAction = {},
                    loading = true,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            is ViewerState.Failed -> {
                AppNavigationBar(title = stringResource(R.string.viewer_unavailable), onBack = leave)
                WebSessionEmptyState(
                    title = stringResource(R.string.viewer_unavailable),
                    description = stringResource(failureDescription(current.reason)),
                    actionLabel = stringResource(R.string.viewer_close),
                    onAction = leave,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            is ViewerState.Markdown -> {
                LaunchedEffect(current.displayPath, current.content) {
                    delay(400)
                    viewModel.offerAddToHome()
                }
                DocumentPreviewTopBar(displayPath = current.displayPath, onBack = leave)
                SafeMarkdown(
                    content = current.content,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
                if (current.askAddToHome) {
                    AddToHomeDialog(
                        displayPath = current.displayPath,
                        onConfirm = viewModel::addToHome,
                        onDismiss = viewModel::dismissAddToHome,
                    )
                }
            }
            is ViewerState.Html -> {
                DocumentPreviewTopBar(
                    displayPath = current.displayPath,
                    onBack = leave,
                    loading = current.loading && !current.rendererGone,
                    progress = current.progress,
                    sessionKey = current.config.sessionId,
                )
                val sessionId = current.config.sessionId
                val requests = rememberWebSessionRequests(
                    sessionId = sessionId,
                    visible = true,
                    onNewWindow = { request ->
                        if (request.targetSessionId != request.sourceSessionId) {
                            viewModel.rejectAdoptedWindow(request.targetSessionId)
                        }
                    },
                    onMessage = {},
                )
                BackHandler(enabled = requests.fullScreenView != null) { requests.exitFullScreen() }
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    if (sessionId != null && !current.rendererGone) {
                        ShellWebViewHost(
                            sessionId = sessionId,
                            configFactory = { current.config },
                            listener = requests.listener,
                            sessionListener = remember(sessionId) { viewModel.htmlListener(sessionId) },
                            parentHandlesInsets = true,
                            onReady = { viewModel.onHtmlReady(current.config) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                        )
                    } else {
                        WebSessionEmptyState(
                            title = stringResource(R.string.viewer_renderer_gone),
                            description = stringResource(R.string.viewer_renderer_gone_hint),
                            actionLabel = stringResource(R.string.viewer_retry),
                            onAction = viewModel::reloadHtml,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                WebSessionDialogs(requests, onRetry = viewModel::reloadHtml, onLeave = leave)
                if (requests.fullScreenView != null) WebSessionFullScreen(requests)
                if (current.askAddToHome) {
                    AddToHomeDialog(
                        displayPath = current.displayPath,
                        onConfirm = viewModel::addToHome,
                        onDismiss = viewModel::dismissAddToHome,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddToHomeDialog(
    displayPath: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppConfirmDialog(
        title = stringResource(R.string.viewer_add_home_title),
        text = stringResource(R.string.viewer_add_home_text, displayPath),
        confirmText = stringResource(R.string.viewer_add_home_confirm),
        dismissText = stringResource(R.string.viewer_add_home_dismiss),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

private fun failureDescription(reason: ViewerFailure): Int = when (reason) {
    ViewerFailure.UNSUPPORTED -> R.string.viewer_unsupported_hint
    ViewerFailure.TOO_LARGE -> R.string.viewer_too_large_hint
    ViewerFailure.UNREADABLE -> R.string.viewer_unreadable_hint
}

@Composable
fun SafeMarkdown(content: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val handler = remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val parsed = runCatching { uri.toUri() }.getOrNull() ?: return
                val scheme = parsed.scheme?.lowercase() ?: return
                if (scheme != "http" && scheme != "https") return
                if (parsed.host.isNullOrBlank() || parsed.userInfo != null) return
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, parsed).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides handler) {
        SelectionContainer(modifier) {
            Markdown(content)
        }
    }
}
