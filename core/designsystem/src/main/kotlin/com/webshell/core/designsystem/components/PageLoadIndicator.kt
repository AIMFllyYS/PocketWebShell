package com.webshell.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 浏览器式“半真半假”顶栏进度：真实 progress 作上限，同时缓慢爬升，完成后短暂收束再消失。
 * 预留 2dp 高度，避免加载态改变视口。
 *
 * [sessionKey] (tab/session id) resets the internal animation state whenever
 * it changes: without it, switching the *active* tab reuses the previous
 * tab's in-flight [Animatable] value/target, so the bar visibly animates
 * from tab A's old progress toward tab B's — a "progress crosstalk" glitch
 * that has nothing to do with either tab's real load state.
 */
@Composable
fun PageLoadIndicator(
    loading: Boolean,
    rawProgress: Int,
    modifier: Modifier = Modifier,
    sessionKey: Any? = null,
) {
    key(sessionKey) {
        PageLoadIndicatorContent(loading, rawProgress, modifier)
    }
}

@Composable
private fun PageLoadIndicatorContent(
    loading: Boolean,
    rawProgress: Int,
    modifier: Modifier,
) {
    val display = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(loading, rawProgress) {
        if (loading) {
            visible = true
            val target = PageLoadProgress.display(rawProgress, loading = true, trickle = display.value)
            display.animateTo(target, tween(PageLoadProgress.CatchUpMs, easing = LinearEasing))
            val ceiling = PageLoadProgress.TrickleCeiling
            if (display.value < ceiling) {
                val remaining = ((ceiling - display.value) * PageLoadProgress.TrickleMs).toInt().coerceAtLeast(1)
                display.animateTo(ceiling, tween(remaining, easing = LinearEasing))
            }
        } else if (visible) {
            display.animateTo(1f, tween(PageLoadProgress.CompleteMs, easing = LinearEasing))
            delay(PageLoadProgress.HoldMs.toLong())
            visible = false
            display.snapTo(0f)
        }
    }

    Box(modifier.fillMaxWidth().height(2.dp)) {
        if (visible) {
            LinearProgressIndicator(
                progress = { display.value.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

object PageLoadProgress {
    const val Floor = 0.12f
    const val Ceiling = 0.92f
    const val TrickleCeiling = 0.86f
    const val CatchUpMs = 180
    const val TrickleMs = 8_000
    const val CompleteMs = 110
    const val HoldMs = 80

    fun display(rawPercent: Int, loading: Boolean, trickle: Float): Float {
        if (!loading) return 1f
        val raw = (rawPercent.coerceIn(0, 100) / 100f) * Ceiling
        return maxOf(Floor, raw, trickle.coerceIn(0f, TrickleCeiling)).coerceAtMost(Ceiling)
    }
}
