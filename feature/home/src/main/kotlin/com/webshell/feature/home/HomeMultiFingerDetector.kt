package com.webshell.feature.home

import androidx.compose.ui.geometry.Offset

/**
 * 多指进/出编辑模式的纯检测器：捏合进、外扩退、双指同向滑动仅进入。
 * 第一帧不足两指时复位，第二指晚到仍能建立 baseline。
 */
internal class MultiFingerEditDetector(
    private val pinchIn: Float = PINCH_IN_THRESHOLD,
    private val pinchOut: Float = PINCH_OUT_THRESHOLD,
    private val swipeSlopPx: Float,
) {
    enum class Signal { NONE, ENTER, EXIT }

    private var hasBaseline = false
    private var baselineDistance = 0f
    private var baselineCentroid = Offset.Zero
    private var baselineFirst = Offset.Zero
    private var baselineSecond = Offset.Zero
    private var _armed = false

    val armed: Boolean get() = _armed

    fun reset() {
        hasBaseline = false
        baselineDistance = 0f
        baselineCentroid = Offset.Zero
        baselineFirst = Offset.Zero
        baselineSecond = Offset.Zero
        _armed = false
    }

    fun onFrame(points: List<Offset>, editMode: Boolean): Signal {
        if (points.size < 2) {
            reset()
            return Signal.NONE
        }
        val first = points[0]
        val second = points[1]
        val distance = (first - second).getDistance()
        val centroid = Offset((first.x + second.x) / 2f, (first.y + second.y) / 2f)
        if (!hasBaseline) {
            hasBaseline = true
            baselineDistance = distance
            baselineCentroid = centroid
            baselineFirst = first
            baselineSecond = second
            return Signal.NONE
        }
        if (_armed) return Signal.NONE
        val ratio = if (baselineDistance > 0f) distance / baselineDistance else 1f
        if (ratio < pinchIn && !editMode) {
            _armed = true
            return Signal.ENTER
        }
        if (ratio > pinchOut && editMode) {
            _armed = true
            return Signal.EXIT
        }
        if (ratio in SWIPE_RATIO_MIN..SWIPE_RATIO_MAX && !editMode) {
            val centroidTravel = (centroid - baselineCentroid).getDistance()
            if (centroidTravel > swipeSlopPx && sameDirection(first, second)) {
                _armed = true
                return Signal.ENTER
            }
        }
        return Signal.NONE
    }

    private fun sameDirection(first: Offset, second: Offset): Boolean {
        val v0 = first - baselineFirst
        val v1 = second - baselineSecond
        val d0 = v0.getDistance()
        val d1 = v1.getDistance()
        if (d0 <= 0f || d1 <= 0f) return false
        val alignment = (v0.x * v1.x + v0.y * v1.y) / (d0 * d1)
        return alignment > 0f
    }

    private companion object {
        const val SWIPE_RATIO_MIN = 0.92f
        const val SWIPE_RATIO_MAX = 1.08f
    }
}
