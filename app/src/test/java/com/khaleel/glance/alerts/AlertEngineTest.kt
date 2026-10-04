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

    @Test fun onlyPublishedNewReadingPostsAndCooldownSurvivesEngineRecreation() {
        val store = MemoryStore()
        val delivery = MemoryDelivery()
        val clock = MutableClock(start)
        val reading = reading(305.0, start)
        val display = display(reading)
        var live = display

        AlertEngine(store, delivery, clock, { live }, { false }).onPollResult(SourceResult.Success(reading), display, GlucoseUnit.MG_DL, false)
        assertEquals(listOf(AlertKind.URGENT_HIGH), delivery.posted)

        clock.now = start.plusSeconds(60)
        AlertEngine(store, delivery, clock, { live }, { false }).onPollResult(SourceResult.Success(reading), display, GlucoseUnit.MG_DL, false)
        live = display(reading(310.0, start.plusSeconds(60)))
        AlertEngine(store, delivery, clock, { live }, { false }).onPollResult(
            SourceResult.Success(reading(310.0, start.plusSeconds(60))),
            live, GlucoseUnit.MG_DL, false,
        )
        assertEquals(1, delivery.posted.size)

        clock.now = start.plusSeconds(300)
        val next = reading(315.0, start.plusSeconds(300))
        live = display(next)
        AlertEngine(store, delivery, clock, { live }, { false }).onPollResult(SourceResult.Success(next), live, GlucoseUnit.MG_DL, false)
        assertEquals(listOf(AlertKind.URGENT_HIGH, AlertKind.URGENT_HIGH), delivery.posted)
    }

    @Test fun blockedChannelAndUnpublishedResponseNeverPost() {
        val store = MemoryStore()
        val delivery = MemoryDelivery().apply { allowed = false }
        val history = MemoryHistory()
        val clock = MutableClock(start)
        val reading = reading(305.0, start)
        var live = display(reading)
        val engine = AlertEngine(store, delivery, clock, { live }, { false }, history)
        engine.onPollResult(SourceResult.Success(reading), display(reading).copy(reading = null), GlucoseUnit.MG_DL, false)
        live = live.copy(person = Connection("other", "Other"))
        engine.onPollResult(SourceResult.Success(reading), display(reading), GlucoseUnit.MG_DL, false)
        live = display(reading)
        engine.onPollResult(SourceResult.Success(reading), display(reading), GlucoseUnit.MG_DL, false)
        assertEquals(emptyList<AlertKind>(), delivery.posted)
        assertEquals(emptyMap<AlertKind, Instant>(), store.state.lastFiredAt)
        assertEquals(emptyList<AlertHistoryEntry>(), history.events.value)
    }

    @Test fun eachPersonUsesCurrentStateOwnStoreAndOwnNotificationScope() {
        val other = Connection("alex", "Alex")
        val patrickReading = reading(305.0, start)
        val alexReading = Reading(
            other.id, start.plusSeconds(1), 52.0, 52.0, GlucoseUnit.MG_DL,
            null, Trend.STEADY, "alex-source", start.plusSeconds(1),
        )
        val displays = mutableMapOf(
            person.id to display(patrickReading),
            other.id to display(patrickReading).copy(person = other, reading = alexReading),
        )
        val stores = mapOf(
            person.id to MemoryStore(setOf(AlertKind.URGENT_HIGH)),
            other.id to MemoryStore(setOf(AlertKind.URGENT_LOW)),
        )
        val delivery = MemoryDelivery()
        val history = MemoryHistory()
        val clock = MutableClock(start.plusSeconds(1))
        val engine = AlertEngine(
            stores.getValue(person.id), delivery, clock, { displays.getValue(person.id) }, { false },
            history = history,
            personStore = { stores.getValue(it) },
            currentPersonDisplay = { displays[it] },
        )

        engine.onPollResult(
            SourceResult.Success(patrickReading), displays.getValue(person.id), GlucoseUnit.MG_DL, false,
        )
        engine.onPollResult(
            SourceResult.Success(alexReading), displays.getValue(other.id), GlucoseUnit.MG_DL, false,
        )

        assertEquals(listOf(AlertKind.URGENT_HIGH, AlertKind.URGENT_LOW), delivery.posted)
        assertEquals(listOf(person.id, other.id), delivery.postedPeople)
        assertEquals(setOf(person.id, other.id), history.events.value.map { it.person.id }.toSet())
        history.events.value.associateBy { it.person.id }.let { logged ->
            assertEquals(AlertKind.URGENT_HIGH, logged.getValue(person.id).kind)
            assertEquals(patrickReading.measuredAt, logged.getValue(person.id).reading.measuredAt)
            assertEquals(patrickReading.valueMgDl, logged.getValue(person.id).reading.valueMgDl)
            assertEquals(clock.now, logged.getValue(person.id).firedAt)
        }
        assertTrue(stores.getValue(person.id).state.lastFiredAt.containsKey(AlertKind.URGENT_HIGH))
        assertTrue(stores.getValue(other.id).state.lastFiredAt.containsKey(AlertKind.URGENT_LOW))

        val normal = reading(110.0, start.plusSeconds(60))
        displays[person.id] = display(normal)
        clock.now = start.plusSeconds(60)
        engine.onPollResult(SourceResult.Success(normal), displays.getValue(person.id), GlucoseUnit.MG_DL, false)
        assertEquals(listOf(person.id), delivery.clearedPeople)
    }

    @Test fun staleCallbackForPersonCannotUseAnotherPersonsCurrentDisplay() {
        val other = Connection("alex", "Alex")
        val returned = reading(305.0, start)
        val wrong = display(returned).copy(
            person = other,
            reading = returned.copy(connectionId = other.id),
        )
        val delivery = MemoryDelivery()
        AlertEngine(
            MemoryStore(), delivery, MutableClock(start), { wrong }, { false },
            currentPersonDisplay = { wrong },
        ).onPollResult(SourceResult.Success(returned), display(returned), GlucoseUnit.MG_DL, false)
        assertEquals(emptyList<AlertKind>(), delivery.posted)
    }

    @Test fun failedNotificationPostDoesNotCreateHistoryOrCooldown() {
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
        assertEquals(emptyMap<AlertKind, Instant>(), store.state.lastFiredAt)
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

    private class MemoryStore(private val enabled: Set<AlertKind> = setOf(AlertKind.URGENT_HIGH)) : AlertStore {
        private val defaults = AlertConfig.defaults()
        var state = AlertSnapshot()
        override fun config() = AlertConfig(defaults.rules.mapValues { (kind, rule) ->
            rule.copy(enabled = kind in enabled)
        })
        override fun snapshot() = state
        override fun saveSnapshot(snapshot: AlertSnapshot) { state = snapshot }
    }

    private class MemoryDelivery : AlertDelivery {
        var allowed = true
        var clears = 0
        var lastVisibleOnLockScreen: Boolean? = null
        val posted = mutableListOf<AlertKind>()
        val postedPeople = mutableListOf<String>()
        val clearedPeople = mutableListOf<String>()
        override fun ensureChannels() = Unit
        override fun canPost(kind: AlertKind) = allowed
        override fun post(kind: AlertKind, display: DisplayState, unit: GlucoseUnit, showOnLockScreen: Boolean) {
            posted += kind
            postedPeople += display.person!!.id
            lastVisibleOnLockScreen = showOnLockScreen
        }
        override fun clear() { clears++ }
        override fun clearPerson(personId: String) { clearedPeople += personId }
    }

    private class MemoryHistory : AlertHistoryPersistence {
        private val mutable = MutableStateFlow<List<AlertHistoryEntry>>(emptyList())
        override val events: StateFlow<List<AlertHistoryEntry>> = mutable
        override fun append(entry: AlertHistoryEntry) { mutable.value = listOf(entry) + mutable.value }
        override fun clear() { mutable.value = emptyList() }
    }

    private class MutableClock(var now: Instant) : Clock {
        override fun now() = now
        override fun elapsedRealtimeMillis() = 0L
    }
}
