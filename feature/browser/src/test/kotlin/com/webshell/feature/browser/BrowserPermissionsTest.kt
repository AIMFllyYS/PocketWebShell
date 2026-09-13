package com.webshell.feature.browser

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPermissionsTest {
    @Test fun webkitNamesMapToOnlyKnownRuntimePermissions() {
        assertEquals(Manifest.permission.CAMERA, permissionForWebResource("android.webkit.resource.VIDEO_CAPTURE"))
        assertEquals(Manifest.permission.RECORD_AUDIO, permissionForWebResource("android.webkit.resource.AUDIO_CAPTURE"))
        assertNull(permissionForWebResource("android.webkit.resource.PROTECTED_MEDIA_ID"))
        assertNull(permissionForWebResource("future.unknown.resource"))
    }

    @Test fun mixedUnknownResourcesStillPromptForCamera() {
        val resources = arrayOf(
            "android.webkit.resource.VIDEO_CAPTURE",
            "android.webkit.resource.PROTECTED_MEDIA_ID",
        )
        assertTrue(shouldPromptWebPermission(resources))
        assertEquals(listOf("android.webkit.resource.VIDEO_CAPTURE"), grantableWebResources(resources))
        assertEquals(listOf(Manifest.permission.CAMERA), androidPermissionsForWebResources(resources))
    }

    @Test fun unknownOnlyDoesNotPrompt() {
        assertFalse(shouldPromptWebPermission(arrayOf("android.webkit.resource.PROTECTED_MEDIA_ID")))
        assertTrue(androidPermissionsForWebResources(arrayOf("android.webkit.resource.PROTECTED_MEDIA_ID")).isEmpty())
    }

    @Test fun fileCaptureRequestsCameraAndOptionalMic() {
        assertTrue(WebFileCapture.runtimePermissions(false, arrayOf("image/*")).isEmpty())
        assertEquals(
            listOf(Manifest.permission.CAMERA),
            WebFileCapture.runtimePermissions(true, arrayOf("image/*")),
        )
        assertEquals(
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            WebFileCapture.runtimePermissions(true, arrayOf("video/*")),
        )
        assertFalse(WebFileCapture.isVideoAccept(arrayOf("image/*")))
        assertTrue(WebFileCapture.isVideoAccept(arrayOf("video/mp4")))
    }
}
