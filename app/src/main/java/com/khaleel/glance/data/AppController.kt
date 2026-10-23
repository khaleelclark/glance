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
import com.khaleel.glance.core.SourceSnapshot
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
    private val nicknameStore: NicknamePersistence = NoNicknamePersistence,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val authGate = Mutex()
    private var inFlight: Deferred<SourceResult<Reading>>? = null
    private var generation = 0L
    private var consecutiveFailures = 0
    private var lastManualRefreshElapsed: Long? = null
    private var authAttemptsStopped = false
    private var monitoringEnabled = settings.monitoringEnabled
    private var retryWindow = settings.retryWindow
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
    private var nicknames: Map<String, String> = auth?.session?.accountId?.let { accountId ->
        runCatching { nicknameStore.load(accountId) }.getOrElse {
            runCatching { nicknameStore.clear() }
            emptyMap()
        }
    } ?: run {
        runCatching { nicknameStore.clear() }
        emptyMap()
    }

    private val mutableDisplay = MutableStateFlow(
        DisplayState(
            monitoringEnabled = monitoringEnabled,
            runtime = RuntimeState.STOPPED,
            connection = if (auth == null) ConnectionState.SIGN_IN_REQUIRED else ConnectionState.READY,
            person = displayedPerson(),
            reading = patient.lastReading,
            freshness = ReadingPolicy.freshness(patient.lastReading, clock.now()),
            lastSuccessfulFetch = null,
            nextAllowedFetch = retryWindow?.until,
            history = patient.history,
            providerRetryAt = retryWindow?.takeIf { it.reason == RetryReason.PROVIDER }?.until,
        ),
    )
    val display: StateFlow<DisplayState> = mutableDisplay
    val hasAccount: Boolean get() = auth != null
    val nickname: String
        @Synchronized get() = patient.selected?.id?.let(nicknames::get).orEmpty()
    val sourcePersonName: String?
        @Synchronized get() = patient.selected?.displayName

    private fun displayedPerson(): Connection? = patient.selected?.let { selected ->
        nicknames[selected.id]?.takeIf(String::isNotBlank)?.let { selected.copy(displayName = it) } ?: selected
    }

    private fun snapshot(connection: ConnectionState, runtime: RuntimeState = mutableDisplay.value.runtime) = DisplayState(
        monitoringEnabled = monitoringEnabled,
        runtime = runtime,
        connection = if (auth == null) ConnectionState.SIGN_IN_REQUIRED else connection,
        person = displayedPerson(),
        reading = patient.lastReading,
        freshness = ReadingPolicy.freshness(patient.lastReading, clock.now()),
        lastSuccessfulFetch = mutableDisplay.value.lastSuccessfulFetch,
        nextAllowedFetch = retryWindow?.until,
        history = patient.history,
        providerRetryAt = retryWindow?.takeIf { it.reason == RetryReason.PROVIDER }?.until,
    )

    @Synchronized
    fun setRuntime(runtime: RuntimeState) {
        if (mutableDisplay.value.runtime != runtime) diagnostics.runtime(clock.now(), runtime.name)
        mutableDisplay.value = snapshot(mutableDisplay.value.connection, runtime)
    }

    @Synchronized
    fun setMonitoringEnabled(enabled: Boolean): Boolean {
        val stored = runCatching { settings.setMonitoringEnabled(enabled) }.getOrDefault(false)
        monitoringEnabled = enabled && stored
        if (!enabled) generation++
        val connection = when {
            !stored -> ConnectionState.LOCAL_ERROR
            enabled && mutableDisplay.value.connection == ConnectionState.LOCAL_ERROR ->
                if (activeProviderCooldown() != null) ConnectionState.BACKING_OFF else ConnectionState.READY
            else -> mutableDisplay.value.connection
        }
        mutableDisplay.value = snapshot(
            connection,
            if (monitoringEnabled) mutableDisplay.value.runtime else RuntimeState.STOPPED,
        )
        if (!stored) diagnostics.sourceFailure(clock.now(), SourceError(SourceErrorKind.STORAGE))
        return stored
    }

    @Synchronized
    fun updateFreshness() {
        mutableDisplay.value = snapshot(mutableDisplay.value.connection)
    }

    private fun persistRetryWindow(value: RetryWindow?): Boolean {
        retryWindow = value
        return runCatching { settings.setRetryWindow(value) }.getOrDefault(false)
    }

    private fun activeProviderCooldown(): RetryWindow? = retryWindow?.takeIf {
        it.reason == RetryReason.PROVIDER && clock.now().isBefore(it.until)
    }

    @Synchronized
    private fun providerCooldownFailure(): SourceError? = activeProviderCooldown()?.let {
        mutableDisplay.value = snapshot(ConnectionState.BACKING_OFF)
        SourceError(SourceErrorKind.RATE_LIMITED, it.until)
    }

    private fun clearRetryPreservingProvider(): Boolean =
        if (activeProviderCooldown() != null) true else persistRetryWindow(null)

    /** Clears old-account data before contacting the new account. */
    suspend fun signIn(credentials: Credentials): SourceResult<List<Connection>> {
        val capturedGeneration: Long
        val previousAccountId: String?
        synchronized(this) {
            generation++
            capturedGeneration = generation
            previousAccountId = auth?.session?.accountId
            source.clearTransportSession()
            authAttemptsStopped = false
            val monitoringStored = runCatching { settings.setMonitoringEnabled(false) }.getOrDefault(false)
            monitoringEnabled = false
            val retryStored = clearRetryPreservingProvider()
            authStore.clear()
            patientStore.clear()
            auth = null
            patient = StoredPatientState(null, null)
            if (!monitoringStored || !retryStored) {
                val error = SourceError(SourceErrorKind.STORAGE)
                diagnostics.sourceFailure(clock.now(), error)
                mutableDisplay.value = snapshot(ConnectionState.LOCAL_ERROR, RuntimeState.STOPPED)
                return SourceResult.Failure(error)
            }
            mutableDisplay.value = snapshot(ConnectionState.SIGN_IN_REQUIRED, RuntimeState.STOPPED)
            providerCooldownFailure()?.let { return SourceResult.Failure(it) }
        }
        val login = source.signIn(credentials)
        if (login is SourceResult.Failure) {
            return SourceResult.Failure(updateFailure(login.error, capturedGeneration))
        }
        val session = (login as SourceResult.Success).value
        providerCooldownFailure()?.let { return SourceResult.Failure(it) }
        val connections = source.connections(session)
        if (connections is SourceResult.Failure) {
            return SourceResult.Failure(updateFailure(connections.error, capturedGeneration))
        }
        synchronized(this) {
            if (generation != capturedGeneration) return SourceResult.Failure(SourceError(SourceErrorKind.AUTH_EXPIRED))
            if (previousAccountId != session.accountId) {
                try {
                    nicknameStore.clear()
                } catch (_: Exception) {
                    val error = SourceError(SourceErrorKind.STORAGE)
                    updateFailure(error, capturedGeneration)
                    return SourceResult.Failure(error)
                }
                nicknames = emptyMap()
            }
            val saved = SavedAuth(credentials, session)
            try {
                authStore.save(saved)
            } catch (_: Exception) {
                val error = SourceError(SourceErrorKind.STORAGE)
                updateFailure(error, capturedGeneration)
                return SourceResult.Failure(error)
            }
            auth = saved
            consecutiveFailures = 0
            mutableDisplay.value = snapshot(ConnectionState.READY, RuntimeState.STOPPED)
        }
        return connections
    }

    @Synchronized
    fun selectPerson(connection: Connection): SourceResult<Unit> {
        val next = StoredPatientState(connection, null)
        try {
            patientStore.save(next)
        } catch (_: Exception) {
            val error = SourceError(SourceErrorKind.STORAGE)
            updateFailure(error, generation)
            return SourceResult.Failure(error)
        }
        generation++
        patient = next
        consecutiveFailures = 0
        if (!clearRetryPreservingProvider()) {
            val error = SourceError(SourceErrorKind.STORAGE)
            updateFailure(error, generation)
            return SourceResult.Failure(error)
        }
        mutableDisplay.value = snapshot(
            if (activeProviderCooldown() == null) ConnectionState.READY else ConnectionState.BACKING_OFF,
        )
        return SourceResult.Success(Unit)
    }

    /** Enter person selection without retaining the prior person's display or late response. */
    @Synchronized
    fun clearSelectedPerson() {
        generation++
        patient = StoredPatientState(null, null)
        patientStore.clear()
        consecutiveFailures = 0
        if (!clearRetryPreservingProvider()) {
            val error = SourceError(SourceErrorKind.STORAGE)
            updateFailure(error, generation)
            return
        }
        mutableDisplay.value = snapshot(
            if (activeProviderCooldown() == null) ConnectionState.READY else ConnectionState.BACKING_OFF,
        )
    }

    /** Changes display text only; provider identity and cached readings remain untouched. */
    @Synchronized
    fun setNickname(value: String): SourceResult<Unit> {
        val selected = patient.selected
            ?: return SourceResult.Failure(SourceError(SourceErrorKind.CONNECTION_REMOVED))
        val accountId = auth?.session?.accountId
            ?: return SourceResult.Failure(SourceError(SourceErrorKind.AUTH_EXPIRED))
        val normalized = normalizeFollowerNickname(value)
        val next = LinkedHashMap(nicknames).apply {
            remove(selected.id)
            if (normalized.isNotBlank()) put(selected.id, normalized)
            while (size > 32) remove(keys.first())
        }
        try {
            nicknameStore.save(accountId, next)
        } catch (_: Exception) {
            diagnostics.sourceFailure(clock.now(), SourceError(SourceErrorKind.STORAGE))
            return SourceResult.Failure(SourceError(SourceErrorKind.STORAGE))
        }
        nicknames = next
        mutableDisplay.value = snapshot(mutableDisplay.value.connection)
        return SourceResult.Success(Unit)
    }

    suspend fun connections(): SourceResult<List<Connection>> {
        val (capturedGeneration, capturedAccountId) = synchronized(this) {
            generation to auth?.session?.accountId
        }
        providerCooldownFailure()?.let { return SourceResult.Failure(it) }
        val session = sessionOrRecover() ?: return SourceResult.Failure(currentAuthError())
        synchronized(this) {
            if (generation != capturedGeneration || auth?.session?.accountId != capturedAccountId ||
                session.accountId != capturedAccountId
            ) return SourceResult.Failure(SourceError(SourceErrorKind.AUTH_EXPIRED))
        }
        providerCooldownFailure()?.let { return SourceResult.Failure(it) }
        var result = source.connections(session)
        if (result is SourceResult.Failure && result.error.kind == SourceErrorKind.AUTH_EXPIRED) {
            val recovered = recoverSession(session.token)
            if (recovered == null) return SourceResult.Failure(currentAuthError())
            synchronized(this) {
                if (generation != capturedGeneration || auth?.session?.accountId != capturedAccountId ||
                    recovered.accountId != capturedAccountId
                ) return SourceResult.Failure(SourceError(SourceErrorKind.AUTH_EXPIRED))
            }
            providerCooldownFailure()?.let { return SourceResult.Failure(it) }
            result = source.connections(recovered)
        }
        if (result is SourceResult.Failure) {
            return SourceResult.Failure(updateFailure(result.error, capturedGeneration))
        }
        if (result is SourceResult.Success) {
            synchronized(this) {
                if (generation != capturedGeneration || auth?.session?.accountId != capturedAccountId) {
                    return SourceResult.Failure(SourceError(SourceErrorKind.AUTH_EXPIRED))
                }
                val selectedId = patient.selected?.id
                if (selectedId != null && result.value.none { it.id == selectedId }) {
                    mutableDisplay.value = snapshot(ConnectionState.CONNECTION_REMOVED)
                    return SourceResult.Failure(SourceError(SourceErrorKind.CONNECTION_REMOVED))
                }
            }
        }
        return result
    }

    /** Refresh taps coalesce; manual retries bypass transient backoff, never provider cooldowns. */
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
        recordPollFailure(SourceErrorKind.SERVER)
    }

    /** Stop an unexpected request and publish a non-fetching state for all callers. */
    suspend fun recordPollFailure(kind: SourceErrorKind) {
        gate.withLock {
            inFlight?.cancel()
            inFlight = null
            synchronized(this) {
                generation++
                updateFailure(SourceError(kind), generation)
            }
        }
    }

    private suspend fun doPoll(manual: Boolean): SourceResult<Reading> {
        if (mutableDisplay.value.connection == ConnectionState.LOCAL_ERROR) {
            return SourceResult.Failure(SourceError(SourceErrorKind.STORAGE))
        }
        val (selected, capturedGeneration) = synchronized(this) { patient.selected to generation }
        if (selected == null) return SourceResult.Failure(SourceError(SourceErrorKind.CONNECTION_REMOVED))
        val now = clock.now()
        providerCooldownFailure()?.let { return SourceResult.Failure(it) }
        retryWindow?.takeIf { now < it.until }?.let {
            if (!manual) {
                return SourceResult.Failure(SourceError(SourceErrorKind.SERVER, it.until))
            }
        }
        if (manual) {
            val elapsed = clock.elapsedRealtimeMillis()
            val remaining = lastManualRefreshElapsed?.let { 3_000L - (elapsed - it) } ?: 0L
            if (remaining > 0L) {
                return SourceResult.Failure(SourceError(SourceErrorKind.REFRESH_DEBOUNCED, now.plusMillis(remaining)))
            }
            lastManualRefreshElapsed = elapsed
        }
        synchronized(this) { if (generation == capturedGeneration) mutableDisplay.value = snapshot(ConnectionState.FETCHING) }
        diagnostics.pollAttempt(now)
        // recoverSession already records the exact failure/deadline; do not count it twice.
        val session = sessionOrRecover() ?: return SourceResult.Failure(currentAuthError())
        providerCooldownFailure()?.let { return SourceResult.Failure(it) }
        var result = source.snapshot(session, selected.id)
        if (result is SourceResult.Failure && result.error.kind == SourceErrorKind.AUTH_EXPIRED) {
            val recovered = recoverSession(session.token)
            if (recovered == null) return SourceResult.Failure(currentAuthError())
            providerCooldownFailure()?.let { return SourceResult.Failure(it) }
            result = source.snapshot(recovered, selected.id)
        }
        if (result is SourceResult.Success && result.value.current.connectionId != selected.id) {
            result = SourceResult.Failure(SourceError(SourceErrorKind.PROTOCOL))
        }
        synchronized(this) {
            if (generation != capturedGeneration || patient.selected?.id != selected.id) return result.toReadingResult()
            when (result) {
                is SourceResult.Success -> {
                    val candidate = result.value.current
                    val next = patient.copy(
                        lastReading = ReadingPolicy.selectCurrent(patient.lastReading, candidate, selected.id),
                        history = ReadingHistory.merge(
                            patient.history,
                            result.value.history + candidate,
                            selected.id,
                            clock.now(),
                        ),
                    )
                    try {
                        patientStore.save(next)
                    } catch (_: Exception) {
                        val error = SourceError(SourceErrorKind.STORAGE)
                        updateFailure(error, capturedGeneration)
                        return SourceResult.Failure(error)
                    }
                    patient = next
                    consecutiveFailures = 0
                    if (!clearRetryPreservingProvider()) {
                        val error = SourceError(SourceErrorKind.STORAGE)
                        updateFailure(error, capturedGeneration)
                        return SourceResult.Failure(error)
                    }
                    mutableDisplay.value = snapshot(
                        if (activeProviderCooldown() == null) ConnectionState.READY else ConnectionState.BACKING_OFF,
                    ).copy(lastSuccessfulFetch = clock.now())
                    diagnostics.pollSuccess(clock.now())
                }
                is SourceResult.Failure -> {
                    return SourceResult.Failure(updateFailure(result.error, capturedGeneration))
                }
            }
        }
        return result.toReadingResult()
    }

    private fun SourceResult<SourceSnapshot>.toReadingResult(): SourceResult<Reading> = when (this) {
        is SourceResult.Success -> SourceResult.Success(value.current)
        is SourceResult.Failure -> this
    }

    @Synchronized
    private fun updateFailure(error: SourceError, capturedGeneration: Long): SourceError {
        if (generation != capturedGeneration) return error
        diagnostics.sourceFailure(clock.now(), error)
        var effectiveError = error
        if (error.kind == SourceErrorKind.RATE_LIMITED) {
            if (!persistRetryWindow(RetryWindow(
                error.retryAfter ?: clock.now().plus(Duration.ofMinutes(15)), RetryReason.PROVIDER,
            ))) effectiveError = SourceError(SourceErrorKind.STORAGE)
        } else if (error.kind == SourceErrorKind.NETWORK ||
            error.kind == SourceErrorKind.SERVER || error.kind == SourceErrorKind.PROTOCOL
        ) {
            // Automatic retries retain exponential backoff. An explicit manual retry can
            // bypass it, but never replace a still-active provider cooldown.
            val minutes = min(15, 1 shl min(4, consecutiveFailures++))
            if (retryWindow?.let { it.reason == RetryReason.PROVIDER && clock.now() < it.until } != true &&
                !persistRetryWindow(RetryWindow(
                    clock.now().plus(Duration.ofMinutes(minutes.toLong()))
                        .plusMillis(Random.nextLong(0, 15_001)),
                    RetryReason.TRANSIENT,
                ))
            ) {
                effectiveError = SourceError(SourceErrorKind.STORAGE)
            }
        }
        if (effectiveError !== error) diagnostics.sourceFailure(clock.now(), effectiveError)
        val state = when (effectiveError.kind) {
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
            SourceErrorKind.STORAGE -> ConnectionState.LOCAL_ERROR
            else -> ConnectionState.BACKING_OFF
        }
        mutableDisplay.value = snapshot(state)
        return effectiveError
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
        providerCooldownFailure()?.let { return@withLock null }
        val result = source.signIn(credentials)
        if (result !is SourceResult.Success) {
            if (result is SourceResult.Failure) updateFailure(result.error, capturedGeneration)
            return@withLock null
        }
        synchronized(this) {
            if (generation != capturedGeneration || auth?.credentials != credentials) return@withLock null
            if (auth?.session?.accountId != result.value.accountId) {
                updateFailure(SourceError(SourceErrorKind.AUTH_EXPIRED), capturedGeneration)
                return@withLock null
            }
            val saved = SavedAuth(credentials, result.value)
            try {
                authStore.save(saved)
            } catch (_: Exception) {
                updateFailure(SourceError(SourceErrorKind.STORAGE), capturedGeneration)
                return@withLock null
            }
            auth = saved
        }
        result.value
    }

    private fun currentAuthError() = SourceError(
        when (mutableDisplay.value.connection) {
            ConnectionState.TERMS_REQUIRED -> SourceErrorKind.TERMS_REQUIRED
            ConnectionState.OFFLINE -> SourceErrorKind.NETWORK
            ConnectionState.PROTOCOL_ERROR -> SourceErrorKind.PROTOCOL
            ConnectionState.LOCAL_ERROR -> SourceErrorKind.STORAGE
            ConnectionState.BACKING_OFF -> if (retryWindow?.reason == RetryReason.PROVIDER) {
                SourceErrorKind.RATE_LIMITED
            } else SourceErrorKind.SERVER
            else -> SourceErrorKind.AUTH_EXPIRED
        },
        retryAfter = retryWindow?.until,
    )

    @Synchronized
    fun signOut(): Boolean {
        generation++
        source.clearTransportSession()
        authAttemptsStopped = true
        settings.clear()
        monitoringEnabled = false
        retryWindow = null
        consecutiveFailures = 0
        authStore.clear()
        patientStore.clear()
        val nicknamesCleared = runCatching { nicknameStore.clear() }.isSuccess
        auth = null
        patient = StoredPatientState(null, null)
        nicknames = emptyMap()
        mutableDisplay.value = snapshot(ConnectionState.SIGN_IN_REQUIRED, RuntimeState.STOPPED)
        diagnostics.clear()
        return nicknamesCleared
    }

    /** Remove cached health data while retaining account, selected person, and preferences. */
    @Synchronized
    fun clearLocalData() {
        generation++
        patient = patient.copy(lastReading = null, history = emptyList())
        patientStore.save(patient)
        diagnostics.clear()
        mutableDisplay.value = snapshot(mutableDisplay.value.connection)
    }
}
