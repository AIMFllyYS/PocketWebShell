package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebEngineDefaultsTest {
    @Test fun `document-start bootstrap is idempotent`() {
        val off = WebEngineDefaults.documentStartBootstrap(false)
        val on = WebEngineDefaults.documentStartBootstrap(true)
        assertEquals(off, WebEngineDefaults.documentStartBootstrap(false))
        assertEquals(on, WebEngineDefaults.documentStartBootstrap(true))
        assertTrue(off.contains("__wsBoot"))
        assertTrue(off.contains("ws-safe-style"))
        assertTrue(on.contains("__wsBoot"))
        assertTrue(on.contains("ws-safe-style"))
    }

    @Test fun `zoom rewrite is omitted when forceEnableZoom is off`() {
        val script = WebEngineDefaults.documentStartBootstrap(false)
        assertFalse(script.contains("user-scalable"))
        assertFalse(script.contains("maximum-scale"))
        assertFalse(script.contains("MutationObserver"))
        assertFalse(script.contains("wsForceZoom"))
    }

    @Test fun `zoom rewrite unlocks viewport when forceEnableZoom is on`() {
        val script = WebEngineDefaults.documentStartBootstrap(true)
        assertTrue(script.contains("user-scalable"))
        assertTrue(script.contains("maximum-scale"))
        assertTrue(script.contains("MutationObserver"))
        assertTrue(script.contains("user-scalable=yes"))
        assertTrue(script.contains("maximum-scale=10"))
        assertTrue(script.contains("subtree:true"))
    }

    @Test fun `local force-zoom observer does not watch the whole subtree`() {
        val offLocal = WebEngineDefaults.documentStartBootstrap(false, localApp = true)
        assertEquals(WebEngineDefaults.documentStartBootstrap(false), offLocal)
        assertFalse(offLocal.contains("MutationObserver"))
        val onLocal = WebEngineDefaults.documentStartBootstrap(true, localApp = true)
        assertTrue(onLocal.contains("MutationObserver"))
        assertTrue(onLocal.contains("subtree:false"))
        assertFalse(onLocal.contains("subtree:true"))
    }

    @Test fun `desktop remote rewrites viewport to chrome desktop width`() {
        val withZoom = WebEngineDefaults.documentStartBootstrap(
            forceEnableZoom = false, localApp = false, desktopMode = true,
        )
        val withoutZoom = WebEngineDefaults.documentStartBootstrap(
            forceEnableZoom = true, localApp = false, desktopMode = true,
        )
        assertEquals(withZoom, withoutZoom)
        assertEquals(withZoom, WebEngineDefaults.documentStartBootstrap(false, false, true))
        assertTrue(withZoom.contains("var W='" + WebEngineDefaults.DESKTOP_VIEWPORT_WIDTH + "'"))
        assertTrue(withZoom.contains("width='+W"))
        assertTrue(withZoom.contains("createElement('meta')"))
        assertTrue(withZoom.contains("k!=='initial-scale'"))
        assertTrue(withZoom.contains("k!=='minimum-scale'"))
        assertTrue(withZoom.contains("user-scalable=yes"))
        assertTrue(withZoom.contains("maximum-scale=10"))
        assertTrue(withZoom.contains("subtree:true"))
        assertFalse(withZoom.contains("device-width"))
    }

    @Test fun `mobile force-zoom does not assign a desktop layout width`() {
        val script = WebEngineDefaults.documentStartBootstrap(true)
        assertFalse(script.contains("var W='" + WebEngineDefaults.DESKTOP_VIEWPORT_WIDTH + "'"))
        assertFalse(script.contains("createElement('meta')"))
        assertTrue(script.contains("user-scalable=yes"))
    }

    @Test fun `local desktop never rewrites viewport width`() {
        val off = WebEngineDefaults.documentStartBootstrap(
            forceEnableZoom = false, localApp = true, desktopMode = true,
        )
        assertFalse(off.contains("var W='" + WebEngineDefaults.DESKTOP_VIEWPORT_WIDTH + "'"))
        assertFalse(off.contains("wsForceZoom"))
        val on = WebEngineDefaults.documentStartBootstrap(
            forceEnableZoom = true, localApp = true, desktopMode = true,
        )
        assertFalse(on.contains("var W='" + WebEngineDefaults.DESKTOP_VIEWPORT_WIDTH + "'"))
        assertTrue(on.contains("subtree:false"))
        assertFalse(on.contains("subtree:true"))
    }

    @Test fun `desktop rewrite tolerates early document-start DOM`() {
        val script = WebEngineDefaults.documentStartBootstrap(desktopMode = true)
        // document-start 时机下 documentElement/head 可能尚未创建：
        // 追加目标必须有 document 兜底，改写函数必须 try 包裹，
        // 观察器挂 document 根节点，并有 DOMContentLoaded 晚到兜底。
        assertTrue(script.contains("document.head||document.documentElement||document"))
        assertTrue(script.contains("try{"))
        assertTrue(script.contains("observe(document,"))
        assertTrue(script.contains("DOMContentLoaded"))
    }

    @Test fun `bootstrap prefix tolerates missing documentElement`() {
        val script = WebEngineDefaults.documentStartBootstrap(false)
        assertTrue(script.contains("(document.head||document.documentElement||document).appendChild"))
        assertTrue(script.contains("try{"))
        assertFalse(script.contains("wsForceZoom"))
    }
}

class DesktopInitialScaleTest {
    @Test fun `unknown width leaves the platform default`() {
        assertEquals(0, WebEngineDefaults.desktopInitialScalePercent(0))
        assertEquals(0, WebEngineDefaults.desktopInitialScalePercent(-10))
    }

    @Test fun `known width is chrome overview percent of 980`() {
        assertEquals(100, WebEngineDefaults.desktopInitialScalePercent(980))
        assertEquals(50, WebEngineDefaults.desktopInitialScalePercent(490))
        assertEquals(1, WebEngineDefaults.desktopInitialScalePercent(1))
        assertEquals(100, WebEngineDefaults.desktopInitialScalePercent(2000))
    }
}

class ResolveForceEnableZoomTest {
    @Test fun `desktop remote sites default on and local imports do not`() {
        assertTrue(resolveForceEnableZoom(desktopMode = true, localApp = false, userEnabled = false))
        assertFalse(resolveForceEnableZoom(desktopMode = false, localApp = false, userEnabled = false))
        assertFalse(resolveForceEnableZoom(desktopMode = true, localApp = true, userEnabled = false))
        assertTrue(resolveForceEnableZoom(desktopMode = false, localApp = false, userEnabled = true))
        assertTrue(resolveForceEnableZoom(desktopMode = true, localApp = true, userEnabled = true))
    }
}
