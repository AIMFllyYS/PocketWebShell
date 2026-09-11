package com.webshell.app.shell

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Normalized center in the site-shell overlay. Unset coordinates restore to top-right. */
internal data class SiteShellOrbAnchor(
    val x: Float = 1f,
    val y: Float = 0f,
    val parked: Boolean = false,
) {
    companion object {
        fun restored(x: Float, y: Float, parked: Boolean): SiteShellOrbAnchor = SiteShellOrbAnchor(
            x = if (x.isFinite() && x >= 0f) x.coerceIn(0f, 1f) else 1f,
            y = if (y.isFinite() && y >= 0f) y.coerceIn(0f, 1f) else 0f,
            parked = parked,
        )
    }
}

internal data class SiteShellOrbPoint(val x: Float, val y: Float)

/**
 * Doubao-style overlay: a 56dp ball when expanded; when parked, only a slice
 * peeks from the right edge and the hit box matches that visible slice.
 */
internal object SiteShellOrbMetrics {
    const val ORB_SIZE = 56f
    const val PARKED_WIDTH = 22f
    const val PARKED_HEIGHT = 56f
    const val PARKED_EDGE_INSET = 6f
}

/** Coordinates are dp in the safe content rectangle and never participate in WebView measurement. */
internal data class SiteShellOrbBounds(val width: Float, val height: Float) {
    val orbSize = min(SiteShellOrbMetrics.ORB_SIZE, min(width, height)).coerceAtLeast(1f)
    private val marginX = min(16f, ((width - orbSize) / 2).coerceAtLeast(0f))
    private val marginY = min(16f, ((height - orbSize) / 2).coerceAtLeast(0f))
    val minX = marginX + orbSize / 2
    val maxX = (width - marginX - orbSize / 2).coerceAtLeast(minX)
    val minY = marginY + orbSize / 2
    val maxY = (height - marginY - orbSize / 2).coerceAtLeast(minY)

    fun centerX(anchor: SiteShellOrbAnchor) = minX + (maxX - minX) * anchor.x
    fun centerY(anchor: SiteShellOrbAnchor) = minY + (maxY - minY) * anchor.y

    fun clampCenter(x: Float, y: Float) = SiteShellOrbPoint(
        x.coerceIn(orbSize / 2, (width - orbSize / 2).coerceAtLeast(orbSize / 2)),
        y.coerceIn(minY, maxY),
    )

    fun anchorAt(x: Float, y: Float) = SiteShellOrbAnchor(
        x = if (maxX == minX) 1f else ((x - minX) / (maxX - minX)).coerceIn(0f, 1f),
        y = if (maxY == minY) 0f else ((y - minY) / (maxY - minY)).coerceIn(0f, 1f),
        parked = false,
    )

    /** Right-edge only. The left extreme stays a normal rest point. */
    fun isRightEdgeDrop(x: Float) = x >= maxX - 36f
}

internal enum class SiteShellOrbRelease {
    TAP,
    REPOSITION,
    PARK_RIGHT,
    REFRESH,
}

internal object SiteShellOrbGesture {
    const val DRAG_START_THRESHOLD_DP = 16f
    const val FLICK_MIN_DX_DP = 48f
    const val FLICK_MAX_DURATION_MS = 280L
    const val FLICK_MIN_SPEED_DP_PER_MS = 0.45f
    const val FLICK_HORIZONTAL_RATIO = 1.4f

    fun classifyRelease(
        dxDp: Float,
        dyDp: Float,
        durationMs: Long,
        releaseCenterX: Float,
        bounds: SiteShellOrbBounds,
    ): SiteShellOrbRelease {
        if (hypot(dxDp, dyDp) < DRAG_START_THRESHOLD_DP) return SiteShellOrbRelease.TAP
        if (isHorizontalFlick(dxDp, dyDp, durationMs, towardNegativeX = true)) {
            return SiteShellOrbRelease.REFRESH
        }
        if (isHorizontalFlick(dxDp, dyDp, durationMs, towardNegativeX = false) ||
            bounds.isRightEdgeDrop(releaseCenterX)
        ) {
            return SiteShellOrbRelease.PARK_RIGHT
        }
        return SiteShellOrbRelease.REPOSITION
    }

    private fun isHorizontalFlick(
        dxDp: Float,
        dyDp: Float,
        durationMs: Long,
        towardNegativeX: Boolean,
    ): Boolean {
        if (durationMs !in 1..FLICK_MAX_DURATION_MS) return false
        if (towardNegativeX && dxDp >= 0f) return false
        if (!towardNegativeX && dxDp <= 0f) return false
        val adx = abs(dxDp)
        val ady = abs(dyDp)
        if (adx < FLICK_MIN_DX_DP) return false
        if (adx <= ady * FLICK_HORIZONTAL_RATIO) return false
        return adx / durationMs >= FLICK_MIN_SPEED_DP_PER_MS
    }
}
