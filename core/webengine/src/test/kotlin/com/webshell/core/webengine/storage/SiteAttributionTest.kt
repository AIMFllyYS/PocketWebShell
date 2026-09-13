package com.webshell.core.webengine.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteAttributionTest {

    private val www = SiteScanInput("app-www", "https://www.example.com/", "www.example.com", isLocal = false)
    private val bare = SiteScanInput("app-bare", "https://example.com/", "example.com", isLocal = false)

    @Test
    fun `www and bare host share cookie indexedDb and quota evidence`() {
        val sites = attributeSites(
            inputs = listOf(www, bare),
            indexedDbByHost = mapOf("www.example.com" to 40L, "example.com" to 10L),
            cookiesBySiteKey = mapOf(
                "example.com" to Metric.Measured(CookieFacts(3, 12), exact = false),
            ),
            quotaByHost = mapOf("www.example.com" to 7L),
            localImportByAppId = mapOf("app-www" to 0L, "app-bare" to 0L),
        )
        assertEquals(2, sites.size)
        assertEquals("example.com", sites[0].siteKey)
        assertEquals(sites[0].siteKey, sites[1].siteKey)
        assertEquals(sites[0].cookie, sites[1].cookie)
        assertEquals(sites[0].indexedDbBytes, sites[1].indexedDbBytes)
        assertEquals(sites[0].quotaUsageBytes, sites[1].quotaUsageBytes)
        assertEquals(Metric.Measured(50L, exact = true), sites[0].indexedDbBytes)
        assertEquals(Metric.Measured(7L, exact = false), sites[0].quotaUsageBytes)
        assertTrue(sites[0].signedIn)
        assertTrue(sites[1].signedIn)
    }

    @Test
    fun `quota null is Unavailable not zero`() {
        val sites = attributeSites(
            inputs = listOf(www),
            indexedDbByHost = emptyMap(),
            cookiesBySiteKey = mapOf("example.com" to Metric.Absent),
            quotaByHost = null,
            localImportByAppId = mapOf("app-www" to 0L),
        )
        assertEquals(Metric.Unavailable, sites.single().quotaUsageBytes)
        assertEquals(0L, sites.single().attributableBytes)
    }

    @Test
    fun `unattributable is non-negative and disk attributable does not exceed default rest`() {
        val sites = attributeSites(
            inputs = listOf(www, bare),
            indexedDbByHost = mapOf("www.example.com" to 40L, "example.com" to 10L),
            cookiesBySiteKey = mapOf("example.com" to Metric.Absent),
            quotaByHost = emptyMap(),
            localImportByAppId = emptyMap(),
        )
        val defaultRest = 80L
        val unattributable = unattributableSharedBytes(defaultRest, sites)
        assertTrue(unattributable >= 0L)
        val attributedDisk = attributedIndexedDbBytes(sites)
        assertEquals(50L, attributedDisk)
        assertTrue(attributedDisk <= defaultRest)
        assertEquals(30L, unattributable)
        assertEquals(50L, sites[0].attributableBytes)
        assertEquals(sites[0].attributableBytes, sites[1].attributableBytes)
    }

    @Test
    fun `local apps do not inherit remote cookies`() {
        val local = SiteScanInput("local-1", "local://local-1/index.html", "", isLocal = true)
        val sites = attributeSites(
            inputs = listOf(local, www),
            indexedDbByHost = mapOf("example.com" to 20L),
            cookiesBySiteKey = mapOf(
                "local:local-1" to Metric.Absent,
                "example.com" to Metric.Measured(CookieFacts(2, 8), exact = false),
            ),
            quotaByHost = mapOf("example.com" to 5L),
            localImportByAppId = mapOf("local-1" to 100L, "app-www" to 0L),
        )
        val localStats = sites.first { it.appId == "local-1" }
        assertEquals(Metric.Absent, localStats.cookie)
        assertEquals(Metric.Absent, localStats.indexedDbBytes)
        assertEquals(Metric.Absent, localStats.quotaUsageBytes)
        assertEquals(Metric.Measured(100L, exact = true), localStats.localImportBytes)
        assertEquals(100L, localStats.attributableBytes)
        assertEquals(false, localStats.signedIn)
    }
}
