package com.webshell.app

import android.graphics.BitmapFactory
import android.webkit.WebSettings
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.webshell.core.data.SiteIconRepository
import com.webshell.core.data.WebShellDatabase
import com.webshell.core.data.metadata.SiteMetadataFetcher
import com.webshell.core.webengine.LocalWebHost
import com.webshell.core.webengine.ShellConfig
import com.webshell.core.webengine.ShellListener
import com.webshell.core.webengine.ShellWebView
import com.webshell.core.webengine.WebViewPool
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Offline device test of the production Chromium host, DOM callback and SVG decoder. */
@RunWith(AndroidJUnit4::class)
class WebIconRefreshInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun inlineSvgFromTheRenderedDocumentBecomesARealPngFile() {
        val id = "icon-test-${System.nanoTime()}"
        val declarations = AtomicReference<List<String>>()
        val received = CountDownLatch(1)
        val shell = AtomicReference<ShellWebView>()
        val db = Room.inMemoryDatabaseBuilder(context, WebShellDatabase::class.java).build()
        try {
            instrumentation.runOnMainSync {
                shell.set(WebViewPool.getOrCreate(context, id) { ShellConfig(sessionId = id) })
                shell.get().sessionListener = object : ShellListener {
                    override fun onIconCandidates(pageUrl: String, candidates: List<String>) {
                        declarations.set(candidates); received.countDown()
                    }
                }
                shell.get().webView.loadDataWithBaseURL("https://icon-test.invalid/",
                    """<html><head><link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32'%3E%3Crect width='32' height='32' fill='blue'/%3E%3C/svg%3E"></head><body>Fixture</body></html>""",
                    "text/html", "UTF-8", null)
            }
            assertTrue("Production callback did not deliver the rendered icon declaration", received.await(12, TimeUnit.SECONDS))
            val client = OkHttpClient()
            val icons = SiteIconRepository(context, client, SiteMetadataFetcher(client), db.webAppDao(), db.historyDao(), db.bookmarkDao())
            // A fake PNG containing HTML must fail before the valid SVG candidate is tried.
            val path = runBlocking { icons.resolve(listOf("data:image/png,%3Chtml%3Enot-an-image%3C/html%3E") + declarations.get()) }
            assertNotNull(path)
            val bitmap = BitmapFactory.decodeFile(path)
            assertNotNull(bitmap)
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
        } finally {
            instrumentation.runOnMainSync { WebViewPool.destroyAndForget(id) }
            db.close()
        }
    }

    @Test fun forcedReloadRetainsBypassThroughPageStartThenRestoresNormalCaching() {
        val id = "refresh-test-${System.nanoTime()}"
        val dir = File(context.filesDir, "localapps/$id").apply { mkdirs() }
        val entry = File(dir, "index.html").apply { writeText("<html><title>version-one</title><body>one</body></html>") }
        val first = CountDownLatch(1)
        val shell = AtomicReference<ShellWebView>()
        val seenMode = AtomicReference<Int>()
        try {
            instrumentation.runOnMainSync {
                shell.set(WebViewPool.getOrCreate(context, id) { ShellConfig(sessionId = id, localAppId = id) })
                shell.get().sessionListener = object : ShellListener {
                    override fun onPageFinished(url: String) { first.countDown() }
                }
                shell.get().load(LocalWebHost.toHttpsUrl(LocalWebHost.buildLocalAppUrl(id, "index.html")))
            }
            assertTrue(first.await(12, TimeUnit.SECONDS))
            entry.writeText("<html><title>version-two</title><body>two</body></html>")
            val finished = CountDownLatch(1)
            instrumentation.runOnMainSync {
                shell.get().sessionListener = object : ShellListener {
                    override fun onPageStarted(url: String) { seenMode.set(shell.get().webView.settings.cacheMode) }
                    override fun onPageFinished(url: String) { finished.countDown() }
                }
                shell.get().reloadBypassingCache()
            }
            assertTrue(finished.await(12, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertEquals(WebSettings.LOAD_NO_CACHE, seenMode.get())
                assertEquals(WebSettings.LOAD_DEFAULT, shell.get().webView.settings.cacheMode)
                assertEquals("version-two", shell.get().webView.title)
            }
        } finally {
            instrumentation.runOnMainSync { WebViewPool.destroyAndForget(id) }
            dir.deleteRecursively()
        }
    }
    @Test fun offlineResetDeletesCacheStorageAndPreservesLocalStorage() {
        val id = "offline-test-${System.nanoTime()}"
        val url = "https://$id.invalid/"
        val shell = AtomicReference<ShellWebView>()
        fun openFixture() {
            val loaded = CountDownLatch(1)
            instrumentation.runOnMainSync {
                shell.set(WebViewPool.getOrCreate(context, id) { ShellConfig(sessionId = id) })
                shell.get().sessionListener = object : ShellListener {
                    override fun onPageFinished(url: String) { loaded.countDown() }
                }
                shell.get().webView.loadDataWithBaseURL(url, "<html><body>Offline fixture</body></html>", "text/html", "UTF-8", url)
            }
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
        }
        try {
            openFixture()
            readJs(shell.get(), "window.__seed='pending';localStorage.setItem('fixture-value','keep');caches.open('$id').then(c=>c.put('/fixture',new Response('old'))).then(()=>window.__seed='done');void 0")
            repeat(50) {
                if (readJs(shell.get(), "window.__seed") == "\"done\"") return@repeat
                android.os.SystemClock.sleep(100)
            }
            assertEquals("\"done\"", readJs(shell.get(), "window.__seed"))
            instrumentation.runOnMainSync { WebViewPool.destroyAndForget(id) }
            val reset = runBlocking { com.webshell.core.webengine.WebPageProbe(context).fetch(url, resetOffline = true) }
            assertEquals(true, reset?.offlineReset)
            openFixture()
            assertEquals("\"keep\"", readJs(shell.get(), "localStorage.getItem('fixture-value')"))
            readJs(shell.get(), "window.__remaining=-1;caches.keys().then(k=>window.__remaining=k.length);void 0")
            repeat(50) {
                if (readJs(shell.get(), "window.__remaining") == "0") return@repeat
                android.os.SystemClock.sleep(100)
            }
            assertEquals("0", readJs(shell.get(), "window.__remaining"))
            readJs(shell.get(), "localStorage.removeItem('fixture-value')")
        } finally { instrumentation.runOnMainSync { WebViewPool.destroyAndForget(id) } }
    }

    private fun readJs(shell: ShellWebView, script: String): String {
        val result = AtomicReference<String>()
        val received = CountDownLatch(1)
        instrumentation.runOnMainSync {
            shell.webView.evaluateJavascript(script) { result.set(it); received.countDown() }
        }
        assertTrue(received.await(5, TimeUnit.SECONDS))
        return result.get()
    }

}
