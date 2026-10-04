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

data class SourceSnapshot(val current: Reading, val history: List<Reading> = emptyList())

enum class SourceErrorKind {
    INVALID_CREDENTIALS, AUTH_EXPIRED, TERMS_REQUIRED, RATE_LIMITED,
    NETWORK, SERVER, CONNECTION_REMOVED, PROTOCOL, NO_READING, STORAGE, REFRESH_DEBOUNCED,
}

/** Fixed categories only: never retain response bodies, URLs, headers, or patient data. */
enum class SourceResponseFormat { JSON, WEB_PAGE, OTHER }

data class SourceResponseInfo(val httpStatus: Int, val format: SourceResponseFormat) {
    fun summary(): String = "HTTP $httpStatus · " + when (format) {
        SourceResponseFormat.JSON -> "JSON response"
        SourceResponseFormat.WEB_PAGE -> "web page response"
        SourceResponseFormat.OTHER -> "other response format"
    }
}

data class SourceError(
    val kind: SourceErrorKind,
    val retryAfter: Instant? = null,
    val responseInfo: SourceResponseInfo? = null,
)

sealed interface SourceResult<out T> {
    data class Success<T>(val value: T) : SourceResult<T>
    data class Failure(val error: SourceError) : SourceResult<Nothing>
}

interface GlucoseSource {
    /** Drop volatile transport state when signing out or changing accounts. */
    fun clearTransportSession() = Unit
    suspend fun signIn(credentials: Credentials): SourceResult<Session>
    suspend fun connections(session: Session): SourceResult<List<Connection>>
    suspend fun latest(session: Session, connectionId: String): SourceResult<Reading>
    suspend fun snapshot(session: Session, connectionId: String): SourceResult<SourceSnapshot> =
        when (val result = latest(session, connectionId)) {
            is SourceResult.Success -> SourceResult.Success(SourceSnapshot(result.value))
            is SourceResult.Failure -> result
        }
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
    val history: List<Reading> = emptyList(),
    val providerRetryAt: Instant? = null,
    val people: List<PersonDisplayState> = emptyList(),
)

/** Account roster; each reading and history remain scoped to the named provider connection. */
data class PersonDisplayState(
    val person: Connection,
    val reading: Reading?,
    val freshness: ReadingFreshness,
    val connection: ConnectionState = ConnectionState.READY,
    val lastSuccessfulFetch: Instant? = null,
    val history: List<Reading> = emptyList(),
)

/** Viewing a person does not change the primary notification/widget/watch person. */
fun DisplayState.forPerson(personId: String): DisplayState? {
    if (person?.id == personId) return this
    val entry = people.firstOrNull { it.person.id == personId } ?: return null
    return copy(
        person = entry.person,
        reading = entry.reading,
        freshness = entry.freshness,
        connection = entry.connection,
        lastSuccessfulFetch = entry.lastSuccessfulFetch,
        history = entry.history,
    )
}
