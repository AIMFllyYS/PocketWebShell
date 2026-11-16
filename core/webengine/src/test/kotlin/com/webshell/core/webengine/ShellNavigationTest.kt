package com.webshell.core.webengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellNavigationTest {
    @Test fun `main-frame data is consumed`() {
        val route = UrlRouter.classify("data:text/html,SECRET").route
        assertEquals(UrlRoute.DATA, route)
        assertTrue(ShellNavigation.consumesOpaqueMainFrame(route, isMainFrame = true))
    }

    @Test fun `subresource data is not consumed`() {
        val route = UrlRouter.classify("data:image/png;base64,SECRET").route
        assertEquals(UrlRoute.DATA, route)
        assertFalse(ShellNavigation.consumesOpaqueMainFrame(route, isMainFrame = false))
    }

    @Test fun `main-frame blob is consumed and subresource blob is not`() {
        val route = UrlRouter.classify("blob:https://example.com/SECRET").route
        assertEquals(UrlRoute.BLOB, route)
        assertTrue(ShellNavigation.consumesOpaqueMainFrame(route, isMainFrame = true))
        assertFalse(ShellNavigation.consumesOpaqueMainFrame(route, isMainFrame = false))
    }

    @Test fun `https is not consumed by the opaque main-frame rule`() {
        val route = UrlRouter.classify("https://example.com/a?q=1").route
        assertEquals(UrlRoute.WEB, route)
        assertFalse(ShellNavigation.consumesOpaqueMainFrame(route, isMainFrame = true))
        assertFalse(ShellNavigation.consumesOpaqueMainFrame(route, isMainFrame = false))
    }

    @Test fun `logHost drops data and blob payloads and keeps the https host`() {
        val data = ShellNavigation.logHost("data:text/html,SECRET")
        assertFalse(data.contains("SECRET"))
        assertEquals("data", data)
        val blob = ShellNavigation.logHost("blob:https://example.com/SECRET")
        assertFalse(blob.contains("SECRET"))
        assertEquals("blob", blob)
        assertEquals("example.com", ShellNavigation.logHost("https://example.com/a?q=1"))
        assertFalse(ShellNavigation.logHost("https://example.com/a?q=1").contains("q=1"))
    }

    @Test fun `desktop probe log keeps numbers and drops page text`() {
        val callback = "\"390,390,400,390,2.75,1080,390,1,2,1,0,-1,0,1,0,3\""
        val line = ShellNavigation.desktopProbeLog(callback)
        assertEquals(
            "iw=390 cw=390 ow=400 rc=390 dpr=2.75 sw=1080 vv=390 vs=1 metas=2 mq=1,0,-1,0,1,0 boot=3",
            line,
        )
        val leaked = ShellNavigation.desktopProbeLog(
            "{\"iw\":390,\"vps\":[\"SECRET_META\"],\"uad\":\"SECRET_UA\"}",
        )
        assertEquals(ShellNavigation.PROBE_FAIL, leaked)
        assertFalse(leaked.contains("SECRET"))
        val failed = ShellNavigation.desktopProbeLog("\"probe-fail:SECRET_MESSAGE\"")
        assertEquals(ShellNavigation.PROBE_FAIL, failed)
        assertFalse(failed.contains("SECRET"))
        assertFalse(ShellNavigation.desktopProbeLog("\"1,SECRET\"").contains("SECRET"))
    }
}
