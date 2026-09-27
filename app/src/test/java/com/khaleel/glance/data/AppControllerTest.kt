package com.khaleel.glance.data

import com.khaleel.glance.auth.AuthPersistence
import com.khaleel.glance.auth.SavedAuth
import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.GlucoseSource
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.Session
import com.khaleel.glance.core.SourceError
import com.khaleel.glance.core.SourceErrorKind
import com.khaleel.glance.core.SourceResult
import com.khaleel.glance.core.Trend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AppControllerTest {
    private val start = Instant.parse("2026-09-27T12:00:00Z")
    private val patrick = Connection("person-p", "Patrick Example")
    private val other = Connection("person-o", "Other Example")
    private val session = Session("synthetic-account", "synthetic-token", "api-us.libreview.io", start.plusSeconds(3600))
    private val credentials = Credentials("example@example.invalid", "synthetic-password")
    private fun reading(connection: Connection, at: Instant = start.minusSeconds(60)) = Reading(
        connection.id, at, 112.0, 112.0, GlucoseUnit.MG_DL, null,
        Trend.STEADY, null, start,
    )

    @Test fun lateResponseCannotCrossPersonBoundary() = runBlocking {
        val pending = CompletableDeferred<SourceResult<Reading>>()
        val source = FakeSource { _, _ -> pending.await() }
        val patient = MemoryPatient()
        val controller = controller(source, patient)
        controller.selectPerson(patrick)
        val oldFetch = async { controller.poll() }
        withTimeout(5_000) { while (source.latestCalls == 0) kotlinx.coroutines.yield() }
        controller.selectPerson(other)
        pending.complete(SourceResult.Success(reading(patrick)))
        oldFetch.await()
        assertEquals(other, controller.display.value.person)
        assertNull(controller.display.value.reading)
        assertNull(patient.state.lastReading)
    }

    @Test fun repeatedSuccessfulResponseDoesNotMakeOldReadingFresh() = runBlocking {
        val clock = FakeClock(start)
        val old = reading(patrick)
        val source = FakeSource { _, _ -> SourceResult.Success(old.copy(receivedAt = clock.now())) }
        val controller = controller(source, MemoryPatient(), MemorySettings(), clock)
        controller.selectPerson(patrick)
        controller.poll()
        clock.instant = start.plusSeconds(6 * 60)
        controller.poll()
        assertEquals(ReadingFreshness.DELAYED, controller.display.value.freshness)
        assertEquals(start.minusSeconds(60), controller.display.value.reading?.measuredAt)
        assertEquals(start, controller.display.value.reading?.receivedAt)
    }

    @Test fun providerCooldownSurvivesControllerRecreationAndRefresh() = runBlocking {
        val retryAt = start.plusSeconds(900)
        val source = FakeSource { _, _ -> SourceResult.Failure(SourceError(SourceErrorKind.RATE_LIMITED, retryAt)) }
        val auth = MemoryAuth(SavedAuth(credentials, session))
        val patient = MemoryPatient()
        val settings = MemorySettings()
        val first = AppController(source, auth, patient, settings, FakeClock(start))
        first.selectPerson(patrick)
        first.poll()
        assertEquals(retryAt, settings.nextAllowedFetch)
        val afterRestart = AppController(source, auth, patient, settings, FakeClock(start.plusSeconds(30)))
        val result = afterRestart.poll(manual = true)
        assertEquals(SourceErrorKind.RATE_LIMITED, (result as SourceResult.Failure).error.kind)
        assertEquals(1, source.latestCalls)
    }

    private fun controller(
        source: FakeSource,
        patient: MemoryPatient,
        settings: MemorySettings = MemorySettings(),
        clock: FakeClock = FakeClock(start),
    ) = AppController(source, MemoryAuth(SavedAuth(credentials, session)), patient, settings, clock)

    private class FakeClock(var instant: Instant) : Clock {
        override fun now() = instant
        override fun elapsedRealtimeMillis() = 0L
    }
    private class MemoryAuth(var saved: SavedAuth?) : AuthPersistence {
        override fun save(auth: SavedAuth) { saved = auth }
        override fun load() = saved
        override fun clear() { saved = null }
    }
    private class MemoryPatient : PatientPersistence {
        var state = StoredPatientState(null, null)
        override fun load() = state
        override fun save(state: StoredPatientState) { this.state = state }
        override fun clear() { state = StoredPatientState(null, null) }
    }
    private class MemorySettings : SettingsPersistence {
        override var unit = GlucoseUnit.MG_DL
        override var showOnLockScreen = false
        override var enhancedNotification = true
        override var intervalMinutes = 1
        override var resumeAfterBoot = true
        override var monitoringEnabled = false
        override var nextAllowedFetch: Instant? = null
        override fun clear() { monitoringEnabled = false; nextAllowedFetch = null }
    }
    private class FakeSource(val onLatest: suspend (Session, String) -> SourceResult<Reading>) : GlucoseSource {
        @Volatile
        var latestCalls = 0
        override suspend fun signIn(credentials: Credentials) = SourceResult.Success(
            Session("synthetic-account", "new-token", "api-us.libreview.io", Instant.parse("2030-01-01T00:00:00Z")),
        )
        override suspend fun connections(session: Session) = SourceResult.Success(emptyList<Connection>())
        override suspend fun latest(session: Session, connectionId: String): SourceResult<Reading> {
            latestCalls++
            return onLatest(session, connectionId)
        }
    }
}
