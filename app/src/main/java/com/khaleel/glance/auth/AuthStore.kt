package com.khaleel.glance.auth

import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.Session
import org.json.JSONObject
import java.time.Instant

data class SavedAuth(val credentials: Credentials, val session: Session?)

interface AuthPersistence {
    fun save(auth: SavedAuth)
    fun load(): SavedAuth?
    fun clear()
}

class AuthStore(private val encrypted: SecureFileStore) : AuthPersistence {
    override fun save(auth: SavedAuth) {
        encrypted.write("auth", JSONObject().apply {
            put("email", auth.credentials.email)
            put("password", auth.credentials.password)
            auth.session?.let { session ->
                put("session", JSONObject().apply {
                    put("accountId", session.accountId)
                    put("token", session.token)
                    put("regionHost", session.regionHost)
                    put("expiresAt", session.expiresAt?.toString())
                })
            }
        })
    }

    override fun load(): SavedAuth? {
        val data = encrypted.read("auth") ?: return null
        val session = data.optJSONObject("session")?.let {
            Session(
                accountId = it.getString("accountId"),
                token = it.getString("token"),
                regionHost = it.getString("regionHost"),
                expiresAt = it.optString("expiresAt").takeIf(String::isNotBlank)?.let(Instant::parse),
            )
        }
        return SavedAuth(Credentials(data.getString("email"), data.getString("password")), session)
    }

    override fun clear() { encrypted.delete("auth") }
}
