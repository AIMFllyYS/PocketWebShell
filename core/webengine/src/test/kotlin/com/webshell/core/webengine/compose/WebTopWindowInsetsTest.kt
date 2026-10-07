package com.webshell.core.webengine.compose

import android.graphics.Rect
import androidx.core.graphics.Insets
import androidx.core.view.DisplayCutoutCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise the Android inset conversion, not only the arithmetic policy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class WebTopWindowInsetsTest {
    @Test fun removesTopCutoutAndPreservesOtherEdgesAndWaterfall() {
        val left = Rect(0, 200, 12, 240)
        val top = Rect(400, 0, 480, 72)
        val right = Rect(1064, 200, 1080, 240)
        val bottom = Rect(450, 2382, 490, 2400)
        val source = WindowInsetsCompat.Builder()
            .setDisplayCutout(DisplayCutoutCompat(
                Insets.of(12, 72, 16, 18), left, top, right, bottom, Insets.of(2, 3, 4, 5),
            ))
            .build()

        val result = source.withoutWebTopInset()
        val cutout = requireNotNull(result.displayCutout)
        assertEquals(0, cutout.safeInsetTop)
        assertEquals(12, cutout.safeInsetLeft)
        assertEquals(16, cutout.safeInsetRight)
        assertEquals(18, cutout.safeInsetBottom)
        assertEquals(Insets.of(2, 0, 4, 5), cutout.waterfallInsets)
        val native = requireNotNull(result.toWindowInsets()?.displayCutout)
        assertTrue(native.boundingRectTop.isEmpty)
        assertEquals(left, native.boundingRectLeft)
        assertEquals(right, native.boundingRectRight)
        assertEquals(bottom, native.boundingRectBottom)
        assertEquals("the root input must remain unchanged", 72, source.displayCutout?.safeInsetTop)
    }

    @Test fun clearingTopLeavesNavigationAndKeyboardInsetsIntact() {
        val source = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 48, 0, 0))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 72, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(8, 0, 0, 24))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 600))
            .build()

        val result = source.withoutWebTopInset()
        assertEquals(0, result.getInsets(WindowInsetsCompat.Type.statusBars()).top)
        assertEquals(0, result.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top)
        assertEquals(Insets.of(8, 0, 0, 24), result.getInsets(WindowInsetsCompat.Type.navigationBars()))
        assertEquals(600, result.getInsets(WindowInsetsCompat.Type.ime()).bottom)
        assertEquals(48, source.getInsets(WindowInsetsCompat.Type.statusBars()).top)
    }
}
