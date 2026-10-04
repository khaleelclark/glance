package com.khaleel.glance.data

import android.content.Context
import com.khaleel.glance.auth.SecureFileStore
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.SpecialValue
import com.khaleel.glance.core.Trend
import com.khaleel.glance.notification.LockScreenPrivacy
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.Duration

data class StoredPatientState(
    val selected: Connection?,
    val lastReading: Reading?,
    val history: List<Reading> = emptyList(),
)

interface PatientPersistence {
    fun load(): StoredPatientState
    fun save(state: StoredPatientState)
    fun clear()
}

class LocalStateStore(private val encrypted: SecureFileStore) : PatientPersistence {
    override fun load(): StoredPatientState {
        val value = encrypted.read("patient") ?: return StoredPatientState(null, null)
        val selected = value.optJSONObject("selected")?.let { Connection(it.getString("id"), it.getString("name")) }
        val reading = value.optJSONObject("reading")?.let(::readingFromJson)?.takeIf {
            it.connectionId == selected?.id &&
                Duration.between(it.measuredAt, Instant.now()) < Duration.ofHours(24)
        }
        val storedHistory = value.optJSONArray("history")?.let { items ->
            (0 until minOf(items.length(), 1500)).mapNotNull { index ->
                items.optJSONObject(index)?.let { runCatching { readingFromJson(it) }.getOrNull() }
            }
        }.orEmpty()
        val history = selected?.let {
            ReadingHistory.merge(emptyList(), storedHistory + listOfNotNull(reading), it.id, Instant.now())
        }.orEmpty()
        if (reading == null && value.has("reading")) {
            save(StoredPatientState(selected, null, history))
        }
        return StoredPatientState(selected, reading, history)
    }

    override fun save(state: StoredPatientState) {
        encrypted.write("patient", JSONObject().apply {
            state.selected?.let { put("selected", JSONObject().apply { put("id", it.id); put("name", it.displayName) }) }
            state.lastReading?.takeIf { it.connectionId == state.selected?.id }?.let { put("reading", readingToJson(it)) }
            state.selected?.let { selected ->
                put("history", JSONArray().apply {
                    ReadingHistory.merge(emptyList(), state.history, selected.id, Instant.now())
                        .forEach { put(readingToJson(it)) }
                })
            }
        })
    }

    private fun readingFromJson(it: JSONObject) = Reading(
        connectionId = it.getString("connectionId"),
        measuredAt = Instant.parse(it.getString("measuredAt")),
        valueMgDl = it.optDouble("valueMgDl").takeUnless(Double::isNaN),
        sourceValue = it.optDouble("sourceValue").takeUnless(Double::isNaN),
        sourceUnit = GlucoseUnit.valueOf(it.getString("sourceUnit")),
        specialValue = it.optString("specialValue").takeIf(String::isNotBlank)?.let(SpecialValue::valueOf),
        trend = Trend.valueOf(it.getString("trend")),
        sourceRecordId = it.optString("sourceRecordId").takeIf(String::isNotBlank),
        receivedAt = Instant.parse(it.getString("receivedAt")),
    )

    private fun readingToJson(reading: Reading) = JSONObject().apply {
        put("connectionId", reading.connectionId)
        put("measuredAt", reading.measuredAt.toString())
        put("valueMgDl", reading.valueMgDl)
        put("sourceValue", reading.sourceValue)
        put("sourceUnit", reading.sourceUnit.name)
        put("specialValue", reading.specialValue?.name)
        put("trend", reading.trend.name)
        put("sourceRecordId", reading.sourceRecordId)
        put("receivedAt", reading.receivedAt.toString())
    }

    override fun clear() { encrypted.delete("patient") }
}

interface NicknamePersistence {
    fun load(accountId: String): Map<String, String>
    fun save(accountId: String, nicknames: Map<String, String>)
    fun clear()
}

object NoNicknamePersistence : NicknamePersistence {
    override fun load(accountId: String): Map<String, String> = emptyMap()
    override fun save(accountId: String, nicknames: Map<String, String>) = Unit
    override fun clear() = Unit
}

/** Encrypted, account-scoped aliases kept separately from the selected-person health cache. */
class LocalNicknameStore(private val encrypted: SecureFileStore) : NicknamePersistence {
    override fun load(accountId: String): Map<String, String> {
        val value = encrypted.read(FILE_NAME) ?: return emptyMap()
        if (value.optString("accountScope") != accountScope(accountId)) {
            encrypted.delete(FILE_NAME)
            return emptyMap()
        }
        val aliases = value.optJSONArray("aliases") ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        for (index in 0 until minOf(aliases.length(), MAX_ENTRIES)) {
            val item = aliases.optJSONObject(index) ?: continue
            val connectionId = item.optString("connectionId").takeIf {
                it.isNotBlank() && it.length <= MAX_CONNECTION_ID_CHARS
            } ?: continue
            val nickname = normalizeFollowerNickname(item.optString("nickname"))
            if (nickname.isNotBlank()) result[connectionId] = nickname
        }
        return result
    }

    override fun save(accountId: String, nicknames: Map<String, String>) {
        val bounded = nicknames.entries
            .asSequence()
            .filter { it.key.isNotBlank() && it.key.length <= MAX_CONNECTION_ID_CHARS }
            .mapNotNull { (connectionId, nickname) ->
                normalizeFollowerNickname(nickname).takeIf(String::isNotBlank)?.let { connectionId to it }
            }
            .toList()
            .takeLast(MAX_ENTRIES)
        if (bounded.isEmpty()) {
            encrypted.delete(FILE_NAME)
            return
        }
        encrypted.write(FILE_NAME, JSONObject().apply {
            put("version", 1)
            put("accountScope", accountScope(accountId))
            put("aliases", JSONArray().apply {
                bounded.forEach { (connectionId, nickname) ->
                    put(JSONObject().apply {
                        put("connectionId", connectionId)
                        put("nickname", nickname)
                    })
                }
            })
        })
    }

    override fun clear() { encrypted.delete(FILE_NAME) }

    private fun accountScope(accountId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(accountId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val FILE_NAME = "nicknames"
        const val MAX_ENTRIES = 32
        const val MAX_CONNECTION_ID_CHARS = 512
    }
}

internal fun normalizeFollowerNickname(input: String): String {
    val output = StringBuilder()
    var codePoints = 0
    var separatorPending = false
    var offset = 0
    while (offset < input.length && codePoints < MAX_NICKNAME_CODE_POINTS) {
        val codePoint = input.codePointAt(offset)
        offset += Character.charCount(codePoint)
        val category = Character.getType(codePoint)
        val separator = Character.isWhitespace(codePoint) || Character.isISOControl(codePoint) ||
            category == Character.FORMAT.toInt()
        if (separator) {
            separatorPending = output.isNotEmpty()
            continue
        }
        if (separatorPending) {
            // Never use the final available code point for trailing whitespace.
            if (codePoints + 2 > MAX_NICKNAME_CODE_POINTS) break
            output.append(' ')
            codePoints++
            separatorPending = false
        }
        output.appendCodePoint(codePoint)
        codePoints++
    }
    return output.toString()
}

private const val MAX_NICKNAME_CODE_POINTS = 40

enum class RetryReason { PROVIDER, TRANSIENT }

data class RetryWindow(val until: Instant, val reason: RetryReason)

interface SettingsPersistence {
    var unit: GlucoseUnit
    var showOnLockScreen: Boolean
    var enhancedNotification: Boolean
    var numericStatusIcon: Boolean
    var accentHex: String
    var intervalMinutes: Int
    var resumeAfterBoot: Boolean
    var monitoringEnabled: Boolean
    var retryWindow: RetryWindow?
    val nextAllowedFetch: Instant? get() = retryWindow?.until
    fun clear()
}

class UserSettings(context: Context) : SettingsPersistence {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val lockScreenPrivacy = LockScreenPrivacy(context)
    override var unit: GlucoseUnit
        get() = runCatching { GlucoseUnit.valueOf(prefs.getString("unit", GlucoseUnit.MG_DL.name)!!) }.getOrDefault(GlucoseUnit.MG_DL)
        set(value) = prefs.edit().putString("unit", value.name).apply()
    override var showOnLockScreen: Boolean
        get() = lockScreenPrivacy.visible
        set(value) { lockScreenPrivacy.visible = value }
    override var enhancedNotification: Boolean
        // A stored choice still wins; new installs and users who never changed this switch
        // request the status chip when Android supports promotion.
        get() = prefs.getBoolean("enhanced_explicit", true)
        set(value) = prefs.edit().putBoolean("enhanced_explicit", value).apply()
    override var accentHex: String
        get() {
            val stored = prefs.getString("accent_hex", "#287F7A") ?: "#287F7A"
            // Carry the owner's new default through upgrades, retaining other custom colors.
            return if (stored.equals("#1677D2", ignoreCase = true)) "#287F7A" else stored
        }
        set(value) = prefs.edit().putString("accent_hex", value).apply()
    override var numericStatusIcon: Boolean
        get() = prefs.getBoolean("numeric_status_icon", true)
        set(value) = prefs.edit().putBoolean("numeric_status_icon", value).apply()
    override var intervalMinutes: Int
        get() = prefs.getInt("interval_minutes", 1).takeIf { it == 1 || it == 5 } ?: 1
        set(value) { require(value == 1 || value == 5); prefs.edit().putInt("interval_minutes", value).apply() }
    override var resumeAfterBoot: Boolean
        get() = prefs.getBoolean("resume_after_boot", true)
        set(value) = prefs.edit().putBoolean("resume_after_boot", value).apply()
    override var monitoringEnabled: Boolean
        get() = prefs.getBoolean("monitoring_enabled", false)
        set(value) = prefs.edit().putBoolean("monitoring_enabled", value).commit().let { require(it) }
    override var retryWindow: RetryWindow?
        get() {
            // Read the reason and deadline from one preference snapshot.
            val stored = prefs.all
            val until = ((stored["next_allowed_fetch"] as? Long) ?: 0L).takeIf { it > 0 }
                ?.let(Instant::ofEpochMilli) ?: return null
            // Older versions did not record a reason. Preserve a possible provider cooldown.
            val reason = runCatching {
                RetryReason.valueOf((stored["retry_reason"] as? String) ?: "PROVIDER")
            }.getOrDefault(RetryReason.PROVIDER)
            return RetryWindow(until, reason)
        }
        set(value) = prefs.edit()
            .putLong("next_allowed_fetch", value?.until?.toEpochMilli() ?: 0L)
            .putString("retry_reason", value?.reason?.name)
            .commit().let { require(it) }

    override fun clear() {
        // Privacy failure must not prevent credentials and patient caches from being cleared.
        // The UI attempts revocation explicitly and reports failure before signing out.
        runCatching { lockScreenPrivacy.visible = false }
        prefs.edit().clear().commit()
    }
}
