package com.webshell.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.min

/** 与启动器图标同源的玄览星核：太极环、鎏金核、倾斜星轨与伴星。 */
object BrandMarkPalette {
    val ring = Color(0xFFF2EEE3)
    val core = Color(0xFFE9D9AE)
    val orbit = Color(0xFFC9A86A)
    val skyStart = Color(0xFF27324A)
    val skyEnd = Color(0xFF0F131B)
}

data class BrandMarkColors(
    val ring: Color,
    val core: Color,
    val orbit: Color,
)

/** Untinted gold is the launcher/splash contract; a tint recolors ring, core and orbit only. */
fun brandMarkColors(tint: Color? = null): BrandMarkColors =
    if (tint == null) {
        BrandMarkColors(BrandMarkPalette.ring, BrandMarkPalette.core, BrandMarkPalette.orbit)
    } else {
        BrandMarkColors(
            ring = lerp(tint, Color.White, 0.42f),
            core = tint,
            orbit = lerp(tint, Color.Black, 0.22f),
        )
    }

/** Sky uses the compact launcher scale; empty states drop the sky and grow the mark. */
fun brandMarkRadius(side: Float, showSky: Boolean): Float =
    side * if (showSky) 0.22f else 0.32f

/**
 * @param radius 太极环半径；其余几何按启动器矢量比例派生。
 */
fun DrawScope.drawBrandMark(
    center: Offset,
    radius: Float,
    alpha: Float,
    rotationDegrees: Float = -28f,
    tint: Color? = null,
) {
    if (radius <= 0f || alpha <= 0f) return
    val colors = brandMarkColors(tint)
    val ringStroke = (radius * 5f / 30f).coerceAtLeast(1f)
    val orbitStroke = (radius * 3f / 30f).coerceAtLeast(1f)
    val coreRadius = radius * 7f / 30f
    val orbitRx = radius * 44f / 30f
    val orbitRy = radius * 15f / 30f
    val companionRadius = radius * 4.5f / 30f
    drawCircle(
        color = colors.ring,
        radius = radius,
        center = center,
        alpha = alpha,
        style = Stroke(width = ringStroke, cap = StrokeCap.Round),
    )
    drawCircle(
        color = colors.core,
        radius = coreRadius,
        center = center,
        alpha = alpha,
    )
    rotate(rotationDegrees, center) {
        drawOval(
            color = colors.orbit,
            topLeft = Offset(center.x - orbitRx, center.y - orbitRy),
            size = Size(orbitRx * 2f, orbitRy * 2f),
            alpha = alpha,
            style = Stroke(width = orbitStroke, cap = StrokeCap.Round),
        )
        drawCircle(
            color = colors.core,
            radius = companionRadius,
            center = Offset(center.x + orbitRx, center.y),
            alpha = alpha,
        )
    }
}

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    showSky: Boolean = true,
    markAlpha: Float = 1f,
    tint: Color? = null,
) {
    Canvas(modifier.aspectRatio(1f)) {
        val side = min(size.width, size.height)
        if (side <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        if (showSky) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to BrandMarkPalette.skyStart,
                    1f to BrandMarkPalette.skyEnd,
                    center = Offset(center.x, center.y - side * 0.08f),
                    radius = side * 0.72f,
                ),
                radius = side / 2f,
                center = center,
            )
        }
        drawBrandMark(
            center = center,
            radius = brandMarkRadius(side, showSky),
            alpha = markAlpha,
            tint = tint,
        )
    }
}
