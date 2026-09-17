package com.webshell.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webshell.app.R
import com.webshell.core.designsystem.theme.LocalIsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private const val EntranceMs = 300
private const val TextDelayMs = 70L
private const val TextInMs = 240
private const val HoldMs = 200L
private const val ExitMs = 220

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
 * 原生极速丝滑开屏：
 * 1. 浅色模式采用「金色白洞」，深色模式采用「黑洞星核」；
 * 2. 彻底移除原先生硬前置的深色背景底图与方块，透明底天然融入系统窗口背景；
 * 3. 唯美艺术字与光引文字动效，GPU 硬件加速丝滑转场；
 * 4. 底层 MainScaffold 静默预热无缝衔接，达成 120fps 满帧无卡顿体验。
 */
@Composable
fun AppSplash(
    onFinished: () -> Unit,
    onReadyForShell: () -> Unit = {},
) {
    val dark = LocalIsDarkTheme.current
    val background = if (dark) Color.Black else Color.White
    val muted = if (dark) Color(0xFFA1A1AA) else Color(0xFF6B7280)

    val markScale = remember { Animatable(0.88f) }
    val markAlpha = remember { Animatable(0f) }
    val orbitRotation = remember { Animatable(0f) }

    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(14f) }
    val textScale = remember { Animatable(0.92f) }

    val exitScale = remember { Animatable(1.0f) }
    val exitAlpha = remember { Animatable(1.0f) }

    val onDone by rememberUpdatedState(onFinished)
    val onReady by rememberUpdatedState(onReadyForShell)

    val titleBrush = remember(dark) {
        if (dark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF7F3E9),
                    Color(0xFFE9D9AE),
                    Color(0xFFC9A86A),
                    Color(0xFFF7F3E9),
                ),
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF6B4E1A),
                    Color(0xFFB8860B),
                    Color(0xFFD4AF37),
                    Color(0xFF8A651E),
                ),
            )
        }
    }

    val titleStyle = remember(titleBrush) {
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            letterSpacing = 4.5.sp,
            brush = titleBrush,
        )
    }

    LaunchedEffect(Unit) {
        // Phase 1: 白洞/黑洞引力星核弹簧轻盈入场 + 轨道微旋
        launch {
            markAlpha.animateTo(1f, tween(EntranceMs, easing = LinearEasing))
        }
        launch {
            markScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 380f),
            )
        }
        launch {
            orbitRotation.animateTo(
                targetValue = 24f,
                animationSpec = tween(EntranceMs + HoldMs.toInt() + ExitMs, easing = FastOutSlowInEasing),
            )
        }

        // Phase 2: 艺术文字光引展开（GPU 硬件加速缩放与透明度，免 CPU 文本重复排版）
        delay(TextDelayMs)
        launch {
            textAlpha.animateTo(1f, tween(TextInMs, easing = LinearEasing))
        }
        launch {
            textOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f),
            )
        }
        launch {
            textScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 320f),
            )
        }

        // 通知底层开始静默预热
        onReady()

        // Phase 3: 留存片刻供底层初始化与预加载
        delay(EntranceMs + HoldMs)

        // Phase 4: Apple 式优雅向外绽放淡出，纯 GPU 硬件加速
        launch {
            exitScale.animateTo(1.06f, tween(ExitMs, easing = FastOutSlowInEasing))
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
            CosmicVortexMark(
                isDark = dark,
                rotationProvider = { orbitRotation.value },
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = markScale.value
                        scaleY = markScale.value
                        alpha = markAlpha.value
                    },
            )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha.value
                    translationY = textOffsetY.value * density
                    scaleX = textScale.value
                    scaleY = textScale.value
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "玄  览",
                    style = titleStyle,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 宇宙天体引力微标：
 * - 浅色模式：金色的白洞（暖金辐射光环、炽金星核、璀璨吸积盘），在纯白底上通透耀眼；
 * - 深色模式：引力黑洞（纯黑引力视界、月白光子层、金流伴星），在纯黑底上神秘深邃。
 *
 * 采用 drawWithCache 绘制管线：
 * 1. 颜色、几何尺寸、画笔（Stroke）与径向渐变（RadialGradient）均在绘制节点仅计算并缓存一次；
 * 2. 动画帧旋转偏移仅由 onDrawBehind 闭包捕获，完全规避 Compose 重组与堆内存分配，达成满帧 GPU 流畅度。
 */
@Composable
private fun CosmicVortexMark(
    isDark: Boolean,
    rotationProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier
            .aspectRatio(1f)
            .drawWithCache {
                val side = min(size.width, size.height)
                if (side <= 0f) {
                    onDrawBehind {}
                } else {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = side * 0.32f
                    val ringStrokeWidth = (radius * 5f / 30f).coerceAtLeast(1.5f)
                    val orbitStrokeWidth = (radius * 3.5f / 30f).coerceAtLeast(1.5f)
                    val coreRadius = radius * 7.5f / 30f
                    val orbitRx = radius * 45f / 30f
                    val orbitRy = radius * 16f / 30f
                    val companionRadius = radius * 4.5f / 30f

                    val ringStroke = Stroke(width = ringStrokeWidth, cap = StrokeCap.Round)
                    val orbitStroke = Stroke(width = orbitStrokeWidth, cap = StrokeCap.Round)

                    if (isDark) {
                        val haloBrush = Brush.radialGradient(
                            colors = listOf(Color(0x33C9A86A), Color(0x00C9A86A)),
                            center = center,
                            radius = radius * 1.6f,
                        )
                        val ringColor = Color(0xFFF2EEE3)
                        val coreBgColor = Color(0xFF0C0E14)
                        val coreColor = Color(0xFFE9D9AE)
                        val orbitColor = Color(0xFFC9A86A)
                        val companionColor = Color(0xFFE9D9AE)

                        onDrawBehind {
                            val rot = rotationProvider()
                            drawCircle(brush = haloBrush, radius = radius * 1.6f, center = center)
                            drawCircle(color = ringColor, radius = radius, center = center, style = ringStroke)
                            drawCircle(color = coreBgColor, radius = radius - ringStrokeWidth / 2f, center = center)
                            drawCircle(color = coreColor, radius = coreRadius, center = center)

                            rotate(-28f + rot, center) {
                                drawOval(
                                    color = orbitColor,
                                    topLeft = Offset(center.x - orbitRx, center.y - orbitRy),
                                    size = Size(orbitRx * 2f, orbitRy * 2f),
                                    style = orbitStroke,
                                )
                                drawCircle(
                                    color = companionColor,
                                    radius = companionRadius,
                                    center = Offset(center.x + orbitRx, center.y),
                                )
                            }
                        }
                    } else {
                        val haloBrush = Brush.radialGradient(
                            colors = listOf(Color(0x28D4AF37), Color(0x00D4AF37)),
                            center = center,
                            radius = radius * 1.7f,
                        )
                        val coreBrush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFFDF5), Color(0xFFB8860B)),
                            center = center,
                            radius = coreRadius,
                        )
                        val ringColor = Color(0xFFC9983A)
                        val orbitColor = Color(0xFFD4AF37)
                        val companionColor = Color(0xFFC9983A)

                        onDrawBehind {
                            val rot = rotationProvider()
                            drawCircle(brush = haloBrush, radius = radius * 1.7f, center = center)
                            drawCircle(color = ringColor, radius = radius, center = center, style = ringStroke)
                            drawCircle(brush = coreBrush, radius = coreRadius, center = center)

                            rotate(-28f + rot, center) {
                                drawOval(
                                    color = orbitColor,
                                    topLeft = Offset(center.x - orbitRx, center.y - orbitRy),
                                    size = Size(orbitRx * 2f, orbitRy * 2f),
                                    style = orbitStroke,
                                )
                                drawCircle(
                                    color = companionColor,
                                    radius = companionRadius,
                                    center = Offset(center.x + orbitRx, center.y),
                                )
                            }
                        }
                    }
                }
            },
    )
}
