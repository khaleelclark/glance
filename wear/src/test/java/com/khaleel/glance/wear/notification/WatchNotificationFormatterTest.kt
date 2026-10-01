package com.khaleel.glance.wear.notification

import com.khaleel.glance.wear.data.WatchCacheState
import com.khaleel.glance.wear.shared.WatchSnapshot
import com.khaleel.glance.wear.shared.WatchStatus
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNotificationFormatterTest {
    @Test fun `fresh notification carries value unit trend arrow and absolute timestamp`() {
        val content = WatchNotificationFormatter.format(state(reading(1)), NOW, ZoneOffset.UTC)
        requireNotNull(content)
        assertEquals("112 mg/dL →", content.title)
        assertTrue(content.text.startsWith("Reading at "))
        assertEquals(NOW - 60_000, content.whenMillis)
        assertEquals(4 * 60_000L, content.timeoutMillis)
    }

    @Test fun `delayed notification labels value as last and expires at transport lease`() {
        val content = WatchNotificationFormatter.format(state(reading(7)), NOW, ZoneOffset.UTC)
        requireNotNull(content)
        assertEquals("Delayed", content.title)
        assertTrue(content.text.startsWith("Last: 112 mg/dL at "))
        assertEquals(WatchSnapshot.LEASE_MILLIS, content.timeoutMillis)
    }

    @Test fun `watch opt out phone disable tombstone and expired lease all suppress card`() {
        val current = reading(1)
        assertNull(WatchNotificationFormatter.format(state(current, optIn = false), NOW))
        assertNull(
            WatchNotificationFormatter.format(
                state(current.copy(notificationsEnabled = false)),
                NOW,
            ),
        )
        assertNull(WatchNotificationFormatter.format(state(current, revoked = true), NOW))
        assertNull(
            WatchNotificationFormatter.format(
                state(current.copy(sentAtMillis = NOW - WatchSnapshot.LEASE_MILLIS)),
                NOW,
            ),
        )
    }

    private fun reading(ageMinutes: Int) = WatchSnapshot(
        revision = 1,
        sentAtMillis = NOW,
        status = WatchStatus.READING,
        measuredAtMillis = NOW - ageMinutes * 60_000L,
        value = "112",
        unit = "mg/dL",
        arrow = "→",
        trend = "Steady",
        notificationsEnabled = true,
    )

    private fun state(
        snapshot: WatchSnapshot,
        optIn: Boolean = true,
        revoked: Boolean = false,
    ) = WatchCacheState(
        sourceNode = "phone",
        lastRevision = snapshot.revision,
        snapshot = snapshot,
        revoked = revoked,
        watchNotificationsOptIn = optIn,
    )

    companion object { const val NOW = 2_000_000_000_000L }
}
