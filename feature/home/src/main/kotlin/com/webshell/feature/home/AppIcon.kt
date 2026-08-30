package com.webshell.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.webshell.core.data.WebAppEntity

/**
 * 主页图标：网站图标 + 圆角遮罩。
 * 收藏（来自"浏览"）与制作的应用以边框样式区分，图标同源。
 */
@Composable
fun AppIcon(
    app: WebAppEntity,
    size: Dp,
    cornerRadiusPercent: Int = 26,
    modifier: Modifier = Modifier,
    folderPreview: List<WebAppEntity> = emptyList(),
) {
    val shape = RoundedCornerShape(cornerRadiusPercent.coerceIn(0, 50))
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .then(
                if (app.isFavorite) {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary,
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (folderPreview.isNotEmpty()) {
            FolderPreview(
                apps = folderPreview,
                iconSize = size * 0.36f,
                cornerRadiusPercent = cornerRadiusPercent,
            )
        } else {
            SiteIcon(app = app, iconSize = size)
        }
    }
}

@Composable
private fun SiteIcon(app: WebAppEntity, iconSize: Dp) {
    val iconUrl = app.iconUrl
    val validIcon = !iconUrl.isNullOrBlank() &&
        (iconUrl.startsWith("http://") || iconUrl.startsWith("https://"))
    if (!validIcon) {
        IconFallback(title = app.title, size = iconSize)
    } else {
        AsyncImage(
            model = iconUrl,
            contentDescription = app.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(iconSize)
                .background(Color.White)
                .padding((iconSize * 0.10f).coerceAtMost(6.dp)),
        )
    }
}

@Composable
private fun FolderPreview(
    apps: List<WebAppEntity>,
    iconSize: Dp,
    cornerRadiusPercent: Int,
) {
    val gap = 3.dp
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(iconSize * 2 + gap)) {
            apps.take(4).forEachIndexed { index, app ->
                Box(
                    modifier = Modifier
                        .padding(
                            start = (iconSize + gap) * (index % 2),
                            top = (iconSize + gap) * (index / 2),
                        )
                        .clip(RoundedCornerShape(cornerRadiusPercent.coerceIn(0, 50))),
                ) {
                    SiteIcon(app = app, iconSize = iconSize)
                }
            }
        }
    }
}

@Composable
private fun IconFallback(title: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
