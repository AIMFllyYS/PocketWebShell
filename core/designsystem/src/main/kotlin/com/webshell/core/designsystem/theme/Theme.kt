package com.webshell.core.designsystem.theme

import android.graphics.BitmapFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import com.webshell.core.data.HomeSettings
import com.webshell.core.data.SettingsRepository
import com.webshell.core.data.THEME_MODE_DARK
import com.webshell.core.data.THEME_MODE_LIGHT
import com.webshell.core.data.THEME_MODE_PHOTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 照片壁纸路径；仅照片主题模式下非空，供主页背景读取 */
val LocalPhotoWallpaperPath = compositionLocalOf<String?> { null }

@Composable
fun WebShellTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // DataStore 按文件名进程内单例，直接构造读取同一份数据
    val settingsRepository = remember { SettingsRepository(context.applicationContext) }
    val settings by settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = HomeSettings())

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        THEME_MODE_LIGHT -> false
        THEME_MODE_DARK -> true
        else -> systemDark
    }
    val isPhotoMode = settings.themeMode == THEME_MODE_PHOTO &&
        !settings.photoWallpaperPath.isNullOrBlank()
    val wallpaperPath = if (isPhotoMode) settings.photoWallpaperPath else null

    // 照片主题：后台线程降采样解码 + Palette 提取主色，结果缓存于 state
    val photoSeedColor by produceState<Color?>(initialValue = null, wallpaperPath) {
        val path = wallpaperPath ?: return@produceState
        value = withContext(Dispatchers.IO) { extractSeedColor(path) }
    }

    val baseScheme = if (darkTheme) AppleDarkColorScheme else AppleLightColorScheme
    val colorScheme = photoSeedColor?.let { seed ->
        baseScheme.copy(primary = seed)
    } ?: baseScheme

    CompositionLocalProvider(
        LocalPhotoWallpaperPath provides wallpaperPath,
        LocalTransitionStyle provides settings.transitionStyle,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WebShellTypography,
            shapes = WebShellShapes,
            content = content,
        )
    }
}

private fun extractSeedColor(path: String): Color? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // 取色无需全尺寸，限制最长边约 256px
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) {
        sample *= 2
    }
    val bitmap = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    val palette = Palette.from(bitmap).generate()
    val rgb = palette.getVibrantColor(palette.getMutedColor(0xFF007AFF.toInt()))
    return Color(rgb)
}
