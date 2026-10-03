package com.khaleel.glance.librelinkup

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Volatile HTTPS cookie state for reviewed LibreView origins only. Never persisted or logged. */
internal class LibreViewCookieJar(
    private val origins: Set<HttpUrl>,
    private val nowMillis: () -> Long,
) : CookieJar {
    private data class Key(val name: String, val domain: String, val path: String)
    private val cookies = LinkedHashMap<Key, Cookie>()
    private val allowedDomains = origins.map { it.host }.toSet() + "libreview.io"

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!allowedOrigin(url)) return
        removeExpired()
        for (cookie in cookies) {
            if (cookie.domain !in allowedDomains ||
                !domainMatches(cookie, url) ||
                cookie.name.length + cookie.value.length + cookie.domain.length + cookie.path.length > 4_096
            ) continue
            val key = Key(cookie.name, cookie.domain, cookie.path)
            // HTTPS providers can delete a Secure cookie without repeating that attribute.
            if (cookie.expiresAt <= nowMillis()) {
                this.cookies.remove(key)
                continue
            }
            if (!cookie.secure) continue
            this.cookies.remove(key)
            this.cookies[key] = cookie
        }
        while (this.cookies.size > 64) this.cookies.remove(this.cookies.keys.first())
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        removeExpired()
        if (!allowedOrigin(url)) return emptyList()
        return cookies.values.filter { it.matches(url) }.sortedByDescending { it.path.length }
    }

    private fun allowedOrigin(url: HttpUrl) = url.isHttps && origins.any {
        it.isHttps && it.host == url.host && it.port == url.port
    }

    private fun domainMatches(cookie: Cookie, url: HttpUrl) =
        cookie.domain == url.host || (!cookie.hostOnly && url.host.endsWith("." + cookie.domain))

    private fun removeExpired() {
        val now = nowMillis()
        cookies.entries.removeAll { it.value.expiresAt <= now }
    }
}
