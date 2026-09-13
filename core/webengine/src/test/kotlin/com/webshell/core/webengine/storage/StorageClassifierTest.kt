package com.webshell.core.webengine.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageClassifierTest {

    @Test
    fun `Cache top-level dir is clearable`() {
        assertEquals(
            StorageCategory.CLEARABLE_CACHE,
            StorageClassifier.classifyProfileEntry("Cache/index.txt"),
        )
    }

    @Test
    fun `Code Cache nested path is clearable`() {
        assertEquals(
            StorageCategory.CLEARABLE_CACHE,
            StorageClassifier.classifyProfileEntry("Code Cache/js/x"),
        )
    }

    @Test
    fun `GPUCache top-level dir is clearable`() {
        assertEquals(
            StorageCategory.CLEARABLE_CACHE,
            StorageClassifier.classifyProfileEntry("GPUCache/data_0"),
        )
    }

    @Test
    fun `bare cache dir name is clearable`() {
        assertEquals(
            StorageCategory.CLEARABLE_CACHE,
            StorageClassifier.classifyProfileEntry("Cache"),
        )
    }

    @Test
    fun `Cookies is site data`() {
        assertEquals(
            StorageCategory.SITE_DATA,
            StorageClassifier.classifyProfileEntry("Cookies"),
        )
    }

    @Test
    fun `Local Storage nested path is site data`() {
        assertEquals(
            StorageCategory.SITE_DATA,
            StorageClassifier.classifyProfileEntry("Local Storage/leveldb/x"),
        )
    }

    @Test
    fun `deep non-cache path is site data`() {
        assertEquals(
            StorageCategory.SITE_DATA,
            StorageClassifier.classifyProfileEntry("IndexedDB/https_example.com_0.indexeddb.leveldb/000003.log"),
        )
    }

    @Test
    fun `modern shader caches are clearable`() {
        assertEquals(
            StorageCategory.CLEARABLE_CACHE,
            StorageClassifier.classifyProfileEntry("GrShaderCache/data_0"),
        )
        assertEquals(
            StorageCategory.CLEARABLE_CACHE,
            StorageClassifier.classifyProfileEntry("DawnWebGPUCache/index"),
        )
    }

    @Test
    fun `modern site data dirs stay site data`() {
        assertEquals(
            StorageCategory.SITE_DATA,
            StorageClassifier.classifyProfileEntry("Network/Cookies"),
        )
        assertEquals(
            StorageCategory.SITE_DATA,
            StorageClassifier.classifyProfileEntry("Shared Storage/leveldb/LOG"),
        )
        assertEquals(
            StorageCategory.SITE_DATA,
            StorageClassifier.classifyProfileEntry("Storage/ext/x"),
        )
    }

    @Test
    fun `Service Worker path is site data`() {
        assertEquals(
            StorageCategory.SITE_DATA,
            StorageClassifier.classifyProfileEntry("Service Worker/CacheStorage/abc/index.txt"),
        )
    }

    @Test
    fun `lowercase cache is site data because matching is case-sensitive`() {
        assertEquals(
            StorageCategory.SITE_DATA,
            StorageClassifier.classifyProfileEntry("cache/index.txt"),
        )
    }

    @Test
    fun `unknown top-level is site data and marked unknown`() {
        assertEquals(
            StorageCategory.SITE_DATA,
            StorageClassifier.classifyProfileEntry("BrandNewStore/index"),
        )
        assertTrue(StorageClassifier.isUnknownProfileTopLevel("BrandNewStore/index"))
        assertFalse(StorageClassifier.isUnknownProfileTopLevel("IndexedDB/https_example.com_0.indexeddb.leveldb"))
        assertFalse(StorageClassifier.isUnknownProfileTopLevel("Cache/index.txt"))
    }

    @Test
    fun `cache constants have a single source`() {
        assertEquals(
            listOf(
                "Cache",
                "Code Cache",
                "GPUCache",
                "GrShaderCache",
                "ShaderCache",
                "DawnGraphiteCache",
                "DawnWebGPUCache",
                "GraphiteDawnCache",
            ),
            StorageClassifier.CACHE_DIR_NAMES,
        )
        assertTrue("WebView" in StorageClassifier.WEBVIEW_CACHE_DIR_NAMES)
        assertTrue("IndexedDB" in StorageClassifier.SITE_DATA_DIR_NAMES)
        assertTrue(StorageClassifier.isLegacyProfileDirName("Profile 1"))
        assertTrue(StorageClassifier.isLegacyProfileDirName("Profile 12"))
        assertFalse(StorageClassifier.isLegacyProfileDirName("profiles"))
        assertFalse(StorageClassifier.isLegacyProfileDirName("Default"))
    }
}
