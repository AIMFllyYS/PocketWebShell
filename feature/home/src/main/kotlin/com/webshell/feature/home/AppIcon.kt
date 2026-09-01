package com.webshell.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.webshell.core.data.WebAppEntity
import java.io.File

/**
 * 主页图标：iOS 主屏规范 —— 图标本体直接落在壁纸上，无底座卡片容器。
 * 圆角由图标本体（白底/首字母色块/文件夹容器）自行裁剪；
 * 收藏应用以 2dp 边框标识。远端图标内容尺寸不参与网格测量。
 *
 * [shadowElevation] > 0 时用图标本体的大圆角 shape 投影（拖拽浮动层用），
 * 阴影跟随圆角而非矩形 —— 不要在外层用 graphicsLayer{ clip=true } 投影，
 * 那会把圆角阴影裁成方块。
 */
@Composable
fun AppIcon(
    app: WebAppEntity,
    size: Dp,
    cornerRadiusPercent: Int = 26,
    modifier: Modifier = Modifier,
    folderPreview: List<WebAppEntity> = emptyList(),
    shadowElevation: Dp = 0.dp,
) {
    val shape = RoundedCornerShape(cornerRadiusPercent.coerceIn(0, 50))
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (shadowElevation > 0.dp) {
                    Modifier.graphicsLayer {
                        this.shadowElevation = shadowElevation.toPx()
                        this.shape = shape
                        this.clip = false // 阴影需超出 bounds 绘制，仅按 shape 投影
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (app.isFavorite) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
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
                shape = shape,
                cornerRadiusPercent = cornerRadiusPercent,
            )
        } else {
            SiteIcon(app = app, iconSize = size, shape = shape)
        }
    }
}

/** 网站图标：本地上传图直接铺满裁圆角；远端 logo 等比放大贴满圆角边界；加载失败/无图标走首字母兜底。 */
@Composable
private fun SiteIcon(app: WebAppEntity, iconSize: Dp, shape: Shape) {
    val iconUrl = app.iconUrl
    val isRemote = !iconUrl.isNullOrBlank() &&
        (iconUrl.startsWith("http://") || iconUrl.startsWith("https://"))
    val isLocalFile = !iconUrl.isNullOrBlank() && iconUrl.startsWith("/") &&
        File(iconUrl).exists()
    when {
        isLocalFile -> {
            // 用户上传图：本身就是完整图标，铺满裁圆角，不加白底。
            AsyncImage(
                model = File(iconUrl),
                contentDescription = app.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(iconSize)
                    .clip(shape),
            )
        }
        isRemote -> {
            // 官方 logo 通常不是正方形：白底衬底（兼容透明 PNG），ContentScale.Crop 等比放大
            // 到完全覆盖圆角方块（四条边贴到圆角边缘），不内缩加边距。
            // 关键兜底：logo 加载失败（404/网络拦截/格式不支持）时回退首字母色块，
            // 否则只剩白底空块（"白底无字" bug 根因）。
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .background(Color.White, shape)
                    .clip(shape),
                contentAlignment = Alignment.Center,
            ) {
                SubcomposeAsyncImage(
                    model = iconUrl,
                    contentDescription = app.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = { IconFallback(title = app.title, size = iconSize, shape = shape) },
                )
            }
        }
        else -> {
            IconFallback(title = app.title, size = iconSize, shape = shape)
        }
    }
}

/** 文件夹：实底容器（浅色纯白/深色卡片色）+ 发丝描边 + 2×2 成员预览，不引入第二处实时模糊。 */
@Composable
private fun FolderPreview(
    apps: List<WebAppEntity>,
    iconSize: Dp,
    shape: Shape,
    cornerRadiusPercent: Int,
) {
    val gap = 3.dp
    val memberShape = RoundedCornerShape(cornerRadiusPercent.coerceIn(0, 50))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(iconSize * 2 + gap)) {
            apps.take(4).forEachIndexed { index, app ->
                Box(
                    modifier = Modifier
                        .padding(
                            start = (iconSize + gap) * (index % 2),
                            top = (iconSize + gap) * (index / 2),
                        ),
                ) {
                    SiteIcon(app = app, iconSize = iconSize, shape = memberShape)
                }
            }
        }
    }
}

/**
 * 首字母兜底：按标题 hash 从一组高对比配色中取色块，随主题取色 ——
 * 浅色主题用 pastel 底 + 深色字，深色主题用深饱和底 + 近白字，
 * 保证浅色/深色主题下首字母都清晰可读（≥4.5:1，区别于 primaryContainer 低对比问题）。
 */
@Composable
private fun IconFallback(title: String, size: Dp, shape: Shape) {
    val darkTheme = isSystemInDarkTheme()
    val palette = remember(title, darkTheme) { fallbackColorsFor(title, darkTheme) }
    Box(
        modifier = Modifier
            .size(size)
            .background(palette.container, shape)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.titleLarge,
            color = palette.content,
        )
    }
}

private data class FallbackColors(val container: Color, val content: Color)

/** 浅色主题：pastel 底色 + 近黑文字，确保 ≥7:1 对比度。 */
private val fallbackPaletteLight = listOf(
    FallbackColors(Color(0xFFDCE9FF), Color(0xFF0B3D91)), // 蓝
    FallbackColors(Color(0xFFDDF3E4), Color(0xFF0B5D3B)), // 绿
    FallbackColors(Color(0xFFFCE3EC), Color(0xFF8A1B4E)), // 品红
    FallbackColors(Color(0xFFFFF0D6), Color(0xFF7A4E00)), // 橙
    FallbackColors(Color(0xFFE9E2FB), Color(0xFF4A2C93)), // 紫
    FallbackColors(Color(0xFFDDF1F4), Color(0xFF0B5563)), // 青
)

/** 深色主题：深饱和底色 + 近白文字，确保 ≥4.5:1 对比度（与浅色 palette 同序同 hue）。 */
private val fallbackPaletteDark = listOf(
    FallbackColors(Color(0xFF1D3A6E), Color(0xFFD6E4FF)), // 蓝
    FallbackColors(Color(0xFF14532D), Color(0xFFD9F2E3)), // 绿
    FallbackColors(Color(0xFF6B1B41), Color(0xFFFBDCE8)), // 品红
    FallbackColors(Color(0xFF7A4A00), Color(0xFFFFE9C2)), // 橙
    FallbackColors(Color(0xFF3B2A73), Color(0xFFE6DFFC)), // 紫
    FallbackColors(Color(0xFF0E4A55), Color(0xFFD3EEF3)), // 青
)

private fun fallbackColorsFor(title: String, darkTheme: Boolean): FallbackColors {
    val palette = if (darkTheme) fallbackPaletteDark else fallbackPaletteLight
    val key = title.trim().ifEmpty { "?" }
    val index = (key.first().code % palette.size + palette.size) % palette.size
    return palette[index]
}
