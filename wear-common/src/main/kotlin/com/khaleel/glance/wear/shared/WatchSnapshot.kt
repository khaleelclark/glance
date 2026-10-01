package com.khaleel.glance.wear.shared

/** Small, credential-free phone projection. No account/person identifiers or history. */
enum class WatchStatus { HIDDEN, STOPPED, WAITING, ACTION_REQUIRED, READING }

data class WatchSnapshot(
    val revision: Long,
    val sentAtMillis: Long,
    val status: WatchStatus,
    val measuredAtMillis: Long = 0,
    val value: String = "",
    val unit: String = "",
    val arrow: String = "",
    val trend: String = "",
    val notificationsEnabled: Boolean = false,
) {
    fun valid(): Boolean = revision > 0 && sentAtMillis > 0 &&
        value.length <= 12 && trend.length <= 32 &&
        unit in setOf("", "mg/dL", "mmol/L") &&
        arrow in setOf("", "↑", "↗", "→", "↘", "↓") &&
        (status == WatchStatus.READING && measuredAtMillis > 0 &&
            value.matches(Regex("(?:[0-9]{1,4}(?:[.,][0-9])?|LOW|HIGH)")) ||
            status != WatchStatus.READING && measuredAtMillis == 0L &&
            value.isEmpty() && unit.isEmpty() && arrow.isEmpty() && trend.isEmpty())

    companion object {
        const val PATH = "/glance/watch/v1/latest"
        const val SCHEMA = 1
        const val LEASE_MILLIS = 5 * 60 * 1000L
        const val FRESH_MILLIS = 5 * 60 * 1000L
        const val STALE_MILLIS = 15 * 60 * 1000L
        const val FUTURE_TOLERANCE_MILLIS = 2 * 60 * 1000L
    }
}

data class WatchPresentation(
    val heading: String,
    val value: String = "",
    val unit: String = "",
    val arrow: String = "",
    val trend: String = "",
    val measuredAtMillis: Long = 0,
    val fresh: Boolean = false,
    val expiresAtMillis: Long = 0,
) {
    val shortText: String get() = if (fresh) "$value$arrow" else heading
    companion object {
        fun from(snapshot: WatchSnapshot?, nowMillis: Long): WatchPresentation {
            if (snapshot == null || !snapshot.valid()) return WatchPresentation("Open phone")
            if (snapshot.status == WatchStatus.HIDDEN) return WatchPresentation("Hidden")
            if (snapshot.status == WatchStatus.STOPPED) return WatchPresentation("Stopped")
            val transportAge = nowMillis - snapshot.sentAtMillis
            if (transportAge < -WatchSnapshot.FUTURE_TOLERANCE_MILLIS ||
                transportAge >= WatchSnapshot.LEASE_MILLIS) return WatchPresentation("Open phone")
            if (snapshot.status != WatchStatus.READING) return WatchPresentation(
                if (snapshot.status == WatchStatus.ACTION_REQUIRED) "Check phone" else "Waiting")
            val age = nowMillis - snapshot.measuredAtMillis
            if (age < -WatchSnapshot.FUTURE_TOLERANCE_MILLIS) return WatchPresentation("Time unknown")
            val fresh = age < WatchSnapshot.FRESH_MILLIS
            return WatchPresentation(
                heading = if (fresh) "Glance" else if (age < WatchSnapshot.STALE_MILLIS) "Delayed" else "Stale",
                value = snapshot.value, unit = snapshot.unit,
                arrow = if (fresh) snapshot.arrow else "", trend = if (fresh) snapshot.trend else "",
                measuredAtMillis = snapshot.measuredAtMillis, fresh = fresh,
                expiresAtMillis = minOf(snapshot.sentAtMillis + WatchSnapshot.LEASE_MILLIS,
                    if (fresh) snapshot.measuredAtMillis + WatchSnapshot.FRESH_MILLIS
                    else snapshot.sentAtMillis + WatchSnapshot.LEASE_MILLIS),
            )
        }
    }
}
