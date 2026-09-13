package com.webshell.app.incoming

import com.webshell.feature.browser.IncomingDocumentAccess
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IncomingDocumentModule {
    @Binds
    @Singleton
    abstract fun bindIncomingDocumentAccess(impl: IncomingBrowserOpener): IncomingDocumentAccess
}
