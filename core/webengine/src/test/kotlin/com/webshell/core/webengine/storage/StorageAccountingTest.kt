package com.webshell.core.webengine.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAccountingTest {

    private fun entries(vararg pairs: Pair<String, Long>) =
        pairs.map { (path, size) -> FileEntry(path, size) }

    private fun emptySites() = emptyList<SiteStorageStats>()

    private fun overview(
        buckets: OverviewBuckets,
        sites: List<SiteStorageStats> = emptySites(),
        capabilities: SiteProbeCapabilities = SiteProbeCapabilities(deleteForSite = false),
    ) = computeOverview(
        buckets = buckets,
        sites = sites,
        multiProfile = true,
        scannedAt = 123L,
        probeCapabilities = capabilities,
    )

    @Test
    fun `empty buckets yield zero exclusive totals`() {
        val result = overview(
            OverviewBuckets(
                legacyProfiles = emptyList(),
                defaultCache = emptyList(),
                defaultRest = emptyList(),
                appDirs = emptyList(),
                imageCache = emptyList(),
                cacheDirRest = emptyList(),
            ),
        )
        assertEquals(0L, result.clearableBytes)
        assertEquals(0L, result.siteDataBytes)
        assertEquals(0L, result.appBytes)
        assertEquals(0L, result.unattributableSharedBytes)
        assertTrue(result.unmeasurableSiteIds.isEmpty())
    }

    @Test
    fun `legacy Profile 1 bytes land in appBytes`() {
        val result = overview(
            OverviewBuckets(
                legacyProfiles = listOf(entries("Cache/x" to 100L, "Cookies" to 50L)),
                defaultCache = emptyList(),
                defaultRest = emptyList(),
                appDirs = emptyList(),
                imageCache = emptyList(),
                cacheDirRest = emptyList(),
            ),
        )
        assertEquals(150L, result.appBytes)
        assertEquals(0L, result.clearableBytes)
        assertEquals(0L, result.siteDataBytes)
    }

    @Test
    fun `default profile cache is clearable and rest is shared site data`() {
        val result = overview(
            OverviewBuckets(
                legacyProfiles = emptyList(),
                defaultCache = entries("Cache/a" to 40L, "GPUCache/b" to 10L),
                defaultRest = entries("Cookies" to 8L, "IndexedDB/x" to 2L),
                appDirs = emptyList(),
                imageCache = emptyList(),
                cacheDirRest = emptyList(),
            ),
        )
        assertEquals(50L, result.clearableBytes)
        assertEquals(10L, result.siteDataBytes)
        assertEquals(10L, result.sharedSiteDataBytes)
        assertEquals(10L, result.unattributableSharedBytes)
        assertEquals(0L, result.appBytes)
    }

    @Test
    fun `image cache is clearable and cacheDir rest is appBytes`() {
        val result = overview(
            OverviewBuckets(
                legacyProfiles = emptyList(),
                defaultCache = emptyList(),
                defaultRest = emptyList(),
                appDirs = entries("databases/webshell.db" to 64L),
                imageCache = entries("image_cache/a.jpg" to 30L),
                cacheDirRest = entries("logs/app.log" to 4L),
            ),
        )
        assertEquals(30L, result.clearableBytes)
        assertEquals(68L, result.appBytes)
    }

    @Test
    fun `buckets are mutually exclusive and unattributable is conserved`() {
        val sites = attributeSites(
            inputs = listOf(SiteScanInput("app-a", "https://example.com/", "example.com", false)),
            indexedDbByHost = mapOf("example.com" to 6L),
            cookiesBySiteKey = mapOf("example.com" to Metric.Absent),
            quotaByHost = emptyMap(),
            localImportByAppId = mapOf("app-a" to 0L),
        )
        val buckets = OverviewBuckets(
            legacyProfiles = listOf(entries("Cache/y" to 20L)),
            defaultCache = entries("Cache/z" to 30L),
            defaultRest = entries("Cookies" to 4L, "IndexedDB/https_example.com_0.indexeddb.leveldb/x" to 6L),
            appDirs = entries("files/icons/i.png" to 8L),
            imageCache = entries("image_cache/c" to 12L),
            cacheDirRest = entries("logs/l" to 2L),
        )
        val result = overview(buckets, sites)
        val everything = 20 + 30 + 4 + 6 + 8 + 12 + 2L
        assertEquals(everything, result.clearableBytes + result.siteDataBytes + result.appBytes)
        assertEquals(42L, result.clearableBytes)
        assertEquals(10L, result.siteDataBytes)
        assertEquals(4L, result.unattributableSharedBytes)
        assertEquals(10L, result.sharedSiteDataBytes)
        assertEquals(30L, result.appBytes)
        assertEquals(result.siteDataBytes, result.unattributableSharedBytes + attributedIndexedDbBytes(sites))
    }

    @Test
    fun `unavailable quota site is not listed as unmeasurable if other metrics exist`() {
        val sites = attributeSites(
            inputs = listOf(
                SiteScanInput("app-a", "https://a.example/", "a.example", false),
                SiteScanInput("app-b", "https://b.example/", "b.example", false),
            ),
            indexedDbByHost = mapOf("a.example" to 10L),
            cookiesBySiteKey = mapOf(
                "a.example" to Metric.Absent,
                "b.example" to Metric.Unavailable,
            ),
            quotaByHost = null,
            localImportByAppId = mapOf("app-a" to 0L, "app-b" to 0L),
        )
        val result = computeOverview(
            OverviewBuckets(
                legacyProfiles = emptyList(),
                defaultCache = emptyList(),
                defaultRest = emptyList(),
                appDirs = emptyList(),
                imageCache = emptyList(),
                cacheDirRest = emptyList(),
            ),
            sites = sites,
            multiProfile = true,
            scannedAt = 123L,
            probeCapabilities = SiteProbeCapabilities(deleteForSite = true),
        )
        assertEquals(emptyList<String>(), result.unmeasurableSiteIds)
        assertEquals(SiteProbeCapabilities(deleteForSite = true), result.probeCapabilities)
        assertEquals(Metric.Unavailable, sites[1].quotaUsageBytes)
        assertEquals(Metric.Unavailable, sites[1].cookie)
    }

    @Test
    fun `multiProfile flag and timestamp propagate to overview`() {
        val result = computeOverview(
            OverviewBuckets(
                legacyProfiles = emptyList(),
                defaultCache = emptyList(),
                defaultRest = emptyList(),
                appDirs = emptyList(),
                imageCache = emptyList(),
                cacheDirRest = emptyList(),
            ),
            sites = emptyList(),
            multiProfile = false,
            scannedAt = 999L,
            probeCapabilities = SiteProbeCapabilities(deleteForSite = false),
        )
        assertFalse(result.multiProfile)
        assertEquals(999L, result.scannedAt)
    }

    @Test
    fun `system stats pass through to overview`() {
        val result = computeOverview(
            OverviewBuckets(
                legacyProfiles = emptyList(),
                defaultCache = emptyList(),
                defaultRest = emptyList(),
                appDirs = emptyList(),
                imageCache = emptyList(),
                cacheDirRest = emptyList(),
            ),
            sites = emptyList(),
            multiProfile = true,
            scannedAt = 123L,
            probeCapabilities = SiteProbeCapabilities(deleteForSite = false),
            systemTotalBytes = 500L,
            systemCacheBytes = 120L,
        )
        assertEquals(500L, result.systemTotalBytes)
        assertEquals(120L, result.systemCacheBytes)
        assertEquals(120L, result.clearableBytes)
        assertEquals(380L, result.siteDataBytes)
        assertEquals(380L, result.sharedSiteDataBytes)
        assertEquals(380L, result.unattributableSharedBytes)
    }

    @Test
    fun `system total of zero does not replace walked buckets`() {
        val result = computeOverview(
            OverviewBuckets(
                legacyProfiles = emptyList(),
                defaultCache = entries("Cache/a" to 40L),
                defaultRest = entries("IndexedDB/x" to 12L),
                appDirs = entries("databases/webshell.db" to 8L),
                imageCache = entries("WebView/Default/HTTP Cache/index" to 64L),
                cacheDirRest = emptyList(),
            ),
            sites = emptyList(),
            multiProfile = false,
            scannedAt = 123L,
            probeCapabilities = SiteProbeCapabilities(deleteForSite = false),
            systemTotalBytes = 0L,
            systemCacheBytes = 0L,
        )
        assertEquals(104L, result.clearableBytes)
        assertEquals(12L, result.siteDataBytes)
        assertEquals(8L, result.appBytes)
        assertEquals(0L, result.systemTotalBytes)
    }

    @Test
    fun `absent system stats behave as before`() {
        val buckets = OverviewBuckets(
            legacyProfiles = emptyList(),
            defaultCache = entries("Cache/z" to 30L),
            defaultRest = entries("IndexedDB/https_example.com_0.indexeddb.leveldb/x" to 6L),
            appDirs = entries("databases/webshell.db" to 8L),
            imageCache = emptyList(),
            cacheDirRest = emptyList(),
        )
        val sites = attributeSites(
            inputs = listOf(SiteScanInput("app-a", "https://example.com/", "example.com", false)),
            indexedDbByHost = mapOf("example.com" to 6L),
            cookiesBySiteKey = mapOf("example.com" to Metric.Absent),
            quotaByHost = emptyMap(),
            localImportByAppId = mapOf("app-a" to 0L),
        )
        val result = computeOverview(
            buckets,
            sites,
            multiProfile = true,
            scannedAt = 123L,
            probeCapabilities = SiteProbeCapabilities(deleteForSite = false),
        )
        assertNull(result.systemTotalBytes)
        assertNull(result.systemCacheBytes)
        assertEquals(30L, result.clearableBytes)
        assertEquals(6L, result.siteDataBytes)
        assertEquals(8L, result.appBytes)
        assertEquals(0L, result.unattributableSharedBytes)
    }
}
