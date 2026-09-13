package com.webshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchUrlMatchTest {
    @Test
    fun trailingSlashHostCaseAndWwwAreEquivalent() {
        assertTrue(LaunchUrlMatch.matches("https://www.Example.com/path/", "https://example.com/path"))
        assertTrue(LaunchUrlMatch.matches("https://example.com", "https://example.com/"))
        assertEquals(
            LaunchUrlMatch.canonical("https://WWW.Example.com/a/"),
            LaunchUrlMatch.canonical("https://example.com/a"),
        )
    }

    @Test
    fun queryAndDifferentHostsDoNotMatch() {
        assertFalse(LaunchUrlMatch.matches("https://example.com/a", "https://example.com/b"))
        assertFalse(LaunchUrlMatch.matches("https://example.com/a?x=1", "https://example.com/a"))
        assertFalse(LaunchUrlMatch.matches("https://one.test", "https://two.test"))
    }

    @Test
    fun localAppUrlsKeepTheHostId() {
        assertTrue(
            LaunchUrlMatch.matches(
                "local://app-abcd/index.html",
                "local://app-abcd/index.html",
            ),
        )
        assertFalse(
            LaunchUrlMatch.matches(
                "local://app-abcd/index.html",
                "local://app-efgh/index.html",
            ),
        )
    }
}
