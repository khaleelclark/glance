package com.khaleel.glance.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDismissalTest {
    @Test fun `retained active session restores only the regular card`() {
        assertEquals(DismissalAction.RESTORE_REGULAR, NotificationDismissal.action(7, 7, true, true, true))
    }

    @Test fun `retention off respects dismissal`() {
        assertEquals(DismissalAction.HIDE, NotificationDismissal.action(7, 7, true, true, false))
    }

    @Test fun `old delete intent cannot restore newer stopped or absent session`() {
        for (active in listOf(null, 8L)) {
            assertEquals(DismissalAction.IGNORE, NotificationDismissal.action(7, active, true, true, true))
        }
        assertEquals(DismissalAction.IGNORE, NotificationDismissal.action(7, 7, false, true, true))
        assertEquals(DismissalAction.IGNORE, NotificationDismissal.action(7, 7, true, false, true))
    }
}
