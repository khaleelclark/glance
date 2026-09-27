package com.khaleel.glance.core

import java.time.Instant

/** Shared API. Change only after coordinating all package owners. */
data class Credentials(val email: String, val password: String)

data class Session(
    val accountId: String,
    val token: String,
    val regionHost: String,
    val expiresAt: Instant?,
)

data class Connection(val id: String, val displayName: String)

enum class GlucoseUnit { MG_DL, MMOL_L }
enum class Trend { RISING_QUICKLY, RISING, RISING_SLOWLY, STEADY, FALLING_SLOWLY, FALLING, FALLING_QUICKLY, UNKNOWN }
enum class SpecialValue { LOW, HIGH, UNAVAILABLE }

data class Reading(
    val connectionId: String,
    val measuredAt: Instant,
    val valueMgDl: Double?,
    val sourceValue: Double?,
    val sourceUnit: GlucoseUnit,
    val specialValue: SpecialValue?,
    val trend: Trend,
    val sourceRecordId: String?,
    val receivedAt: Instant,
)

enum class SourceErrorKind {
    INVALID_CREDENTIALS, AUTH_EXPIRED, TERMS_REQUIRED, RATE_LIMITED,
    NETWORK, SERVER, CONNECTION_REMOVED, PROTOCOL, NO_READING, STORAGE,
}

data class SourceError(val kind: SourceErrorKind, val retryAfter: Instant? = null)

sealed interface SourceResult<out T> {
    data class Success<T>(val value: T) : SourceResult<T>
    data class Failure(val error: SourceError) : SourceResult<Nothing>
}

interface GlucoseSource {
    suspend fun signIn(credentials: Credentials): SourceResult<Session>
    suspend fun connections(session: Session): SourceResult<List<Connection>>
    suspend fun latest(session: Session, connectionId: String): SourceResult<Reading>
}

interface Clock {
    fun now(): Instant
    fun elapsedRealtimeMillis(): Long
}

enum class ReadingFreshness { ABSENT, FRESH, DELAYED, STALE, INVALID_TIME }
enum class RuntimeState { STOPPED, STARTING, RUNNING, UNABLE_TO_START }
enum class ConnectionState { READY, FETCHING, OFFLINE, BACKING_OFF, SIGN_IN_REQUIRED, TERMS_REQUIRED, CONNECTION_REMOVED, PROTOCOL_ERROR, LOCAL_ERROR }

data class DisplayState(
    val monitoringEnabled: Boolean,
    val runtime: RuntimeState,
    val connection: ConnectionState,
    val person: Connection?,
    val reading: Reading?,
    val freshness: ReadingFreshness,
    val lastSuccessfulFetch: Instant?,
    val nextAllowedFetch: Instant?,
)
