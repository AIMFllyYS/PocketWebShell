package com.webshell.core.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * iOS Liquid Glass 玻璃材质：实时背景模糊 + 上亮下暗的高光描边。
 * 全屏实时模糊 backdrop 预算不超过 1 处（当前为底部导航条），见 docs/PERFORMANCE.md。
 */
@Composable
fun Modifier.glassSurface(
    hazeState: HazeState,
    shape: Shape = RoundedCornerShape(32.dp),
    borderWidth: Dp = 1.dp,
): Modifier = this
    .clip(shape)
    .hazeEffect(
        state = hazeState,
        style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
    )
    .border(
        width = borderWidth,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.08f),
            ),
        ),
        shape = shape,
    )
