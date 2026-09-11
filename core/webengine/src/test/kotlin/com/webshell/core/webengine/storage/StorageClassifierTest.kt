package com.webshell.core.webengine.storage

import org.junit.Assert.assertEquals
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
}
