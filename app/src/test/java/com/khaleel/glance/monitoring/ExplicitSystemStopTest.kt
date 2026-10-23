package com.khaleel.glance.monitoring

import org.junit.Assert.*
import org.junit.Test

class ExplicitSystemStopTest {
    @Test fun failedDisableStillHidesAndLeavesRecoveryPending() {
        val actions = mutableListOf<String>()
        ExplicitSystemStop().handle(
            timestamp = 10L, lastHandled = 0L,
            disable = { actions += "disable"; false },
            hide = { actions += "hide" },
            markHandled = { actions += "handled" },
        )
        assertEquals(listOf("disable", "hide"), actions)
    }

    @Test fun successfulDisableHidesBeforeRecordingHandled() {
        val actions = mutableListOf<String>()
        ExplicitSystemStop().handle(
            timestamp = 10L, lastHandled = 0L,
            disable = { actions += "disable"; true },
            hide = { actions += "hide" },
            markHandled = { actions += "handled" },
        )
        assertEquals(listOf("disable", "hide", "handled"), actions)
    }
    @Test fun failedDisableThenSuccessfulExplicitStartIsNotReplayedOnActivityRecreation() {
        for (acknowledgementSaved in listOf(true, false)) {
            val recovery = ExplicitSystemStop()
            var lastHandled = 0L
            var monitoring: Boolean
            var disables = 0
            recovery.handle(10L, lastHandled,
                disable = { disables++; false }, hide = {}, markHandled = { lastHandled = 10L })
            monitoring = true // The explicit Start has durably saved true.
            recovery.afterStart(10L, explicit = true, saved = true) {
                if (acknowledgementSaved) lastHandled = 10L
            }
            // Activity recreation uses the same application-scoped recovery object.
            recovery.handle(10L, lastHandled,
                disable = { disables++; monitoring = false; true }, hide = {}, markHandled = { lastHandled = 10L })
            assertTrue(monitoring)
            assertEquals(1, disables)
        }
    }

    @Test fun failedExplicitStartDoesNotRetirePendingSystemStop() {
        val recovery = ExplicitSystemStop()
        var acknowledged = false
        var disabled = false
        recovery.afterStart(10L, explicit = true, saved = false) { acknowledged = true }
        recovery.handle(10L, 0L, disable = { disabled = true; true }, hide = {}, markHandled = {})
        assertFalse(acknowledged)
        assertTrue(disabled)
    }

    @Test fun automaticStartDoesNotRetirePendingSystemStop() {
        val recovery = ExplicitSystemStop()
        var acknowledged = false
        var disabled = false
        recovery.afterStart(10L, explicit = false, saved = true) { acknowledged = true }
        recovery.handle(10L, 0L, disable = { disabled = true; true }, hide = {}, markHandled = {})
        assertFalse(acknowledged)
        assertTrue(disabled)
    }
}
