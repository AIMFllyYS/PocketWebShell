package com.webshell.core.designsystem.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween

/** 统一动效规范：常规过渡 150–300ms；弹簧物理曲线；不做改变布局 bounds 的装饰动画。 */
object AppMotion {
    const val FastMs = 150
    const val NormalMs = 250

    fun <T> fade() = tween<T>(FastMs)

    fun <T> spring(): SpringSpec<T> = SpringSpec(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}
