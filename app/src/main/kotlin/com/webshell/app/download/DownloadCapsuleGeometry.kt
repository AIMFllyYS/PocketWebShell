package com.webshell.app.download

/** Right-edge download capsule. Default Y sits between the site-shell orb (y=0) and the browse dock (y=1). */
internal object DownloadCapsuleGeometry {
    const val DEFAULT_Y = 0.62f
    const val EXPANDED_WIDTH = 220f
    const val EXPANDED_HEIGHT = 72f
    const val COMPLETE_SIZE = 56f
    const val PARKED_WIDTH = 18f
    const val PARKED_HEIGHT = 48f
    const val DRAG_START_THRESHOLD_DP = 16f
    const val PARK_DIRECTION_DP = 24f
    const val PARK_EDGE_BAND_DP = 48f

    fun defaultY(): Float = DEFAULT_Y

    fun normalizeY(y: Float): Float =
        if (y.isFinite() && y >= 0f) y.coerceIn(0f, 1f) else DEFAULT_Y

    fun clearsDefaultSiteShellAndDock(y: Float): Boolean = y > 0f && y < 1f

    fun parksToRight(dxDp: Float, releaseCenterX: Float, boundsWidth: Float): Boolean {
        if (releaseCenterX >= boundsWidth - PARK_EDGE_BAND_DP) return true
        return dxDp >= PARK_DIRECTION_DP
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
