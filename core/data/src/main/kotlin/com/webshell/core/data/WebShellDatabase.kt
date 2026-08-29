package com.webshell.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WebAppEntity::class, HistoryEntity::class, BookmarkEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class WebShellDatabase : RoomDatabase() {
    abstract fun webAppDao(): WebAppDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        const val NAME = "webshell.db"
    }
}
