package com.webshell.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// GPT 版本中更成熟的蓝灰色体系，作为不支持动态取色设备的稳定回退主题。
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBBC3FF),
    secondary = Color(0xFFC4C5DD),
    tertiary = Color(0xFFE6BAD9),
    background = Color(0xFF11131B),
    surface = Color(0xFF11131B),
    surfaceContainer = Color(0xFF1D1F29),
)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4865E8),
    secondary = Color(0xFF5B5F72),
    tertiary = Color(0xFF77536F),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFF9F9FF),
    surfaceContainer = Color(0xFFEEF0FA),
)

@Composable
fun WebShellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
