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
    val parkedLeft: Boolean get() = parked && x < 0.5f

    companion object {
        fun restored(x: Float, y: Float, parked: Boolean): SiteShellOrbAnchor = SiteShellOrbAnchor(
            x = if (x.isFinite() && x >= 0f) x.coerceIn(0f, 1f) else 1f,
            y = if (y.isFinite() && y >= 0f) y.coerceIn(0f, 1f) else 0f,
            parked = parked,
        )

        fun expandedCenter() = SiteShellOrbAnchor(x = 0.5f, y = 0.5f, parked = false)

        fun parked(left: Boolean, y: Float) = SiteShellOrbAnchor(
            x = if (left) 0f else 1f,
            y = y.coerceIn(0f, 1f),
            parked = true,
        )
    }
}

internal data class SiteShellOrbPoint(val x: Float, val y: Float)

/**
 * Expanded glass ball vs edge capsule. The parked hit box is the capsule itself.
 */
internal object SiteShellOrbMetrics {
    const val ORB_SIZE = 56f
    /** Visible parked tab is the hit box. Slightly larger than the previous 12×40 sliver. */
    const val PARKED_WIDTH = 18f
    const val PARKED_HEIGHT = 48f
    const val PARKED_EDGE_INSET = 0f
    const val PARK_DIRECTION_DP = 24f
    /** Release inside this band of an edge docks even without a strong horizontal flick. */
    const val PARK_EDGE_BAND_DP = 48f
    const val SNAP_MS = 220
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

    fun nearerLeft(releaseCenterX: Float) = releaseCenterX < width / 2f

    /** Where the expanded ball should land before it becomes the edge capsule. */
    fun parkSnapCenter(anchor: SiteShellOrbAnchor): SiteShellOrbPoint = clampCenter(
        x = if (anchor.parkedLeft) orbSize / 2f else (width - orbSize / 2f).coerceAtLeast(orbSize / 2f),
        y = centerY(anchor),
    )
}

internal enum class SiteShellOrbRelease {
    TAP,
    PARK_LEFT,
    PARK_RIGHT,
}

internal object SiteShellOrbGesture {
    const val DRAG_START_THRESHOLD_DP = 16f

    fun classifyRelease(
        dxDp: Float,
        dyDp: Float,
        releaseCenterX: Float,
        bounds: SiteShellOrbBounds,
    ): SiteShellOrbRelease {
        if (hypot(dxDp, dyDp) < DRAG_START_THRESHOLD_DP) return SiteShellOrbRelease.TAP
        if (releaseCenterX <= SiteShellOrbMetrics.PARK_EDGE_BAND_DP) {
            return SiteShellOrbRelease.PARK_LEFT
        }
        if (releaseCenterX >= bounds.width - SiteShellOrbMetrics.PARK_EDGE_BAND_DP) {
            return SiteShellOrbRelease.PARK_RIGHT
        }
        val directed = abs(dxDp) >= SiteShellOrbMetrics.PARK_DIRECTION_DP &&
            abs(dxDp) > abs(dyDp) * 0.5f
        val left = if (directed) dxDp < 0f else bounds.nearerLeft(releaseCenterX)
        return if (left) SiteShellOrbRelease.PARK_LEFT else SiteShellOrbRelease.PARK_RIGHT
    }
}
