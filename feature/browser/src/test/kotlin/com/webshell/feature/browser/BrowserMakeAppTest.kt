package com.webshell.feature.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserMakeAppTest {
    @Test fun anOrdinaryHttpsTabCanBeMadeIntoAnApp() {
        assertTrue(BrowserTab("a", "DeepSeek", "https://platform.deepseek.com/").canMakeApp())
    }
    @Test fun blankAndInternalAssetTabsCannotBecomeOrdinaryWebsiteApps() {
        for (url in listOf("about:blank", "", "javascript:alert(1)", "https://appassets.androidplatform.net/local/tmp-a/index.html")) {
            assertFalse(url, BrowserTab("a", "Test", url).canMakeApp())
        }
    }
    @Test fun importedHtmlRetainsItsDocumentPersistencePath() {
        assertTrue(BrowserTab("a", "Local", "local://tmp-a/index.html",
            kind = BrowserTabKind.INCOMING_HTML, localAppId = "tmp-a").canMakeApp())
    }
}
