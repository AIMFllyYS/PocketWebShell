package com.webshell.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.webshell.core.designsystem.R

/**
 * 内置 MiSans（小米开源，免费商用），子集覆盖完整 CJK 基本区。
 * 目的：渲染一致，不被系统主题字体替换破坏字度量（真机文字截半事故）。
 */
val MiSansFamily = FontFamily(
    Font(R.font.misans_regular, FontWeight.Normal),
    Font(R.font.misans_medium, FontWeight.Medium),
    Font(R.font.misans_semibold, FontWeight.SemiBold),
    Font(R.font.misans_semibold, FontWeight.Bold),
)

private fun miSans(size: Int, weight: FontWeight, lineHeight: Int, spacing: Float = 0f) = TextStyle(
    fontFamily = MiSansFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = spacing.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** iOS-oriented large title / title / body / footnote hierarchy, using the licensed MiSans family. */
val WebShellTypography = Typography(
    displayLarge = miSans(40, FontWeight.SemiBold, 52),
    displayMedium = miSans(34, FontWeight.SemiBold, 45, -0.6f),
    displaySmall = miSans(28, FontWeight.SemiBold, 37, -0.35f),
    headlineLarge = miSans(34, FontWeight.SemiBold, 45, -0.6f),
    headlineMedium = miSans(28, FontWeight.SemiBold, 37, -0.35f),
    headlineSmall = miSans(22, FontWeight.SemiBold, 29, -0.2f),
    titleLarge = miSans(17, FontWeight.SemiBold, 23, -0.15f),
    titleMedium = miSans(16, FontWeight.Medium, 24),
    titleSmall = miSans(15, FontWeight.Medium, 21),
    bodyLarge = miSans(17, FontWeight.Normal, 23, -0.15f),
    bodyMedium = miSans(15, FontWeight.Normal, 21),
    bodySmall = miSans(13, FontWeight.Normal, 18),
    labelLarge = miSans(15, FontWeight.Medium, 21),
    labelMedium = miSans(12, FontWeight.Medium, 16),
    labelSmall = miSans(11, FontWeight.Medium, 16),
)
