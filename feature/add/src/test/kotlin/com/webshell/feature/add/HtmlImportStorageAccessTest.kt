package com.webshell.feature.add

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlImportStorageAccessTest {
    @Test
    fun runtimeReadPermissionIsReadOnApi29And32() {
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, HtmlImportStorageAccess.runtimeReadPermission(29))
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, HtmlImportStorageAccess.runtimeReadPermission(32))
    }

    @Test
    fun runtimeReadPermissionIsNullOnApi33And36() {
        assertNull(HtmlImportStorageAccess.runtimeReadPermission(33))
        assertNull(HtmlImportStorageAccess.runtimeReadPermission(36))
    }

    @Test
    fun legacyBroadReadOnlyWhenApi29AndGranted() {
        assertTrue(HtmlImportStorageAccess.hasLegacyBroadRead(29, readGranted = true))
        assertFalse(HtmlImportStorageAccess.hasLegacyBroadRead(29, readGranted = false))
        assertFalse(HtmlImportStorageAccess.hasLegacyBroadRead(30, readGranted = true))
        assertFalse(HtmlImportStorageAccess.hasLegacyBroadRead(32, readGranted = true))
        assertFalse(HtmlImportStorageAccess.hasLegacyBroadRead(33, readGranted = true))
    }

    @Test
    fun publicListingNeedsAllFilesOnApi30Plus() {
        assertTrue(HtmlImportStorageAccess.canAttemptPublicListing(29, readGranted = true, allFilesAccess = false))
        assertFalse(HtmlImportStorageAccess.canAttemptPublicListing(29, readGranted = false, allFilesAccess = false))
        assertFalse(HtmlImportStorageAccess.canAttemptPublicListing(30, readGranted = true, allFilesAccess = false))
        assertFalse(HtmlImportStorageAccess.canAttemptPublicListing(32, readGranted = true, allFilesAccess = false))
        assertFalse(HtmlImportStorageAccess.canAttemptPublicListing(33, readGranted = false, allFilesAccess = false))
        assertTrue(HtmlImportStorageAccess.canAttemptPublicListing(32, readGranted = false, allFilesAccess = true))
        assertTrue(HtmlImportStorageAccess.canAttemptPublicListing(33, readGranted = false, allFilesAccess = true))
    }
}
