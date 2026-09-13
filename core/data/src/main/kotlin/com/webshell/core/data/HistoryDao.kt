package com.webshell.core.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM history WHERE title LIKE :pattern OR url LIKE :pattern " +
            "ORDER BY visitedAt DESC LIMIT :limit",
    )
    fun observeSearch(pattern: String, limit: Int): Flow<List<HistoryEntity>>

    @Query("UPDATE history SET iconUrl = :iconUrl WHERE url = :url")
    suspend fun updateIcon(url: String, iconUrl: String)

    /** 相同 URL 的近期记录合并为一条最新记录，避免历史列表刷屏 */
    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @androidx.room.Insert
    suspend fun insert(entity: HistoryEntity): Long

    @androidx.room.Insert
    suspend fun insertAll(entities: List<HistoryEntity>)
}
