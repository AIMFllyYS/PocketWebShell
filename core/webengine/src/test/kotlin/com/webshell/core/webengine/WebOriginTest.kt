package com.webshell.core.webengine

import org.junit.Assert.*
import org.junit.Test

class WebOriginTest {
    @Test fun equivalentPortsAndCaseShareAnOrigin() {
        assertTrue(sameWebOrigin("https://EXAMPLE.com/a", "https://example.com:443/b"))
        assertTrue(sameWebOrigin("http://example.com/a", "http://example.com:80/b"))
    }
    @Test fun offlineResetCannotFollowAnAuthenticationRedirectToAnotherOrigin() {
        assertFalse(sameWebOrigin("https://example.com", "https://login.example.com"))
        assertFalse(sameWebOrigin("https://example.com", "https://example.com.attacker.invalid"))
        assertFalse(sameWebOrigin("https://example.com", "http://example.com"))
        assertFalse(sameWebOrigin("https://example.com", "https://example.com:8443"))
        assertFalse(sameWebOrigin("https://example.com", "data:text/html,example.com"))
    }
}
