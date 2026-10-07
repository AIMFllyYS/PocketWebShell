package com.webshell.core.webengine

import com.webshell.core.model.WebTopInsetMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WebTopInsetPolicyTest {
    @Test fun fullBleedHostReservesSafeTopOnce() {
        for (mode in listOf(WebTopInsetMode.AUTO, WebTopInsetMode.AVOID)) {
            assertEquals(72, remainingWebTopInset(mode, 72, 0, false))
        }
    }

    @Test fun systemOrParentOffsetMustNotBeAddedAgain() {
        for (mode in listOf(WebTopInsetMode.AUTO, WebTopInsetMode.AVOID)) {
            assertEquals(0, remainingWebTopInset(mode, 72, 72, false))
            assertEquals(0, remainingWebTopInset(mode, 72, 144, false))
            assertEquals(0, remainingWebTopInset(mode, 72, 0, true))
        }
    }

    @Test fun partiallyProtectedHostOnlyAddsRemainingDistance() {
        assertEquals(32, remainingWebTopInset(WebTopInsetMode.AUTO, 72, 40, false))
    }

    @Test fun manualEdgeToEdgeOverridesKnownAndUnknownPositions() {
        assertEquals(0, remainingWebTopInset(WebTopInsetMode.EDGE_TO_EDGE, 96, 0, false))
        assertEquals(0, remainingWebTopInset(WebTopInsetMode.EDGE_TO_EDGE, 96, null, false))
    }

    @Test fun initialLayoutConservativelyReservesSafeTopUntilPositionIsKnown() {
        assertEquals(72, remainingWebTopInset(WebTopInsetMode.AUTO, 72, null, false))
        assertEquals(0, remainingWebTopInset(WebTopInsetMode.AUTO, 72, 72, false))
    }

    @Test fun offsetAboveWindowMustNotInflateTheInset() {
        assertEquals(72, remainingWebTopInset(WebTopInsetMode.AUTO, 72, -30, false))
        assertEquals(0, remainingWebTopInset(WebTopInsetMode.AUTO, -30, 0, false))
    }
}
