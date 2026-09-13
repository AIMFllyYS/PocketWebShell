package com.webshell.core.webengine.storage

import androidx.webkit.WebViewFeature
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteDataEraserContractTest {

    @Test
    fun `unsupported feature deletes nothing`() = runBlocking {
        val events = mutableListOf<String>()
        val outcome = eraseSiteInternal(
            siteOrUrl = "https://example.com/",
            features = WebViewFeatureGate { false },
            sessions = SiteSessionGateway(
                running = {
                    events += "running"
                    listOf("session-1")
                },
                destroy = {
                    events += "destroy"
                    it
                },
            ),
            browsingData = object : SiteBrowsingDataGateway {
                override suspend fun deleteBrowsingDataForSite(siteOrUrl: String): String {
                    events += "delete"
                    return "example.com"
                }

                override fun flushCookies() {
                    events += "flush"
                }
            },
            remainingCookies = RemainingCookieProbe { 0 },
            invalidate = { events += "invalidate" },
        )
        assertEquals(EraseSiteOutcome.Unsupported, outcome)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `destroys running sessions before deleting`() = runBlocking {
        val events = mutableListOf<String>()
        val outcome = eraseSiteInternal(
            siteOrUrl = "https://www.example.com/",
            features = WebViewFeatureGate { it == WebViewFeature.DELETE_BROWSING_DATA },
            sessions = SiteSessionGateway(
                running = { listOf("session-a", "session-b") },
                destroy = { ids ->
                    events += "destroy:${ids.joinToString()}"
                    ids
                },
            ),
            browsingData = object : SiteBrowsingDataGateway {
                override suspend fun deleteBrowsingDataForSite(siteOrUrl: String): String {
                    events += "delete:$siteOrUrl"
                    return "example.com"
                }

                override fun flushCookies() {
                    events += "flush"
                }
            },
            remainingCookies = RemainingCookieProbe { events += "recheck"; 0 },
            invalidate = { events += "invalidate" },
        )
        assertEquals(EraseSiteOutcome.Done(deletedDomain = "example.com", cookiesRemaining = 0), outcome)
        assertEquals(
            listOf(
                "destroy:session-a, session-b",
                "delete:https://www.example.com/",
                "flush",
                "recheck",
                "invalidate",
            ),
            events,
        )
    }
}
