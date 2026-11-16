package com.webshell.feature.viewer

import android.content.Intent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import com.mikepenz.markdown.m3.Markdown

/**
 * Renders sanitized markdown and only forwards http(s) links to an external viewer.
 * Remote images are not loaded; the renderer modules that would fetch them are not on the classpath.
 */
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
