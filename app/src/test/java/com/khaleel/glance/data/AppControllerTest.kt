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
import com.khaleel.glance.core.SourceResponseFormat
import com.khaleel.glance.core.SourceResponseInfo
import com.khaleel.glance.core.Trend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
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

    @Test fun rejectedResponseRetainsDiagnosticsAndNeverReplacesLastReading() = runBlocking {
        val previous = reading(patrick)
        val error = SourceError(SourceErrorKind.PROTOCOL,
            responseInfo = SourceResponseInfo(403, SourceResponseFormat.WEB_PAGE))
        val source = FakeSource { _, _ -> SourceResult.Failure(error) }
        var recorded: SourceError? = null
        val diagnostics = object : DiagnosticsRecorder {
            override fun pollAttempt(at: Instant) = Unit
            override fun pollSuccess(at: Instant) = Unit
            override fun pollFailure(at: Instant, kind: SourceErrorKind) = Unit
            override fun sourceFailure(at: Instant, error: SourceError) { recorded = error }
            override fun runtime(at: Instant, state: String) = Unit
            override fun snapshot() = DiagnosticsSnapshot(null, null, recorded?.kind, null, 0)
            override fun clear() = Unit
        }
        val patient = MemoryPatient().apply { state = StoredPatientState(patrick, previous) }
        val controller = AppController(source, MemoryAuth(SavedAuth(credentials, session)), patient,
            MemorySettings(), FakeClock(start), diagnostics)

        controller.poll()

        assertEquals(error, recorded)
        assertEquals(previous, controller.display.value.reading)
        assertEquals(ConnectionState.PROTOCOL_ERROR, controller.display.value.connection)
        assertEquals(previous, patient.state.lastReading)
        assertEquals(0, source.signInCalls)
    }

    @Test fun accountStartAndSignOutClearTransientTransportState() = runBlocking {
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }
        val controller = AppController(source, MemoryAuth(null), MemoryPatient(), MemorySettings(), FakeClock(start))

        assertTrue(controller.signIn(credentials) is SourceResult.Success)
        assertEquals(1, source.transportClears)
        controller.signOut()
        assertEquals(2, source.transportClears)
        assertTrue(!controller.hasAccount)
    }

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

    @Test fun fixedDnsCanRefreshWhileAutomaticNetworkRetryIsStillWaiting() = runBlocking {
        var offline = true
        val source = FakeSource { _, _ ->
            if (offline) SourceResult.Failure(SourceError(SourceErrorKind.NETWORK))
            else SourceResult.Success(reading(patrick))
        }
        val settings = MemorySettings()
        val controller = controller(source, MemoryPatient(), settings)
        controller.selectPerson(patrick)
        controller.poll()
        assertEquals(RetryReason.TRANSIENT, settings.retryWindow?.reason)
        assertTrue(settings.nextAllowedFetch!! >= start.plusSeconds(60))
        assertTrue(settings.nextAllowedFetch!! <= start.plusSeconds(75))
        offline = false
        assertTrue(controller.poll() is SourceResult.Failure)
        assertEquals(1, source.latestCalls)
        assertTrue(controller.poll(manual = true) is SourceResult.Success)
        assertEquals(2, source.latestCalls)
        assertEquals(ConnectionState.READY, controller.display.value.connection)
        assertNull(settings.retryWindow)
    }

    @Test fun repeatedManualFailuresCanRetryAfterThreeSecondsEvenAtMaximumBackoff() = runBlocking {
        val clock = FakeClock(start)
        val source = FakeSource { _, _ -> SourceResult.Failure(SourceError(SourceErrorKind.NETWORK)) }
        val settings = MemorySettings()
        val controller = controller(source, MemoryPatient(), settings, clock)
        controller.selectPerson(patrick)
        repeat(6) {
            assertEquals(SourceErrorKind.NETWORK, (controller.poll(manual = true) as SourceResult.Failure).error.kind)
            clock.advanceMillis(3_000)
        }
        assertTrue(settings.nextAllowedFetch!! >= clock.now().plusSeconds(897))
        assertEquals(SourceErrorKind.NETWORK, (controller.poll(manual = true) as SourceResult.Failure).error.kind)
        assertEquals(7, source.latestCalls)
        assertNull(controller.display.value.providerRetryAt)
    }

    @Test fun rapidManualTapsAreDebouncedUsingElapsedTimeNotWallTime() = runBlocking {
        val clock = FakeClock(start)
        val source = FakeSource { _, _ -> SourceResult.Failure(SourceError(SourceErrorKind.NETWORK)) }
        val settings = MemorySettings()
        val controller = controller(source, MemoryPatient(), settings, clock)
        controller.selectPerson(patrick)
        controller.poll(manual = true)
        val deadline = settings.retryWindow
        clock.advanceMillis(2_999)
        clock.instant = start.plusSeconds(3600) // Changing the phone clock cannot bypass debounce.
        val blocked = controller.poll(manual = true) as SourceResult.Failure
        assertEquals(SourceErrorKind.REFRESH_DEBOUNCED, blocked.error.kind)
        assertEquals(clock.now().plusMillis(1), blocked.error.retryAfter)
        assertEquals(1, source.latestCalls)
        assertEquals(deadline, settings.retryWindow)
        clock.advanceMillis(1)
        assertEquals(SourceErrorKind.NETWORK, (controller.poll(manual = true) as SourceResult.Failure).error.kind)
        assertEquals(2, source.latestCalls)
    }

    @Test fun networkBackoffRemainsAutomaticOnlyAfterControllerRecreation() = runBlocking {
        var offline = true
        val source = FakeSource { _, _ ->
            if (offline) SourceResult.Failure(SourceError(SourceErrorKind.NETWORK))
            else SourceResult.Success(reading(patrick))
        }
        val auth = MemoryAuth(SavedAuth(credentials, session))
        val patient = MemoryPatient()
        val settings = MemorySettings()
        val first = AppController(source, auth, patient, settings, FakeClock(start))
        first.selectPerson(patrick)
        first.poll()
        offline = false
        val afterRestart = AppController(source, auth, patient, settings, FakeClock(start.plusSeconds(5)))
        assertTrue(afterRestart.poll() is SourceResult.Failure)
        assertEquals(1, source.latestCalls)
        assertTrue(afterRestart.poll(manual = true) is SourceResult.Success)
        assertEquals(2, source.latestCalls)
    }

    @Test fun serverFailureCanAlsoBeRetriedManuallyBeforeAutomaticDeadline() = runBlocking {
        var failed = true
        val source = FakeSource { _, _ ->
            if (failed) SourceResult.Failure(SourceError(SourceErrorKind.SERVER))
            else SourceResult.Success(reading(patrick))
        }
        val controller = controller(source, MemoryPatient())
        controller.selectPerson(patrick)
        controller.poll()
        assertEquals(ConnectionState.BACKING_OFF, controller.display.value.connection)
        failed = false
        assertTrue(controller.poll(manual = true) is SourceResult.Success)
        assertEquals(2, source.latestCalls)
    }

    @Test fun networkFailureDuringSessionRecoveryIsCountedOnceAndKeepsItsReason() = runBlocking {
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }.apply {
            signInResult = SourceResult.Failure(SourceError(SourceErrorKind.NETWORK))
        }
        val settings = MemorySettings()
        val clock = FakeClock(start.plusSeconds(7200))
        val controller = controller(source, MemoryPatient(), settings, clock)
        controller.selectPerson(patrick)
        val result = controller.poll() as SourceResult.Failure
        assertEquals(SourceErrorKind.NETWORK, result.error.kind)
        assertTrue(settings.nextAllowedFetch!! <= clock.now().plusSeconds(75))
        assertEquals(RetryReason.TRANSIENT, settings.retryWindow?.reason)
        source.signInResult = SourceResult.Success(session.copy(expiresAt = clock.now().plusSeconds(3600)))
        assertTrue(controller.poll(manual = true) is SourceResult.Success)
        assertEquals(1, source.latestCalls)
    }

    @Test fun providerLimitDuringSessionRecoveryRemainsHardAfterRecreation() = runBlocking {
        val retryAt = start.plusSeconds(8100)
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }.apply {
            signInResult = SourceResult.Failure(SourceError(SourceErrorKind.RATE_LIMITED, retryAt))
        }
        val auth = MemoryAuth(SavedAuth(credentials, session))
        val patient = MemoryPatient()
        val settings = MemorySettings()
        val clock = FakeClock(start.plusSeconds(7200))
        val first = AppController(source, auth, patient, settings, clock)
        first.selectPerson(patrick)
        assertEquals(SourceErrorKind.RATE_LIMITED, (first.poll() as SourceResult.Failure).error.kind)
        assertEquals(retryAt, first.display.value.providerRetryAt)
        val restarted = AppController(source, auth, patient, settings, clock)
        assertEquals(SourceErrorKind.RATE_LIMITED, (restarted.poll(manual = true) as SourceResult.Failure).error.kind)
        assertEquals(retryAt, settings.nextAllowedFetch)
        assertEquals(0, source.latestCalls)
    }

    @Test fun manualAndAutomaticPollsStillShareOneRequestAfterNetworkFailure() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val pending = CompletableDeferred<SourceResult<Reading>>()
        var failed = true
        val source = FakeSource { _, _ ->
            if (failed) SourceResult.Failure(SourceError(SourceErrorKind.NETWORK))
            else { entered.complete(Unit); pending.await() }
        }
        val controller = controller(source, MemoryPatient())
        controller.selectPerson(patrick)
        controller.poll()
        failed = false
        val manual = async { controller.poll(manual = true) }
        entered.await()
        val automatic = async { controller.poll() }
        kotlinx.coroutines.yield()
        pending.complete(SourceResult.Success(reading(patrick)))
        assertTrue(manual.await() is SourceResult.Success)
        assertTrue(automatic.await() is SourceResult.Success)
        assertEquals(2, source.latestCalls)
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

    @Test fun minuteSamplesSurviveEqualValuesSparseBackfillDuplicatePollsAndRecreation() = runBlocking {
        val clock = FakeClock(start)
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick, clock.now().minusSeconds(60))) }.apply {
            snapshotHistory = listOf(reading(patrick, start.minusSeconds(1800)), reading(patrick, start.minusSeconds(900)))
        }
        val patient = MemoryPatient()
        val settings = MemorySettings()
        val first = controller(source, patient, settings, clock)
        first.selectPerson(patrick)
        val observedTimes = (0..5).map {
            first.poll()
            val time = clock.now().minusSeconds(60)
            clock.advanceMillis(60_000)
            time
        }
        assertEquals(8, patient.state.history.size)
        assertTrue(patient.state.history.map { it.measuredAt }.containsAll(observedTimes))
        val restored = controller(source, patient, settings, clock)
        assertEquals(patient.state.history, restored.display.value.history)
        // Repeated HTTP success with one source time must not create artificial minute samples.
        restored.poll()
        val stored = patient.state.history
        restored.poll()
        assertEquals(stored, patient.state.history)
        assertEquals(9, patient.state.history.size)
        assertTrue(patient.state.history.map { it.measuredAt }.containsAll(observedTimes))
    }

    @Test fun missingPollsKeepRealHistoryWithoutInventingMeasurementsInTheGap() = runBlocking {
        val clock = FakeClock(start)
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick, clock.now().minusSeconds(60))) }
        val patient = MemoryPatient()
        val controller = controller(source, patient, clock = clock)
        controller.selectPerson(patrick)
        controller.poll()
        clock.advanceMillis(60 * 60_000)
        controller.poll()
        assertEquals(listOf(start.minusSeconds(60), start.plusSeconds(3540)), patient.state.history.map { it.measuredAt })
    }

    @Test fun nicknameIsNormalizedAndChangesDisplayIdentityOnly() = runBlocking {
        val patient = MemoryPatient()
        val nicknames = MemoryNicknames()
        val source = FakeSource { _, connectionId ->
            assertEquals(patrick.id, connectionId)
            SourceResult.Success(reading(patrick))
        }
        val controller = controller(source, patient, nicknameStore = nicknames)
        controller.selectPerson(patrick)
        controller.poll()
        val readingBefore = controller.display.value.reading
        val historyBefore = controller.display.value.history

        val result = controller.setNickname("  My\n\u0000   Patrick  ")

        assertTrue(result is SourceResult.Success)
        assertEquals("My Patrick", controller.nickname)
        assertEquals("Patrick Example", controller.sourcePersonName)
        assertEquals(Connection(patrick.id, "My Patrick"), controller.display.value.person)
        assertEquals(patrick, patient.state.selected)
        assertEquals(readingBefore, controller.display.value.reading)
        assertEquals(historyBefore, controller.display.value.history)
        assertEquals(patrick.id, controller.display.value.reading?.connectionId)
    }

    @Test fun nicknameSurvivesRelaunchAndPersonSwitchThenBlankRestoresSourceName() {
        val patient = MemoryPatient()
        val auth = MemoryAuth(SavedAuth(credentials, session))
        val nicknames = MemoryNicknames()
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }
        val first = AppController(source, auth, patient, MemorySettings(), FakeClock(start), nicknameStore = nicknames)
        first.selectPerson(patrick)
        first.setNickname("P")
        first.selectPerson(other)
        first.setNickname("O")
        first.selectPerson(patrick)

        val relaunched = AppController(
            source, auth, patient, MemorySettings(), FakeClock(start), nicknameStore = nicknames,
        )
        assertEquals("P", relaunched.nickname)
        assertEquals("P", relaunched.display.value.person?.displayName)
        relaunched.selectPerson(other)
        assertEquals("O", relaunched.nickname)
        assertEquals("Other Example", relaunched.sourcePersonName)
        relaunched.setNickname(" \n\t ")
        assertEquals("", relaunched.nickname)
        assertEquals(other, relaunched.display.value.person)
        assertEquals(mapOf(patrick.id to "P"), nicknames.values)
    }

    @Test fun nicknameScopeSurvivesSameAccountReconnectButClearsForNewAccountAndSignOut() = runBlocking {
        val patient = MemoryPatient().apply { state = StoredPatientState(patrick, null) }
        val auth = MemoryAuth(SavedAuth(credentials, session))
        val nicknames = MemoryNicknames(session.accountId, mapOf(patrick.id to "P"))
        val source = FakeSource(
            onConnections = { SourceResult.Success(listOf(patrick)) },
            onLatest = { _, _ -> SourceResult.Success(reading(patrick)) },
        )
        val controller = AppController(
            source, auth, patient, MemorySettings(), FakeClock(start), nicknameStore = nicknames,
        )

        assertTrue(controller.signIn(credentials) is SourceResult.Success)
        controller.selectPerson(patrick)
        assertEquals("P", controller.nickname)
        assertEquals(0, nicknames.clearCalls)

        source.signInResult = SourceResult.Success(session.copy(accountId = "different-account"))
        assertTrue(controller.signIn(credentials) is SourceResult.Success)
        controller.selectPerson(patrick)
        assertEquals("", controller.nickname)
        assertEquals("Patrick Example", controller.display.value.person?.displayName)
        assertEquals(1, nicknames.clearCalls)

        controller.setNickname("Again")
        controller.signOut()
        assertEquals("", controller.nickname)
        assertTrue(nicknames.values.isEmpty())
        assertEquals(2, nicknames.clearCalls)
    }

    @Test fun nicknameStorageFailureKeepsTheEntirePriorDisplay() {
        val patient = MemoryPatient().apply { state = StoredPatientState(patrick, null) }
        val nicknames = MemoryNicknames(session.accountId, mapOf(patrick.id to "Old alias")).apply {
            failSave = true
        }
        val controller = controller(
            FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }, patient,
            nicknameStore = nicknames,
        )
        val prior = controller.display.value

        val result = controller.setNickname("New alias")

        assertEquals(SourceErrorKind.STORAGE, (result as SourceResult.Failure).error.kind)
        assertEquals("Old alias", controller.nickname)
        assertEquals(prior, controller.display.value)
        assertEquals(mapOf(patrick.id to "Old alias"), nicknames.values)
    }

    @Test fun signOutReportsResidualEncryptedNicknameFailureAndClearsVisibleAccount() {
        val nicknames = MemoryNicknames(session.accountId, mapOf(patrick.id to "P"))
        val patient = MemoryPatient().apply { save(StoredPatientState(patrick, reading(patrick))) }
        val auth = MemoryAuth(SavedAuth(credentials, session))
        val source = FakeSource { _, _ -> SourceResult.Success(reading(patrick)) }
        val controller = AppController(source, auth, patient, MemorySettings(), FakeClock(start), nicknameStore = nicknames)
        nicknames.failClear = true
        assertFalse(controller.signOut())
        assertFalse(controller.hasAccount)
        assertNull(controller.display.value.person)
        assertNull(controller.display.value.reading)
        assertEquals("", controller.nickname)
        assertEquals(mapOf(patrick.id to "P"), nicknames.values)
    }

    @Test fun nicknameBoundUsesUnicodeCodePointsWithoutSplittingSurrogates() {
        val normalized = normalizeFollowerNickname("😀".repeat(41))

        assertEquals(40, normalized.codePointCount(0, normalized.length))
        assertEquals(80, normalized.length)
        assertEquals(0x1F600, normalized.codePointBefore(normalized.length))
    }

    private fun controller(
        source: FakeSource,
        patient: MemoryPatient,
        settings: MemorySettings = MemorySettings(),
        clock: FakeClock = FakeClock(start),
        nicknameStore: NicknamePersistence = NoNicknamePersistence,
    ) = AppController(
        source, MemoryAuth(SavedAuth(credentials, session)), patient, settings, clock,
        nicknameStore = nicknameStore,
    )

    private class FakeClock(var instant: Instant) : Clock {
        override fun now() = instant
        private var elapsed = 0L
        override fun elapsedRealtimeMillis() = elapsed
        fun advanceMillis(millis: Long) { instant = instant.plusMillis(millis); elapsed += millis }
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
    private class MemoryNicknames(
        private var accountId: String? = null,
        initial: Map<String, String> = emptyMap(),
    ) : NicknamePersistence {
        var values = initial.toMap()
        var failSave = false
        var failClear = false
        var clearCalls = 0
        override fun load(accountId: String): Map<String, String> =
            if (this.accountId == null || this.accountId == accountId) values else emptyMap()
        override fun save(accountId: String, nicknames: Map<String, String>) {
            if (failSave) throw IllegalStateException("synthetic nickname storage failure")
            this.accountId = accountId
            values = nicknames.toMap()
        }
        override fun clear() {
            clearCalls++
            if (failClear) throw IllegalStateException("synthetic nickname clear failure")
            accountId = null
            values = emptyMap()
        }
    }
    private class MemorySettings : SettingsPersistence {
        override var unit = GlucoseUnit.MG_DL
        override var showOnLockScreen = false
        override var enhancedNotification = true
        override var numericStatusIcon = true
        override var accentHex = "#087E82"
        override var intervalMinutes = 1
        override var resumeAfterBoot = true
        override var monitoringEnabled = false
        override var retryWindow: com.khaleel.glance.data.RetryWindow? = null
        override fun clear() { monitoringEnabled = false; retryWindow = null }
    }
    private class FakeSource(
        val onConnections: suspend (Session) -> SourceResult<List<Connection>> = { SourceResult.Success(emptyList()) },
        val onLatest: suspend (Session, String) -> SourceResult<Reading>,
    ) : GlucoseSource {
        var snapshotHistory: List<Reading> = emptyList()
        @Volatile
        var latestCalls = 0
        var signInCalls = 0
        var transportClears = 0
        override fun clearTransportSession() { transportClears++ }
        var signInResult: SourceResult<Session> = SourceResult.Success(
            Session("synthetic-account", "new-token", "api-us.libreview.io", Instant.parse("2030-01-01T00:00:00Z")),
        )
        override suspend fun signIn(credentials: Credentials): SourceResult<Session> {
            signInCalls++
            return signInResult
        }
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
