package com.webshell.core.data

import android.content.Context
import coil3.imageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Clear the active loader, rather than unlinking a directory it still has open. */
@Singleton
class ImageCacheRepository @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun clear() = withContext(Dispatchers.IO) {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
    }
}
