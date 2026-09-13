package com.webshell.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WebAppEntity::class,
        HistoryEntity::class,
        BookmarkEntity::class,
        LogEntity::class,
        BrowserOpenTabEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class WebShellDatabase : RoomDatabase() {
    abstract fun webAppDao(): WebAppDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun logDao(): LogDao
    abstract fun browserOpenTabDao(): BrowserOpenTabDao

    companion object {
        const val NAME = "webshell.db"

        /** v2 → v3：新增 app_log 表（应用内日志持久化），存量用户数据不动。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timeMillis` INTEGER NOT NULL, " +
                        "`level` TEXT NOT NULL, " +
                        "`tag` TEXT NOT NULL, " +
                        "`message` TEXT NOT NULL)",
                )
            }
        }

        /** v3 → v4：web_apps 新增 folderName/folderCellIndex 两列（文件夹重命名 + 文件夹内排序）。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE web_apps ADD COLUMN folderName TEXT")
                db.execSQL("ALTER TABLE web_apps ADD COLUMN folderCellIndex INTEGER")
            }
        }

        /**
         * v4 → v5：web_apps 增加站点壳新窗口策略；新增 browser_open_tabs
         * 保存浏览标签清单（不含 WebView 快照 / Cookie）。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE web_apps ADD COLUMN siteShellNewWindowPolicy TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `browser_open_tabs` (" +
                        "`tabId` TEXT NOT NULL, " +
                        "`sessionId` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`desktopMode` INTEGER NOT NULL, " +
                        "`restoreStartUrlIfBlank` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`displayPath` TEXT, " +
                        "`localAppId` TEXT, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`tabId`))",
                )
            }
        }

        /**
         * v5 → v6：本地应用与浏览标签记住规范化后的源路径，外部再次打开同一文件时复用。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE web_apps ADD COLUMN importSourceKey TEXT")
                db.execSQL("ALTER TABLE browser_open_tabs ADD COLUMN sourceKey TEXT")
            }
        }
    }
}
