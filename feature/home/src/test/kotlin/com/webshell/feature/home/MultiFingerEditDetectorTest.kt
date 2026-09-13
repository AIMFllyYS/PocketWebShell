package com.webshell.feature.home

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiFingerEditDetectorTest {

    private val p1 = Offset(0f, 0f)
    private val p2 = Offset(100f, 0f)

    private fun detector() = MultiFingerEditDetector(swipeSlopPx = 16f)

    @Test
    fun `late second finger still recognizes pinch in`() {
        val detector = detector()
        assertEquals(MultiFingerEditDetector.Signal.NONE, detector.onFrame(listOf(p1), editMode = false))
        assertEquals(MultiFingerEditDetector.Signal.NONE, detector.onFrame(listOf(p1, p2), editMode = false))
        assertEquals(
            MultiFingerEditDetector.Signal.ENTER,
            detector.onFrame(listOf(p1, Offset(80f, 0f)), editMode = false),
        )
        assertTrue(detector.armed)
    }

    @Test
    fun `lift then place again can pinch in once more`() {
        val detector = detector()
        detector.onFrame(listOf(p1, p2), editMode = false)
        detector.onFrame(listOf(p1, Offset(80f, 0f)), editMode = false)
        assertEquals(MultiFingerEditDetector.Signal.NONE, detector.onFrame(listOf(p1), editMode = false))
        assertFalse(detector.armed)
        assertEquals(MultiFingerEditDetector.Signal.NONE, detector.onFrame(listOf(p1, p2), editMode = false))
        assertEquals(
            MultiFingerEditDetector.Signal.ENTER,
            detector.onFrame(listOf(p1, Offset(80f, 0f)), editMode = false),
        )
        assertTrue(detector.armed)
    }

    @Test
    fun `single finger never fires`() {
        val detector = detector()
        assertEquals(MultiFingerEditDetector.Signal.NONE, detector.onFrame(listOf(p1), editMode = false))
        assertEquals(MultiFingerEditDetector.Signal.NONE, detector.onFrame(listOf(Offset(48f, 48f)), editMode = false))
        assertEquals(MultiFingerEditDetector.Signal.NONE, detector.onFrame(emptyList(), editMode = false))
        assertFalse(detector.armed)
    }

    @Test
    fun `dead zone does not fire`() {
        val detector = detector()
        detector.onFrame(listOf(p1, p2), editMode = false)
        assertEquals(
            MultiFingerEditDetector.Signal.NONE,
            detector.onFrame(listOf(p1, Offset(90f, 0f)), editMode = false),
        )
        assertEquals(
            MultiFingerEditDetector.Signal.NONE,
            detector.onFrame(listOf(Offset(-6f, 0f), Offset(106f, 0f)), editMode = false),
        )
        assertFalse(detector.armed)
    }

    @Test
    fun `pinch out exits only while editing`() {
        val expanded = listOf(Offset(-30f, 0f), Offset(150f, 0f))
        val detector = detector()
        detector.onFrame(listOf(p1, p2), editMode = false)
        assertEquals(MultiFingerEditDetector.Signal.NONE, detector.onFrame(expanded, editMode = false))
        detector.reset()
        detector.onFrame(listOf(p1, p2), editMode = true)
        assertEquals(MultiFingerEditDetector.Signal.EXIT, detector.onFrame(expanded, editMode = true))
        assertTrue(detector.armed)
    }

    @Test
    fun `parallel same-direction swipe enters and never exits`() {
        val detector = detector()
        detector.onFrame(listOf(p1, p2), editMode = false)
        val swiped = listOf(Offset(0f, 24f), Offset(100f, 24f))
        assertEquals(MultiFingerEditDetector.Signal.ENTER, detector.onFrame(swiped, editMode = false))
        assertTrue(detector.armed)
        detector.reset()
        detector.onFrame(listOf(p1, p2), editMode = true)
        assertEquals(MultiFingerEditDetector.Signal.NONE, detector.onFrame(swiped, editMode = true))
        assertFalse(detector.armed)
    }

    @Test
    fun `reset after signal does not fire on the next baseline frame`() {
        val detector = detector()
        detector.onFrame(listOf(p1, p2), editMode = false)
        detector.onFrame(listOf(p1, Offset(80f, 0f)), editMode = false)
        detector.reset()
        assertFalse(detector.armed)
        assertEquals(
            MultiFingerEditDetector.Signal.NONE,
            detector.onFrame(listOf(p1, Offset(80f, 0f)), editMode = false),
        )
        assertFalse(detector.armed)
    }
}
