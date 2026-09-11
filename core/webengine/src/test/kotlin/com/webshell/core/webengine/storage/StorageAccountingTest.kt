package com.webshell.core.webengine.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAccountingTest {

    private fun entries(vararg pairs: Pair<String, Long>) =
        pairs.map { (path, size) -> FileEntry(path, size) }

    private fun overview(buckets: OverviewBuckets, appIds: List<String> = listOf("app-a")) =
        computeOverview(buckets, appIds, multiProfile = true, scannedAt = 123L)

    @Test
    fun `unmeasurable site has zero bytes and is listed`() {
        val stats = computeSiteStats("app-a", null)
        assertFalse(stats.measurable)
        assertEquals(0L, stats.totalBytes)
    }

    @Test
    fun `site stats split cache from site data`() {
        val stats = computeSiteStats(
            "app-a",
            entries(
                "Cache/index.txt" to 10L,
                "Code Cache/js/x" to 20L,
                "GPUCache/g" to 5L,
                "Cookies" to 7L,
                "Local Storage/leveldb/x" to 3L,
            ),
        )
        assertTrue(stats.measurable)
        assertEquals(35L, stats.clearableBytes)
        assertEquals(10L, stats.siteDataBytes)
        assertEquals(45L, stats.totalBytes)
    }

    @Test
    fun `absent profile dir is measurable with zero bytes`() {
        val result = overview(
            OverviewBuckets(
                siteProfiles = emptyMap(),
                orphanProfiles = emptyList(),
                defaultCache = emptyList(),
                defaultRest = emptyList(),
                appDirs = emptyList(),
                imageCache = emptyList(),
                cacheDirRest = emptyList(),
            ),
        )
        val stats = result.sites.single()
        assertTrue(stats.measurable)
        assertEquals(0L, stats.totalBytes)
        assertTrue(result.unmeasurableSiteIds.isEmpty())
    }

    @Test
    fun `orphan profile bytes land in appBytes`() {
        val result = overview(
            OverviewBuckets(
                siteProfiles = emptyMap(),
                orphanProfiles = listOf(entries("Cache/x" to 100L, "Cookies" to 50L)),
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
                siteProfiles = emptyMap(),
                orphanProfiles = emptyList(),
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
        assertEquals(0L, result.appBytes)
    }

    @Test
    fun `image cache is clearable and cacheDir rest is appBytes`() {
        val result = overview(
            OverviewBuckets(
                siteProfiles = emptyMap(),
                orphanProfiles = emptyList(),
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
    fun `buckets are mutually exclusive with no double counting`() {
        val buckets = OverviewBuckets(
            siteProfiles = mapOf(
                "app-a" to entries("Cache/x" to 10L, "Cookies" to 6L),
            ),
            orphanProfiles = listOf(entries("Cache/y" to 20L)),
            defaultCache = entries("Cache/z" to 30L),
            defaultRest = entries("Cookies" to 4L),
            appDirs = entries("files/icons/i.png" to 8L),
            imageCache = entries("image_cache/c" to 12L),
            cacheDirRest = entries("logs/l" to 2L),
        )
        val result = overview(buckets)
        val everything = 10 + 6 + 20 + 30 + 4 + 8 + 12 + 2L
        assertEquals(everything, result.clearableBytes + result.siteDataBytes + result.appBytes)
        assertEquals(52L, result.clearableBytes) // 10 site + 30 default + 12 image cache
        assertEquals(10L, result.siteDataBytes) // 6 per-site + 4 shared default rest
        assertEquals(4L, result.sharedSiteDataBytes)
        assertEquals(30L, result.appBytes) // 20 orphan + 8 files + 2 logs
    }

    @Test
    fun `unmeasurable site is excluded from sums and propagated`() {
        val result = computeOverview(
            OverviewBuckets(
                siteProfiles = mapOf(
                    "app-a" to entries("Cache/x" to 10L),
                    "app-b" to null,
                ),
                orphanProfiles = emptyList(),
                defaultCache = emptyList(),
                defaultRest = emptyList(),
                appDirs = emptyList(),
                imageCache = emptyList(),
                cacheDirRest = emptyList(),
            ),
            appIds = listOf("app-a", "app-b"),
            multiProfile = true,
            scannedAt = 123L,
        )
        assertEquals(listOf("app-b"), result.unmeasurableSiteIds)
        assertEquals(10L, result.clearableBytes)
        assertEquals(0L, result.siteDataBytes)
    }

    @Test
    fun `multiProfile flag and timestamp propagate to overview`() {
        val result = computeOverview(
            OverviewBuckets(
                siteProfiles = emptyMap(),
                orphanProfiles = emptyList(),
                defaultCache = emptyList(),
                defaultRest = emptyList(),
                appDirs = emptyList(),
                imageCache = emptyList(),
                cacheDirRest = emptyList(),
            ),
            appIds = emptyList(),
            multiProfile = false,
            scannedAt = 999L,
        )
        assertFalse(result.multiProfile)
        assertEquals(999L, result.scannedAt)
    }

    @Test
    fun `system stats pass through to overview`() {
        val result = computeOverview(
            OverviewBuckets(
                siteProfiles = emptyMap(),
                orphanProfiles = emptyList(),
                defaultCache = emptyList(),
                defaultRest = emptyList(),
                appDirs = emptyList(),
                imageCache = emptyList(),
                cacheDirRest = emptyList(),
            ),
            appIds = emptyList(),
            multiProfile = true,
            scannedAt = 123L,
            systemTotalBytes = 500L,
            systemCacheBytes = 120L,
        )
        assertEquals(500L, result.systemTotalBytes)
        assertEquals(120L, result.systemCacheBytes)
        assertEquals(120L, result.clearableBytes)
        assertEquals(380L, result.siteDataBytes)
        assertEquals(380L, result.sharedSiteDataBytes)
    }

    @Test
    fun `system total of zero does not replace walked buckets`() {
        val result = computeOverview(
            OverviewBuckets(
                siteProfiles = emptyMap(),
                orphanProfiles = emptyList(),
                defaultCache = entries("Cache/a" to 40L),
                defaultRest = entries("IndexedDB/x" to 12L),
                appDirs = entries("databases/webshell.db" to 8L),
                imageCache = entries("WebView/Default/HTTP Cache/index" to 64L),
                cacheDirRest = emptyList(),
            ),
            appIds = emptyList(),
            multiProfile = false,
            scannedAt = 123L,
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
            siteProfiles = mapOf("app-a" to entries("Cache/x" to 10L, "Cookies" to 6L)),
            orphanProfiles = emptyList(),
            defaultCache = entries("Cache/z" to 30L),
            defaultRest = emptyList(),
            appDirs = entries("databases/webshell.db" to 8L),
            imageCache = emptyList(),
            cacheDirRest = emptyList(),
        )
        val result = computeOverview(buckets, listOf("app-a"), multiProfile = true, scannedAt = 123L)
        assertNull(result.systemTotalBytes)
        assertNull(result.systemCacheBytes)
        assertEquals(40L, result.clearableBytes)
        assertEquals(6L, result.siteDataBytes)
        assertEquals(8L, result.appBytes)
    }
}
