package com.webshell.core.webengine

import kotlin.math.abs

/** Native touch classifier. A scroll callback alone (including script/fling) cannot qualify. */
internal class ReadingGestureClassifier(
    private val thresholdPx: Float,
    private val touchSlopPx: Float,
    private val longPressTimeoutMs: Long,
) {
    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    private var vertical = false
    private var eligible = false
    private var requested = false

    fun start(x: Float, y: Float, timeMs: Long, pointerCount: Int = 1) {
        startX = x
        startY = y
        startTime = timeMs
        vertical = false
        requested = false
        eligible = pointerCount == 1
    }

    /** True once per touch, only when a prompt upward finger movement passes the threshold. */
    fun move(x: Float, y: Float, timeMs: Long, pointerCount: Int, selectingText: Boolean): Boolean {
        if (!eligible || requested) return false
        if (pointerCount != 1 || selectingText) {
            cancel()
            return false
        }
        val dx = abs(x - startX)
        val dy = startY - y
        if (!vertical) {
            // Stationary long-press followed by selection-handle movement is not reading.
            if (timeMs - startTime >= longPressTimeoutMs || (dx > touchSlopPx && dx >= abs(dy))) {
                cancel()
                return false
            }
            if (dy > touchSlopPx && dy > dx * 1.25f) vertical = true
            if (dy < -touchSlopPx) cancel()
        }
        if (vertical && dy >= thresholdPx && dy > dx * 1.25f) {
            requested = true
            return true
        }
        return false
    }

    fun cancel() { eligible = false }
}
