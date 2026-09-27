package com.khaleel.glance.librelinkup

import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.GlucoseSource
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.SourceSnapshot
import com.khaleel.glance.core.Session
import com.khaleel.glance.core.SourceError
import com.khaleel.glance.core.SourceErrorKind
import com.khaleel.glance.core.SourceResult
import com.khaleel.glance.core.Trend
import java.io.IOException
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import okio.Buffer

/**
 * Credential-free LibreLinkUp protocol adapter.
 *
 * The caller owns encrypted credential/session persistence, refresh serialization, cooldown
 * persistence, and the one-reauthentication policy. This class never logs request or response data.
 */
class LibreLinkUpSource private constructor(
    client: OkHttpClient,
    private val clock: Clock,
    private val bootstrapBaseUrl: HttpUrl,
    private val regionalBaseUrls: Map<String, HttpUrl>,
) : GlucoseSource {
    constructor(client: OkHttpClient, clock: Clock) : this(
        client = client,
        clock = clock,
        bootstrapBaseUrl = BOOTSTRAP_BASE_URL,
        regionalBaseUrls = REGION_BASE_URLS,
    )

    private val clientVersion = AtomicReference(BASELINE_CLIENT_VERSION)
    private val allowedSessionUrls = (regionalBaseUrls.values + bootstrapBaseUrl).associateBy(::canonicalBase)
    private val client = if (client.callTimeoutMillis == 0 || client.callTimeoutMillis > REQUEST_TIMEOUT_MILLIS) {
        client.newBuilder().callTimeout(REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).build()
    } else {
        client
    }

    override suspend fun signIn(credentials: Credentials): SourceResult<Session> {
        if (credentials.email.isBlank() || credentials.password.isEmpty()) {
            return failure(SourceErrorKind.INVALID_CREDENTIALS)
        }
        val first = loginAt(bootstrapBaseUrl, credentials)
        if (first !is LoginResult.Redirect) return first.toSourceResult()
        val regionalUrl = regionalBaseUrls[first.region] ?: return failure(SourceErrorKind.PROTOCOL)
        return when (val second = loginAt(regionalUrl, credentials)) {
            is LoginResult.Success -> SourceResult.Success(second.session)
            is LoginResult.Redirect -> failure(SourceErrorKind.PROTOCOL)
            is LoginResult.Failure -> SourceResult.Failure(second.error)
        }
    }

    override suspend fun connections(session: Session): SourceResult<List<Connection>> {
        val baseUrl = validateSession(session) ?: return failure(SourceErrorKind.AUTH_EXPIRED)
        val url = baseUrl.newBuilder().addPathSegment("llu").addPathSegment("connections").build()
        return when (val result = executeWithVersionRetry(request = { version ->
            authenticatedRequest(url, session, version).get().build()
        })) {
            is HttpResult.Failure -> SourceResult.Failure(result.error)
            is HttpResult.Success -> parseConnections(result.root)
        }
    }

    override suspend fun latest(session: Session, connectionId: String): SourceResult<Reading> =
        when (val result = snapshot(session, connectionId)) {
            is SourceResult.Success -> SourceResult.Success(result.value.current)
            is SourceResult.Failure -> result
        }

    override suspend fun snapshot(session: Session, connectionId: String): SourceResult<SourceSnapshot> {
        val baseUrl = validateSession(session) ?: return failure(SourceErrorKind.AUTH_EXPIRED)
        if (!isValidConnectionId(connectionId)) return failure(SourceErrorKind.PROTOCOL)
        val url = baseUrl.newBuilder()
            .addPathSegment("llu")
            .addPathSegment("connections")
            .addPathSegment(connectionId)
            .addPathSegment("graph")
            .build()
        return when (val result = executeWithVersionRetry(
            notFoundKind = SourceErrorKind.CONNECTION_REMOVED,
            request = { version -> authenticatedRequest(url, session, version).get().build() },
        )) {
            is HttpResult.Failure -> SourceResult.Failure(result.error)
            is HttpResult.Success -> parseSnapshot(result.root, connectionId)
        }
    }

    private suspend fun loginAt(baseUrl: HttpUrl, credentials: Credentials): LoginResult {
        val url = baseUrl.newBuilder()
            .addPathSegment("llu")
            .addPathSegment("auth")
            .addPathSegment("login")
            .build()
        val json = "{\"email\":${Json.quote(credentials.email)},\"password\":${Json.quote(credentials.password)}}"
        return when (val result = executeWithVersionRetry(
            status2Kind = SourceErrorKind.INVALID_CREDENTIALS,
            request = { version ->
                commonRequest(url, version)
                    .post(json.toByteArray(Charsets.UTF_8).toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            },
        )) {
            is HttpResult.Failure -> LoginResult.Failure(result.error)
            is HttpResult.Success -> parseLogin(result.root, baseUrl)
        }
    }

    private suspend fun executeWithVersionRetry(
        status2Kind: SourceErrorKind = SourceErrorKind.PROTOCOL,
        notFoundKind: SourceErrorKind = SourceErrorKind.PROTOCOL,
        request: (String) -> Request,
    ): HttpResult {
        var version = clientVersion.get()
        val first = execute(request(version), status2Kind, notFoundKind)
        if (first !is RawHttpResult.MinimumVersion) return first.toHttpResult()
        val minimum = first.version.takeIf(::isValidVersion)
            ?: return HttpResult.Failure(SourceError(SourceErrorKind.PROTOCOL))
        version = clientVersion.updateAndGet { current ->
            if (compareVersions(minimum, current) > 0) minimum else current
        }
        val second = execute(request(version), status2Kind, notFoundKind)
        return if (second is RawHttpResult.MinimumVersion) {
            HttpResult.Failure(SourceError(SourceErrorKind.PROTOCOL))
        } else {
            second.toHttpResult()
        }
    }

    private suspend fun execute(
        request: Request,
        status2Kind: SourceErrorKind,
        notFoundKind: SourceErrorKind,
    ): RawHttpResult {
        val response = try {
            client.newCall(request).await()
        } catch (_: IOException) {
            return RawHttpResult.Failure(SourceError(SourceErrorKind.NETWORK))
        }
        try {
            response.use {
                if (it.code == 401) return RawHttpResult.Failure(SourceError(SourceErrorKind.AUTH_EXPIRED))
                if (it.code == 429) {
                    return RawHttpResult.Failure(
                        SourceError(SourceErrorKind.RATE_LIMITED, retryAfter(it.header("Retry-After"))),
                    )
                }
                val body = readBoundedBody(it)
                    ?: return RawHttpResult.Failure(SourceError(SourceErrorKind.PROTOCOL))
                val root = parseRoot(body)
                if (it.code == 403) {
                    val minimumVersion = root?.minimumVersionForStatus920()
                    if (minimumVersion != null) return RawHttpResult.MinimumVersion(minimumVersion)
                }
                if (it.code in 400..499 && root?.status() == 4) {
                    return RawHttpResult.Failure(SourceError(SourceErrorKind.TERMS_REQUIRED))
                }
                if (it.code in 400..499 && root?.status() == 2) {
                    return RawHttpResult.Failure(SourceError(status2Kind))
                }
                if (it.code == 404) return RawHttpResult.Failure(SourceError(notFoundKind))
                if (it.code in 500..599) return RawHttpResult.Failure(SourceError(SourceErrorKind.SERVER))
                if (!it.isSuccessful || root == null) {
                    return RawHttpResult.Failure(SourceError(SourceErrorKind.PROTOCOL))
                }
                return when (root.status()) {
                    0 -> RawHttpResult.Success(root)
                    2 -> RawHttpResult.Failure(SourceError(status2Kind))
                    4 -> RawHttpResult.Failure(SourceError(SourceErrorKind.TERMS_REQUIRED))
                    else -> RawHttpResult.Failure(SourceError(SourceErrorKind.PROTOCOL))
                }
            }
        } catch (_: IOException) {
            return RawHttpResult.Failure(SourceError(SourceErrorKind.NETWORK))
        }
    }

    private fun parseLogin(root: JsonValue.Object, baseUrl: HttpUrl): LoginResult {
        val data = root.values["data"]?.asObject() ?: return LoginResult.protocolFailure()
        if (data.values["redirect"]?.asBoolean() == true) {
            val region = data.values["region"]?.asString() ?: return LoginResult.protocolFailure()
            return LoginResult.Redirect(region)
        }
        val userId = data.values["user"]?.asObject()?.values?.get("id")?.asString()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return LoginResult.protocolFailure()
        val ticket = data.values["authTicket"]?.asObject() ?: return LoginResult.protocolFailure()
        val token = ticket.values["token"]?.asString()?.takeIf { it.isNotBlank() }
            ?: return LoginResult.protocolFailure()
        val expirySeconds = ticket.values["expires"]?.asNumber()?.toLongExactOrNull()
            ?: return LoginResult.protocolFailure()
        val expiry = try {
            Instant.ofEpochSecond(expirySeconds)
        } catch (_: RuntimeException) {
            return LoginResult.protocolFailure()
        }
        if (!expiry.isAfter(clock.now())) return LoginResult.protocolFailure()
        return LoginResult.Success(
            Session(
                accountId = sha256(userId),
                token = token,
                regionHost = canonicalBase(baseUrl),
                expiresAt = expiry,
            ),
        )
    }

    private fun parseConnections(root: JsonValue.Object): SourceResult<List<Connection>> {
        val items = root.values["data"]?.asArray()?.values ?: return failure(SourceErrorKind.PROTOCOL)
        if (items.size > MAX_CONNECTIONS) return failure(SourceErrorKind.PROTOCOL)
        val seen = mutableSetOf<String>()
        val connections = items.map { item ->
            val connection = item.asObject() ?: return failure(SourceErrorKind.PROTOCOL)
            val id = connection.values["patientId"]?.asString()?.takeIf(::isValidConnectionId)
                ?: return failure(SourceErrorKind.PROTOCOL)
            if (!seen.add(id)) return failure(SourceErrorKind.PROTOCOL)
            val first = connection.values["firstName"]?.asString()?.trim().orEmpty()
            val last = connection.values["lastName"]?.asString()?.trim().orEmpty()
            val name = listOf(first, last).filter(String::isNotEmpty).joinToString(" ")
            if (name.isEmpty() || name.length > MAX_DISPLAY_NAME_LENGTH) {
                return failure(SourceErrorKind.PROTOCOL)
            }
            Connection(id, name)
        }
        return SourceResult.Success(connections)
    }

    private fun parseSnapshot(root: JsonValue.Object, connectionId: String): SourceResult<SourceSnapshot> {
        val data = root.values["data"]?.asObject() ?: return failure(SourceErrorKind.PROTOCOL)
        val connection = data.values["connection"]?.asObject()
            ?: return failure(SourceErrorKind.CONNECTION_REMOVED)
        val measurementValue = connection.values["glucoseMeasurement"]
            ?: return failure(SourceErrorKind.NO_READING)
        if (measurementValue === JsonValue.NullValue) return failure(SourceErrorKind.NO_READING)
        val measurement = measurementValue.asObject() ?: return failure(SourceErrorKind.PROTOCOL)

        val current = parseMeasurement(measurement, connectionId)
            ?: return failure(SourceErrorKind.PROTOCOL)
        val history = data.values["graphData"]?.asArray()?.values.orEmpty()
            .mapNotNull { it.asObject()?.let { item -> parseMeasurement(item, connectionId) } }
            .filter { it.measuredAt.isBefore(current.measuredAt) }
            .associateBy { it.measuredAt }
            .values
            .sortedBy { it.measuredAt }
            .takeLast(300)
        return SourceResult.Success(SourceSnapshot(current, history))
    }

    private fun parseMeasurement(measurement: JsonValue.Object, connectionId: String): Reading? {
        val measuredAt = parseFactoryTimestamp(measurement.values["FactoryTimestamp"]?.asString())
            ?: return null
        if (measuredAt.isAfter(clock.now().plus(MAX_FUTURE_SKEW))) {
            return null
        }
        val canonical = measurement.values["ValueInMgPerDl"]?.asNumber()?.toFiniteDouble()
            ?.takeIf { it > 0.0 && it <= MAX_GLUCOSE_MG_DL && it == Math.rint(it) }
            ?: return null
        val unit = when (measurement.values["GlucoseUnits"]?.asNumber()?.toIntExactOrNull()) {
            0 -> GlucoseUnit.MMOL_L
            1 -> GlucoseUnit.MG_DL
            else -> return null
        }
        val source = measurement.values["Value"]?.asNumber()?.toFiniteDouble()
            ?.takeIf { it > 0.0 && it <= MAX_SOURCE_VALUE }
            ?: return null
        val trend = when (measurement.values["TrendArrow"]?.asNumber()?.toIntExactOrNull()) {
            1 -> Trend.FALLING_QUICKLY
            2 -> Trend.FALLING
            3 -> Trend.STEADY
            4 -> Trend.RISING
            5 -> Trend.RISING_QUICKLY
            else -> Trend.UNKNOWN
        }
        return Reading(
            connectionId = connectionId,
            measuredAt = measuredAt,
            valueMgDl = canonical,
            sourceValue = source,
            sourceUnit = unit,
            specialValue = null,
            trend = trend,
            sourceRecordId = null,
            receivedAt = clock.now(),
        )
    }

    private fun commonRequest(url: HttpUrl, version: String): Request.Builder = Request.Builder()
        .url(url)
        .header("product", PRODUCT)
        .header("version", version)
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("cache-control", "no-cache")

    private fun authenticatedRequest(
        url: HttpUrl,
        session: Session,
        version: String,
    ): Request.Builder = commonRequest(url, version)
        .header("Account-Id", session.accountId)
        .header("Authorization", "Bearer ${session.token}")

    private fun validateSession(session: Session): HttpUrl? {
        if (session.token.isBlank() || !ACCOUNT_HASH.matches(session.accountId)) return null
        if (session.expiresAt?.let { !it.isAfter(clock.now()) } == true) return null
        val parsed = session.regionHost.toHttpUrlOrNull() ?: return null
        return allowedSessionUrls[canonicalBase(parsed)]
    }

    private fun retryAfter(value: String?): Instant {
        val now = clock.now()
        value?.trim()?.toLongOrNull()?.takeIf { it >= 0 && it <= MAX_RETRY_SECONDS }?.let {
            return now.plusSeconds(it)
        }
        value?.trim()?.let {
            try {
                val parsed = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(it))
                if (!parsed.isBefore(now) && !parsed.isAfter(now.plusSeconds(MAX_RETRY_SECONDS))) {
                    return parsed
                }
            } catch (_: DateTimeParseException) {
                // Use the conservative fallback below.
            }
        }
        return now.plus(DEFAULT_RATE_LIMIT_COOLDOWN)
    }

    private fun parseFactoryTimestamp(value: String?): Instant? {
        if (value == null) return null
        return try {
            LocalDateTime.parse(value, FACTORY_TIMESTAMP_FORMATTER).toInstant(ZoneOffset.UTC)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun readBoundedBody(response: Response): String? {
        val body = response.body ?: return null
        if (body.contentLength() > MAX_RESPONSE_BYTES) return null
        val source = body.source()
        val buffer = Buffer()
        while (buffer.size <= MAX_RESPONSE_BYTES) {
            val remaining = MAX_RESPONSE_BYTES + 1L - buffer.size
            if (source.read(buffer, minOf(8_192L, remaining)) == -1L) break
        }
        val bytes = buffer.readByteArray()
        if (bytes.size > MAX_RESPONSE_BYTES) return null
        return bytes.toString(Charsets.UTF_8)
    }

    private fun parseRoot(body: String): JsonValue.Object? = try {
        Json.parse(body).asObject()
    } catch (_: JsonParseException) {
        null
    }

    private fun JsonValue.Object.status(): Int? = values["status"]?.asNumber()?.toIntExactOrNull()

    private fun JsonValue.Object.minimumVersionForStatus920(): String? {
        if (status() != 920) return null
        return values["data"]?.asObject()?.values?.get("minimumVersion")?.asString()
    }

    private fun RawHttpResult.toHttpResult(): HttpResult = when (this) {
        is RawHttpResult.Success -> HttpResult.Success(root)
        is RawHttpResult.Failure -> HttpResult.Failure(error)
        is RawHttpResult.MinimumVersion -> HttpResult.Failure(SourceError(SourceErrorKind.PROTOCOL))
    }

    private fun LoginResult.toSourceResult(): SourceResult<Session> = when (this) {
        is LoginResult.Success -> SourceResult.Success(session)
        is LoginResult.Failure -> SourceResult.Failure(error)
        is LoginResult.Redirect -> failure(SourceErrorKind.PROTOCOL)
    }

    private sealed interface LoginResult {
        data class Success(val session: Session) : LoginResult
        data class Redirect(val region: String) : LoginResult
        data class Failure(val error: SourceError) : LoginResult

        companion object {
            fun protocolFailure() = Failure(SourceError(SourceErrorKind.PROTOCOL))
        }
    }

    private sealed interface RawHttpResult {
        data class Success(val root: JsonValue.Object) : RawHttpResult
        data class MinimumVersion(val version: String) : RawHttpResult
        data class Failure(val error: SourceError) : RawHttpResult
    }

    private sealed interface HttpResult {
        data class Success(val root: JsonValue.Object) : HttpResult
        data class Failure(val error: SourceError) : HttpResult
    }

    companion object {
        private const val PRODUCT = "llu.android"
        private const val BASELINE_CLIENT_VERSION = "4.17.0"
        private const val MAX_RESPONSE_BYTES = 1_048_576
        private const val REQUEST_TIMEOUT_MILLIS = 30_000L
        private const val MAX_CONNECTIONS = 100
        private const val MAX_DISPLAY_NAME_LENGTH = 200
        private const val MAX_GLUCOSE_MG_DL = 1_000.0
        private const val MAX_SOURCE_VALUE = 1_000.0
        private const val MAX_RETRY_SECONDS = 604_800L
        private val DEFAULT_RATE_LIMIT_COOLDOWN = Duration.ofMinutes(15)
        private val MAX_FUTURE_SKEW = Duration.ofMinutes(2)
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val ACCOUNT_HASH = Regex("[0-9a-f]{64}")
        private val VERSION = Regex("[0-9]{1,3}(?:\\.[0-9]{1,3}){1,3}")
        private val FACTORY_TIMESTAMP_FORMATTER = DateTimeFormatterBuilder()
            .parseCaseSensitive()
            .appendPattern("M/d/uuuu h:mm:ss a")
            .toFormatter(Locale.US)
            .withResolverStyle(ResolverStyle.STRICT)
        private val BOOTSTRAP_BASE_URL = "https://api.libreview.io/".toHttpUrl()
        private val REGION_BASE_URLS = listOf("ae", "ap", "au", "ca", "de", "eu", "eu2", "fr", "jp", "la", "us")
            .associateWith { "https://api-$it.libreview.io/".toHttpUrl() }

        private fun canonicalBase(url: HttpUrl): String = url.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()

        private fun isValidVersion(value: String): Boolean =
            value.length <= 16 && VERSION.matches(value) && value.split('.').all { it.toInt() <= 999 }

        private fun compareVersions(left: String, right: String): Int {
            val leftParts = left.split('.').map(String::toInt)
            val rightParts = right.split('.').map(String::toInt)
            repeat(maxOf(leftParts.size, rightParts.size)) { index ->
                val comparison = (leftParts.getOrElse(index) { 0 }).compareTo(rightParts.getOrElse(index) { 0 })
                if (comparison != 0) return comparison
            }
            return 0
        }

        private fun isValidConnectionId(value: String): Boolean =
            value.isNotBlank() && value.toByteArray(Charsets.UTF_8).size <= 512 && value.none(Char::isISOControl)

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }

        private fun failure(kind: SourceErrorKind): SourceResult.Failure =
            SourceResult.Failure(SourceError(kind))

        internal fun forTesting(
            client: OkHttpClient,
            clock: Clock,
            bootstrapBaseUrl: HttpUrl,
            regionalBaseUrls: Map<String, HttpUrl> = emptyMap(),
        ): LibreLinkUpSource = LibreLinkUpSource(client, clock, bootstrapBaseUrl, regionalBaseUrls)
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}

private fun BigDecimal.toIntExactOrNull(): Int? = try {
    intValueExact()
} catch (_: ArithmeticException) {
    null
}

private fun BigDecimal.toLongExactOrNull(): Long? = try {
    longValueExact()
} catch (_: ArithmeticException) {
    null
}

private fun BigDecimal.toFiniteDouble(): Double? = toDouble().takeIf(Double::isFinite)
