package com.webshell.core.webengine.compose

import androidx.core.graphics.Insets
import androidx.core.view.DisplayCutoutCompat
import androidx.core.view.WindowInsetsCompat

/** The host owns the top edge. Preserve side/bottom cutouts and IME for descendants. */
internal fun WindowInsetsCompat.withoutWebTopInset(): WindowInsetsCompat {
    val builder = WindowInsetsCompat.Builder(this)
    for (type in listOf(WindowInsetsCompat.Type.statusBars(), WindowInsetsCompat.Type.displayCutout())) {
        val visible = getInsets(type)
        val stable = getInsetsIgnoringVisibility(type)
        builder.setInsets(type, Insets.of(visible.left, 0, visible.right, visible.bottom))
        builder.setInsetsIgnoringVisibility(type, Insets.of(stable.left, 0, stable.right, stable.bottom))
    }
    displayCutout?.let { cutout ->
        // API 29+ exposes each edge explicitly; do not infer edge ownership from rect sizes.
        val nativeCutout = toWindowInsets()?.displayCutout
        val waterfall = cutout.waterfallInsets
        builder.setDisplayCutout(
            DisplayCutoutCompat(
                Insets.of(cutout.safeInsetLeft, 0, cutout.safeInsetRight, cutout.safeInsetBottom),
                nativeCutout?.boundingRectLeft?.takeUnless { it.isEmpty },
                null,
                nativeCutout?.boundingRectRight?.takeUnless { it.isEmpty },
                nativeCutout?.boundingRectBottom?.takeUnless { it.isEmpty },
                Insets.of(waterfall.left, 0, waterfall.right, waterfall.bottom),
            ),
        )
    }
    return builder.build()
}
