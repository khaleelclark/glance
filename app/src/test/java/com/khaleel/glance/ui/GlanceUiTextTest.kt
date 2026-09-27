package com.khaleel.glance.ui

import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.SpecialValue
import com.khaleel.glance.core.Trend
import com.khaleel.glance.notification.NotificationContentFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class GlanceUiTextTest {
    private val measuredAt = Instant.parse("2026-09-27T10:42:00Z")
    private val now = Instant.parse("2026-09-27T10:43:30Z")
    private val formatter = GlanceUiText(ZoneId.of("UTC"), Locale.US)

    @Test
    fun freshReadingShowsSourceValueUnitTrendAndAbsoluteTime() {
        val text = formatter.reading(state(), GlucoseUnit.MG_DL, now)!!

        assertEquals("112", text.value)
        assertEquals("mg/dL", text.unit)
        assertEquals("→", text.trendSymbol)
        assertEquals("steady", text.trendDescription)
        assertEquals("Reading at 10:42 AM · 1 min ago", text.measured)
        assertEquals("Current reading", text.freshness)
    }

    @Test
    fun mmolConversionUsesCanonicalValueAndOneDecimal() {
        val reading = reading().copy(sourceValue = null, sourceUnit = GlucoseUnit.MG_DL)
        val text = formatter.reading(state(reading = reading), GlucoseUnit.MMOL_L, now)!!

        assertEquals("6.2", text.value)
        assertEquals("mmol/L", text.unit)
    }

    @Test
    fun mmolRoundingMatchesNotificationAtBoundaryValues() {
        val notification = NotificationContentFormatter(ZoneId.of("UTC"), Locale.US)
        for (mgDl in listOf(100.0, 109.0)) {
            val state = state(reading = reading().copy(valueMgDl = mgDl, sourceValue = mgDl))
            val ui = formatter.reading(state, GlucoseUnit.MMOL_L, now)!!
            val card = notification.format(state, GlucoseUnit.MMOL_L)
            assertEquals("${ui.value} ${ui.unit} →", card.text)
        }
    }

    @Test
    fun sourceDisplayValueWinsWhenRequestedUnitMatches() {
        val reading = reading().copy(
            valueMgDl = 111.7,
            sourceValue = 6.3,
            sourceUnit = GlucoseUnit.MMOL_L,
        )

        assertEquals("6.3", formatter.reading(state(reading = reading), GlucoseUnit.MMOL_L, now)!!.value)
    }

    @Test
    fun staleReadingDoesNotPresentCurrentTrend() {
        val text = formatter.reading(state(freshness = ReadingFreshness.STALE), GlucoseUnit.MG_DL, now)!!

        assertEquals("No recent reading", text.freshness)
        assertEquals("", text.trendSymbol)
        assertEquals("Trend unavailable", text.trendDescription)
    }

    @Test
    fun specialLowDoesNotInventNumericValue() {
        val reading = reading().copy(valueMgDl = null, sourceValue = null, specialValue = SpecialValue.LOW)

        assertEquals("LOW", formatter.reading(state(reading = reading), GlucoseUnit.MG_DL, now)!!.value)
    }

    @Test
    fun hiddenLockScreenPreviewContainsNoPersonOrValue() {
        val preview = formatter.notificationPreview(
            state = state(),
            settings = settings(showOnLockScreen = false),
            now = now,
        )

        assertEquals("Glance", preview.first)
        assertEquals("New glucose reading available", preview.second)
    }

    @Test
    fun visibleDelayedPreviewLabelsAgeHonestly() {
        val preview = formatter.notificationPreview(
            state = state(freshness = ReadingFreshness.DELAYED),
            settings = settings(showOnLockScreen = true),
            now = now.plusSeconds(6 * 60),
        )

        assertEquals("Patrick · Reading delayed", preview.first)
        assertEquals("Reading at 10:42 AM · 7 min ago", preview.second)
    }

    @Test
    fun connectionAndMonitoringMessagesStayIndependent() {
        val state = state().copy(
            monitoringEnabled = true,
            runtime = RuntimeState.RUNNING,
            connection = ConnectionState.OFFLINE,
        )

        assertEquals("Monitoring is running", formatter.monitoring(state))
        assertEquals("No internet. Showing the last reading.", formatter.connection(state))
        assertNull(formatter.connection(state()))
    }

    @Test
    fun settingsRejectUnsupportedCadence() {
        assertThrows(IllegalArgumentException::class.java) {
            settings(intervalMinutes = 2)
        }
    }

    private fun state(
        freshness: ReadingFreshness = ReadingFreshness.FRESH,
        reading: Reading? = reading(),
    ) = DisplayState(
        monitoringEnabled = true,
        runtime = RuntimeState.RUNNING,
        connection = ConnectionState.READY,
        person = Connection("connection-1", "Patrick"),
        reading = reading,
        freshness = freshness,
        lastSuccessfulFetch = now,
        nextAllowedFetch = null,
    )

    private fun reading() = Reading(
        connectionId = "connection-1",
        measuredAt = measuredAt,
        valueMgDl = 112.0,
        sourceValue = 112.0,
        sourceUnit = GlucoseUnit.MG_DL,
        specialValue = null,
        trend = Trend.STEADY,
        sourceRecordId = "reading-1",
        receivedAt = measuredAt.plusSeconds(20),
    )

    private fun settings(
        showOnLockScreen: Boolean = false,
        intervalMinutes: Int = 1,
    ) = UserSettingsUi(
        unit = GlucoseUnit.MG_DL,
        showOnLockScreen = showOnLockScreen,
        intervalMinutes = intervalMinutes,
        enhancedNotification = true,
        resumeAfterBoot = true,
    )
}
