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
        val clock = MutableClock(start)
        val reading = reading(305.0, start)
        var live = display(reading)
        val engine = AlertEngine(store, delivery, clock, { live }, { false })
        engine.onPollResult(SourceResult.Success(reading), display(reading).copy(reading = null), GlucoseUnit.MG_DL, false)
        live = live.copy(person = Connection("other", "Other"))
        engine.onPollResult(SourceResult.Success(reading), display(reading), GlucoseUnit.MG_DL, false)
        live = display(reading)
        engine.onPollResult(SourceResult.Success(reading), display(reading), GlucoseUnit.MG_DL, false)
        assertEquals(emptyList<AlertKind>(), delivery.posted)
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

    private class MemoryStore : AlertStore {
        private val defaults = AlertConfig.defaults()
        var state = AlertSnapshot()
        override fun config() = AlertConfig(defaults.rules.mapValues { (kind, rule) ->
            rule.copy(enabled = kind == AlertKind.URGENT_HIGH)
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

    private class MutableClock(var now: Instant) : Clock {
        override fun now() = now
        override fun elapsedRealtimeMillis() = 0L
    }
}
