package com.webshell.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.webshell.core.designsystem.theme.AppMotion

/**
 * First-appear spring: scale from a point to 1. Layout bounds stay at the
 * final size; only the draw transform animates.
 */
@Composable
fun RevealFromPoint(
    modifier: Modifier = Modifier,
    origin: TransformOrigin = TransformOrigin(0.5f, 0f),
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, AppMotion.revealSpring())
    }
    val t = progress.value
    Box(
        modifier.graphicsLayer {
            val scale = 0.08f + 0.92f * t
            scaleX = scale
            scaleY = scale
            alpha = t
            transformOrigin = origin
        },
    ) {
        content()
    }
}
