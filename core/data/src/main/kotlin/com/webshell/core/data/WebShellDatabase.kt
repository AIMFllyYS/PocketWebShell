package com.webshell.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WebAppEntity::class, HistoryEntity::class, BookmarkEntity::class, LogEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class WebShellDatabase : RoomDatabase() {
    abstract fun webAppDao(): WebAppDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun logDao(): LogDao

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
    }
}
