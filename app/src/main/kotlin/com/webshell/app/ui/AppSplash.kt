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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.webshell.app.R
import com.webshell.core.designsystem.components.BrandMarkPalette
import com.webshell.core.designsystem.components.drawBrandMark
import com.webshell.core.designsystem.theme.LocalIsDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SplashDurationMs = 1140
private const val FadeOutDurationMs = 90
private const val TextDurationMs = 125
private const val ParticleCount = 32
private const val StarCount = 8
private const val SurgeStart = 0.54f
private const val FlashStart = 0.73f
private const val ShellReadyAt = 0.35f
private const val RingRadiusFrac = 0.145f
private const val FieldRadiusFrac = 0.42f
private const val GalaxyCenterYFrac = 0.60f

private val SplashColorsLight = listOf(
    BrandMarkPalette.core,
    BrandMarkPalette.orbit,
    Color(0xFFF5E6C8),
    BrandMarkPalette.ring,
)

private val SplashColorsDark = listOf(
    BrandMarkPalette.core,
    BrandMarkPalette.orbit,
    BrandMarkPalette.ring,
    Color(0xFFD4C4A0),
)

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
            eccentricity = 0.88f + random.nextFloat() * 0.24f,
            glow = i % 16 == 0,
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

private fun DrawScope.drawSoftGlow(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
    layers: Int = 2,
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
 * Cold-start splash: copy first, then a small accretion field pulls particles into a
 * black-hole core (event horizon + photon ring) and collapses into the brand mark.
 * Light and dark share that drawing path; only the page color and particle tint change.
 */
@Composable
fun AppSplash(
    onFinished: () -> Unit,
    onReadyForShell: () -> Unit = {},
) {
    val isDark = LocalIsDarkTheme.current
    val palette = if (isDark) SplashColorsDark else SplashColorsLight
    val background = if (isDark) Color.Black else Color.White
    val contentColor = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val ringColor = BrandMarkPalette.orbit
    val coreColor = BrandMarkPalette.core
    val starColor = if (isDark) BrandMarkPalette.ring else BrandMarkPalette.orbit
    val particles = remember { buildParticles() }
    val stars = remember { buildStars() }
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val textProgress = remember { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnReady by rememberUpdatedState(onReadyForShell)

    LaunchedEffect(Unit) {
        launch {
            snapshotFlow { progress.value }.first { it >= ShellReadyAt }
            currentOnReady()
        }
        launch {
            textProgress.animateTo(1f, tween(TextDurationMs, easing = FastOutSlowInEasing))
        }
        progress.animateTo(1f, tween(SplashDurationMs, easing = LinearEasing))
        fade.animateTo(0f, tween(FadeOutDurationMs, easing = LinearEasing))
        currentOnFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(background)
            .then(BlockInputModifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawBlackHoleField(
                progress = progress.value,
                isDark = isDark,
                palette = palette,
                ringColor = ringColor,
                coreColor = coreColor,
                starColor = starColor,
                particles = particles,
                stars = stars,
            )
        }
        Column(
            modifier = Modifier.align(BiasAlignment(0f, -0.30f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val textT = textProgress.value
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

private fun DrawScope.drawBlackHoleField(
    progress: Float,
    isDark: Boolean,
    palette: List<Color>,
    ringColor: Color,
    coreColor: Color,
    starColor: Color,
    particles: List<SplashParticle>,
    stars: List<BackdropStar>,
) {
    val fadeIn = (progress / 0.06f).coerceIn(0f, 1f)
    if (fadeIn <= 0f) return
    val center = Offset(size.width * 0.5f, size.height * GalaxyCenterYFrac)
    val minDim = minOf(size.width, size.height)
    val ringRadius = minDim * RingRadiusFrac
    if (ringRadius <= 0f) return
    val startSpread = (minDim * FieldRadiusFrac - ringRadius * 1.15f).coerceAtLeast(1f)
    val twoPi = 2f * PI.toFloat()
    val fieldRotation = progress * 0.45f
    val surgeT = ((progress - SurgeStart) / (FlashStart - SurgeStart)).coerceIn(0f, 1f)
    val flashT = ((progress - FlashStart) / (1f - FlashStart)).coerceIn(0f, 1f)

    drawStars(progress, fadeIn, starColor, stars, twoPi)
    drawSoftGlow(
        center = center,
        radius = ringRadius * (0.85f + 0.55f * surgeT),
        color = coreColor,
        alpha = fadeIn * (0.10f + 0.45f * surgeT) * (1f - flashT),
    )
    if (flashT <= 0f) {
        drawHorizon(center, ringRadius, fadeIn, surgeT, isDark)
        drawAccretionRing(center, ringRadius, fadeIn, progress, surgeT, fieldRotation, ringColor)
    }
    drawInfallingParticles(
        progress = progress,
        fadeIn = fadeIn,
        surgeT = surgeT,
        center = center,
        ringRadius = ringRadius,
        startSpread = startSpread,
        fieldRotation = fieldRotation,
        twoPi = twoPi,
        palette = palette,
        particles = particles,
    )
    if (flashT > 0f) {
        drawImplosion(center, ringRadius, fadeIn, flashT, ringColor)
    }
    val markT = ((progress - 0.70f) / 0.26f).coerceIn(0f, 1f)
    if (markT > 0f) {
        val markEase = FastOutSlowInEasing.transform(markT)
        drawBrandMark(
            center = center,
            radius = ringRadius * (0.72f + 0.28f * markEase),
            alpha = fadeIn * markEase,
        )
    }
}

private fun DrawScope.drawStars(
    progress: Float,
    fadeIn: Float,
    starColor: Color,
    stars: List<BackdropStar>,
    twoPi: Float,
) {
    val starT = ((progress - 0.02f) / 0.16f).coerceIn(0f, 1f)
    if (starT <= 0f) return
    val starBase = 1.dp.toPx()
    for (star in stars) {
        val twinkle = 0.55f + 0.45f * sin(star.phase + progress * twoPi * star.freq)
        drawCircle(
            color = starColor,
            radius = starBase * star.radiusFactor,
            center = Offset(star.u * size.width, star.v * size.height),
            alpha = fadeIn * starT * 0.30f * twinkle,
        )
    }
}

private fun DrawScope.drawHorizon(
    center: Offset,
    ringRadius: Float,
    fadeIn: Float,
    surgeT: Float,
    isDark: Boolean,
) {
    val horizon = ringRadius * 0.42f * (1f + 0.10f * surgeT)
    drawCircle(
        color = Color.Black,
        radius = horizon,
        center = center,
        alpha = fadeIn * if (isDark) (0.25f + 0.65f * surgeT) else (0.18f + 0.50f * surgeT),
    )
    if (surgeT <= 0f) return
    drawCircle(
        color = Color.White,
        radius = horizon * 1.06f,
        center = center,
        alpha = fadeIn * (0.10f + 0.50f * surgeT),
        style = Stroke(0.8.dp.toPx()),
    )
}

private fun DrawScope.drawAccretionRing(
    center: Offset,
    ringRadius: Float,
    fadeIn: Float,
    progress: Float,
    surgeT: Float,
    fieldRotation: Float,
    ringColor: Color,
) {
    val ringVisibility = ((progress - 0.05f) / 0.22f).coerceIn(0f, 1f)
    if (ringVisibility <= 0f) return
    val twoPi = 2f * PI.toFloat()
    val pulse = 1f + 0.03f * sin(progress * twoPi * 2f)
    val ringAlpha = fadeIn * ringVisibility * (0.35f + 0.45f * surgeT)
    val strokeWidth = 1.2.dp.toPx() * (1f + 1.2f * surgeT)
    val ringRadiusPulsed = ringRadius * pulse
    drawCircle(
        color = ringColor,
        radius = ringRadiusPulsed,
        center = center,
        alpha = ringAlpha * 0.25f,
        style = Stroke(strokeWidth * 3.2f),
    )
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

private fun DrawScope.drawInfallingParticles(
    progress: Float,
    fadeIn: Float,
    surgeT: Float,
    center: Offset,
    ringRadius: Float,
    startSpread: Float,
    fieldRotation: Float,
    twoPi: Float,
    palette: List<Color>,
    particles: List<SplashParticle>,
) {
    for (particle in particles) {
        if (progress <= particle.delay) continue
        val u = ((progress - particle.delay) / particle.span).coerceIn(0f, 1f)
        val eased = u * u * u
        val startR = ringRadius * 1.15f + particle.startRadius * startSpread
        val r = startR * (1f - eased)
        val theta = particle.startAngle + particle.twist * eased + fieldRotation
        val drift = (1f - eased) * 5.dp.toPx() *
            sin(particle.driftPhase + progress * twoPi * particle.driftFreq)
        val x = center.x + r * particle.eccentricity * cos(theta) - drift * sin(theta)
        val y = center.y + r * sin(theta) + drift * cos(theta)
        val vanish = (r / (ringRadius * 0.9f)).coerceIn(0f, 1f)
        if (vanish <= 0f) continue
        val doppler = 0.80f + 0.40f * (0.5f + 0.5f * sin(theta - fieldRotation))
        val alpha = fadeIn * (0.35f + 0.65f * eased) * vanish * vanish * doppler
        if (alpha <= 0.01f) continue
        val dotRadius = particle.size.dp.toPx() * (0.75f + 0.45f * eased) *
            (0.35f + 0.65f * vanish)
        val color = palette[particle.colorIndex]
        if (particle.glow) {
            drawSoftGlow(Offset(x, y), dotRadius * 3f, color, alpha * 0.35f)
        }
        if (surgeT > 0f && u > 0f && r > 0.5f) {
            val streakLen = (dotRadius * (2f + 5f * surgeT)).coerceAtMost(r)
            val ux = (x - center.x) / r
            val uy = (y - center.y) / r
            drawLine(
                color = color,
                start = Offset(x + ux * streakLen, y + uy * streakLen),
                end = Offset(x, y),
                strokeWidth = dotRadius * 1.1f,
                cap = StrokeCap.Round,
                alpha = alpha * 0.8f,
            )
        }
        drawCircle(color = color, radius = dotRadius, center = Offset(x, y), alpha = alpha)
    }
}

private fun DrawScope.drawImplosion(
    center: Offset,
    ringRadius: Float,
    fadeIn: Float,
    flashT: Float,
    ringColor: Color,
) {
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
            color = BrandMarkPalette.core,
            alpha = fadeIn * sin(pointT * PI.toFloat()) * 0.9f,
        )
    }
}
