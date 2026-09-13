package com.webshell.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "browser_open_tabs")
data class BrowserOpenTabEntity(
    @PrimaryKey val tabId: String,
    val sessionId: String,
    val url: String,
    val title: String,
    val position: Int,
    val desktopMode: Boolean,
    val restoreStartUrlIfBlank: Boolean,
    val kind: String,
    val displayPath: String?,
    val localAppId: String?,
    val updatedAt: Long,
    val sourceKey: String? = null,
)
