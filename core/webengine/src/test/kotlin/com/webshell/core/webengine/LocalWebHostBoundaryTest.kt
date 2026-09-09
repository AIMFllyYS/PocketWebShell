package com.webshell.core.webengine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWebHostBoundaryTest {
    @Test fun `local path accepts only an app relative safe path`() {
        assertTrue(LocalWebHost.isSafeLocalPath("app-a", "index.html"))
        assertTrue(LocalWebHost.isSafeLocalPath("app-a", "assets/icon.svg"))
        assertFalse(LocalWebHost.isSafeLocalPath("app-a", "../app-b/index.html"))
        assertFalse(LocalWebHost.isSafeLocalPath("app-a", "assets/../../app-b/index.html"))
        assertFalse(LocalWebHost.isSafeLocalPath("app/a", "index.html"))
    }
}
