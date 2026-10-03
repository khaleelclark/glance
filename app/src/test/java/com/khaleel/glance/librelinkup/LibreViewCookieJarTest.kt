package com.khaleel.glance.librelinkup

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibreViewCookieJarTest {
    private val bootstrap = "https://api.libreview.io/llu/auth/login".toHttpUrl()
    private val regional = "https://api-us.libreview.io/llu/connections".toHttpUrl()
    private var now = 1_800_000_000_000L
    private fun jar() = LibreViewCookieJar(setOf(bootstrap, regional)) { now }
    private fun cookie(name: String = "__cf_bm", domain: String = "libreview.io", path: String = "/",
        value: String = "synthetic-cookie", expiresAt: Long = now + 1_800_000L) =
        Cookie.Builder().name(name).value(value).domain(domain).path(path)
            .secure().httpOnly().expiresAt(expiresAt).build()

    @Test fun parentCookieFollowsRegionalRouteButNeverLeavesReviewedOrigins() {
        val jar = jar()
        jar.saveFromResponse(bootstrap, listOf(cookie()))
        assertEquals(listOf("__cf_bm"), jar.loadForRequest(regional).map { it.name })
        for (url in listOf("https://other.libreview.io/", "https://libreview.io.bad.invalid/",
            "http://api-us.libreview.io/", "https://api-us.libreview.io:444/")) {
            assertTrue(jar.loadForRequest(url.toHttpUrl()).isEmpty())
        }
    }

    @Test fun insecureForeignAndPublicSuffixCookiesAreRejected() {
        val jar = jar()
        jar.saveFromResponse(bootstrap, listOf(
            Cookie.Builder().name("insecure").value("fixture").domain("libreview.io").build(),
            cookie("foreign", "bad.invalid"), cookie("suffix", "io"),
        ))
        jar.saveFromResponse("https://bad.invalid/".toHttpUrl(), listOf(cookie()))
        jar.saveFromResponse("http://api.libreview.io/".toHttpUrl(), listOf(cookie()))
        assertTrue(jar.loadForRequest(bootstrap).isEmpty())
    }

    @Test fun hostOnlyAndPathScopeAreRespectedWithLongestPathFirst() {
        val jar = jar()
        val hostOnly = Cookie.Builder().name("host").value("fixture").hostOnlyDomain(bootstrap.host)
            .path("/").secure().expiresAt(now + 1000).build()
        jar.saveFromResponse(bootstrap, listOf(cookie("root"), hostOnly,
            cookie("llu", path = "/llu"), cookie("private", path = "/private")))
        assertEquals(listOf("llu", "root"), jar.loadForRequest(regional).map { it.name })
        assertEquals(listOf("llu", "root", "host"), jar.loadForRequest(bootstrap).map { it.name })
    }

    @Test fun replacementDeletionAndExpiryDoNotResendStaleValues() {
        val jar = jar()
        jar.saveFromResponse(bootstrap, listOf(cookie()))
        val replacement = cookie(value = "replacement")
        jar.saveFromResponse(bootstrap, listOf(replacement))
        assertEquals(listOf("replacement"), jar.loadForRequest(regional).map { it.value })
        jar.saveFromResponse(bootstrap, listOf(cookie(value = "replacement", expiresAt = now - 1)))
        assertTrue(jar.loadForRequest(regional).isEmpty())
        jar.saveFromResponse(bootstrap, listOf(cookie()))
        now += 1_800_000L
        assertTrue(jar.loadForRequest(regional).isEmpty())
    }

    @Test fun expiredDeletionCanOmitSecureButCannotComeFromAnUnreviewedOrigin() {
        val jar = jar()
        jar.saveFromResponse(bootstrap, listOf(cookie()))
        val deletion = Cookie.Builder().name("__cf_bm").value("").domain("libreview.io")
            .path("/").expiresAt(now - 1).build()
        jar.saveFromResponse("https://other.libreview.io/".toHttpUrl(), listOf(deletion))
        jar.saveFromResponse("http://api.libreview.io/".toHttpUrl(), listOf(deletion))
        assertEquals(1, jar.loadForRequest(regional).size)
        jar.saveFromResponse(bootstrap, listOf(
            Cookie.Builder().name("__cf_bm").value("insecure").domain("libreview.io")
                .path("/").expiresAt(now + 1000).build(),
        ))
        assertEquals("synthetic-cookie", jar.loadForRequest(regional).single().value)
        jar.saveFromResponse(bootstrap, listOf(deletion))
        assertTrue(jar.loadForRequest(regional).isEmpty())
    }

    @Test fun jarBoundsMemoryAndRejectsOversizedCookies() {
        val jar = jar()
        jar.saveFromResponse(bootstrap, (0..64).map { cookie("c$it") })
        val names = jar.loadForRequest(regional).map { it.name }
        assertEquals(64, names.size)
        assertTrue("c0" !in names)
        jar.saveFromResponse(bootstrap, listOf(cookie("oversized", value = "x".repeat(4097))))
        assertTrue(jar.loadForRequest(regional).none { it.name == "oversized" })
    }
}
