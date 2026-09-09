package com.webshell.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.webshell.app.R
import com.webshell.core.designsystem.theme.LocalIsDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val SplashDurationMs = 2400
private const val FadeOutDurationMs = 220
private const val ParticleCount = 110
private const val StarCount = 36
private const val SurgeStart = 0.62f
private const val FlashStart = 0.80f
// 文字开场即到：先见文案，再见星河收束。
private const val TextStart = 0.02f
private const val TextEnd = 0.18f
// 小星河：整体只占短边一小块，文案让出上半屏。
private const val RingRadiusFrac = 0.145f
private const val FieldRadiusFrac = 0.42f
private const val GalaxyCenterYFrac = 0.60f

private val SplashColorsLight = listOf(
    Color(0xFFE8B339),
    Color(0xFFF5D67B),
    Color(0xFFD49A1E),
    Color(0xFFFFE9A8),
)

private val SplashColorsDark = listOf(
    Color(0xFF7AA2FF),
    Color(0xFFB48CFF),
    Color(0xFFFFFFFF),
    Color(0xFF9EC1FF),
)

private val RingColorLight = Color(0xFFE8B339)
private val CoreColorLight = Color(0xFFF2C14E)
private val RingColorDark = Color(0xFF9A8CFF)
private val CoreColorDark = Color(0xFF8FA8FF)
// 远景星尘：浅色主题淡金、深色主题冷白，只做氛围不做主体。
private val StarColorLight = Color(0xFFC9A24B)
private val StarColorDark = Color(0xFFBFD0FF)

private class SplashParticle(
    val startRadius: Float,
    val startAngle: Float,
    val twist: Float,
    val delay: Float,
    val span: Float,
    val size: Float,
    val colorIndex: Int,
    val driftPhase: Float,
    val driftFreq: Float,
    val eccentricity: Float,
    val glow: Boolean,
)

private class BackdropStar(
    val u: Float,
    val v: Float,
    val radiusFactor: Float,
    val phase: Float,
    val freq: Float,
)

private fun buildParticles(): List<SplashParticle> {
    val random = Random(20240817)
    return List(ParticleCount) { i ->
        SplashParticle(
            startRadius = random.nextFloat(),
            startAngle = random.nextFloat() * (2f * PI.toFloat()),
            twist = (1.4f + random.nextFloat() * 1.8f) * (2f * PI.toFloat()),
            delay = random.nextFloat() * 0.18f,
            span = 0.50f + random.nextFloat() * 0.12f,
            size = 1.8f + random.nextFloat() * 2.6f,
            colorIndex = random.nextInt(SplashColorsLight.size),
            driftPhase = random.nextFloat() * (2f * PI.toFloat()),
            driftFreq = 0.6f + random.nextFloat() * 1.2f,
            // 轻微椭圆轨道：绕行不是正圆，更像真实吸积流
            eccentricity = 0.88f + random.nextFloat() * 0.24f,
            glow = i % 9 == 0,
        )
    }
}

private fun buildStars(): List<BackdropStar> {
    val random = Random(970502)
    return List(StarCount) {
        BackdropStar(
            u = random.nextFloat(),
            v = random.nextFloat(),
            radiusFactor = 0.5f + random.nextFloat(),
            phase = random.nextFloat() * (2f * PI.toFloat()),
            freq = 0.5f + random.nextFloat() * 1.4f,
        )
    }
}

/** 4 层径向衰减近似辉光；层数压到 4 兼顾性能（相比 6 层省 1/3 叠加绘制）。 */
private fun DrawScope.drawSoftGlow(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
    layers: Int = 4,
) {
    if (radius <= 0f || alpha <= 0f) return
    for (i in layers downTo 1) {
        val f = i.toFloat() / layers
        val a = (alpha * (1f - f) * (1f - f)).coerceIn(0f, 1f)
        if (a > 0.003f) {
            drawCircle(color = color, radius = radius * f, center = center, alpha = a)
        }
    }
}

private val BlockInputModifier = Modifier.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown().consume()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * 冷启动开屏：文案先行（淡入 + 字距收紧），下方一弯"小星河"把局部粒子缓缓吸入核心
 * （亮色=白洞金辉，暗色=黑洞吸积环 + 事件视界 + 光子环），寓意"把互联网收进桌面"。
 * 单 Canvas、无实时模糊，远景星尘只用小圆点，单次播放后淡出移除。
 */
@Composable
fun AppSplash(onFinished: () -> Unit) {
    val isDark = LocalIsDarkTheme.current
    val palette = if (isDark) SplashColorsDark else SplashColorsLight
    val background = if (isDark) Color.Black else Color.White
    val contentColor = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val ringColor = if (isDark) RingColorDark else RingColorLight
    val coreColor = if (isDark) CoreColorDark else CoreColorLight
    val starColor = if (isDark) StarColorDark else StarColorLight
    val density = LocalDensity.current
    val glowRadius = with(density) { 22.dp.toPx() }
    val glowBrushes = remember(palette, glowRadius) {
        palette.map { color ->
            Brush.radialGradient(
                0f to color.copy(alpha = 0.55f),
                1f to color.copy(alpha = 0f),
                center = Offset.Zero,
                radius = glowRadius,
                tileMode = TileMode.Clamp,
            )
        }
    }
    val particles = remember { buildParticles() }
    val stars = remember { buildStars() }
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var playing by remember { mutableStateOf(true) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(SplashDurationMs, easing = LinearEasing))
        playing = false
        fade.animateTo(0f, tween(FadeOutDurationMs, easing = LinearEasing))
        currentOnFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(background)
            .then(if (playing) BlockInputModifier else Modifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            val fadeIn = (p / 0.06f).coerceIn(0f, 1f)
            if (fadeIn <= 0f) return@Canvas
            val width = size.width
            val height = size.height
            val center = Offset(width * 0.5f, height * GalaxyCenterYFrac)
            val minDim = minOf(width, height)
            val ringRadius = minDim * RingRadiusFrac
            if (ringRadius <= 0f) return@Canvas
            // 粒子只在星河周围的局部椭圆域内散布，不再铺满全屏对角线。
            val startSpread = (minDim * FieldRadiusFrac - ringRadius * 1.15f).coerceAtLeast(1f)
            val twoPi = 2f * PI.toFloat()
            val fieldRotation = p * 0.45f
            val surgeT = ((p - SurgeStart) / (FlashStart - SurgeStart)).coerceIn(0f, 1f)
            val flashT = ((p - FlashStart) / (1f - FlashStart)).coerceIn(0f, 1f)

            // 远景星尘：开场随 fadeIn 浮现并微微闪烁，衬出"小星河"纵深。
            val starT = ((p - 0.04f) / 0.24f).coerceIn(0f, 1f)
            if (starT > 0f) {
                val starBase = 1.dp.toPx()
                for (star in stars) {
                    val twinkle = 0.55f + 0.45f * sin(star.phase + p * twoPi * star.freq)
                    drawCircle(
                        color = starColor,
                        radius = starBase * star.radiusFactor,
                        center = Offset(star.u * width, star.v * height),
                        alpha = fadeIn * starT * 0.30f * twinkle,
                    )
                }
            }

            // 核心辉光：surge 阶段增强，闪光阶段收敛
            drawSoftGlow(
                center = center,
                radius = ringRadius * (0.85f + 0.55f * surgeT),
                color = coreColor,
                alpha = fadeIn * (0.10f + 0.45f * surgeT) * (1f - flashT),
            )
            if (isDark && flashT <= 0f) {
                // 事件视界：纯黑圆盘随 surge 微膨胀，外缘紧贴一圈细光子环。
                val horizon = ringRadius * 0.42f * (1f + 0.10f * surgeT)
                drawCircle(
                    color = Color.Black,
                    radius = horizon,
                    center = center,
                    alpha = fadeIn * (0.25f + 0.65f * surgeT),
                )
                if (surgeT > 0f) {
                    drawCircle(
                        color = Color.White,
                        radius = horizon * 1.06f,
                        center = center,
                        alpha = fadeIn * (0.10f + 0.50f * surgeT),
                        style = Stroke(0.8.dp.toPx()),
                    )
                }
            }
            // 吸积环：淡入后随 surge 增亮；亮暗两半弧模拟多普勒集束（接近侧更亮）。
            if (flashT <= 0f) {
                val ringVisibility = ((p - 0.08f) / 0.30f).coerceIn(0f, 1f)
                if (ringVisibility > 0f) {
                    val pulse = 1f + 0.03f * sin(p * twoPi * 2f)
                    val ringAlpha = fadeIn * ringVisibility * (0.35f + 0.45f * surgeT)
                    val strokeWidth = 1.2.dp.toPx() * (1f + 1.2f * surgeT)
                    val ringRadiusPulsed = ringRadius * pulse
                    // 外侧宽淡晕环
                    drawCircle(
                        color = ringColor,
                        radius = ringRadiusPulsed,
                        center = center,
                        alpha = ringAlpha * 0.25f,
                        style = Stroke(strokeWidth * 3.2f),
                    )
                    // 多普勒不对称：与场旋转同向的半弧亮、背向半弧暗
                    val arcStart = Math.toDegrees(fieldRotation.toDouble()).toFloat()
                    val arcSize = Size(ringRadiusPulsed * 2f, ringRadiusPulsed * 2f)
                    val arcTopLeft = Offset(center.x - ringRadiusPulsed, center.y - ringRadiusPulsed)
                    drawArc(
                        color = ringColor,
                        startAngle = arcStart,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        alpha = ringAlpha,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = arcStart + 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        alpha = ringAlpha * 0.38f,
                        style = Stroke(strokeWidth * 0.8f, cap = StrokeCap.Round),
                    )
                }
            }

            for (particle in particles) {
                val u = ((p - particle.delay) / particle.span).coerceIn(0f, 1f)
                val eased = u * u * u
                val startR = ringRadius * 1.15f + particle.startRadius * startSpread
                val r = startR * (1f - eased)
                val theta = particle.startAngle + particle.twist * eased + fieldRotation
                val drift = (1f - eased) * 5.dp.toPx() *
                    sin(particle.driftPhase + p * twoPi * particle.driftFreq)
                val x = center.x + r * particle.eccentricity * cos(theta) - drift * sin(theta)
                val y = center.y + r * sin(theta) + drift * cos(theta)
                // 越过吸积环的粒子缩小、变暗直至消失
                val vanish = (r / (ringRadius * 0.9f)).coerceIn(0f, 1f)
                if (vanish <= 0f) continue
                // 多普勒集束：顺行侧（朝观察者）略亮，逆行侧略暗
                val doppler = 0.80f + 0.40f * (0.5f + 0.5f * sin(theta - fieldRotation))
                val alpha = fadeIn * (0.35f + 0.65f * eased) * vanish * vanish * doppler
                if (alpha <= 0.01f) continue
                val dotRadius = particle.size.dp.toPx() * (0.75f + 0.45f * eased) *
                    (0.35f + 0.65f * vanish)
                if (particle.glow) {
                    translate(x, y) {
                        drawCircle(
                            brush = glowBrushes[particle.colorIndex],
                            radius = glowRadius,
                            center = Offset.Zero,
                            alpha = alpha * 0.45f,
                        )
                    }
                }
                // surge 阶段粒子拖出径向短迹线，模拟加速坠入
                if (surgeT > 0f && u > 0f && r > 0.5f) {
                    val streakLen = (dotRadius * (2f + 5f * surgeT)).coerceAtMost(r)
                    val ux = (x - center.x) / r
                    val uy = (y - center.y) / r
                    drawLine(
                        color = palette[particle.colorIndex],
                        start = Offset(x + ux * streakLen, y + uy * streakLen),
                        end = Offset(x, y),
                        strokeWidth = dotRadius * 1.1f,
                        cap = StrokeCap.Round,
                        alpha = alpha * 0.8f,
                    )
                }
                drawCircle(
                    color = palette[particle.colorIndex],
                    radius = dotRadius,
                    center = Offset(x, y),
                    alpha = alpha,
                )
            }

            if (flashT > 0f) {
                if (isDark) {
                    // 光子环骤亮后内爆坍缩成一点
                    val flareT = (flashT / 0.35f).coerceIn(0f, 1f)
                    val dimT = ((flashT - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    val implRadius = ringRadius * (1f - flashT * flashT)
                    val flashAlpha = fadeIn * (0.55f + 0.45f * flareT) * (1f - dimT)
                    if (implRadius > 0.5f && flashAlpha > 0f) {
                        val w = 1.4.dp.toPx() * (1f + 2f * flareT) * (1f - 0.6f * dimT)
                        drawCircle(
                            color = ringColor,
                            radius = implRadius,
                            center = center,
                            alpha = flashAlpha * 0.35f,
                            style = Stroke(w * 3f),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = implRadius,
                            center = center,
                            alpha = flashAlpha,
                            style = Stroke(w),
                        )
                    }
                    val pointT = ((flashT - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    if (pointT > 0f) {
                        drawSoftGlow(
                            center = center,
                            radius = ringRadius * 0.35f * (1f - 0.7f * pointT),
                            color = Color.White,
                            alpha = fadeIn * sin(pointT * PI.toFloat()) * 0.9f,
                        )
                    }
                } else {
                    // 金色闪环在白底上向外绽放：双环 + 余晖，收束成一点暖芯
                    val easeOut = 1f - (1f - flashT) * (1f - flashT)
                    val bloomRadius = ringRadius * (1f + 2.6f * easeOut)
                    val bloomAlpha = fadeIn * (1f - flashT)
                    if (bloomAlpha > 0f) {
                        val w = 1.6.dp.toPx() * (1f + 4f * (1f - flashT))
                        drawCircle(
                            color = coreColor,
                            radius = bloomRadius,
                            center = center,
                            alpha = bloomAlpha * 0.5f,
                            style = Stroke(w),
                        )
                        drawCircle(
                            color = ringColor,
                            radius = bloomRadius * 0.92f,
                            center = center,
                            alpha = bloomAlpha * 0.9f,
                            style = Stroke(w * 0.45f),
                        )
                    }
                    val fillT = (flashT / 0.30f).coerceIn(0f, 1f)
                    if (fillT < 1f) {
                        drawSoftGlow(
                            center = center,
                            radius = ringRadius * (1.2f + 1.5f * fillT),
                            color = coreColor,
                            alpha = fadeIn * 0.55f * (1f - fillT),
                        )
                    }
                }
            }
        }
        // 文案先于星河收束登场：淡入 + 字距收紧 + 轻微上浮，位于上半屏。
        Column(
            modifier = Modifier.align(BiasAlignment(0f, -0.30f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val textT = FastOutSlowInEasing.transform(
                ((progress.value - TextStart) / (TextEnd - TextStart)).coerceIn(0f, 1f),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.62f * textT),
                letterSpacing = lerp(10f, 4f, textT).sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor.copy(alpha = textT),
                letterSpacing = lerp(7f, 1.5f, textT).sp,
                modifier = Modifier.offset {
                    IntOffset(0, ((1f - textT) * 16.dp.toPx()).roundToInt())
                },
            )
        }
    }
}
