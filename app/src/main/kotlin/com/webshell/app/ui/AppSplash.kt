package com.webshell.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webshell.app.R
import com.webshell.core.designsystem.components.BrandMark
import com.webshell.core.designsystem.theme.LocalIsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MarkInMs = 260
private const val TextDelayMs = 60L
private const val TextInMs = 220
private const val HoldMs = 120L
private const val ExitMs = 240

private val EatTouches = Modifier.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown().consume()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * 原生级丝滑开屏：
 * 1. 纯粹以项目灵魂标识「玄览星核 BrandMark」为中心；
 * 2. 动效采用轻盈弹簧微入与优雅绽放淡出，全由 GPU graphicsLayer 驱动；
 * 3. 彻底告别冷启动逐帧主线程粒子运算，与底层并行预热结合达成 10/10 满帧无卡顿。
 */
@Composable
fun AppSplash(
    onFinished: () -> Unit,
    onReadyForShell: () -> Unit = {},
) {
    val dark = LocalIsDarkTheme.current
    val background = if (dark) Color.Black else Color.White
    val ink = if (dark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val muted = if (dark) Color(0xFFAEAEB2) else Color(0xFF55555B)

    val markScale = remember { Animatable(0.90f) }
    val markAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(12f) }

    val exitScale = remember { Animatable(1.0f) }
    val exitAlpha = remember { Animatable(1.0f) }

    val onDone by rememberUpdatedState(onFinished)
    val onReady by rememberUpdatedState(onReadyForShell)

    LaunchedEffect(Unit) {
        // Phase 1: 品牌星核与文案微弹入
        launch {
            markAlpha.animateTo(1f, tween(MarkInMs, easing = LinearEasing))
        }
        launch {
            markScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
            )
        }

        delay(TextDelayMs)
        launch {
            textAlpha.animateTo(1f, tween(TextInMs, easing = LinearEasing))
        }
        launch {
            textOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 450f),
            )
        }

        onReady()

        // Phase 2: 静默展示片刻，留出充足时间让底层 MainScaffold 完成静默预热
        delay(MarkInMs + HoldMs)

        // Phase 3: Apple 式优雅向外绽放淡出，纯 GPU 硬件加速
        launch {
            exitScale.animateTo(1.05f, tween(ExitMs, easing = FastOutSlowInEasing))
        }
        exitAlpha.animateTo(0f, tween(ExitMs, easing = FastOutSlowInEasing))

        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = exitAlpha.value
                scaleX = exitScale.value
                scaleY = exitScale.value
            }
            .background(background)
            .then(EatTouches),
    ) {
        Column(
            modifier = Modifier.align(BiasAlignment(0f, -0.06f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(
                modifier = Modifier
                    .size(92.dp)
                    .graphicsLayer {
                        scaleX = markScale.value
                        scaleY = markScale.value
                        alpha = markAlpha.value
                    },
                showSky = true,
            )
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha.value
                    translationY = textOffsetY.value * density
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                    ),
                    color = ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        letterSpacing = 0.2.sp,
                    ),
                    color = muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
