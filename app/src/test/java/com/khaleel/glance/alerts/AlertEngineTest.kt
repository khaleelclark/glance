package com.khaleel.glance.alerts

import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.SourceResult
import com.khaleel.glance.core.Trend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AlertEngineTest {
    private val start = Instant.parse("2026-09-27T12:00:00Z")
    private val person = Connection("patrick", "Patrick")

    @Test fun onlySuccessfulPostsLogAndCooldownSurvivesEngineRecreation() {
        val store = MemoryStore()
        val delivery = MemoryDelivery()
        val history = MemoryHistory()
        val clock = MutableClock(start)
        val reading = reading(305.0, start)
        val display = display(reading)
        var live = display

        AlertEngine(store, delivery, clock, { live }, { false }, history)
            .onPollResult(SourceResult.Success(reading), display, GlucoseUnit.MG_DL, false)
        assertEquals(listOf(AlertKind.URGENT_HIGH), delivery.posted)
        history.events.value.single().let { event ->
            assertEquals(person, event.person)
            assertEquals(AlertKind.URGENT_HIGH, event.kind)
            assertEquals(reading, event.reading)
            assertEquals(start, event.firedAt)
        }

        clock.now = start.plusSeconds(60)
        AlertEngine(store, delivery, clock, { live }, { false }, history)
            .onPollResult(SourceResult.Success(reading), display, GlucoseUnit.MG_DL, false)
        live = display(reading(310.0, start.plusSeconds(60)))
        AlertEngine(store, delivery, clock, { live }, { false }, history).onPollResult(
            SourceResult.Success(reading(310.0, start.plusSeconds(60))),
            live, GlucoseUnit.MG_DL, false,
        )
        assertEquals(1, delivery.posted.size)
        assertEquals(1, history.events.value.size)

        clock.now = start.plusSeconds(300)
        val next = reading(315.0, start.plusSeconds(300))
        live = display(next)
        AlertEngine(store, delivery, clock, { live }, { false }, history)
            .onPollResult(SourceResult.Success(next), live, GlucoseUnit.MG_DL, false)
        assertEquals(listOf(AlertKind.URGENT_HIGH, AlertKind.URGENT_HIGH), delivery.posted)
        assertEquals(2, history.events.value.size)
    }

    @Test fun blockedChannelAndUnpublishedResponseNeverPostOrLog() {
        val store = MemoryStore()
        val delivery = MemoryDelivery().apply { allowed = false }
        val history = MemoryHistory()
        val reading = reading(305.0, start)
        var live = display(reading)
        val engine = AlertEngine(store, delivery, MutableClock(start), { live }, { false }, history)
        engine.onPollResult(SourceResult.Success(reading), live.copy(reading = null), GlucoseUnit.MG_DL, false)
        live = live.copy(person = Connection("other", "Other"))
        engine.onPollResult(SourceResult.Success(reading), display(reading), GlucoseUnit.MG_DL, false)
        live = display(reading)
        engine.onPollResult(SourceResult.Success(reading), live, GlucoseUnit.MG_DL, false)
        assertEquals(emptyList<AlertKind>(), delivery.posted)
        assertEquals(emptyMap<AlertKind, Instant>(), store.state.lastFiredAt)
        assertEquals(emptyList<AlertHistoryEntry>(), history.events.value)
    }

    @Test fun failedNotificationPostDoesNotCreateHistory() {
        val store = MemoryStore()
        val reading = reading(305.0, start)
        val live = display(reading)
        val history = MemoryHistory()
        val delivery = object : AlertDelivery {
            override fun ensureChannels() = Unit
            override fun canPost(kind: AlertKind) = true
            override fun post(
                kind: AlertKind,
                display: DisplayState,
                unit: GlucoseUnit,
                showOnLockScreen: Boolean,
            ) = throw java.io.IOException("synthetic notify failure")
            override fun clear() = Unit
        }
        assertThrows(java.io.IOException::class.java) {
            AlertEngine(store, delivery, MutableClock(start), { live }, { false }, history)
                .onPollResult(SourceResult.Success(reading), live, GlucoseUnit.MG_DL, false)
        }
        assertEquals(emptyList<AlertHistoryEntry>(), history.events.value)
        assertTrue(store.state.lastFiredAt.containsKey(AlertKind.URGENT_HIGH))
    }

    @Test fun recordsPostingTimeAfterDeliveryReturns() {
        val clock = MutableClock(start)
        val postedAt = start.plusSeconds(2)
        val reading = reading(305.0, start.minusSeconds(30))
        val live = display(reading)
        val history = MemoryHistory()
        val delivery = object : AlertDelivery {
            override fun ensureChannels() = Unit
            override fun canPost(kind: AlertKind) = true
            override fun post(kind: AlertKind, display: DisplayState, unit: GlucoseUnit, showOnLockScreen: Boolean) {
                clock.now = postedAt
            }
            override fun clear() = Unit
        }

        AlertEngine(MemoryStore(), delivery, clock, { live }, { false }, history)
            .onPollResult(SourceResult.Success(reading), live, GlucoseUnit.MG_DL, false)

        assertEquals(postedAt, history.events.value.single().firedAt)
        assertEquals(start.minusSeconds(30), history.events.value.single().reading.measuredAt)
    }

    @Test fun logsAllFourExactTypesWithUrgentPriority() {
        val values = mapOf(AlertKind.LOW to 65.0, AlertKind.URGENT_LOW to 50.0,
            AlertKind.HIGH to 250.0, AlertKind.URGENT_HIGH to 310.0)
        values.forEach { (kind, value) ->
            val sample = reading(value, start.minusSeconds(30))
            val live = display(sample)
            val history = MemoryHistory()
            val delivery = MemoryDelivery()
            AlertEngine(MemoryStore(AlertKind.entries.toSet()), delivery, MutableClock(start),
                { live }, { false }, history)
                .onPollResult(SourceResult.Success(sample), live, GlucoseUnit.MG_DL, false)
            assertEquals(listOf(kind), delivery.posted)
            assertEquals(kind, history.events.value.single().kind)
            assertEquals(sample, history.events.value.single().reading)
        }
    }

    @Test fun historyWriteFailurePropagatesAfterDeliveredAlert() {
        val store = MemoryStore()
        val delivery = MemoryDelivery()
        val history = MemoryHistory(failAppend = true)
        val reading = reading(305.0, start)
        val live = display(reading)
        assertThrows(java.io.IOException::class.java) {
            AlertEngine(store, delivery, MutableClock(start), { live }, { false }, history)
                .onPollResult(SourceResult.Success(reading), live, GlucoseUnit.MG_DL, false)
        }
        assertEquals(listOf(AlertKind.URGENT_HIGH), delivery.posted)
        assertTrue(store.state.lastFiredAt.containsKey(AlertKind.URGENT_HIGH))
        assertEquals(emptyList<AlertHistoryEntry>(), history.events.value)
    }

    @Test fun clearHistoryPreservesDedupAndCooldownState() {
        val store = MemoryStore()
        val history = MemoryHistory()
        val reading = reading(305.0, start)
        val live = display(reading)
        val engine = AlertEngine(store, MemoryDelivery(), MutableClock(start), { live }, { false }, history)
        engine.onPollResult(SourceResult.Success(reading), live, GlucoseUnit.MG_DL, false)
        val snapshot = store.state

        engine.clearHistory()

        assertEquals(emptyList<AlertHistoryEntry>(), history.events.value)
        assertEquals(snapshot, store.state)
    }

    @Test fun clearHistoryWaitsForPendingAppendAndClearsItLast() {
        val appendEntered = CountDownLatch(1)
        val resumeAppend = CountDownLatch(1)
        val mutable = MutableStateFlow<List<AlertHistoryEntry>>(emptyList())
        val history = object : AlertHistoryPersistence {
            override val events: StateFlow<List<AlertHistoryEntry>> = mutable
            override fun append(entry: AlertHistoryEntry) {
                appendEntered.countDown()
                check(resumeAppend.await(3, TimeUnit.SECONDS))
                mutable.value = listOf(entry)
            }
            override fun clear() { mutable.value = emptyList() }
        }
        val reading = reading(305.0, start)
        val live = display(reading)
        val engine = AlertEngine(
            MemoryStore(), MemoryDelivery(), MutableClock(start), { live }, { false }, history,
        )
        val executor = Executors.newFixedThreadPool(2)
        try {
            val post = executor.submit {
                engine.onPollResult(SourceResult.Success(reading), live, GlucoseUnit.MG_DL, false)
            }
            assertTrue(appendEntered.await(3, TimeUnit.SECONDS))
            val clear = executor.submit { engine.clearHistory() }
            assertFalse(clear.isDone)
            resumeAppend.countDown()
            post.get(3, TimeUnit.SECONDS)
            clear.get(3, TimeUnit.SECONDS)
            assertEquals(emptyList<AlertHistoryEntry>(), history.events.value)
        } finally {
            resumeAppend.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun currentPrivacyChoiceWinsOverStalePollPreference() {
        val store = MemoryStore()
        val delivery = MemoryDelivery()
        val reading = reading(305.0, start)
        val live = display(reading)
        AlertEngine(store, delivery, MutableClock(start), { live }, { false })
            .onPollResult(SourceResult.Success(reading), live, GlucoseUnit.MG_DL, true)
        assertEquals(false, delivery.lastVisibleOnLockScreen)
    }

    @Test fun privacyChangeWaitsForAnInFlightAlertAndClearsItLast() {
        val visible = AtomicBoolean(true)
        val hostVisible = AtomicBoolean(false)
        val postEntered = CountDownLatch(1)
        val resumePost = CountDownLatch(1)
        val privacyRequested = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val reading = reading(305.0, start)
        val live = display(reading)
        val delivery = object : AlertDelivery {
            override fun ensureChannels() = Unit
            override fun canPost(kind: AlertKind) = true
            override fun post(kind: AlertKind, display: DisplayState, unit: GlucoseUnit, showOnLockScreen: Boolean) {
                postEntered.countDown()
                check(resumePost.await(3, TimeUnit.SECONDS))
                check(showOnLockScreen && visible.get())
                hostVisible.set(true)
            }
            override fun clear() { hostVisible.set(false) }
        }
        val engine = AlertEngine(MemoryStore(), delivery, MutableClock(start), { live }, { visible.get() })
        try {
            val post = executor.submit {
                engine.onPollResult(SourceResult.Success(reading), live, GlucoseUnit.MG_DL, true)
            }
            assertTrue(postEntered.await(3, TimeUnit.SECONDS))
            val privacy = executor.submit {
                privacyRequested.countDown()
                engine.changePrivacy { visible.set(false) }
            }
            assertTrue(privacyRequested.await(3, TimeUnit.SECONDS))
            assertTrue(visible.get())
            resumePost.countDown()
            post.get(3, TimeUnit.SECONDS)
            privacy.get(3, TimeUnit.SECONDS)
            assertFalse(visible.get())
            assertFalse(hostVisible.get())
        } finally {
            resumePost.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun failedPrivacyPersistenceStillClearsExistingAlerts() {
        val delivery = MemoryDelivery()
        val reading = reading(305.0, start)
        val live = display(reading)
        val engine = AlertEngine(MemoryStore(), delivery, MutableClock(start), { live }, { true })
        engine.onPollResult(SourceResult.Success(reading), live, GlucoseUnit.MG_DL, true)
        assertEquals(1, delivery.posted.size)
        assertThrows(java.io.IOException::class.java) {
            engine.changePrivacy { throw java.io.IOException("synthetic failed revocation") }
        }
        assertEquals(1, delivery.clears)
    }

    private fun reading(value: Double, time: Instant) = Reading(
        person.id, time, value, value, GlucoseUnit.MG_DL, null, Trend.STEADY, null, time,
    )

    private fun display(reading: Reading) = DisplayState(
        true, RuntimeState.RUNNING, ConnectionState.READY, person, reading,
        ReadingFreshness.FRESH, reading.receivedAt, null,
    )

    private class MemoryStore(private val enabledKinds: Set<AlertKind> = setOf(AlertKind.URGENT_HIGH)) : AlertStore {
        private val defaults = AlertConfig.defaults()
        var state = AlertSnapshot()
        override fun config() = AlertConfig(defaults.rules.mapValues { (kind, rule) ->
            rule.copy(enabled = kind in enabledKinds)
        })
        override fun snapshot() = state
        override fun saveSnapshot(snapshot: AlertSnapshot) { state = snapshot }
    }

    private class MemoryDelivery : AlertDelivery {
        var allowed = true
        var clears = 0
        var lastVisibleOnLockScreen: Boolean? = null
        val posted = mutableListOf<AlertKind>()
        override fun ensureChannels() = Unit
        override fun canPost(kind: AlertKind) = allowed
        override fun post(kind: AlertKind, display: DisplayState, unit: GlucoseUnit, showOnLockScreen: Boolean) {
            posted += kind
            lastVisibleOnLockScreen = showOnLockScreen
        }
        override fun clear() { clears++ }
    }

    private class MemoryHistory(private val failAppend: Boolean = false) : AlertHistoryPersistence {
        private val mutable = MutableStateFlow<List<AlertHistoryEntry>>(emptyList())
        override val events: StateFlow<List<AlertHistoryEntry>> = mutable
        override fun append(entry: AlertHistoryEntry) {
            if (failAppend) throw java.io.IOException("synthetic history failure")
            mutable.value = listOf(entry) + mutable.value
        }
        override fun clear() { mutable.value = emptyList() }
    }

    private class MutableClock(var now: Instant) : Clock {
        override fun now() = now
        override fun elapsedRealtimeMillis() = 0L
    }
}
