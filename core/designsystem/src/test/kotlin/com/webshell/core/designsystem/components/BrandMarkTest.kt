package com.webshell.core.designsystem.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BrandMarkTest {
    @Test
    fun untintedPaletteStaysLauncherGold() {
        val colors = brandMarkColors(null)
        assertEquals(BrandMarkPalette.ring, colors.ring)
        assertEquals(BrandMarkPalette.core, colors.core)
        assertEquals(BrandMarkPalette.orbit, colors.orbit)
    }

    @Test
    fun tintRecolorsRingCoreAndOrbitWithoutTouchingGoldDefaults() {
        val tint = Color(0xA6007AFF)
        val colors = brandMarkColors(tint)
        assertEquals(tint, colors.core)
        assertNotEquals(BrandMarkPalette.ring, colors.ring)
        assertNotEquals(BrandMarkPalette.orbit, colors.orbit)
        assertEquals(BrandMarkPalette.ring, brandMarkColors(null).ring)
    }

    @Test
    fun emptyStateMarkUsesALargerRadiusThanTheSkyVariant() {
        assertEquals(22f, brandMarkRadius(100f, showSky = true), 0f)
        assertEquals(32f, brandMarkRadius(100f, showSky = false), 0f)
    }
}
