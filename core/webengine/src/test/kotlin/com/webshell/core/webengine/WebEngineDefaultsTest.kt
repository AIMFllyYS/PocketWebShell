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
        // 改写函数 try 包裹、观察器挂 document 根、DOMContentLoaded 晚到兜底。
        assertTrue(script.contains("try{"))
        assertTrue(script.contains("observe(document,"))
        assertTrue(script.contains("DOMContentLoaded"))
    }

    @Test fun `bootstrap never appends elements to the document root`() {
        // 向 document 根节点追加元素会把其变成文档根、破坏整个解析——任何路径都不允许。
        val desktop = WebEngineDefaults.documentStartBootstrap(desktopMode = true)
        val plain = WebEngineDefaults.documentStartBootstrap(false)
        assertFalse(desktop.contains("||document).appendChild"))
        assertFalse(plain.contains("||document).appendChild"))
        assertTrue(plain.contains("var r=document.head||document.documentElement;"))
        assertFalse(plain.contains("wsForceZoom"))
    }

    @Test fun `desktop rewrite fixes every viewport meta instead of the first one`() {
        val script = WebEngineDefaults.documentStartBootstrap(desktopMode = true)
        // Blink 对多个 viewport meta 后解析者覆盖先前者：只改第一个等于没改。
        assertFalse(script.contains("break"))
        assertTrue(script.contains("found"))
        // 无 meta 时补建，且只在 head/documentElement 已存在时挂接
        assertTrue(script.contains("if(!found)"))
        assertTrue(script.contains("if(head)"))
        // 二次注入幂等守卫；改写函数暴露给宿主在页面完成后强制重断
        assertTrue(script.contains("__wsBoot"))
        assertTrue(script.contains("v===2"))
        assertTrue(script.contains("window.__wsForceZoom=wsForceZoom"))
    }
}

class DesktopInitialScaleTest {
    @Test fun `unknown width leaves the platform default`() {
        assertEquals(0, WebEngineDefaults.desktopInitialScalePercent(0))
        assertEquals(0, WebEngineDefaults.desktopInitialScalePercent(-10))
    }

    @Test fun `known width is overview percent of the desktop layout width`() {
        assertEquals(100, WebEngineDefaults.desktopInitialScalePercent(WebEngineDefaults.DESKTOP_VIEWPORT_WIDTH))
        assertEquals(50, WebEngineDefaults.desktopInitialScalePercent(WebEngineDefaults.DESKTOP_VIEWPORT_WIDTH / 2))
    }

    @Test fun `desktop layout width clears mainstream desktop breakpoints`() {
        // 980 只到"平板/窄桌面"：Bootstrap lg=992、Tailwind lg=1024 都不会命中。
        assertTrue(WebEngineDefaults.DESKTOP_VIEWPORT_WIDTH >= 1280)
    }

    @Test fun `wide screens zoom in beyond 100 instead of clamping`() {
        // 钳在 100 会让物理像素宽于布局宽的屏幕铺不满（右侧留白带、形如缩小的窄条）。
        assertEquals(156, WebEngineDefaults.desktopInitialScalePercent(2000))
        assertEquals(250, WebEngineDefaults.desktopInitialScalePercent(12800))
        assertEquals(25, WebEngineDefaults.desktopInitialScalePercent(1))
    }
}

class UserAgentMetadataTest {
    @Test fun `desktop metadata builds a complete windows identity`() {
        // BrandVersion.Builder.build() 对空 brand/majorVersion/fullVersion 必抛
        // IllegalStateException：0.1.58–0.1.60 因 GREASE 品牌缺 fullVersion，
        // 整套 UA-CH 从未真正设置（异常被调用方 runCatching 吞掉）。
        val md = WebEngineDefaults.userAgentMetadata(desktop = true)
        assertEquals("Windows", md.platform)
        assertFalse(md.isMobile)
        assertEquals("10.0.0", md.platformVersion)
        assertEquals("x86", md.architecture)
        assertEquals(64, md.bitness)
        assertEquals("", md.model)
        val brands = md.brandVersionList
        assertEquals(2, brands.size)
        assertEquals("Chromium", brands[0].brand)
        assertEquals(WebEngineDefaults.UA_MAJOR_VERSION, brands[0].majorVersion)
        assertEquals(WebEngineDefaults.UA_FULL_VERSION, brands[0].fullVersion)
        assertEquals(WebEngineDefaults.UA_GREASE_BRAND, brands[1].brand)
        brands.forEach {
            assertTrue(it.brand.isNotBlank())
            assertTrue(it.majorVersion.isNotBlank())
            assertTrue(it.fullVersion.isNotBlank())
        }
    }

    @Test fun `mobile metadata builds a complete android identity`() {
        val md = WebEngineDefaults.userAgentMetadata(
            desktop = false, mobilePlatformVersion = "15", mobileModel = "Pixel 9",
        )
        assertEquals("Android", md.platform)
        assertTrue(md.isMobile)
        assertEquals("15", md.platformVersion)
        assertEquals("Pixel 9", md.model)
        assertEquals(0, md.bitness)
        md.brandVersionList.forEach {
            assertTrue(it.brand.isNotBlank())
            assertTrue(it.majorVersion.isNotBlank())
            assertTrue(it.fullVersion.isNotBlank())
        }
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
