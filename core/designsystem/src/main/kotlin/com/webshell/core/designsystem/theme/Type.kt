package com.webshell.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
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
)

/** 全端统一字阶：标题 Semibold、功能文本 Medium、正文 Regular；行高 ≥1.3×字号。 */
val WebShellTypography = Typography(
    displayLarge = miSans(40, FontWeight.SemiBold, 52),
    displayMedium = miSans(32, FontWeight.SemiBold, 42),
    displaySmall = miSans(28, FontWeight.SemiBold, 36),
    headlineLarge = miSans(28, FontWeight.SemiBold, 36),
    headlineMedium = miSans(24, FontWeight.SemiBold, 32),
    headlineSmall = miSans(20, FontWeight.SemiBold, 28),
    titleLarge = miSans(18, FontWeight.SemiBold, 26),
    titleMedium = miSans(16, FontWeight.Medium, 24),
    titleSmall = miSans(14, FontWeight.Medium, 20),
    bodyLarge = miSans(16, FontWeight.Normal, 26),
    bodyMedium = miSans(14, FontWeight.Normal, 22),
    bodySmall = miSans(12, FontWeight.Normal, 18),
    labelLarge = miSans(14, FontWeight.Medium, 20),
    labelMedium = miSans(12, FontWeight.Medium, 16),
    labelSmall = miSans(11, FontWeight.Medium, 16),
)
