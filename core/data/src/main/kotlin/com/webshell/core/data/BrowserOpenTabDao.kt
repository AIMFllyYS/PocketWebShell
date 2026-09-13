package com.webshell.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface BrowserOpenTabDao {
    @Query("SELECT * FROM browser_open_tabs ORDER BY position ASC")
    suspend fun loadAll(): List<BrowserOpenTabEntity>

    @Query("DELETE FROM browser_open_tabs")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertAll(rows: List<BrowserOpenTabEntity>)

    @Transaction
    suspend fun replaceAll(rows: List<BrowserOpenTabEntity>) {
        deleteAll()
        if (rows.isNotEmpty()) upsertAll(rows)
    }
}
