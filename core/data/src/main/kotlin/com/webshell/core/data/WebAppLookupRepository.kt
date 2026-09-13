package com.webshell.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Read-only launch lookup. Explicit IDs never silently fall through to another site's config. */
@Singleton
class WebAppLookupRepository @Inject constructor(private val webAppDao: WebAppDao) {
    suspend fun getById(id: String): WebAppEntity? = webAppDao.getById(id)

    suspend fun updateDesktopMode(id: String, enabled: Boolean) {
        val app = webAppDao.getById(id) ?: return
        webAppDao.upsert(app.copy(desktopMode = enabled))
    }

    suspend fun findByUrl(url: String): WebAppEntity? {
        webAppDao.getByUrl(url)?.let { return it }
        val wanted = LaunchUrlMatch.canonical(url) ?: return null
        return webAppDao.observeAll().first()
            .filter { LaunchUrlMatch.canonical(it.url) == wanted }
            .minByOrNull { it.createdAt }
    }
}
