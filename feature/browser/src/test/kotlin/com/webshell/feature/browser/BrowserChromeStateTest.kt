package com.webshell.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserChromeStateTest {
    private val browsing = BrowserChromeState(visible = true, activeSessionId = "browser-a")

    @Test fun activeReadingCollapsesOnlyBottomChrome() {
        val collapsed = reduceBrowserChrome(browsing, BrowserChromeEvent.Reading("browser-a"))
        assertEquals(BottomChromeMode.Orb, collapsed.bottomMode)
        assertTrue(collapsed.toolbarVisible)
    }

    @Test fun staleSessionAndBackgroundCannotCollapse() {
        assertEquals(browsing, reduceBrowserChrome(browsing, BrowserChromeEvent.Reading("browser-b")))
        val background = browsing.copy(visible = false)
        assertEquals(background, reduceBrowserChrome(background, BrowserChromeEvent.Reading("browser-a")))
    }

    @Test fun editingKeyboardFindPermissionAndOverlayBlockReading() {
        val blocked = browsing.copy(interactionBlocked = true)
        assertEquals(blocked, reduceBrowserChrome(blocked, BrowserChromeEvent.Reading("browser-a")))
        BrowserOverlay.entries.forEach { overlay ->
            val state = browsing.copy(overlay = overlay)
            assertEquals(state, reduceBrowserChrome(state, BrowserChromeEvent.Reading("browser-a")))
        }
    }

    @Test fun disabledAutoCollapseStillAllowsManualCollapse() {
        val disabled = browsing.copy(autoCollapse = false)
        assertEquals(disabled, reduceBrowserChrome(disabled, BrowserChromeEvent.Reading("browser-a")))
        assertEquals(BottomChromeMode.Orb, reduceBrowserChrome(disabled, BrowserChromeEvent.Collapse).bottomMode)
    }

    @Test fun orbCanParkAndBothShapesRevealToolbarWithoutKeyboardState() {
        val collapsed = reduceBrowserChrome(browsing.copy(toolbarVisible = false), BrowserChromeEvent.Collapse)
        val parked = reduceBrowserChrome(collapsed, BrowserChromeEvent.Park)
        assertEquals(BottomChromeMode.Edge, parked.bottomMode)
        listOf(collapsed, parked).forEach {
            val restored = reduceBrowserChrome(it, BrowserChromeEvent.Reveal)
            assertEquals(BottomChromeMode.Expanded, restored.bottomMode)
            assertTrue(restored.toolbarVisible)
            assertFalse(restored.interactionBlocked)
        }
        assertEquals(browsing, reduceBrowserChrome(browsing, BrowserChromeEvent.Park))
    }

    @Test fun hidingTopDoesNotAffectBottomOrRestoreOnLaterReading() {
        val hidden = reduceBrowserChrome(browsing, BrowserChromeEvent.HideToolbar)
        assertFalse(hidden.toolbarVisible)
        assertEquals(BottomChromeMode.Expanded, hidden.bottomMode)
        assertFalse(reduceBrowserChrome(hidden, BrowserChromeEvent.Reading("browser-a")).toolbarVisible)
    }

    @Test fun leavingPreservesCollapsedShapeButDropsOverlay() {
        val state = browsing.copy(bottomMode = BottomChromeMode.Edge, toolbarVisible = false, overlay = BrowserOverlay.Menu)
        val hidden = reduceBrowserChrome(state, BrowserChromeEvent.Environment(false, "browser-a", true, false))
        assertEquals(BottomChromeMode.Edge, hidden.bottomMode)
        assertEquals(null, hidden.overlay)
        assertFalse(hidden.toolbarVisible)
    }

    @Test fun emptyOrDisablingAutomaticBehaviorRecoversReachableNavigation() {
        val state = browsing.copy(bottomMode = BottomChromeMode.Edge, toolbarVisible = false)
        val empty = reduceBrowserChrome(state, BrowserChromeEvent.Environment(true, null, true, false))
        assertEquals(BottomChromeMode.Expanded, empty.bottomMode)
        assertTrue(empty.toolbarVisible)
        val disabled = reduceBrowserChrome(state, BrowserChromeEvent.Environment(true, "browser-a", false, false))
        assertEquals(BottomChromeMode.Expanded, disabled.bottomMode)
    }

    @Test fun restoringPresentationDoesNotRestoreStaleSessionOrInteraction() {
        val restored = restoreChromePresentation("Edge", false)
        assertEquals(BottomChromeMode.Edge, restored.bottomMode)
        assertFalse(restored.toolbarVisible)
        assertEquals(null, restored.activeSessionId)
        assertEquals(null, restored.overlay)
        assertFalse(restored.visible)
        assertFalse(restored.interactionBlocked)
    }

    @Test fun invalidSavedPresentationFallsBackToReachableControls() {
        assertEquals(BrowserChromeState(), restoreChromePresentation("removed-mode", false))
        assertEquals(BrowserChromeState(), restoreChromePresentation(null, null))
    }
}
