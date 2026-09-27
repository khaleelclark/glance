package com.khaleel.glance.data

import android.os.SystemClock
import com.khaleel.glance.auth.AuthPersistence
import com.khaleel.glance.auth.SavedAuth
import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseSource
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.SourceError
import com.khaleel.glance.core.SourceErrorKind
import com.khaleel.glance.core.SourceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import kotlin.math.min
import kotlin.random.Random

object DeviceClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

/** Owns selected-person identity, last validated reading, and source freshness. */
class AppController(
    private val source: GlucoseSource,
    private val authStore: AuthPersistence,
    private val patientStore: PatientPersistence,
    val settings: SettingsPersistence,
    private val clock: Clock = DeviceClock,
    val diagnostics: DiagnosticsRecorder = NoDiagnostics,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val authGate = Mutex()
    private var inFlight: Deferred<SourceResult<Reading>>? = null
    private var generation = 0L
    private var consecutiveFailures = 0
    private var lastManualRefreshAt: Instant? = null
    private var authAttemptsStopped = false
    private var auth: SavedAuth? = try { authStore.load() } catch (_: Exception) {
        authStore.clear() // restored ciphertext or invalidated Keystore key requires sign-in
        null
    }
    private var patient: StoredPatientState = if (auth != null) {
        runCatching { patientStore.load() }.getOrDefault(StoredPatientState(null, null))
    } else {
        patientStore.clear()
        StoredPatientState(null, null)
    }

    private val mutableDisplay = MutableStateFlow(
        DisplayState(
            monitoringEnabled = settings.monitoringEnabled,
            runtime = RuntimeState.STOPPED,
            connection = if (auth == null) ConnectionState.SIGN_IN_REQUIRED else ConnectionState.READY,
            person = patient.selected,
            reading = patient.lastReading,
            freshness = ReadingPolicy.freshness(patient.lastReading, clock.now()),
            lastSuccessfulFetch = null,
            nextAllowedFetch = settings.nextAllowedFetch,
        ),
    )
    val display: StateFlow<DisplayState> = mutableDisplay
    val hasAccount: Boolean get() = auth != null

    private fun snapshot(connection: ConnectionState, runtime: RuntimeState = mutableDisplay.value.runtime) = DisplayState(
        monitoringEnabled = settings.monitoringEnabled,
        runtime = runtime,
        connection = if (auth == null) ConnectionState.SIGN_IN_REQUIRED else connection,
        person = patient.selected,
        reading = patient.lastReading,
        freshness = ReadingPolicy.freshness(patient.lastReading, clock.now()),
        lastSuccessfulFetch = mutableDisplay.value.lastSuccessfulFetch,
        nextAllowedFetch = settings.nextAllowedFetch,
    )

    @Synchronized
    fun setRuntime(runtime: RuntimeState) {
        if (mutableDisplay.value.runtime != runtime) diagnostics.runtime(clock.now(), runtime.name)
        mutableDisplay.value = snapshot(mutableDisplay.value.connection, runtime)
    }

    @Synchronized
    fun setMonitoringEnabled(enabled: Boolean) {
        settings.monitoringEnabled = enabled
        if (!enabled) generation++
        mutableDisplay.value = snapshot(mutableDisplay.value.connection, if (enabled) mutableDisplay.value.runtime else RuntimeState.STOPPED)
    }

    @Synchronized
    fun updateFreshness() {
        mutableDisplay.value = snapshot(mutableDisplay.value.connection)
    }

    /** Clears old-account data before contacting the new account. */
    suspend fun signIn(credentials: Credentials): SourceResult<List<Connection>> {
        val capturedGeneration: Long
        synchronized(this) {
            generation++
            capturedGeneration = generation
            authAttemptsStopped = false
            settings.monitoringEnabled = false
            settings.nextAllowedFetch = null
            authStore.clear()
            patientStore.clear()
            auth = null
            patient = StoredPatientState(null, null)
            mutableDisplay.value = snapshot(ConnectionState.SIGN_IN_REQUIRED, RuntimeState.STOPPED)
        }
        val login = source.signIn(credentials)
        if (login is SourceResult.Failure) {
            updateFailure(login.error, capturedGeneration)
            return login
        }
        val session = (login as SourceResult.Success).value
        val connections = source.connections(session)
        if (connections is SourceResult.Failure) {
            updateFailure(connections.error, capturedGeneration)
            return connections
        }
        synchronized(this) {
            if (generation != capturedGeneration) return SourceResult.Failure(SourceError(SourceErrorKind.AUTH_EXPIRED))
            auth = SavedAuth(credentials, session)
            authStore.save(auth!!)
            mutableDisplay.value = snapshot(ConnectionState.READY, RuntimeState.STOPPED)
        }
        return connections
    }

    @Synchronized
    fun selectPerson(connection: Connection) {
        generation++
        patient = StoredPatientState(connection, null)
        patientStore.save(patient)
        settings.nextAllowedFetch = null
        mutableDisplay.value = snapshot(ConnectionState.READY)
    }

    /** Enter person selection without retaining the prior person's display or late response. */
    @Synchronized
    fun clearSelectedPerson() {
        generation++
        patient = StoredPatientState(null, null)
        patientStore.clear()
        settings.nextAllowedFetch = null
        mutableDisplay.value = snapshot(ConnectionState.READY)
    }

    suspend fun connections(): SourceResult<List<Connection>> {
        val session = sessionOrRecover() ?: return SourceResult.Failure(currentAuthError())
        val capturedGeneration = synchronized(this) { generation }
        var result = source.connections(session)
        if (result is SourceResult.Failure && result.error.kind == SourceErrorKind.AUTH_EXPIRED) {
            val recovered = recoverSession(session.token)
            result = if (recovered != null) source.connections(recovered)
            else SourceResult.Failure(currentAuthError())
        }
        if (result is SourceResult.Failure) {
            updateFailure(result.error, capturedGeneration)
            return result
        }
        if (result is SourceResult.Success) {
            val selectedId = synchronized(this) { patient.selected?.id }
            if (selectedId != null && result.value.none { it.id == selectedId }) {
                synchronized(this) { mutableDisplay.value = snapshot(ConnectionState.CONNECTION_REMOVED) }
                return SourceResult.Failure(SourceError(SourceErrorKind.CONNECTION_REMOVED))
            }
        }
        return result
    }

    /** Refresh taps share the same in-flight request and cannot bypass cooldown. */
    suspend fun poll(manual: Boolean = false): SourceResult<Reading> {
        val deferred = gate.withLock {
            inFlight?.takeUnless { it.isCompleted } ?: scope.async { doPoll(manual) }.also { inFlight = it }
        }
        return try { deferred.await() } finally {
            gate.withLock { if (inFlight === deferred && deferred.isCompleted) inFlight = null }
        }
    }

    /** Abort a timed-out source call and invalidate any late response before publishing failure. */
    suspend fun recordPollTimeout() {
        gate.withLock {
            inFlight?.cancel()
            inFlight = null
            synchronized(this) {
                generation++
                updateFailure(SourceError(SourceErrorKind.SERVER), generation)
            }
        }
    }

    private suspend fun doPoll(manual: Boolean): SourceResult<Reading> {
        val (selected, capturedGeneration) = synchronized(this) { patient.selected to generation }
        if (selected == null) return SourceResult.Failure(SourceError(SourceErrorKind.CONNECTION_REMOVED))
        val now = clock.now()
        settings.nextAllowedFetch?.takeIf { now < it }?.let {
            synchronized(this) { mutableDisplay.value = snapshot(ConnectionState.BACKING_OFF) }
            return SourceResult.Failure(SourceError(SourceErrorKind.RATE_LIMITED, it))
        }
        if (manual && lastManualRefreshAt?.let { Duration.between(it, now) < Duration.ofSeconds(15) } == true) {
            return SourceResult.Failure(SourceError(SourceErrorKind.RATE_LIMITED, now.plusSeconds(15)))
        }
        if (manual) lastManualRefreshAt = now
        synchronized(this) { if (generation == capturedGeneration) mutableDisplay.value = snapshot(ConnectionState.FETCHING) }
        diagnostics.pollAttempt(now)
        val session = sessionOrRecover() ?: return failure(currentAuthError(), capturedGeneration)
        var result = source.latest(session, selected.id)
        if (result is SourceResult.Failure && result.error.kind == SourceErrorKind.AUTH_EXPIRED) {
            val recovered = recoverSession(session.token)
            result = if (recovered != null) source.latest(recovered, selected.id)
            else SourceResult.Failure(currentAuthError())
        }
        if (result is SourceResult.Success && result.value.connectionId != selected.id) {
            result = SourceResult.Failure(SourceError(SourceErrorKind.PROTOCOL))
        }
        synchronized(this) {
            if (generation != capturedGeneration || patient.selected?.id != selected.id) return result
            when (result) {
                is SourceResult.Success -> {
                    val candidate = result.value
                    patient = patient.copy(lastReading = ReadingPolicy.selectCurrent(patient.lastReading, candidate, selected.id))
                    patientStore.save(patient)
                    consecutiveFailures = 0
                    settings.nextAllowedFetch = null
                    mutableDisplay.value = snapshot(ConnectionState.READY).copy(lastSuccessfulFetch = clock.now())
                    diagnostics.pollSuccess(clock.now())
                }
                is SourceResult.Failure -> updateFailure(result.error, capturedGeneration)
            }
        }
        return result
    }

    private fun failure(error: SourceError, capturedGeneration: Long): SourceResult.Failure {
        updateFailure(error, capturedGeneration)
        return SourceResult.Failure(error)
    }

    @Synchronized
    private fun updateFailure(error: SourceError, capturedGeneration: Long) {
        if (generation != capturedGeneration) return
        diagnostics.pollFailure(clock.now(), error.kind)
        val state = when (error.kind) {
            SourceErrorKind.NETWORK -> ConnectionState.OFFLINE
            SourceErrorKind.INVALID_CREDENTIALS, SourceErrorKind.AUTH_EXPIRED -> {
                authAttemptsStopped = true
                ConnectionState.SIGN_IN_REQUIRED
            }
            SourceErrorKind.TERMS_REQUIRED -> {
                authAttemptsStopped = true
                ConnectionState.TERMS_REQUIRED
            }
            SourceErrorKind.CONNECTION_REMOVED -> ConnectionState.CONNECTION_REMOVED
            SourceErrorKind.PROTOCOL -> ConnectionState.PROTOCOL_ERROR
            else -> ConnectionState.BACKING_OFF
        }
        if (error.kind == SourceErrorKind.RATE_LIMITED) {
            settings.nextAllowedFetch = error.retryAfter ?: clock.now().plus(Duration.ofMinutes(15))
        } else if (error.kind == SourceErrorKind.NETWORK ||
            error.kind == SourceErrorKind.SERVER || error.kind == SourceErrorKind.PROTOCOL
        ) {
            // Persist a bounded deadline so process restarts and manual taps cannot retry-storm.
            val minutes = min(15, 1 shl min(4, consecutiveFailures++))
            settings.nextAllowedFetch = clock.now()
                .plus(Duration.ofMinutes(minutes.toLong()))
                .plusMillis(Random.nextLong(0, 15_001))
        }
        mutableDisplay.value = snapshot(state)
    }

    private suspend fun sessionOrRecover() = synchronized(this) { if (authAttemptsStopped) null else auth?.session }
        ?.takeIf { it.expiresAt == null || clock.now().isBefore(it.expiresAt.minusSeconds(60)) }
        ?: recoverSession()

    private suspend fun recoverSession(rejectedToken: String? = null): com.khaleel.glance.core.Session? = authGate.withLock {
        synchronized(this) { auth?.session }?.takeIf {
            !authAttemptsStopped && (rejectedToken == null || it.token != rejectedToken) &&
                (it.expiresAt == null || clock.now().isBefore(it.expiresAt.minusSeconds(60)))
        }?.let { return@withLock it }
        val (credentials, capturedGeneration) = synchronized(this) {
            if (authAttemptsStopped) return@withLock null
            (auth?.credentials ?: return@withLock null) to generation
        }
        val result = source.signIn(credentials)
        if (result !is SourceResult.Success) {
            if (result is SourceResult.Failure) updateFailure(result.error, capturedGeneration)
            return@withLock null
        }
        synchronized(this) {
            if (generation != capturedGeneration || auth?.credentials != credentials) return@withLock null
            auth = SavedAuth(credentials, result.value)
            authStore.save(auth!!)
        }
        result.value
    }

    private fun currentAuthError() = SourceError(
        when (mutableDisplay.value.connection) {
            ConnectionState.TERMS_REQUIRED -> SourceErrorKind.TERMS_REQUIRED
            ConnectionState.OFFLINE -> SourceErrorKind.NETWORK
            ConnectionState.PROTOCOL_ERROR -> SourceErrorKind.PROTOCOL
            ConnectionState.BACKING_OFF -> SourceErrorKind.SERVER
            else -> SourceErrorKind.AUTH_EXPIRED
        },
    )

    @Synchronized
    fun signOut() {
        generation++
        authAttemptsStopped = true
        settings.clear()
        authStore.clear()
        patientStore.clear()
        auth = null
        patient = StoredPatientState(null, null)
        mutableDisplay.value = snapshot(ConnectionState.SIGN_IN_REQUIRED, RuntimeState.STOPPED)
        diagnostics.clear()
    }

    /** Remove cached health data while retaining account, selected person, and preferences. */
    @Synchronized
    fun clearLocalData() {
        generation++
        patient = patient.copy(lastReading = null)
        patientStore.save(patient)
        diagnostics.clear()
        mutableDisplay.value = snapshot(mutableDisplay.value.connection)
    }
}
