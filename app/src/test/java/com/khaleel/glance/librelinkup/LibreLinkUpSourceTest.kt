package com.khaleel.glance.librelinkup

import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.data.ReadingHistory
import com.khaleel.glance.core.Session
import com.khaleel.glance.core.SourceErrorKind
import com.khaleel.glance.core.SourceResult
import com.khaleel.glance.core.SourceSnapshot
import com.khaleel.glance.core.SourceResponseFormat
import com.khaleel.glance.core.SourceResponseInfo
import com.khaleel.glance.core.Trend
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibreLinkUpSourceTest {
    private lateinit var bootstrap: MockWebServer
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        bootstrap = MockWebServer().apply { start() }
        clock = FakeClock(Instant.parse("2026-09-27T05:00:00Z"))
    }

    @After
    fun tearDown() {
        bootstrap.shutdown()
    }

    @Test
    fun `graph samples are validated sorted and deduplicated without replacing current`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("graph_with_history.json")))

        val snapshot = source().snapshot(session(), "synthetic-patient-1").successValue<SourceSnapshot>()

        assertEquals(112.0, snapshot.current.valueMgDl!!, 0.0)
        assertEquals(listOf(104.0, 109.0), snapshot.history.map { it.valueMgDl })
        assertEquals(
            listOf("2026-09-27T04:32:00Z", "2026-09-27T04:37:00Z"),
            snapshot.history.map { it.measuredAt.toString() },
        )
    }

    @Test
    fun `minimal graph rows retain canonical values without inventing display values or trends`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("graph_minimal_history.json")))
        val snapshot = source().snapshot(session(), "synthetic-patient-1").successValue<SourceSnapshot>()
        assertEquals(112.0, snapshot.current.sourceValue!!, 0.0)
        assertEquals(Trend.STEADY, snapshot.current.trend)
        assertEquals(listOf(95.0, 99.0, 104.0, 108.0), snapshot.history.map { it.valueMgDl })
        assertEquals(listOf(Trend.STEADY, Trend.UNKNOWN, Trend.UNKNOWN, Trend.RISING), snapshot.history.map { it.trend })
        assertEquals(5.3, snapshot.history.first().sourceValue!!, 0.0)
        assertEquals(GlucoseUnit.MMOL_L, snapshot.history.first().sourceUnit)
        val minimal = snapshot.history[2]
        assertNull(minimal.sourceValue)
        assertEquals(GlucoseUnit.MG_DL, minimal.sourceUnit)
        assertEquals("104", GlucoseFormat.value(minimal, GlucoseUnit.MG_DL, Locale.US))
        assertEquals("5.8", GlucoseFormat.value(minimal, GlucoseUnit.MMOL_L, Locale.US))
    }

    @Test
    fun `mixed shape graph duplicates preserve provider precision in either order`() = runBlocking {
        val full = """{"FactoryTimestamp":"9/27/2026 4:37:00 AM","ValueInMgPerDl":69,"GlucoseUnits":0,"Value":3.9,"TrendArrow":3}"""
        val minimal = """{"FactoryTimestamp":"9/27/2026 4:37:00 AM","ValueInMgPerDl":69}"""
        for (rows in listOf("$full,$minimal", "$minimal,$full")) {
            val json = fixture("graph_current_mgdl.json").substringBefore("\"graphData\"") + "\"graphData\":[$rows]}}"
            bootstrap.enqueue(jsonResponse(json))
            val snapshot = source().snapshot(session(), "synthetic-patient-1").successValue<SourceSnapshot>()
            val reading = snapshot.history.single()
            assertEquals(3.9, reading.sourceValue!!, 0.0)
            assertEquals(GlucoseUnit.MMOL_L, reading.sourceUnit)
            assertEquals("3.9", GlucoseFormat.value(reading, GlucoseUnit.MMOL_L, Locale.US))
            assertEquals(if (rows.startsWith(full)) Trend.UNKNOWN else Trend.STEADY, reading.trend)
            assertEquals(112.0, snapshot.current.valueMgDl!!, 0.0)
        }
    }

    @Test
    fun `corrected minimal duplicate invalidates earlier provider display value`() = runBlocking {
        val rows = """[
          {"FactoryTimestamp":"9/27/2026 4:37:00 AM","ValueInMgPerDl":69,"GlucoseUnits":0,"Value":3.9},
          {"FactoryTimestamp":"9/27/2026 4:37:00 AM","ValueInMgPerDl":70}
        ]"""
        val json = fixture("graph_current_mgdl.json").substringBefore("\"graphData\"") + "\"graphData\":$rows}}"
        bootstrap.enqueue(jsonResponse(json))
        val snapshot = source().snapshot(session(), "synthetic-patient-1").successValue<SourceSnapshot>()
        val reading = snapshot.history.single()
        assertEquals(70.0, reading.valueMgDl!!, 0.0)
        assertNull(reading.sourceValue)
        assertEquals(GlucoseUnit.MG_DL, reading.sourceUnit)
        assertEquals(Trend.UNKNOWN, reading.trend)
    }

    @Test
    fun `full day of minute backfill survives adapter and history merge instead of 300 row truncation`() = runBlocking {
        val current = clock.now()
        val format = DateTimeFormatter.ofPattern("M/d/uuuu h:mm:ss a", Locale.US).withZone(ZoneOffset.UTC)
        val rows = (1..1440).joinToString(",") { minute ->
            """{"FactoryTimestamp":"${format.format(current.minusSeconds(minute * 60L))}","ValueInMgPerDl":112}"""
        }
        bootstrap.enqueue(jsonResponse("""{"status":0,"data":{"connection":{"glucoseMeasurement":{"FactoryTimestamp":"${format.format(current)}","ValueInMgPerDl":112,"GlucoseUnits":1,"Value":112}},"graphData":[$rows]}}"""))
        val snapshot = source().snapshot(session(), "synthetic-patient-1").successValue<SourceSnapshot>()
        assertEquals(1440, snapshot.history.size)
        val merged = ReadingHistory.merge(emptyList(), snapshot.history + snapshot.current, snapshot.current.connectionId, current)
        assertEquals(1441, merged.size)
        assertEquals(current.minusSeconds(86400), merged.first().measuredAt)
        assertEquals(current, merged.last().measuredAt)
    }

    @Test
    fun `minimal history keeps timestamp and numeric validation and cannot replace current`() = runBlocking {
        val rows = """[
          {"FactoryTimestamp":"9/27/2026 4:37:00 AM","ValueInMgPerDl":108},
          {"FactoryTimestamp":"9/27/2026 4:37:00 AM","ValueInMgPerDl":109},
          {"FactoryTimestamp":"9/27/2026 4:42:00 AM","ValueInMgPerDl":999},
          {"FactoryTimestamp":"9/27/2026 5:10:00 AM","ValueInMgPerDl":110},
          {"FactoryTimestamp":"bad","ValueInMgPerDl":105},
          {"Timestamp":"9/27/2026 4:32:00 AM","ValueInMgPerDl":105},
          {"FactoryTimestamp":"9/27/2026 4:32:00 AM"},
          {"FactoryTimestamp":"9/27/2026 4:32:00 AM","ValueInMgPerDl":0},
          {"FactoryTimestamp":"9/27/2026 4:32:00 AM","ValueInMgPerDl":-1},
          {"FactoryTimestamp":"9/27/2026 4:32:00 AM","ValueInMgPerDl":105.5},
          {"FactoryTimestamp":"9/27/2026 4:32:00 AM","ValueInMgPerDl":1001},
          {"FactoryTimestamp":"9/27/2026 4:32:00 AM","ValueInMgPerDl":1e309},
          null
        ]"""
        val json = fixture("graph_current_mgdl.json").substringBefore("\"graphData\"") + "\"graphData\":$rows}}"
        bootstrap.enqueue(jsonResponse(json))
        val snapshot = source().snapshot(session(), "synthetic-patient-1").successValue<SourceSnapshot>()
        assertEquals(112.0, snapshot.current.valueMgDl!!, 0.0)
        assertEquals(listOf(109.0), snapshot.history.map { it.valueMgDl })
        assertEquals(Trend.UNKNOWN, snapshot.history.single().trend)
    }

    @Test
    fun `history optional fields do not weaken current reading validation`() = runBlocking {
        for (missing in listOf("GlucoseUnits", "Value")) {
            val json = fixture("graph_current_mgdl.json").replace(Regex("\\\"$missing\\\"\\s*:\\s*[0-9.]+,?"), "")
                .replace(Regex(",\\s*}"), "}")
            bootstrap.enqueue(jsonResponse(json))
            assertFailure(source().snapshot(session(), "synthetic-patient-1"), SourceErrorKind.PROTOCOL)
        }
    }

    @Test
    fun `login hashes account id and sends exact unauthenticated headers`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("login_success.json")))
        val source = source()

        val result = source.signIn(Credentials("follower@example.invalid", "synthetic-password"))

        val session = result.successValue<Session>()
        assertEquals("3deb1c6edcc01176f6b4b5cd67970e8a4e2f5a58982ac7016543361a3b1acb6a", session.accountId)
        assertEquals("synthetic-token", session.token)
        assertEquals(Instant.parse("2030-01-01T00:00:00Z"), session.expiresAt)
        assertEquals(bootstrap.url("/").toString(), session.regionHost)
        val request = bootstrap.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/llu/auth/login", request.path)
        assertEquals("llu.android", request.getHeader("product"))
        assertEquals("4.17.0", request.getHeader("version"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("application/json", request.getHeader("Content-Type"))
        assertEquals("no-cache", request.getHeader("cache-control"))
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("Account-Id"))
        assertEquals(
            "{\"email\":\"follower@example.invalid\",\"password\":\"synthetic-password\"}",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `redirect repeats login once only at an allowlisted regional host`() = runBlocking {
        val regional = MockWebServer().apply { start() }
        try {
            bootstrap.enqueue(jsonResponse(fixture("login_redirect_us.json")))
            regional.enqueue(jsonResponse(fixture("login_success.json")))
            val source = source(mapOf("us" to regional))

            val session = source.signIn(credentials()).successValue<Session>()

            assertEquals(regional.url("/").toString(), session.regionHost)
            assertEquals(1, bootstrap.requestCount)
            assertEquals(1, regional.requestCount)
            assertNull(regional.takeRequest().getHeader("Authorization"))
        } finally {
            regional.shutdown()
        }
    }

    @Test
    fun `unknown or full URL redirect fails closed without another request`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("login_redirect_unknown.json")))

        val result = source().signIn(credentials())

        assertFailure(result, SourceErrorKind.PROTOCOL)
        assertEquals(SourceResponseInfo(200, SourceResponseFormat.JSON), result.failure().responseInfo)
        assertEquals(1, bootstrap.requestCount)
    }

    @Test
    fun `status 2 and status 4 have distinct typed outcomes without continuation`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("login_invalid_credentials.json")))
        bootstrap.enqueue(jsonResponse(fixture("login_terms_required.json")))
        bootstrap.enqueue(jsonResponse(fixture("login_policy_required.json")))
        val source = source()

        assertFailure(source.signIn(credentials()), SourceErrorKind.INVALID_CREDENTIALS)
        assertFailure(source.signIn(credentials()), SourceErrorKind.TERMS_REQUIRED)
        assertFailure(source.signIn(credentials()), SourceErrorKind.TERMS_REQUIRED)
        repeat(3) { assertEquals("/llu/auth/login", bootstrap.takeRequest().path) }
        assertEquals(3, bootstrap.requestCount)
    }

    @Test
    fun `status 920 adopts a bounded minimum version for one retry`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("login_minimum_version.json"), code = 403))
        bootstrap.enqueue(jsonResponse(fixture("login_success.json")))

        val result = source().signIn(credentials())

        result.successValue<Session>()
        assertEquals("4.17.0", bootstrap.takeRequest().getHeader("version"))
        assertEquals("4.18.2", bootstrap.takeRequest().getHeader("version"))
        assertEquals(2, bootstrap.requestCount)
    }

    @Test
    fun `malformed minimum version fails closed and is not retried`() = runBlocking {
        bootstrap.enqueue(jsonResponse("""{"status":920,"data":{"minimumVersion":"https://bad.invalid"}}""", 403))

        val result = source().signIn(credentials())

        assertFailure(result, SourceErrorKind.PROTOCOL)
        assertEquals(1, bootstrap.requestCount)
    }

    @Test
    fun `connections preserve stable ids and send authenticated headers`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("connections_multiple.json")))

        val result = source().connections(session())

        val connections = result.successValue<List<com.khaleel.glance.core.Connection>>()
        assertEquals(listOf("synthetic-patient-1", "synthetic-patient-2"), connections.map { it.id })
        assertEquals(listOf("Pat Example", "Alex Fixture"), connections.map { it.displayName })
        val request = bootstrap.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/llu/connections", request.path)
        assertEquals("Bearer synthetic-token", request.getHeader("Authorization"))
        assertEquals("a".repeat(64), request.getHeader("Account-Id"))
    }

    @Test
    fun `empty connections remain an explicit successful empty list`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("connections_empty.json")))

        val connections = source().connections(session()).successValue<List<com.khaleel.glance.core.Connection>>()

        assertTrue(connections.isEmpty())
    }

    @Test
    fun `blocked shared person request retains only safe status and format without retrying`() = runBlocking {
        bootstrap.enqueue(MockResponse().setResponseCode(403)
            .addHeader("Content-Type", "text/html; charset=UTF-8")
            .addHeader("Set-Cookie", "secret-session-cookie")
            .setBody("<html>Blocked: synthetic-token follower@example.invalid Pat Example</html>"))

        val error = source().connections(session()).failure()

        assertEquals(SourceErrorKind.PROTOCOL, error.kind)
        assertEquals(SourceResponseInfo(403, SourceResponseFormat.WEB_PAGE), error.responseInfo)
        assertFalse(error.toString().contains("synthetic-token"))
        assertFalse(error.toString().contains("follower@example.invalid"))
        assertFalse(error.toString().contains("Pat Example"))
        assertFalse(error.toString().contains("secret-session-cookie"))
        assertEquals(1, bootstrap.requestCount)
        assertEquals("/llu/connections", bootstrap.takeRequest().path)
    }

    @Test
    fun `challenge web page with HTTP 200 cannot become an empty connection list`() = runBlocking {
        bootstrap.enqueue(MockResponse().setResponseCode(200)
            .addHeader("Content-Type", "text/html").setBody("<html>Verify your browser</html>"))

        val error = source().connections(session()).failure()

        assertEquals(SourceErrorKind.PROTOCOL, error.kind)
        assertEquals(SourceResponseInfo(200, SourceResponseFormat.WEB_PAGE), error.responseInfo)
        assertEquals(1, bootstrap.requestCount)
    }

    @Test
    fun `invalid JSON envelope reports a schema problem with response metadata`() = runBlocking {
        bootstrap.enqueue(jsonResponse("""{"status":0,"data":{"unexpected":"private-value"}}"""))

        val error = source().connections(session()).failure()

        assertEquals(SourceErrorKind.PROTOCOL, error.kind)
        assertEquals(SourceResponseInfo(200, SourceResponseFormat.JSON), error.responseInfo)
        assertFalse(error.toString().contains("private-value"))
    }

    @Test
    fun `latest normalizes UTC measurement source values and trend`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("graph_current_mgdl.json")))

        val reading = source().latest(session(), "synthetic-patient-1").successValue<com.khaleel.glance.core.Reading>()

        assertEquals("synthetic-patient-1", reading.connectionId)
        assertEquals(Instant.parse("2026-09-27T04:42:00Z"), reading.measuredAt)
        assertEquals(112.0, reading.valueMgDl!!, 0.0)
        assertEquals(112.0, reading.sourceValue!!, 0.0)
        assertEquals(GlucoseUnit.MG_DL, reading.sourceUnit)
        assertEquals(Trend.STEADY, reading.trend)
        assertNull(reading.specialValue)
        assertEquals(clock.now(), reading.receivedAt)
    }

    @Test
    fun `mmol display value is preserved while canonical mgdl remains unchanged`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("graph_current_mmol.json")))

        val reading = source().latest(session(), "synthetic-patient-1").successValue<com.khaleel.glance.core.Reading>()

        assertEquals(117.0, reading.valueMgDl!!, 0.0)
        assertEquals(6.5, reading.sourceValue!!, 0.0)
        assertEquals(GlucoseUnit.MMOL_L, reading.sourceUnit)
        assertEquals(Trend.RISING, reading.trend)
    }

    @Test
    fun `unknown trend is retained as unknown`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("graph_unknown_trend.json")))

        val reading = source().latest(session(), "synthetic-patient-1").successValue<com.khaleel.glance.core.Reading>()

        assertEquals(Trend.UNKNOWN, reading.trend)
    }

    @Test
    fun `unverified high and low flags never invent a special value`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("graph_high_low_flags.json")))

        val reading = source().latest(session(), "synthetic-patient-1").successValue<com.khaleel.glance.core.Reading>()

        assertEquals(500.0, reading.valueMgDl!!, 0.0)
        assertNull(reading.specialValue)
    }

    @Test
    fun `future and malformed measurements fail protocol validation`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("graph_future_timestamp.json")))
        bootstrap.enqueue(jsonResponse(fixture("graph_malformed.json")))
        val source = source()

        assertFailure(source.latest(session(), "synthetic-patient-1"), SourceErrorKind.PROTOCOL)
        assertFailure(source.latest(session(), "synthetic-patient-1"), SourceErrorKind.PROTOCOL)
    }

    @Test
    fun `local Timestamp is never used when FactoryTimestamp is absent`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("graph_missing_factory_timestamp.json")))

        val result = source().latest(session(), "synthetic-patient-1")

        assertFailure(result, SourceErrorKind.PROTOCOL)
    }

    @Test
    fun `connection id is encoded as exactly one safe path segment`() = runBlocking {
        bootstrap.enqueue(jsonResponse(fixture("graph_current_mgdl.json")))

        source().latest(session(), "../patient?query#fragment").successValue<com.khaleel.glance.core.Reading>()

        val path = bootstrap.takeRequest().path.orEmpty()
        assertEquals("/llu/connections/..%2Fpatient%3Fquery%23fragment/graph", path)
        assertFalse(path.contains("?query"))
    }

    @Test
    fun `expired sessions fail before network access`() = runBlocking {
        val expired = session().copy(expiresAt = clock.now())

        val result = source().latest(expired, "synthetic-patient-1")

        assertFailure(result, SourceErrorKind.AUTH_EXPIRED)
        assertEquals(0, bootstrap.requestCount)
    }

    @Test
    fun `401 and 404 map to auth expired and removed connection`() = runBlocking {
        bootstrap.enqueue(MockResponse().setResponseCode(401))
        bootstrap.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        val source = source()

        assertFailure(source.connections(session()), SourceErrorKind.AUTH_EXPIRED)
        assertFailure(source.latest(session(), "synthetic-patient-1"), SourceErrorKind.CONNECTION_REMOVED)
    }

    @Test
    fun `404 and status 2 are operation specific outside login`() = runBlocking {
        bootstrap.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        bootstrap.enqueue(jsonResponse(fixture("login_invalid_credentials.json")))
        val source = source()

        assertFailure(source.signIn(credentials()), SourceErrorKind.PROTOCOL)
        assertFailure(source.connections(session()), SourceErrorKind.PROTOCOL)
    }

    @Test
    fun `server errors are typed without parsing or retrying`() = runBlocking {
        bootstrap.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))

        val result = source().connections(session())

        assertFailure(result, SourceErrorKind.SERVER)
        assertEquals(1, bootstrap.requestCount)
    }

    @Test
    fun `Retry-After delta seconds and fallback produce absolute cooldowns`() = runBlocking {
        bootstrap.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "120"))
        bootstrap.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "invalid"))
        val source = source()

        val seconds = source.connections(session()).failure()
        val fallback = source.connections(session()).failure()

        assertEquals(SourceErrorKind.RATE_LIMITED, seconds.kind)
        assertEquals(clock.now().plusSeconds(120), seconds.retryAfter)
        assertEquals(clock.now().plusSeconds(15 * 60), fallback.retryAfter)
    }

    @Test
    fun `Retry-After HTTP date is parsed against injected clock`() = runBlocking {
        bootstrap.enqueue(
            MockResponse().setResponseCode(429)
                .addHeader("Retry-After", "Sun, 27 Sep 2026 05:05:00 GMT"),
        )

        val error = source().connections(session()).failure()

        assertEquals(Instant.parse("2026-09-27T05:05:00Z"), error.retryAfter)
    }

    @Test
    fun `malformed and oversized responses are protocol failures`() = runBlocking {
        bootstrap.enqueue(jsonResponse("{not-json"))
        bootstrap.enqueue(jsonResponse(" ".repeat(1_048_577)))
        val source = source()

        assertFailure(source.connections(session()), SourceErrorKind.PROTOCOL)
        assertFailure(source.connections(session()), SourceErrorKind.PROTOCOL)
    }

    private fun source(regions: Map<String, MockWebServer> = emptyMap()): LibreLinkUpSource =
        LibreLinkUpSource.forTesting(
            client = OkHttpClient(),
            clock = clock,
            bootstrapBaseUrl = bootstrap.url("/"),
            regionalBaseUrls = regions.mapValues { it.value.url("/") },
        )

    private fun credentials() = Credentials("follower@example.invalid", "synthetic-password")

    private fun session() = Session(
        accountId = "a".repeat(64),
        token = "synthetic-token",
        regionHost = bootstrap.url("/").toString(),
        expiresAt = clock.now().plusSeconds(3_600),
    )

    private fun jsonResponse(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .addHeader("Content-Type", "application/json")
        .setBody(body)

    private fun fixture(name: String): String = checkNotNull(
        javaClass.getResource("/librelinkup/$name"),
    ).readText()

    private fun <T> SourceResult<T>.failure() = (this as SourceResult.Failure).error

    private fun assertFailure(result: SourceResult<*>, expected: SourceErrorKind) {
        assertTrue("Expected failure but got $result", result is SourceResult.Failure)
        assertEquals(expected, (result as SourceResult.Failure).error.kind)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> SourceResult<*>.successValue(): T {
        assertTrue("Expected success but got $this", this is SourceResult.Success<*>)
        return (this as SourceResult.Success<*>).value as T
    }

    private class FakeClock(private var instant: Instant) : Clock {
        override fun now(): Instant = instant
        override fun elapsedRealtimeMillis(): Long = 0
    }
}
