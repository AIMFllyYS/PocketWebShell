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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.webshell.app.R
import com.webshell.core.designsystem.components.BrandMarkPalette
import com.webshell.core.designsystem.components.drawBrandMark
import com.webshell.core.designsystem.theme.LocalIsDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copy and particles start together. Copy stays for the whole hole.
 * One overlay fade hides copy, hole, mark and mask, then the shell is revealed.
 */
private const val CopyInMs = 140
private const val FieldMs = 860
private const val CollapseMs = 280
private const val HoldMs = 100L
private const val OverlayMs = 200
private const val ParticleCount = 8
private const val FieldY = 0.58f
private val TwoPi = (2.0 * PI).toFloat()
private val RadToDeg = (180.0 / PI).toFloat()
private val Gold = listOf(BrandMarkPalette.core, BrandMarkPalette.orbit, BrandMarkPalette.ring)

private class Infall(
    val start: Float,
    val angle: Float,
    val twist: Float,
    val delay: Float,
    val span: Float,
    val size: Float,
    val color: Int,
    val ecc: Float,
)

private fun infalls(): List<Infall> {
    val rng = Random(7)
    return List(ParticleCount) {
        Infall(
            start = rng.nextFloat(),
            angle = rng.nextFloat() * TwoPi,
            twist = (1.2f + rng.nextFloat() * 1.4f) * TwoPi,
            delay = rng.nextFloat() * 0.18f,
            span = 0.55f + rng.nextFloat() * 0.28f,
            size = 1.6f + rng.nextFloat() * 2.0f,
            color = rng.nextInt(Gold.size),
            ecc = 0.90f + rng.nextFloat() * 0.18f,
        )
    }
}

private val EatTouches = Modifier.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown().consume()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
fun AppSplash(
    onFinished: () -> Unit,
    onReadyForShell: () -> Unit = {},
) {
    val dark = LocalIsDarkTheme.current
    val background = if (dark) Color.Black else Color.White
    val ink = if (dark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val muted = if (dark) Color(0xFFAEAEB2) else Color(0xFF55555B)
    val particles = remember { infalls() }
    val copyAlpha = remember { Animatable(0f) }
    val fieldPlay = remember { Animatable(0f) }
    val collapse = remember { Animatable(0f) }
    val overlay = remember { Animatable(1f) }
    val onDone by rememberUpdatedState(onFinished)
    val onReady by rememberUpdatedState(onReadyForShell)

    LaunchedEffect(Unit) {
        launch { copyAlpha.animateTo(1f, tween(CopyInMs, easing = LinearEasing)) }
        fieldPlay.animateTo(1f, tween(FieldMs, easing = LinearEasing))
        collapse.animateTo(1f, tween(CollapseMs, easing = FastOutSlowInEasing))
        delay(HoldMs)
        onReady()
        repeat(2) { withFrameNanos { } }
        overlay.animateTo(0f, tween(OverlayMs, easing = LinearEasing))
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlay.value }
            .background(background)
            .then(EatTouches),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawField(fieldPlay.value, collapse.value, dark, particles)
        }
        Column(
            Modifier
                .align(BiasAlignment(0f, -0.42f))
                .graphicsLayer { alpha = copyAlpha.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun DrawScope.drawField(
    play: Float,
    collapse: Float,
    dark: Boolean,
    particles: List<Infall>,
) {
    val center = Offset(size.width * 0.5f, size.height * FieldY)
    val minDim = minOf(size.width, size.height)
    val ring = minDim * 0.13f
    if (ring <= 0f) return
    val px = 1.dp.toPx()
    val hole = ((play - 0.16f) / 0.22f).coerceIn(0f, 1f)
    val keep = (1f - collapse) * hole
    val mark = collapse
    if (keep > 0f) {
        val shrink = 1f - collapse * collapse
        drawCircle(
            color = Color.Black,
            radius = ring * 0.40f * shrink,
            center = center,
            alpha = keep * if (dark) 0.55f else 0.28f,
        )
        val r = ring * shrink
        val stroke = 1.15f * px
        drawCircle(
            color = BrandMarkPalette.orbit,
            radius = r,
            center = center,
            alpha = keep * 0.35f,
            style = Stroke(stroke),
        )
        drawArc(
            color = BrandMarkPalette.orbit,
            startAngle = play * 2.2f * RadToDeg,
            sweepAngle = 168f,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2f, r * 2f),
            alpha = keep * 0.85f,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
    val spread = (minDim * 0.36f - ring).coerceAtLeast(1f)
    val spin = play * 0.35f
    val particleKeep = 1f - collapse
    for (p in particles) {
        if (play <= p.delay) continue
        val local = ((play - p.delay) / p.span).coerceIn(0f, 1f)
        val eased = local * local * local
        val rad = (ring * 1.2f + p.start * spread) * (1f - eased) * particleKeep
        if (rad <= ring * 0.18f) continue
        val theta = p.angle + p.twist * eased + spin
        val vanish = (rad / (ring * 0.9f)).coerceIn(0f, 1f)
        val a = (0.40f + 0.55f * eased) * vanish * vanish * particleKeep
        if (a <= 0.02f) continue
        drawCircle(
            color = Gold[p.color],
            radius = p.size * px * (0.75f + 0.35f * eased),
            center = Offset(center.x + rad * p.ecc * cos(theta), center.y + rad * sin(theta)),
            alpha = a,
        )
    }
    if (mark > 0f) {
        drawBrandMark(
            center = center,
            radius = ring * (0.72f + 0.28f * mark),
            alpha = mark,
        )
    }
}
