package com.khaleel.glance.data

import com.khaleel.glance.auth.AuthPersistence
import com.khaleel.glance.auth.SavedAuth
import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.GlucoseSource
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.Session
import com.khaleel.glance.core.SourceError
import com.khaleel.glance.core.SourceErrorKind
import com.khaleel.glance.core.SourceResult
import com.khaleel.glance.core.SourceSnapshot
import com.khaleel.glance.core.Trend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun networkFailurePersistsRetryDeadlineAndBlocksManualRefresh() = runBlocking {
        val source = FakeSource { _, _ -> SourceResult.Failure(SourceError(SourceErrorKind.NETWORK)) }
        val settings = MemorySettings()
        val controller = controller(source, MemoryPatient(), settings)
        controller.selectPerson(patrick)
        controller.poll()
        assertTrue(settings.nextAllowedFetch!! >= start.plusSeconds(60))
        assertTrue(settings.nextAllowedFetch!! <= start.plusSeconds(75))
        assertEquals(SourceErrorKind.RATE_LIMITED, (controller.poll(manual = true) as SourceResult.Failure).error.kind)
        assertEquals(1, source.latestCalls)
    }

    @Test fun rejectedConnectionSessionReauthenticatesAndReplaysOnce() = runBlocking {
        var calls = 0
        val source = FakeSource(
            onLatest = { _, _ -> SourceResult.Success(reading(patrick)) },
            onConnections = {
                calls++
                if (calls == 1) SourceResult.Failure(SourceError(SourceErrorKind.AUTH_EXPIRED))
                else SourceResult.Success(listOf(patrick))
            },
        )
        val controller = controller(source, MemoryPatient())
        assertEquals(listOf(patrick), (controller.connections() as SourceResult.Success).value)
        assertEquals(2, calls)
    }

    @Test fun timeoutInvalidatesLateSourceResultAndPublishesBackoff() = runBlocking {
        val pending = CompletableDeferred<SourceResult<Reading>>()
        val source = FakeSource { _, _ -> withContext(NonCancellable) { pending.await() } }
        val settings = MemorySettings()
        val controller = controller(source, MemoryPatient(), settings)
        controller.selectPerson(patrick)
        val poll = async { runCatching { controller.poll() } }
        withTimeout(5_000) { while (source.latestCalls == 0) kotlinx.coroutines.yield() }
        controller.recordPollTimeout()
        pending.complete(SourceResult.Success(reading(patrick)))
        poll.await()
        assertNull(controller.display.value.reading)
        assertTrue(settings.nextAllowedFetch!! >= start.plusSeconds(60))
        assertTrue(settings.nextAllowedFetch!! <= start.plusSeconds(75))
    }

    @Test fun deletingLocalDataKeepsAccountPersonAndSettings() = runBlocking {
        val patient = MemoryPatient()
        val settings = MemorySettings().apply { unit = GlucoseUnit.MMOL_L }
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }
        val controller = controller(source, patient, settings)
        controller.selectPerson(patrick)
        controller.poll()

        controller.clearLocalData()

        assertNull(patient.state.lastReading)
        assertNull(controller.display.value.reading)
        assertTrue(controller.display.value.history.isEmpty())
        assertEquals(patrick, controller.display.value.person)
        assertEquals(GlucoseUnit.MMOL_L, settings.unit)
        assertTrue(controller.hasAccount)
    }

    @Test fun failedReadingPersistenceDoesNotPublishNewValueOrStayFetching() = runBlocking {
        val patient = MemoryPatient()
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }
        val controller = controller(source, patient)
        controller.selectPerson(patrick)
        patient.failSave = true

        val result = controller.poll()

        assertEquals(SourceErrorKind.STORAGE, (result as SourceResult.Failure).error.kind)
        assertEquals(ConnectionState.LOCAL_ERROR, controller.display.value.connection)
        assertNull(controller.display.value.reading)
        assertNull(patient.state.lastReading)
        assertEquals(1, source.latestCalls)
    }

    @Test fun failedSignInPersistenceKeepsAccountSignedOut() = runBlocking {
        val auth = MemoryAuth(null).apply { failSave = true }
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }
        val controller = AppController(source, auth, MemoryPatient(), MemorySettings(), FakeClock(start))

        val result = controller.signIn(credentials)

        assertEquals(SourceErrorKind.STORAGE, (result as SourceResult.Failure).error.kind)
        assertNull(auth.saved)
        assertTrue(!controller.hasAccount)
        assertEquals(ConnectionState.SIGN_IN_REQUIRED, controller.display.value.connection)
    }

    @Test fun failedSessionRecoveryPersistenceStopsPollingWithLocalError() = runBlocking {
        val auth = MemoryAuth(SavedAuth(credentials, session)).apply { failSave = true }
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }
        val controller = AppController(
            source, auth, MemoryPatient(), MemorySettings(), FakeClock(start.plusSeconds(7200)),
        )
        controller.selectPerson(patrick)

        val result = controller.poll()

        assertEquals(SourceErrorKind.STORAGE, (result as SourceResult.Failure).error.kind)
        assertEquals(ConnectionState.LOCAL_ERROR, controller.display.value.connection)
        assertEquals(session, auth.saved?.session)
        assertEquals(0, source.latestCalls)
        assertEquals(SourceErrorKind.STORAGE, (controller.poll() as SourceResult.Failure).error.kind)
        assertEquals(0, source.latestCalls)
    }

    @Test fun failedPersonSelectionPersistenceKeepsPriorPerson() {
        val patient = MemoryPatient()
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }
        val controller = controller(source, patient)
        controller.selectPerson(patrick)
        patient.failSave = true

        val result = controller.selectPerson(other)

        assertEquals(SourceErrorKind.STORAGE, (result as SourceResult.Failure).error.kind)
        assertEquals(ConnectionState.LOCAL_ERROR, controller.display.value.connection)
        assertEquals(patrick, controller.display.value.person)
        assertEquals(patrick, patient.state.selected)
    }

    @Test fun successfulPollsBuildPersonScopedHistoryAndSelectionClearsIt() = runBlocking {
        var calls = 0
        val source = FakeSource { _, _ ->
            calls++
            SourceResult.Success(reading(patrick, start.minusSeconds(if (calls == 1) 300 else 60)))
        }
        val patient = MemoryPatient()
        val controller = controller(source, patient)
        controller.selectPerson(patrick)

        controller.poll()
        controller.poll()

        assertEquals(2, controller.display.value.history.size)
        assertEquals(2, patient.state.history.size)
        controller.selectPerson(other)
        assertTrue(controller.display.value.history.isEmpty())
        assertTrue(patient.state.history.isEmpty())
    }

    @Test fun sourceGraphSamplesAreSavedWithCurrentReading() = runBlocking {
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }.apply {
            snapshotHistory = listOf(
                reading(patrick, start.minusSeconds(660)),
                reading(patrick, start.minusSeconds(360)),
            )
        }
        val patient = MemoryPatient()
        val controller = controller(source, patient)
        controller.selectPerson(patrick)

        controller.poll()

        assertEquals(3, controller.display.value.history.size)
        assertEquals(3, patient.state.history.size)
        assertEquals(start.minusSeconds(660), controller.display.value.history.first().measuredAt)
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
        var failSave = false
        override fun save(auth: SavedAuth) {
            if (failSave) throw IllegalStateException("synthetic storage failure")
            saved = auth
        }
        override fun load() = saved
        override fun clear() { saved = null }
    }
    private class MemoryPatient : PatientPersistence {
        var state = StoredPatientState(null, null)
        var failSave = false
        override fun load() = state
        override fun save(state: StoredPatientState) {
            if (failSave) throw IllegalStateException("synthetic storage failure")
            this.state = state
        }
        override fun clear() { state = StoredPatientState(null, null) }
    }
    private class MemorySettings : SettingsPersistence {
        override var unit = GlucoseUnit.MG_DL
        override var showOnLockScreen = false
        override var enhancedNotification = true
        override var accentHex = "#087E82"
        override var intervalMinutes = 1
        override var resumeAfterBoot = true
        override var monitoringEnabled = false
        override var nextAllowedFetch: Instant? = null
        override fun clear() { monitoringEnabled = false; nextAllowedFetch = null }
    }
    private class FakeSource(
        val onConnections: suspend (Session) -> SourceResult<List<Connection>> = { SourceResult.Success(emptyList()) },
        val onLatest: suspend (Session, String) -> SourceResult<Reading>,
    ) : GlucoseSource {
        var snapshotHistory: List<Reading> = emptyList()
        @Volatile
        var latestCalls = 0
        override suspend fun signIn(credentials: Credentials) = SourceResult.Success(
            Session("synthetic-account", "new-token", "api-us.libreview.io", Instant.parse("2030-01-01T00:00:00Z")),
        )
        override suspend fun connections(session: Session) = onConnections(session)
        override suspend fun latest(session: Session, connectionId: String): SourceResult<Reading> {
            latestCalls++
            return onLatest(session, connectionId)
        }
        override suspend fun snapshot(session: Session, connectionId: String): SourceResult<SourceSnapshot> =
            when (val result = latest(session, connectionId)) {
                is SourceResult.Success -> SourceResult.Success(SourceSnapshot(result.value, snapshotHistory))
                is SourceResult.Failure -> result
            }
    }
}
