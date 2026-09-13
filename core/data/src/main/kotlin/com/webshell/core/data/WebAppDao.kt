package com.webshell.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WebAppDao {

    @Query("SELECT * FROM web_apps ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<WebAppEntity>>

    @Query("SELECT * FROM web_apps WHERE id = :id")
    suspend fun getById(id: String): WebAppEntity?

    @Query("SELECT * FROM web_apps WHERE url = :url ORDER BY createdAt ASC LIMIT 1")
    suspend fun getByUrl(url: String): WebAppEntity?

    @Query("SELECT * FROM web_apps WHERE isLocal = 1 AND importSourceKey = :key ORDER BY createdAt ASC LIMIT 1")
    suspend fun findLocalBySourceKey(key: String): WebAppEntity?

    @Upsert
    suspend fun upsert(app: WebAppEntity)

    @Upsert
    suspend fun upsertAll(apps: List<WebAppEntity>)

    @Query("DELETE FROM web_apps WHERE id = :id")
    suspend fun deleteById(id: String)
}
