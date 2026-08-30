package com.webshell.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "webshell_settings")

/** 主页外观与全局行为设置（DataStore Preferences） */
data class HomeSettings(
    val gridColumns: Int = 4,
    val gridRows: Int = 5,
    val iconCornerRadiusPercent: Int = 26,
    val iconSizeDp: Int = 56,
    val showLabels: Boolean = true,
    val showPageIndicator: Boolean = true,
    val keepAliveServiceEnabled: Boolean = true,
    val batteryWhitelistAcknowledged: Boolean = false,
    val themeMode: String = THEME_MODE_SYSTEM,
    val photoWallpaperPath: String? = null,
)

/** 主题模式取值，见 docs/DESIGN.md */
const val THEME_MODE_SYSTEM = "system"
const val THEME_MODE_LIGHT = "light"
const val THEME_MODE_DARK = "dark"
const val THEME_MODE_PHOTO = "photo"

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val GRID_ROWS = intPreferencesKey("grid_rows")
        val ICON_CORNER = intPreferencesKey("icon_corner_percent")
        val ICON_SIZE = intPreferencesKey("icon_size_dp")
        val SHOW_LABELS = booleanPreferencesKey("show_labels")
        val PAGE_INDICATOR = booleanPreferencesKey("page_indicator")
        val KEEP_ALIVE_SERVICE = booleanPreferencesKey("keep_alive_service")
        val BATTERY_ACK = booleanPreferencesKey("battery_whitelist_ack")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PHOTO_WALLPAPER = stringPreferencesKey("photo_wallpaper_path")
    }

    val settings: Flow<HomeSettings> = context.settingsStore.data.map { prefs ->
        HomeSettings(
            gridColumns = prefs[Keys.GRID_COLUMNS] ?: 4,
            gridRows = prefs[Keys.GRID_ROWS] ?: 5,
            iconCornerRadiusPercent = prefs[Keys.ICON_CORNER] ?: 26,
            iconSizeDp = prefs[Keys.ICON_SIZE] ?: 56,
            showLabels = prefs[Keys.SHOW_LABELS] ?: true,
            showPageIndicator = prefs[Keys.PAGE_INDICATOR] ?: true,
            keepAliveServiceEnabled = prefs[Keys.KEEP_ALIVE_SERVICE] ?: true,
            batteryWhitelistAcknowledged = prefs[Keys.BATTERY_ACK] ?: false,
            themeMode = prefs[Keys.THEME_MODE] ?: THEME_MODE_SYSTEM,
            photoWallpaperPath = prefs[Keys.PHOTO_WALLPAPER],
        )
    }

    suspend fun setGridColumns(value: Int) =
        context.settingsStore.edit { it[Keys.GRID_COLUMNS] = value.coerceIn(3, 6) }

    suspend fun setGridRows(value: Int) =
        context.settingsStore.edit { it[Keys.GRID_ROWS] = value.coerceIn(4, 7) }

    suspend fun setIconCornerRadiusPercent(value: Int) =
        context.settingsStore.edit { it[Keys.ICON_CORNER] = value.coerceIn(0, 50) }

    suspend fun setIconSizeDp(value: Int) =
        context.settingsStore.edit { it[Keys.ICON_SIZE] = value.coerceIn(44, 72) }

    suspend fun setShowLabels(value: Boolean) =
        context.settingsStore.edit { it[Keys.SHOW_LABELS] = value }

    suspend fun setShowPageIndicator(value: Boolean) =
        context.settingsStore.edit { it[Keys.PAGE_INDICATOR] = value }

    suspend fun setKeepAliveServiceEnabled(value: Boolean) =
        context.settingsStore.edit { it[Keys.KEEP_ALIVE_SERVICE] = value }

    suspend fun setBatteryWhitelistAcknowledged(value: Boolean) =
        context.settingsStore.edit { it[Keys.BATTERY_ACK] = value }

    suspend fun setThemeMode(value: String) =
        context.settingsStore.edit { it[Keys.THEME_MODE] = value }

    suspend fun setPhotoWallpaperPath(value: String?) =
        context.settingsStore.edit {
            if (value == null) it.remove(Keys.PHOTO_WALLPAPER) else it[Keys.PHOTO_WALLPAPER] = value
        }
}
