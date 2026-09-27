package com.khaleel.glance.monitoring

import android.app.Notification
import android.app.Service
import com.khaleel.glance.auth.AuthPersistence
import com.khaleel.glance.auth.SavedAuth
import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.GlucoseSource
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.Session
import com.khaleel.glance.core.SourceResult
import com.khaleel.glance.core.Trend
import com.khaleel.glance.data.AppController
import com.khaleel.glance.data.PatientPersistence
import com.khaleel.glance.data.SettingsPersistence
import com.khaleel.glance.data.StoredPatientState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PollCoordinatorTest {
    private val base = Instant.parse("2026-09-27T12:00:00Z")
    private val patrick = Connection("person-a", "Synthetic Person A")
    private val other = Connection("person-b", "Synthetic Person B")

    @Test
    fun concurrentRefreshCoalescesInsteadOfStartingSecondFetch() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fixture = fixture(sourceResult = {
            entered.complete(Unit)
            release.await()
            SourceResult.Success(reading(patrick))
        })

        val first = async { fixture.coordinator.pollIfEligible(PollTrigger.AUTOMATIC, 7L) }
        entered.await()
        val second = fixture.coordinator.pollIfEligible(PollTrigger.MANUAL, 7L)
        release.complete(Unit)

        assertEquals(PollDecision.COALESCED, second)
        assertEquals(PollDecision.COMPLETED, first.await())
        assertEquals(1, fixture.source.latestCalls)
    }

    @Test
    fun lateResponseNeverRendersAcrossSelectedPersonGeneration() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fixture = fixture(sourceResult = {
            entered.complete(Unit)
            release.await()
            SourceResult.Success(reading(patrick))
        })

        val poll = async { fixture.coordinator.pollIfEligible(PollTrigger.AUTOMATIC, 7L) }
        entered.await()
        fixture.controller.selectPerson(other)
        release.complete(Unit)

        assertEquals(PollDecision.COMPLETED, poll.await())
        assertEquals(other, fixture.controller.display.value.person)
        assertNull(fixture.controller.display.value.reading)
        assertTrue(fixture.notifications.presentations.all { it.display.reading?.connectionId != patrick.id })
    }

    @Test
    fun persistedCooldownBlocksAutomaticAndManualPolls() = runBlocking {
        val fixture = fixture(
            nextAllowed = base.plusSeconds(900),
            sourceResult = { SourceResult.Success(reading(patrick)) },
        )

        assertEquals(PollDecision.COOLDOWN, fixture.coordinator.pollIfEligible(PollTrigger.AUTOMATIC, 7L))
        assertEquals(PollDecision.COOLDOWN, fixture.coordinator.pollIfEligible(PollTrigger.MANUAL, 7L))
        assertEquals(0, fixture.source.latestCalls)
    }

    private fun fixture(
        nextAllowed: Instant? = null,
        sourceResult: suspend () -> SourceResult<Reading>,
    ): Fixture {
        val clock = FakeClock(base, 1_000L)
        val source = FakeSource(sourceResult)
        val settings = MemorySettings().apply {
            monitoringEnabled = true
            nextAllowedFetch = nextAllowed
        }
        val controller = AppController(
            source = source,
            authStore = MemoryAuth(savedAuth()),
            patientStore = MemoryPatient(StoredPatientState(patrick, null)),
            settings = settings,
            clock = clock,
        )
        val notifications = FakeNotifications()
        val dependencies = MonitoringDependencies(controller, notifications, clock)
        return Fixture(
            coordinator = PollCoordinator(dependencies, kotlinx.coroutines.CoroutineScope(Job())),
            controller = controller,
            source = source,
            notifications = notifications,
        )
    }

    private fun savedAuth() = SavedAuth(
        Credentials("synthetic@example.invalid", "synthetic-password"),
        Session("synthetic-account", "synthetic-token", "api-us.libreview.io", base.plusSeconds(3600)),
    )

    private fun reading(connection: Connection) = Reading(
        connectionId = connection.id,
        measuredAt = base.minusSeconds(60),
        valueMgDl = 112.0,
        sourceValue = 112.0,
        sourceUnit = GlucoseUnit.MG_DL,
        specialValue = null,
        trend = Trend.STEADY,
        sourceRecordId = "synthetic-1",
        receivedAt = base,
    )

    private data class Fixture(
        val coordinator: PollCoordinator,
        val controller: AppController,
        val source: FakeSource,
        val notifications: FakeNotifications,
    )

    private class FakeClock(
        private val wall: Instant,
        private val elapsed: Long,
    ) : Clock {
        override fun now(): Instant = wall
        override fun elapsedRealtimeMillis(): Long = elapsed
    }

    private class MemoryAuth(private var saved: SavedAuth?) : AuthPersistence {
        override fun save(auth: SavedAuth) { saved = auth }
        override fun load(): SavedAuth? = saved
        override fun clear() { saved = null }
    }

    private class MemoryPatient(initial: StoredPatientState) : PatientPersistence {
        private var state = initial
        override fun load(): StoredPatientState = state
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

    private class FakeSource(
        private val sourceResult: suspend () -> SourceResult<Reading>,
    ) : GlucoseSource {
        @Volatile var latestCalls = 0
        override suspend fun signIn(credentials: Credentials) = SourceResult.Success(
            Session("synthetic-account", "new-token", "api-us.libreview.io", Instant.parse("2030-01-01T00:00:00Z")),
        )
        override suspend fun connections(session: Session) = SourceResult.Success(emptyList<Connection>())
        override suspend fun latest(session: Session, connectionId: String): SourceResult<Reading> {
            latestCalls++
            return sourceResult()
        }
    }

    private class FakeNotifications : MonitoringNotifications {
        val presentations = mutableListOf<MonitoringPresentation>()
        override fun startingNotification(
            service: Service,
            presentation: MonitoringPresentation,
        ): Notification = error("not used by coordinator tests")

        override fun render(presentation: MonitoringPresentation) {
            presentations += presentation
        }

        override fun hide() = Unit
    }
}

