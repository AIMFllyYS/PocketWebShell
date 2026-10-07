package com.webshell.app

import android.content.Intent
import android.os.SystemClock
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.webshell.core.data.SettingsRepository
import com.webshell.core.model.WebTopInsetMode
import com.webshell.core.webengine.ShellWebView
import com.webshell.core.webengine.WebViewPool
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real-device regression: production host, persistent settings, Chromium DOM and view geometry. */
@RunWith(AndroidJUnit4::class)
class WebTopInsetInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val settings = SettingsRepository(context)
    private var originalMode = WebTopInsetMode.AUTO
    private var scenario: ActivityScenario<MainActivity>? = null
    private var sessionId: String? = null

    @Before fun rememberSettings() {
        originalMode = runBlocking { settings.settings.first().webTopInsetMode }
    }

    @After fun restoreSettings() {
        runBlocking { settings.setWebTopInsetMode(originalMode) }
        scenario?.close()
        instrumentation.runOnMainSync { sessionId?.let(WebViewPool::destroyAndForget) }
    }

    @Test fun manualModesChangeTopGeometryWithoutReloadingTheForm() {
        runBlocking { settings.setWebTopInsetMode(WebTopInsetMode.EDGE_TO_EDGE) }
        scenario = ActivityScenario.launch(
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_URL, "https://inset-test.invalid/"),
        )
        val shell = waitForShell()
        val webView = shell.webView
        instrumentation.runOnMainSync {
            webView.stopLoading()
            webView.loadDataWithBaseURL("https://inset-test.invalid/", HTML, "text/html", "UTF-8", null)
        }
        awaitCondition("fixture loaded") { javascript(webView, "document.getElementById('login')!==null") == "true" }
        javascript(webView, "document.getElementById('login').value='validation-value';window.scrollTo(0,150);true")
        awaitCondition("fixture scrolled") { javascript(webView, "window.scrollY>100") == "true" }

        for (mode in listOf(WebTopInsetMode.AVOID, WebTopInsetMode.AUTO, WebTopInsetMode.EDGE_TO_EDGE)) {
            runBlocking { settings.setWebTopInsetMode(mode) }
            awaitCondition("mode applied: $mode") {
                var matches = false
                instrumentation.runOnMainSync { matches = shell.config.topInsetMode == mode }
                matches
            }
            awaitCondition("top geometry settled: $mode") {
                var matches = false
                instrumentation.runOnMainSync {
                    val root = ViewCompat.getRootWindowInsets(shell)
                    val types = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
                    val safeTop = if (mode == WebTopInsetMode.AVOID) root?.getInsetsIgnoringVisibility(types)?.top
                        else root?.getInsets(types)?.top
                    val location = IntArray(2)
                    shell.getLocationInWindow(location)
                    val expected = if (mode == WebTopInsetMode.EDGE_TO_EDGE) 0
                        else ((safeTop ?: 0) - location[1]).coerceAtLeast(0)
                    matches = shell.paddingTop == expected
                }
                matches
            }
            assertSame("mode switching must reuse the WebView", webView, shell.webView)
            assertEquals("\"validation-value\"", javascript(webView, "document.getElementById('login').value"))
            assertEquals("scroll position should survive", "true", javascript(webView, "window.scrollY>100"))
            awaitCondition("no second CSS top safe area") {
                javascript(webView, "parseFloat(getComputedStyle(document.getElementById('header')).paddingTop)===0") == "true" &&
                    javascript(webView, "parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--ws-safe-top')||'0')===0") == "true"
            }
            val screenshot = File(context.getExternalFilesDir(null), "top-inset-${mode.storedValue}.png")
            assertTrue(UiDevice.getInstance(instrumentation).takeScreenshot(screenshot))
        }
        assertEquals(WebTopInsetMode.EDGE_TO_EDGE, runBlocking { settings.settings.first().webTopInsetMode })
    }

    private fun waitForShell(): ShellWebView {
        val found = AtomicReference<ShellWebView>()
        awaitCondition("site shell mounted") {
            instrumentation.runOnMainSync {
                WebViewPool.liveSessions().mapNotNull(WebViewPool::get)
                    .firstOrNull { it.config.startUrl.contains("inset-test.invalid") && it.isAttachedToWindow }
                    ?.let { sessionId = it.sessionId; found.set(it) }
            }
            found.get() != null
        }
        return found.get()
    }

    private fun javascript(view: WebView, code: String): String {
        val result = AtomicReference<String>()
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(code) { result.set(it); latch.countDown() }
        }
        assertTrue("Chromium callback timed out", latch.await(20, TimeUnit.SECONDS))
        return result.get()
    }

    private fun awaitCondition(label: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 90_000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(150)
        }
        fail("Timed out: $label")
    }

    private companion object {
        val HTML = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>html,body{margin:0;padding:0;background:#eef2f7}header{padding-top:env(safe-area-inset-top,0px);background:#ffee99;height:48px}main{height:2500px;padding:16px}input{font-size:20px}</style>
            </head><body><header id="header">Top safe-area fixture</header>
            <main><label>Test login <input id="login"></label><p>Scrollable content</p></main></body></html>
        """.trimIndent()
    }
}
