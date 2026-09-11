package com.webshell.app.download

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webshell.app.R
import com.webshell.core.designsystem.components.staticGlassSurface
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.core.designsystem.theme.AppSpacing
import com.webshell.core.model.DownloadItem
import com.webshell.core.model.DownloadStatus

private val CardOrigin = TransformOrigin(0f, 1f)

@Composable
internal fun DownloadCompleteCard(
    item: DownloadItem,
    openFailed: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    var visible by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) { visible = true }
    BackHandler(onBack = onDismiss)
    val openLabel = stringResource(R.string.download_complete_open)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        AnimatedVisibility(
            visible = visible,
            enter = AppMotion.popupEnter(CardOrigin),
            exit = AppMotion.popupExit(CardOrigin),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = AppSpacing.lg, bottom = AppSpacing.xl, end = 72.dp),
        ) {
            val shape = RoundedCornerShape(28.dp)
            val title = stringResource(
                if (item.status == DownloadStatus.Failed) {
                    R.string.download_failed_title
                } else {
                    R.string.download_complete_title
                },
            )
            val message = stringResource(
                if (item.status == DownloadStatus.Failed) {
                    R.string.download_failed_message
                } else {
                    R.string.download_complete_message
                },
                item.displayName,
            )
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.12f),
                        spotColor = Color.Black.copy(alpha = 0.12f),
                    )
                    .staticGlassSurface(shape = shape, opacity = 0.96f)
                    .clickable(onClick = onOpen)
                    .semantics {
                        role = Role.Button
                        contentDescription = openLabel
                    }
                    .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xl),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppSpacing.sm),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.relativePath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppSpacing.sm),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = openLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = AppSpacing.md),
                )
                if (openFailed) {
                    Text(
                        text = stringResource(R.string.download_open_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = AppSpacing.sm),
                    )
                }
            }
        }
    }
}
