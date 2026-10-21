package com.webshell.core.data

import android.graphics.Bitmap
import androidx.room.Room
import com.webshell.core.data.metadata.SiteMetadataFetcher
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class SiteIconRepositoryTest {
    private lateinit var db: WebShellDatabase
    private lateinit var icons: SiteIconRepository
    private fun app(id: String = "test", path: String? = null) = WebAppEntity(
        id, "Test", "https://example.com/", path, false, false, false, false, 0, -1, null, 1,
    )

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, WebShellDatabase::class.java).allowMainThreadQueries().build()
        val client = OkHttpClient()
        icons = SiteIconRepository(context, client, SiteMetadataFetcher(client), db.webAppDao(), db.historyDao(), db.bookmarkDao())
    }
    @After fun close() { db.close() }

    @Test fun nativeBitmapIsStoredAsADeduplicatedImmutablePng() = runBlocking {
        val image = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.BLUE)
        val first = icons.save(image)!!
        assertEquals(first, icons.save(image))
        assertTrue(File(first).isFile)
        assertEquals(listOf(137, 80, 78, 71), File(first).readBytes().take(4).map { it.toInt() and 255 })
    }

    @Test fun userChosenRemoteIconAndCrossOriginLoginAreNotOverwritten() = runBlocking {
        db.webAppDao().upsert(app(path = "https://example.com/custom.svg"))
        icons.markCustom("test")
        icons.publish("https://example.com/", "/icons/auto_new.png", "test")
        assertEquals("https://example.com/custom.svg", db.webAppDao().getById("test")!!.iconUrl)
        icons.markCustom("test", false)
        icons.publish("https://login.other.example/", "/icons/auto_login.png", "test")
        assertEquals("https://example.com/custom.svg", db.webAppDao().getById("test")!!.iconUrl)
    }

    @Test fun finishedNavigationKeepsAnIconReceivedBeforeTheHistoryRowExists() = runBlocking {
        icons.publish("https://example.com/", "/icons/auto_first.png")
        val pages = BrowserSavedPagesRepository(db.historyDao(), db.bookmarkDao(), icons)
        pages.recordVisit("https://example.com/", "Test")
        assertEquals("/icons/auto_first.png", db.historyDao().getByUrl("https://example.com/")!!.iconUrl)
        pages.recordVisit("https://example.com/", "Updated")
        assertEquals("/icons/auto_first.png", db.historyDao().getByUrl("https://example.com/")!!.iconUrl)
        val oldGeneration = pages.historyGeneration()
        pages.clearHistory()
        pages.recordVisit("https://example.com/", "Late completion", expectedGeneration = oldGeneration)
        icons.publish("https://example.com/", "/icons/auto_late.png")
        assertNull(db.historyDao().getByUrl("https://example.com/"))
    }
}
