package com.khaleel.glance

import android.content.Context
import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.GlucoseSource
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.Session
import com.khaleel.glance.core.SourceError
import com.khaleel.glance.core.SourceErrorKind
import com.khaleel.glance.core.SourceResult
import com.khaleel.glance.core.Trend
import com.khaleel.glance.librelinkup.LibreLinkUpSource
import okhttp3.OkHttpClient

/** Debug-only synthetic source selected solely by an explicit debug fixture action. */
internal object SourceFactory {
    const val DEMO_EMAIL = "demo@example.invalid"
    const val DEMO_PASSWORD = "demo"
    const val DEMO_PERSON_ID = "synthetic-person"

    fun create(context: Context, http: OkHttpClient, clock: Clock): GlucoseSource {
        val prefs = context.getSharedPreferences("debug_fixture", Context.MODE_PRIVATE)
        val real = LibreLinkUpSource(http, clock)
        return object : GlucoseSource {
            private fun demo() = prefs.getBoolean("enabled", false)

            override suspend fun signIn(credentials: Credentials): SourceResult<Session> {
                if (!demo()) return real.signIn(credentials)
                if (credentials.email != DEMO_EMAIL || credentials.password != DEMO_PASSWORD) {
                    return SourceResult.Failure(SourceError(SourceErrorKind.INVALID_CREDENTIALS))
                }
                return SourceResult.Success(
                    Session("0".repeat(64), "synthetic-token", "https://example.invalid/", clock.now().plusSeconds(86_400)),
                )
            }

            override suspend fun connections(session: Session): SourceResult<List<Connection>> =
                if (demo()) SourceResult.Success(listOf(Connection(DEMO_PERSON_ID, "DEMO · Patrick Example")))
                else real.connections(session)

            override suspend fun latest(session: Session, connectionId: String): SourceResult<Reading> {
                if (!demo()) return real.latest(session, connectionId)
                if (connectionId != DEMO_PERSON_ID) {
                    return SourceResult.Failure(SourceError(SourceErrorKind.CONNECTION_REMOVED))
                }
                val count = prefs.getInt("poll_count", 0) + 1
                prefs.edit().putInt("poll_count", count).putLong("last_poll_ms", clock.now().toEpochMilli()).commit()
                return SourceResult.Success(
                    Reading(
                        connectionId = DEMO_PERSON_ID,
                        measuredAt = clock.now().minusSeconds(60),
                        valueMgDl = (111 + count % 3).toDouble(),
                        sourceValue = (111 + count % 3).toDouble(),
                        sourceUnit = GlucoseUnit.MG_DL,
                        specialValue = null,
                        trend = Trend.STEADY,
                        sourceRecordId = "synthetic-" + count,
                        receivedAt = clock.now(),
                    ),
                )
            }
        }
    }
}
