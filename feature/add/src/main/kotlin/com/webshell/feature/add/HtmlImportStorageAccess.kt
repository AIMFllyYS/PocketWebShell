package com.webshell.feature.add

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * Storage access for the in-app HTML picker only.
 * Do not reuse [com.webshell.app.download.DownloadStorageAccess]: downloads never need All-files.
 */
object HtmlImportStorageAccess {
    const val LEGACY_READ_MAX_SDK = 32

    fun runtimeReadPermission(sdkInt: Int = Build.VERSION.SDK_INT): String? =
        if (sdkInt in 29..LEGACY_READ_MAX_SDK) Manifest.permission.READ_EXTERNAL_STORAGE else null

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    fun hasLegacyBroadRead(sdk: Int, readGranted: Boolean): Boolean = sdk == 29 && readGranted

    /**
     * Public shared HTML is non-media. On API 30+ only All-files makes
     * `File.listFiles`/`isFile` reliable for other apps' downloads; READ alone
     * can still show folder names while HTML files look empty.
     */
    fun canAttemptPublicListing(sdk: Int, readGranted: Boolean, allFilesAccess: Boolean): Boolean =
        allFilesAccess || hasLegacyBroadRead(sdk, readGranted)

    /** MANAGE_APP_ALL_FILES → MANAGE_ALL_FILES → APPLICATION_DETAILS. */
    fun manageAccessIntent(packageName: String): List<Intent> {
        val pkg = Uri.parse("package:$packageName")
        return listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).setData(pkg),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(pkg),
        )
    }
}
