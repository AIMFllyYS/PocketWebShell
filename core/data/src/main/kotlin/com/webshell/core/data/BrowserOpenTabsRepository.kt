package com.webshell.core.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowserOpenTabsRepository @Inject constructor(
    private val dao: BrowserOpenTabDao,
) {
    suspend fun load(): List<BrowserOpenTabEntity> = dao.loadAll()

    suspend fun replaceAll(rows: List<BrowserOpenTabEntity>) = dao.replaceAll(rows)

    suspend fun clear() = dao.deleteAll()
}
