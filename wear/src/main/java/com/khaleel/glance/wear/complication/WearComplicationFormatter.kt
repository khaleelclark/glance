package com.khaleel.glance.wear.complication

import com.khaleel.glance.wear.shared.WatchPresentation
import com.khaleel.glance.wear.shared.WatchSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ComplicationFrame(
    val startMillis: Long,
    val endMillis: Long,
    val shortText: String,
    val shortTitle: String = "",
    val longText: String,
)

data class FormattedTimeline(
    val defaultText: String,
    val frames: List<ComplicationFrame>,
)

object WearComplicationFormatter {
    fun format(snapshot: WatchSnapshot?, nowMillis: Long): FormattedTimeline {
        val current = WatchPresentation.from(snapshot, nowMillis)
        if (snapshot == null || !snapshot.valid() || current.value.isEmpty()) {
            return FormattedTimeline(current.heading, emptyList())
        }
        val leaseEnd = snapshot.sentAtMillis + WatchSnapshot.LEASE_MILLIS
        if (leaseEnd <= nowMillis) return FormattedTimeline("Open phone", emptyList())

        val frames = mutableListOf<ComplicationFrame>()
        if (current.fresh) {
            val freshEnd = minOf(leaseEnd, snapshot.measuredAtMillis + WatchSnapshot.FRESH_MILLIS)
            if (freshEnd > nowMillis) frames += frame(current, nowMillis, freshEnd)
            if (freshEnd < leaseEnd) {
                val delayed = WatchPresentation.from(snapshot, freshEnd)
                frames += frame(delayed, freshEnd, leaseEnd)
            }
        } else {
            frames += frame(current, nowMillis, leaseEnd)
        }
        return FormattedTimeline(defaultText = "Open phone", frames = frames)
    }

    private fun frame(
        presentation: WatchPresentation,
        startMillis: Long,
        endMillis: Long,
    ): ComplicationFrame {
        val combined = "${presentation.value}${presentation.arrow}"
        val shortText = if (presentation.fresh) {
            if (combined.length <= 7) combined else presentation.value.take(7)
        } else {
            presentation.heading
        }
        val shortTitle = when {
            presentation.fresh && combined.length > 7 -> presentation.arrow
            presentation.fresh -> presentation.unit
            else -> ""
        }
        val value = listOf(presentation.value, presentation.unit, presentation.arrow)
            .filter { it.isNotBlank() }.joinToString(" ")
        val longText = if (presentation.fresh) {
            "$value · ${formatTime(presentation.measuredAtMillis)}"
        } else {
            "${presentation.heading} · Last $value at ${formatTime(presentation.measuredAtMillis)}"
        }
        return ComplicationFrame(startMillis, endMillis, shortText, shortTitle, longText)
    }

    private fun formatTime(millis: Long): String = TIME_FORMAT.format(
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()),
    )

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
}
