package com.khaleel.glance.monitoring

import org.junit.Assert.*
import org.junit.Test

class ExplicitSystemStopTest {
    @Test fun failedDisableStillHidesAndLeavesRecoveryPending() {
        val actions = mutableListOf<String>()
        ExplicitSystemStop.handle(
            disable = { actions += "disable"; false },
            hide = { actions += "hide" },
            markHandled = { actions += "handled" },
        )
        assertEquals(listOf("disable", "hide"), actions)
    }

    @Test fun successfulDisableHidesBeforeRecordingHandled() {
        val actions = mutableListOf<String>()
        ExplicitSystemStop.handle(
            disable = { actions += "disable"; true },
            hide = { actions += "hide" },
            markHandled = { actions += "handled" },
        )
        assertEquals(listOf("disable", "hide", "handled"), actions)
    }
}
