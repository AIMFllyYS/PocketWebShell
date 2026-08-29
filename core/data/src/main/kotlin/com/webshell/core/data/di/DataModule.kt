package com.webshell.core.data.di

import android.content.Context
import androidx.room.Room
import com.webshell.core.data.BookmarkDao
import com.webshell.core.data.HistoryDao
import com.webshell.core.data.WebAppDao
import com.webshell.core.data.WebShellDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WebShellDatabase =
        Room.databaseBuilder(context, WebShellDatabase::class.java, WebShellDatabase.NAME)
            // v1 → v2：新增 isLocal/externalLinksToBrowser/textZoomPercent 三列，
            // 里程碑阶段无迁移需求，直接重建库。
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideWebAppDao(db: WebShellDatabase): WebAppDao = db.webAppDao()

    @Provides
    fun provideHistoryDao(db: WebShellDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideBookmarkDao(db: WebShellDatabase): BookmarkDao = db.bookmarkDao()
}
