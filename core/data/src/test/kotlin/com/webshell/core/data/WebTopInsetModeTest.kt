package com.webshell.core.data

import com.webshell.core.model.WebTopInsetMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WebTopInsetModeTest {
    @Test fun existingInstallAndInvalidPreferenceUseAuto() {
        for (value in listOf(null, "", "oppo", "forced", "EDGE_TO_EDGE")) {
            assertEquals(WebTopInsetMode.AUTO, WebTopInsetMode.fromStored(value))
        }
        assertEquals(WebTopInsetMode.AUTO, HomeSettings().webTopInsetMode)
    }

    @Test fun allManualChoicesSurviveStoredValueRoundTrip() {
        for (mode in WebTopInsetMode.entries) {
            assertEquals(mode, WebTopInsetMode.fromStored(mode.storedValue))
        }
    }
}
