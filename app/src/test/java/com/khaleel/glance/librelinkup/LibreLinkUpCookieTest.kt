package com.khaleel.glance.librelinkup

import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.Session
import com.khaleel.glance.core.SourceResult
import java.net.InetAddress
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real HTTPS/OkHttp cookie replay, with synthetic certificates and loopback-only DNS. */
class LibreLinkUpCookieTest {
    private lateinit var bootstrap: MockWebServer
    private lateinit var regional: MockWebServer
    private lateinit var client: OkHttpClient
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-03T00:00:00Z")
        override fun elapsedRealtimeMillis() = 0L
    }
    private val securityCookie = "__cf_bm=synthetic-bot-state; Domain=libreview.io; Path=/; Secure; HttpOnly"

    @Before fun setUp() {
        val held = HeldCertificate.Builder().commonName("synthetic LibreView fixture")
            .addSubjectAlternativeName("api.libreview.io")
            .addSubjectAlternativeName("api-us.libreview.io").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(held).build()
        bootstrap = MockWebServer().apply { useHttps(serverCertificates.sslSocketFactory(), false); start() }
        regional = MockWebServer().apply { useHttps(serverCertificates.sslSocketFactory(), false); start() }
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .dns(object : Dns {
                override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
            }).build()
    }

    @After fun tearDown() { bootstrap.shutdown(); regional.shutdown() }

    @Test fun bootstrapSecurityCookieReachesRegionalLoginAndSharedPeopleOverHttps() = runBlocking {
        bootstrap.enqueue(json(fixture("login_redirect_us.json")).addHeader("Set-Cookie", securityCookie))
        regional.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Cookie") != "__cf_bm=synthetic-bot-state") {
                    return MockResponse().setResponseCode(403).addHeader("Content-Type", "text/html")
                        .setBody("<html>synthetic rejected client</html>")
                }
                return json(fixture(if (request.path == "/llu/auth/login")
                    "login_success.json" else "connections_multiple.json"))
            }
        }
        val source = source()
        val result = source.signIn(Credentials("fixture@example.invalid", "synthetic-password"))
        assertTrue(result is SourceResult.Success)
        val session = (result as SourceResult.Success).value
        assertTrue(source.connections(session) is SourceResult.Success)
        assertNull(bootstrap.takeRequest().getHeader("Cookie"))
        assertEquals("__cf_bm=synthetic-bot-state", regional.takeRequest().getHeader("Cookie"))
        assertEquals("__cf_bm=synthetic-bot-state", regional.takeRequest().getHeader("Cookie"))
        assertEquals(1, bootstrap.requestCount)
        assertEquals(2, regional.requestCount)
    }

    @Test fun newSignInAndExplicitClearDropPreviousCookieState() = runBlocking {
        val source = source()
        repeat(2) {
            bootstrap.enqueue(json(fixture("login_success.json")).addHeader("Set-Cookie", securityCookie))
            assertTrue(source.signIn(Credentials("fixture@example.invalid", "synthetic-password")) is SourceResult.Success)
            assertNull(bootstrap.takeRequest().getHeader("Cookie"))
        }
        source.clearTransportSession()
        bootstrap.enqueue(json(fixture("connections_empty.json")))
        assertTrue(source.connections(session()) is SourceResult.Success)
        assertNull(bootstrap.takeRequest().getHeader("Cookie"))
    }

    @Test fun lateResponseCannotRefillCookieStateAfterExplicitClear() = runBlocking {
        val release = CountDownLatch(1)
        bootstrap.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                check(release.await(5, TimeUnit.SECONDS))
                return json(fixture("connections_empty.json")).addHeader("Set-Cookie", securityCookie)
            }
        }
        try {
            val source = source()
            val oldRequest = async(Dispatchers.IO) { source.connections(session()) }
            assertTrue(bootstrap.takeRequest(5, TimeUnit.SECONDS) != null)
            source.clearTransportSession()
            release.countDown()
            assertTrue(oldRequest.await() is SourceResult.Success)
            assertTrue(source.connections(session()) is SourceResult.Success)
            assertNull(bootstrap.takeRequest().getHeader("Cookie"))
        } finally { release.countDown() }
    }

    private fun bootstrapUrl() = bootstrap.url("/").newBuilder().host("api.libreview.io").build()
    private fun source() = LibreLinkUpSource.forTesting(client, clock, bootstrapUrl(),
        mapOf("us" to regional.url("/").newBuilder().host("api-us.libreview.io").build()))
    private fun session() = Session("a".repeat(64), "synthetic-token", bootstrapUrl().toString(),
        clock.now().plusSeconds(3600))
    private fun json(body: String) = MockResponse().addHeader("Content-Type", "application/json").setBody(body)
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/librelinkup/$name")).readText()
}
