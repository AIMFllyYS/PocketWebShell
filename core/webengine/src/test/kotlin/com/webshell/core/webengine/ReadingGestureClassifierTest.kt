package com.webshell.core.webengine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingGestureClassifierTest {
    private fun detector() = ReadingGestureClassifier(64f, 8f, 500L)

    @Test fun oneUpwardFingerPastThresholdQualifiesOnce() {
        val detector = detector()
        detector.start(100f, 200f, 0)
        assertFalse(detector.move(102f, 170f, 40, 1, false))
        assertTrue(detector.move(103f, 130f, 100, 1, false))
        assertFalse(detector.move(103f, 100f, 200, 1, false))
    }

    @Test fun noDownMeansScriptOrFlingCannotQualify() {
        assertFalse(detector().move(0f, -100f, 100, 1, false))
    }

    @Test fun downwardAndHorizontalGesturesStayWithWebContent() {
        val detector = detector()
        detector.start(100f, 200f, 0)
        assertFalse(detector.move(110f, 300f, 100, 1, false))
        assertFalse(detector.move(110f, 10f, 200, 1, false))
        detector.start(100f, 200f, 0)
        assertFalse(detector.move(150f, 199f, 60, 1, false))
        assertFalse(detector.move(150f, 80f, 100, 1, false))
    }

    @Test fun multiTouchThenSingleTouchCannotMasqueradeAsReading() {
        val detector = detector()
        detector.start(100f, 200f, 0)
        assertFalse(detector.move(100f, 180f, 60, 2, false))
        assertFalse(detector.move(100f, 100f, 120, 1, false))
    }

    @Test fun longPressSelectionAndCancellationDoNotCollapse() {
        val detector = detector()
        detector.start(100f, 200f, 0)
        assertFalse(detector.move(100f, 100f, 600, 1, false))
        detector.start(100f, 200f, 0)
        assertFalse(detector.move(100f, 100f, 100, 1, true))
        detector.start(100f, 200f, 0)
        detector.cancel()
        assertFalse(detector.move(100f, 100f, 100, 1, false))
    }

    @Test fun slowReadingAfterVerticalIntentIsAllowed() {
        val detector = detector()
        detector.start(100f, 200f, 0)
        assertFalse(detector.move(100f, 180f, 100, 1, false))
        assertTrue(detector.move(100f, 100f, 900, 1, false))
    }
}
