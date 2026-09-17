package com.webshell.feature.me

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.mikepenz.markdown.m3.Markdown
import com.webshell.core.designsystem.components.RevealFromPoint
import com.webshell.core.designsystem.components.staticGlassSurface

/**
 * 版本更新弹窗：
 * - 顶部固定标头（标题与当前/目标版本提示）
 * - 中间内容区自适应卡片宽度，支持完整 Markdown 富文本渲染与上下安全滚动
 * - 底部固定双按钮操作坞（稍后 / 立即下载）
 */
@Composable
fun UpdateAvailableDialog(
    offer: UpdateOffer,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = remember(context) {
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            RevealFromPoint(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 360.dp)
                    .fillMaxWidth(),
                origin = TransformOrigin.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staticGlassSurface(
                            shape = RoundedCornerShape(26.dp),
                            tint = MaterialTheme.colorScheme.surfaceContainerLow,
                            opacity = 0.98f,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 顶部固定标头
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.me_update_available_title, offer.latest),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.me_update_available_text, offer.installed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    val separatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
                    HorizontalDivider(thickness = 0.5.dp, color = separatorColor)

                    // 中间安全滚动区域（依据卡片宽度自适应约束，支持平滑上下滚动）
                    if (offer.notes.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 340.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                                SelectionContainer {
                                    Markdown(
                                        content = offer.notes,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = separatorColor)
                    }

                    // 底部固定双按钮操作坞
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RectangleShape,
                        ) {
                            Text(
                                text = stringResource(R.string.me_update_later),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(0.5.dp)
                                .background(separatorColor),
                        )
                        TextButton(
                            onClick = onConfirm,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RectangleShape,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.me_update_download),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
