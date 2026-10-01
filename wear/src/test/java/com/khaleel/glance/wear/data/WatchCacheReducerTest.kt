package com.khaleel.glance.wear.data

import com.khaleel.glance.wear.shared.WatchSnapshot
import com.khaleel.glance.wear.shared.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchCacheReducerTest {
    @Test fun `monotonic revision rejects callback older than tombstone`() {
        val initial = WatchCacheReducer.reduce(
            WatchCacheState(), envelope("phone", reading(10)), allowSourceSwitch = false,
        ).state
        val tombstoned = WatchCacheReducer.reduce(
            initial, envelope("phone", tombstone(12)), allowSourceSwitch = false,
        ).state
        val late = WatchCacheReducer.reduce(
            tombstoned, envelope("phone", reading(11)), allowSourceSwitch = false,
        )

        assertEquals(ReductionDecision.OLD_REVISION, late.decision)
        assertEquals(12, late.state.lastRevision)
        assertTrue(late.state.revoked)
        assertEquals(WatchStatus.HIDDEN, late.state.snapshot?.status)
    }

    @Test fun `equal revision cannot mutate committed snapshot`() {
        val state = WatchCacheReducer.reduce(
            WatchCacheState(), envelope("phone", reading(4, value = "111")), false,
        ).state
        val replay = WatchCacheReducer.reduce(
            state, envelope("phone", reading(4, value = "222")), false,
        )

        assertEquals(ReductionDecision.OLD_REVISION, replay.decision)
        assertEquals("111", replay.state.snapshot?.value)
    }

    @Test fun `other node is rejected outside explicit source switch`() {
        val state = WatchCacheState(sourceNode = "phone-a", lastRevision = 20)
        val result = WatchCacheReducer.reduce(state, envelope("phone-b", reading(99)), false)
        assertEquals(ReductionDecision.WRONG_SOURCE, result.decision)
    }

    @Test fun `initial resolver keeps preferred source even when another revision is larger`() {
        val chosen = PhoneSnapshotSelector.select(
            listOf(envelope("phone-a", reading(2)), envelope("phone-b", reading(50))),
            preferredSource = "phone-a",
            nearbyNodes = setOf("phone-b"),
        )
        assertEquals("phone-a", chosen?.sourceNode)
    }

    @Test fun `initial resolver prefers nearby phone and newest item from that source`() {
        val chosen = PhoneSnapshotSelector.select(
            listOf(
                envelope("remote", reading(90, sentAt = NOW + 10)),
                envelope("near", reading(3, sentAt = NOW)),
                envelope("near", reading(4, sentAt = NOW + 1)),
            ),
            preferredSource = null,
            nearbyNodes = setOf("near"),
        )
        assertEquals("near", chosen?.sourceNode)
        assertEquals(4L, chosen?.snapshot?.revision)
    }

    @Test fun `tombstone push bypasses bounded complication interval`() {
        assertTrue(ComplicationPushPolicy.shouldPush(NOW, NOW + 1, tombstone = true))
        assertEquals(false, ComplicationPushPolicy.shouldPush(NOW, NOW + 1, tombstone = false))
        assertTrue(
            ComplicationPushPolicy.shouldPush(
                NOW,
                NOW + ComplicationPushPolicy.INTERVAL_MILLIS,
                tombstone = false,
            ),
        )
    }

    @Test fun `failed persistence clears health data and keeps late callback barrier`() {
        val attempted = WatchCacheState(sourceNode = "phone", lastRevision = 12,
            snapshot = tombstone(12), watchNotificationsOptIn = true)
        val hidden = WatchCacheReducer.failedPersistence(attempted)
        assertTrue(hidden.revoked)
        assertEquals(null, hidden.snapshot)
        assertEquals(false, hidden.watchNotificationsOptIn)
        assertEquals(ReductionDecision.OLD_REVISION,
            WatchCacheReducer.reduce(hidden, envelope("phone", reading(11)), false).decision)
    }

    private fun envelope(source: String, snapshot: WatchSnapshot) = SnapshotEnvelope(source, snapshot)

    private fun reading(revision: Long, value: String = "112", sentAt: Long = NOW) = WatchSnapshot(
        revision = revision,
        sentAtMillis = sentAt,
        status = WatchStatus.READING,
        measuredAtMillis = sentAt - 60_000,
        value = value,
        unit = "mg/dL",
        arrow = "→",
        trend = "Steady",
        notificationsEnabled = true,
    )

    private fun tombstone(revision: Long) = WatchSnapshot(
        revision = revision,
        sentAtMillis = NOW,
        status = WatchStatus.HIDDEN,
    )

    companion object { const val NOW = 2_000_000_000_000L }
}
