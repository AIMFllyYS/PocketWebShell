package com.webshell.core.webengine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererRecoveryGateTest {
    @Test fun `allows one automatic retry then stops`() {
        val gate = RendererRecoveryGate()
        assertTrue(gate.tryBeginRecovery())
        assertFalse(gate.tryBeginRecovery())
    }

    @Test fun `stable page and explicit navigation reset the budget`() {
        val gate = RendererRecoveryGate()
        assertTrue(gate.tryBeginRecovery())
        gate.markStable()
        assertTrue(gate.tryBeginRecovery())
        assertFalse(gate.tryBeginRecovery())
        gate.resetForExplicitNavigation()
        assertTrue(gate.tryBeginRecovery())
    }
}
