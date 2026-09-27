package com.khaleel.glance.notification

import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.SpecialValue
import com.khaleel.glance.core.Trend
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentFormatterTest {
    private val formatter = NotificationContentFormatter(ZoneId.of("America/Chicago"), Locale.US)

    @Test
    fun `fresh reading is prominent and always has absolute measurement time`() {
        val content = formatter.format(state(), GlucoseUnit.MG_DL)

        assertEquals("Patrick · 112 mg/dL →", content.title)
        assertEquals("Reading 10:42 AM", content.text)
        assertEquals("112", content.statusChipText)
        assertTrue(content.bigText.contains("Trend: steady"))
    }

    @Test
    fun `delayed reading is explicitly labeled and retains value arrow and time`() {
        val content = formatter.format(
            state(freshness = ReadingFreshness.DELAYED),
            GlucoseUnit.MG_DL,
        )

        assertEquals("Patrick · Reading delayed", content.title)
        assertEquals("Last: 112 mg/dL → at 10:42 AM", content.text)
        assertEquals("OLD", content.statusChipText)
    }

    @Test
    fun `stale reading is historical and does not show trend`() {
        val content = formatter.format(
            state(freshness = ReadingFreshness.STALE, trend = Trend.RISING),
            GlucoseUnit.MG_DL,
        )

        assertEquals("Patrick · No recent reading", content.title)
        assertEquals("Last: 112 mg/dL at 10:42 AM", content.text)
        assertFalse(content.bigText.contains("Trend"))
        assertFalse(content.text.contains("↗"))
        assertEquals("STALE", content.statusChipText)
    }

    @Test
    fun `paused state wins and preserves absolute last-reading time`() {
        val content = formatter.format(state(monitoringEnabled = false), GlucoseUnit.MG_DL)

        assertEquals("Patrick · Monitoring paused", content.title)
        assertEquals("Last: 112 mg/dL at 10:42 AM", content.text)
        assertTrue(content.bigText.contains("Open Glance to resume"))
    }

    @Test
    fun `absent reading describes offline wait without a numeric value`() {
        val content = formatter.format(
            state(reading = null, freshness = ReadingFreshness.ABSENT, connection = ConnectionState.OFFLINE),
            GlucoseUnit.MG_DL,
        )

        assertEquals("Patrick · Waiting for first reading", content.title)
        assertEquals("No internet. Glance will retry when allowed.", content.text)
        assertFalse(content.bigText.contains("mg/dL"))
    }

    @Test
    fun `absent reading surfaces sign-in required`() {
        val content = formatter.format(
            state(
                reading = null,
                freshness = ReadingFreshness.ABSENT,
                connection = ConnectionState.SIGN_IN_REQUIRED,
            ),
            GlucoseUnit.MG_DL,
        )

        assertEquals("Patrick · Sign-in required", content.title)
        assertEquals("Open Glance to reconnect.", content.text)
    }

    @Test
    fun `future or invalid timestamp is never formatted as fresh`() {
        val content = formatter.format(
            state(freshness = ReadingFreshness.INVALID_TIME),
            GlucoseUnit.MG_DL,
        )

        assertEquals("Patrick · Reading time unavailable", content.title)
        assertTrue(content.text.contains("10:42 AM"))
        assertTrue(content.text.contains("time unverified"))
    }

    @Test
    fun `offline can coexist with fresh reading without hiding measurement age`() {
        val content = formatter.format(
            state(connection = ConnectionState.OFFLINE),
            GlucoseUnit.MG_DL,
        )

        assertEquals("Reading 10:42 AM", content.text)
        assertTrue(content.bigText.contains("No internet. Showing the last reading."))
    }

    @Test
    fun `chosen mmol unit converts canonical value to one decimal`() {
        val content = formatter.format(state(valueMgDl = 112.0), GlucoseUnit.MMOL_L)

        assertEquals("Patrick · 6.2 mmol/L →", content.title)
        assertEquals("6.2", content.statusChipText)
    }

    @Test
    fun `matching source unit uses source display value`() {
        val content = formatter.format(
            state(
                valueMgDl = 111.0,
                sourceValue = 6.3,
                sourceUnit = GlucoseUnit.MMOL_L,
            ),
            GlucoseUnit.MMOL_L,
        )

        assertEquals("Patrick · 6.3 mmol/L →", content.title)
        assertEquals("6.3", content.statusChipText)
    }

    @Test
    fun `special low retains chosen unit and does not invent a number`() {
        val content = formatter.format(
            state(valueMgDl = null, sourceValue = null, specialValue = SpecialValue.LOW),
            GlucoseUnit.MG_DL,
        )

        assertEquals("Patrick · LOW mg/dL →", content.title)
        assertEquals("LOW", content.statusChipText)
    }

    @Test
    fun `unknown trend does not invent an arrow or trend description`() {
        val content = formatter.format(state(trend = Trend.UNKNOWN), GlucoseUnit.MG_DL)

        assertEquals("Patrick · 112 mg/dL", content.title)
        assertFalse(content.bigText.contains("Trend"))
    }

    @Test
    fun `unable to start is truthful even when a last reading exists`() {
        val content = formatter.format(
            state(runtime = RuntimeState.UNABLE_TO_START),
            GlucoseUnit.MG_DL,
        )

        assertEquals("Patrick · Monitoring unavailable", content.title)
        assertEquals("Last: 112 mg/dL at 10:42 AM", content.text)
        assertTrue(content.bigText.contains("Open Glance"))
    }

    private fun state(
        monitoringEnabled: Boolean = true,
        runtime: RuntimeState = RuntimeState.RUNNING,
        connection: ConnectionState = ConnectionState.READY,
        reading: Reading? = reading(),
        freshness: ReadingFreshness = ReadingFreshness.FRESH,
        trend: Trend = Trend.STEADY,
        valueMgDl: Double? = 112.0,
        sourceValue: Double? = 112.0,
        sourceUnit: GlucoseUnit = GlucoseUnit.MG_DL,
        specialValue: SpecialValue? = null,
    ): DisplayState {
        val adjustedReading = reading?.copy(
            trend = trend,
            valueMgDl = valueMgDl,
            sourceValue = sourceValue,
            sourceUnit = sourceUnit,
            specialValue = specialValue,
        )
        return DisplayState(
            monitoringEnabled = monitoringEnabled,
            runtime = runtime,
            connection = connection,
            person = Connection("connection-1", "Patrick"),
            reading = adjustedReading,
            freshness = freshness,
            lastSuccessfulFetch = Instant.parse("2026-09-27T15:43:00Z"),
            nextAllowedFetch = null,
        )
    }

    private fun reading() = Reading(
        connectionId = "connection-1",
        measuredAt = Instant.parse("2026-09-27T15:42:00Z"),
        valueMgDl = 112.0,
        sourceValue = 112.0,
        sourceUnit = GlucoseUnit.MG_DL,
        specialValue = null,
        trend = Trend.STEADY,
        sourceRecordId = "record-1",
        receivedAt = Instant.parse("2026-09-27T15:43:00Z"),
    )
}
