package com.webshell.app

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.webshell.core.data.HistoryEntity
import com.webshell.core.data.HomeSettings
import com.webshell.core.data.SettingsRepository
import com.webshell.core.data.WebShellDatabase
import com.webshell.core.webengine.WebViewPool
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

/** Local debug-only documents; real UI navigation and touch, never an external website. */
@RunWith(AndroidJUnit4::class)
class BrowserReadingInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val settings = SettingsRepository(context)
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var database: WebShellDatabase
    private var originalSettings: HomeSettings? = null
    private var originalFixtureHistory: List<HistoryEntity> = emptyList()
    private var originalSessions = emptySet<String>()
    private val prefix = "https://appassets.androidplatform.net/assets/browser-reading/"

    @Before fun setUp(): Unit = runBlocking {
        originalSettings = settings.settings.first()
        originalSessions = onMain { WebViewPool.liveSessions().toSet() }
        database = Room.databaseBuilder(context, WebShellDatabase::class.java, WebShellDatabase.NAME)
            .addMigrations(WebShellDatabase.MIGRATION_2_3).build()
        originalFixtureHistory = database.historyDao().observeRecent(Int.MAX_VALUE).first().filter { it.url.startsWith(prefix) }
        settings.setBrowserAutoCollapse(true)
        // Start away from either edge so both horizontal parking gestures have real travel.
        settings.setBrowserOrbPosition(.5f, .5f)
        settings.setAppTypography(originalSettings!!.appFontFamily, 100)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        description("浏览").click()
        description("网址")
    }

    @After fun tearDown(): Unit = runBlocking {
        scenario?.close()
        instrumentation.waitForIdleSync()
        onMain {
            WebViewPool.liveSessions().filter { it !in originalSessions && it.startsWith("browser-") }
                .forEach(WebViewPool::destroyAndForget)
        }
        if (::database.isInitialized) {
            database.historyDao().observeRecent(Int.MAX_VALUE).first().filter { it.url.startsWith(prefix) }
                .forEach { database.historyDao().deleteByUrl(it.url) }
            database.historyDao().insertAll(originalFixtureHistory)
            database.close()
        }
        originalSettings?.let {
            settings.setBrowserAutoCollapse(it.browserAutoCollapse)
            settings.setBrowserOrbPosition(it.browserOrbX, it.browserOrbY)
            settings.setAppTypography(it.appFontFamily, it.appFontScalePercent)
        }
    }

    @Test fun readingCollapsesWithoutResizingAndRecallRestoresToolbar() {
        load("index.html", "Reading Fixture A")
        val beforeHeight = onMain { activeShell().webView.height }
        val beforeViewport = js("window.innerHeight").toInt()
        js("window.scrollTo(0,900);true")
        await("programmatic document scroll") { js("window.scrollY").toDouble() > 100 }
        assertExpanded()
        js("window.scrollTo(0,0);true")
        await("reset document scroll") { js("window.scrollY").toDouble() == 0.0 }
        readSwipe()
        description("恢复导航")
        settle()
        assertEquals("Dock collapse must not remeasure the native viewport", beforeHeight, onMain { activeShell().webView.height })
        assertEquals("Dock collapse must not resize the CSS viewport", beforeViewport, js("window.innerHeight").toInt())
        assertNotNull(description("网址"))
        shot("document-orb")
        revealParkedNavigation()
        assertExpanded()
        assertEquals(beforeHeight, onMain { activeShell().webView.height })

        description("菜单").click()
        text("当前网页")
        scrollToText("隐藏地址工具栏").click()
        await("toolbar deliberately hidden") { !device.hasObject(By.desc("网址")) }
        assertTrue("Manual toolbar hiding releases just the top row", onMain { activeShell().webView.height } > beforeHeight)
        description("浏览").click()
        description("网址")
        settle()
        assertEquals(beforeHeight, onMain { activeShell().webView.height })
        shot("toolbar-restored")
    }

    @Test fun nestedScrollingCanCollapseAndOrbCanParkAtEitherEdge() {
        load("nested.html", "Reading Fixture Nested")
        val nativeHeight = onMain { activeShell().webView.height }
        js("document.getElementById('scroller').scrollTop=700;true")
        await("programmatic nested scroll") { js("document.getElementById('scroller').scrollTop").toDouble() > 100 }
        assertExpanded()
        js("document.getElementById('scroller').scrollTop=0;true")
        await("reset nested scroll") { js("document.getElementById('scroller').scrollTop").toDouble() == 0.0 }
        readSwipe()
        description("恢复导航")
        assertEquals("Only inner container scrolled", 0.0, js("window.scrollY").toDouble(), 0.01)
        assertTrue(js("document.getElementById('scroller').scrollTop").toDouble() > 0)
        assertEquals(nativeHeight, onMain { activeShell().webView.height })
        shot("nested-orb")

        for (right in listOf(true, false)) {
            settle()
            val orb = description("恢复导航").visibleBounds
            assertTrue(device.swipe(orb.centerX(), orb.centerY(), if (right) device.displayWidth - 3 else 3, orb.centerY(), 250))
            await("orb parked at edge (right=$right)") {
                device.hasObject(By.desc(context.getString(com.webshell.app.R.string.browser_navigation_parked))) ||
                    hasParkedHandle(instrumentation.uiAutomation.rootInActiveWindow)
            }
            assertEquals(nativeHeight, onMain { activeShell().webView.height })
            shot(if (right) "edge-right" else "edge-left")
            revealParkedNavigation()
            assertExpanded()
            if (right) {
                readSwipe()
                description("恢复导航")
            }
        }
    }

    @Test fun twoTabsAttachOwnViewsAndBackgroundCallbacksCannotHideChrome() {
        load("index.html", "Reading Fixture A")
        val first = activeId()
        // Chromium may mark script-only history entries skippable when no user activation exists.
        // Exercise the actual SPA link gesture, not evaluateJavascript pretending to be a user.
        clickFixtureDetail()
        await("user-activated SPA detail ready") { js("document.title") == "\"Reading Fixture A Detail\"" }
        await("first tab history ready") { onMain { activeShell().canGoBack() } }
        description("标签页").click()
        text("新标签页").click()
        load("second.html", "Reading Fixture B")
        val second = activeId()
        assertTrue(first != second)
        assertTrue(onMain { activeShell().webView.isAttachedToWindow })
        assertFalse(onMain { WebViewPool.get(first)!!.webView.isAttachedToWindow })
        description("标签页").click()
        text("Reading Fixture A Detail").click()
        await("first native view restored") {
            onMain { WebViewPool.activeSessionId == first && WebViewPool.get(first)?.webView?.isAttachedToWindow == true } &&
                js("document.title") == "\"Reading Fixture A Detail\""
        }
        assertTrue(onMain { WebViewPool.get(first)!!.webView.isAttachedToWindow })
        assertFalse(onMain { WebViewPool.get(second)!!.webView.isAttachedToWindow })
        device.pressBack()
        await("back navigates first tab rather than closing it") { activeId() == first && js("location.hash") == "\"\"" }
        val staleReadingCallback = onMain { activeShell().onReadingGesture }
        assertNotNull("Expanded visible page has reading observer", staleReadingCallback)
        description("主页").click()
        await("background UI observer detached") { onMain { WebViewPool.get(first)?.onReadingGesture == null } }
        assertEquals(null, onMain { WebViewPool.activeSessionId })
        onMain { staleReadingCallback?.invoke() }
        js("document.title='Reading Fixture B background';true", second)
        description("浏览").click()
        assertExpanded()
        assertEquals(first, activeId())
        assertEquals("\"Reading Fixture A\"", js("document.title"))
        description("标签页").click()
        text("Reading Fixture B background")
        shot("two-tab-isolation")
    }

    private fun load(file: String, title: String) {
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)
            ?: throw AssertionError("Missing address editor")
        field.click()
        field.text = prefix + file
        device.pressEnter()
        await("fixture navigation ready") {
            onMain {
                val shell = WebViewPool.activeSessionId?.takeIf { it.startsWith("browser-") }?.let(WebViewPool::get)
                shell?.let { it.webView.title == title && it.webView.isAttachedToWindow } ?: false
            }
        }
        if (file == "index.html") {
            await("fixture detail button ready") {
                js("document.querySelector('#article button') != null") == "true"
            }
        }
        description("浏览") // IME has closed and the full Dock is reachable.
        settle()
    }

    private fun clickFixtureDetail() {
        val accessibleButton = device.findObject(By.text("Open local detail"))
        if (accessibleButton != null && !accessibleButton.visibleBounds.isEmpty) {
            accessibleButton.click()
            return
        }

        val buttonRect = JSONObject(
            js(
                """
                (() => {
                  const button = document.querySelector('#article button');
                  if (!button) return null;
                  const rect = button.getBoundingClientRect();
                  return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2, viewportWidth: innerWidth };
                })()
                """.trimIndent(),
            ),
        )
        check(!buttonRect.isNull("x")) { "fixture detail button has no bounds" }
        val webViewBounds = onMain {
            val location = IntArray(2)
            activeShell().webView.getLocationOnScreen(location)
            Triple(location, activeShell().webView.width, activeShell().webView.height)
        }
        val cssToPixels = webViewBounds.second.toDouble() / buttonRect.getDouble("viewportWidth")
        val screenX = webViewBounds.first[0] + (buttonRect.getDouble("x") * cssToPixels).roundToInt()
        val screenY = webViewBounds.first[1] + (buttonRect.getDouble("y") * cssToPixels).roundToInt()
        device.click(screenX, screenY)
    }

    private fun assertExpanded() {
        description("浏览")
        description("网址")
        settle()
        assertFalse("No collapsed control while navigation is expanded", device.hasObject(By.desc("恢复导航")))
    }

    private fun readSwipe() {
        val rect = onMain {
            val view = activeShell().webView
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        assertTrue(device.swipe(rect.centerX(), rect.top + rect.height() * 7 / 10,
            rect.centerX(), rect.top + rect.height() * 3 / 10, 35))
    }

    private fun hasParkedHandle(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val parkedLabel = context.getString(com.webshell.app.R.string.browser_navigation_parked)
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        if (compat.stateDescription?.toString() == parkedLabel || node.contentDescription?.toString() == parkedLabel) return true
        return (0 until node.childCount).any { hasParkedHandle(node.getChild(it)) }
    }

    private fun orbState(): String {
        val orbBounds = device.findObject(By.desc("恢复导航"))?.visibleBounds
        val parkedBounds = device.findObject(By.desc(context.getString(com.webshell.app.R.string.browser_navigation_parked)))?.visibleBounds
        return "orb=$orbBounds parked=$parkedBounds"
    }

    private fun revealParkedNavigation() {
        device.waitForIdle()
        repeat(4) {
            val handle = device.findObject(By.desc("恢复导航"))
            val bounds = handle?.visibleBounds?.takeUnless { it.isEmpty }
                ?: parkedHandleBounds(parkedHandleNode(instrumentation.uiAutomation.rootInActiveWindow))
            if (handle != null && bounds != null && !bounds.isEmpty) {
                // Prefer the exposed Compose action when the device reports one.
                runCatching { handle.click() }
            }
            device.waitForIdle()
            if (device.wait(Until.findObject(By.desc("浏览")), 10_000) != null) return
            if (bounds != null && !bounds.isEmpty) {
                // Tap the painted 6dp bar inside the inset hit target. Center-clicking the
                // transparent 48dp parent is unreliable on gesture-navigation emulators.
                val barCenterOffset = maxOf(4, bounds.width() / 16)
                val edgeX = if (bounds.centerX() < device.displayWidth / 2) {
                    bounds.left + barCenterOffset
                } else {
                    bounds.right - barCenterOffset
                }
                device.click(edgeX, bounds.centerY())
            }
            device.waitForIdle()
            if (device.wait(Until.findObject(By.desc("浏览")), 10_000) != null) return
            SystemClock.sleep(250)
        }
        throw AssertionError("Parked navigation handle did not reveal the browser Dock: ${parkedNavigationState()}")
    }

    private fun parkedHandleNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val parkedLabel = context.getString(com.webshell.app.R.string.browser_navigation_parked)
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        if (compat.stateDescription?.toString() == parkedLabel || node.contentDescription?.toString() == parkedLabel) {
            return node
        }
        return (0 until node.childCount)
            .asSequence()
            .mapNotNull { parkedHandleNode(node.getChild(it)) }
            .firstOrNull()
    }

    private fun parkedHandleBounds(node: AccessibilityNodeInfo?): Rect? {
        val bounds = Rect()
        node?.getBoundsInScreen(bounds)
        return bounds.takeUnless { it.isEmpty }
    }

    private fun parkedNavigationState(): String {
        val restore = device.findObject(By.desc("恢复导航"))?.visibleBounds
        val node = parkedHandleNode(instrumentation.uiAutomation.rootInActiveWindow)
        return "restore=$restore parked=${parkedHandleBounds(node)} clickable=${node?.isClickable} " +
            "visible=${node?.isVisibleToUser} actions=${node?.actionList?.joinToString()}"
    }

    private fun scrollToText(value: String): UiObject2 {
        repeat(10) {
            // A clickable AppListRow merges title + subtitle into one accessibility text node.
            device.findObject(By.textContains(value))?.takeIf { !it.visibleBounds.isEmpty }?.let { return it }
            device.swipe(device.displayWidth / 2, device.displayHeight * 8 / 10,
                device.displayWidth / 2, device.displayHeight * 4 / 10, 30)
        }
        throw AssertionError("Missing menu action: $value")
    }

    private fun activeId(): String = onMain { requireNotNull(WebViewPool.activeSessionId) }
    private fun activeShell() = requireNotNull(WebViewPool.get(requireNotNull(WebViewPool.activeSessionId)))

    private fun js(script: String, sessionId: String = activeId()): String {
        val ready = CountDownLatch(1)
        var result: String? = null
        onMain { WebViewPool.get(sessionId)!!.webView.evaluateJavascript(script) { result = it; ready.countDown() } }
        assertTrue("Renderer query timed out", ready.await(5, TimeUnit.SECONDS))
        return requireNotNull(result)
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(60)
        }
        val state = if (message.startsWith("orb parked at edge")) " (${orbState()})" else ""
        throw AssertionError("Timed out: $message$state")
    }

    private fun description(value: String) = device.wait(Until.findObject(By.desc(value)), 5_000)
        ?: throw AssertionError("Missing description: $value")
    private fun text(value: String) = device.wait(Until.findObject(By.text(value)), 10_000)
        ?: throw AssertionError("Missing text: $value")
    private fun settle() { SystemClock.sleep(700) }

    private fun shot(name: String) {
        settle()
        val directory = File(context.getExternalFilesDir(null), "browser-qa").apply { mkdirs() }
        val output = File(directory, "browser15-$name.png")
        assertTrue(device.takeScreenshot(output))
        device.executeShellCommand("mkdir -p /sdcard/Download/webshell-ios-qa")
        device.executeShellCommand("cp ${output.absolutePath} /sdcard/Download/webshell-ios-qa/${output.name}")
    }
}
