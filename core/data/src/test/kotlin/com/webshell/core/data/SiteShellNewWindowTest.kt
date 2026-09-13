package com.webshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteShellNewWindowTest {
    @Test
    fun unknownAndMissingValuesAdopt() {
        assertEquals(SITE_SHELL_NEW_WINDOW_ADOPT, normalizeSiteShellNewWindowPolicy(null))
        assertEquals(SITE_SHELL_NEW_WINDOW_ADOPT, normalizeSiteShellNewWindowPolicy(""))
        assertEquals(SITE_SHELL_NEW_WINDOW_ADOPT, normalizeSiteShellNewWindowPolicy("system_browser"))
        assertEquals(SITE_SHELL_NEW_WINDOW_REPLACE, normalizeSiteShellNewWindowPolicy(SITE_SHELL_NEW_WINDOW_REPLACE))
    }

    @Test
    fun perAppOverrideCyclesThroughFollowGlobal() {
        assertEquals(SITE_SHELL_NEW_WINDOW_ADOPT, nextSiteShellNewWindowOverride(null))
        assertEquals(SITE_SHELL_NEW_WINDOW_REPLACE, nextSiteShellNewWindowOverride(SITE_SHELL_NEW_WINDOW_ADOPT))
        assertNull(nextSiteShellNewWindowOverride(SITE_SHELL_NEW_WINDOW_REPLACE))
    }
}
