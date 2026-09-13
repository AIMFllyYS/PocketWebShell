package com.webshell.app.download

import kotlin.math.abs
import kotlin.math.hypot

internal enum class DownloadCapsuleRelease {
    TAP,
    PARK,
    MOVE,
}

/** Right-edge download capsule. Default Y sits between the site-shell orb (y=0) and the browse dock (y=1). */
internal object DownloadCapsuleGeometry {
    const val DEFAULT_Y = 0.62f
    const val EXPANDED_WIDTH = 220f
    const val EXPANDED_HEIGHT = 72f
    const val COMPLETE_SIZE = 56f
    const val PARKED_WIDTH = 18f
    const val PARKED_HEIGHT = 48f
    const val DRAG_START_THRESHOLD_DP = 16f
    /** Short rightward slide; the capsule already sits on the right so 24dp was unreachable. */
    const val PARK_DIRECTION_DP = 12f
    const val PARK_FLICK_DP = 10f

    fun defaultY(): Float = DEFAULT_Y

    fun normalizeY(y: Float): Float =
        if (y.isFinite() && y >= 0f) y.coerceIn(0f, 1f) else DEFAULT_Y

    fun clearsDefaultSiteShellAndDock(y: Float): Boolean = y > 0f && y < 1f

    /** Park only on a rightward-dominant slide. Being on the right edge is not a signal. */
    fun parksToRight(dxDp: Float, dyDp: Float): Boolean =
        dxDp.isFinite() && dyDp.isFinite() && dxDp >= PARK_DIRECTION_DP && dxDp >= abs(dyDp)

    fun isRightFlick(dxDp: Float, dyDp: Float): Boolean =
        dxDp.isFinite() && dyDp.isFinite() && dxDp >= PARK_FLICK_DP && dxDp >= abs(dyDp)

    fun classifyRelease(dxDp: Float, dyDp: Float): DownloadCapsuleRelease {
        val travel = hypot(dxDp.toDouble(), dyDp.toDouble()).toFloat()
        if (isRightFlick(dxDp, dyDp) || parksToRight(dxDp, dyDp)) {
            return DownloadCapsuleRelease.PARK
        }
        return if (travel < DRAG_START_THRESHOLD_DP) {
            DownloadCapsuleRelease.TAP
        } else {
            DownloadCapsuleRelease.MOVE
        }
    }
}

internal data class DownloadCapsuleBounds(val width: Float, val height: Float) {
    val minY = 8f
    val maxY = (height - 8f).coerceAtLeast(minY)

    fun centerY(normalizedY: Float): Float {
        val t = DownloadCapsuleGeometry.normalizeY(normalizedY)
        return minY + (maxY - minY) * t
    }

    fun normalizeFromCenter(centerY: Float): Float {
        val span = maxY - minY
        if (span <= 0f) return DownloadCapsuleGeometry.DEFAULT_Y
        return ((centerY - minY) / span).coerceIn(0f, 1f)
    }

    fun clampCenterY(y: Float): Float = y.coerceIn(minY, maxY)
}
