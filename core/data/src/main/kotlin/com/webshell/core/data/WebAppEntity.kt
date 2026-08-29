package com.webshell.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "web_apps")
data class WebAppEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val iconUrl: String?,
    val desktopMode: Boolean,
    val darkMode: Boolean,
    val keepAlive: Boolean,
    val isFavorite: Boolean,
    val homePage: Int,
    val homeCellIndex: Int,
    val folderId: String?,
    val createdAt: Long,
    /**
     * 本地导入的 HTML 应用：url 形如 local://<appId>/index.html，
     * 文件存于 filesDir/localapps/<appId>/，由 LocalWebHost 以
     * https://appassets.androidplatform.net/local/<appId>/... 提供渲染。
     */
    val isLocal: Boolean = false,
    /** 外链（非当前站点域）在系统浏览器打开；false = 站内打开 */
    val externalLinksToBrowser: Boolean = false,
    /** 页面文本缩放百分比（80–130，100 = 不缩放） */
    val textZoomPercent: Int = 100,
)
