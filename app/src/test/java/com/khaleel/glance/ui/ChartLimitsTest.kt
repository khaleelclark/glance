package com.khaleel.glance.ui

import com.khaleel.glance.alerts.AlertConfig
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.alerts.AlertRule
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.Trend
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartLimitsTest {
    @Test fun disabledAlertsUseSeventyAndTwoFortyEvenWithSavedCustomValues() {
        val config = config(false, 85, false, 210)
        assertEquals(ChartLimits(70.0, 240.0), chartLimits(config))
    }

    @Test fun eachEnabledOrdinaryAlertSuppliesItsOwnLimit() {
        assertEquals(ChartLimits(85.0, 210.0), chartLimits(config(true, 85, true, 210)))
        assertEquals(ChartLimits(85.0, 240.0), chartLimits(config(true, 85, false, 210)))
        assertEquals(ChartLimits(70.0, 210.0), chartLimits(config(false, 85, true, 210)))
    }

    @Test fun urgentSettingsDoNotReplaceOrdinaryLowAndHigh() {
        val config = AlertConfig(AlertConfig.defaults().rules + mapOf(
            AlertKind.URGENT_LOW to AlertRule(true, 60),
            AlertKind.URGENT_HIGH to AlertRule(true, 320),
        ))
        assertEquals(ChartLimits(70.0, 240.0), chartLimits(config))
    }

    @Test fun onlyStrictlyOutsideLimitsUsesLowOrHighColor() {
        val limits = ChartLimits(70.0, 240.0)
        assertEquals(ChartBand.LOW, limits.band(69.9))
        assertEquals(ChartBand.IN_RANGE, limits.band(70.0))
        assertEquals(ChartBand.IN_RANGE, limits.band(240.0))
        assertEquals(ChartBand.HIGH, limits.band(240.1))
    }

    @Test fun fixedScaleKeepsFortyAndThreeFiftyButDoesNotRewriteOutOfScaleValues() {
        assertEquals(1.0, chartYFraction(40.0), 0.000001)
        assertEquals(0.0, chartYFraction(350.0), 0.000001)
        assertEquals(true, chartYFraction(25.0) > 1.0)
        assertEquals(true, chartYFraction(400.0) < 0.0)
        assertEquals(listOf(350.0, 300.0, 250.0, 200.0, 150.0, 100.0, 50.0), chartGridValues())
        assertEquals(chartGridValues() + chartBottomMgDl, chartGridLabelValues())
        assertEquals(listOf(350.0, 250.0, 150.0, 40.0), chartGridLabelValues(compact = true))
    }

    @Test fun limitsOutsideScaleStayExactWithoutChangingRegularGrid() {
        val limits = chartLimits(config(true, 80, true, 380))
        assertEquals(380.0, limits.highMgDl, 0.0)
        assertEquals(listOf(350.0, 300.0, 250.0, 200.0, 150.0, 100.0, 50.0), chartGridValues())
    }

    @Test fun independentlyDisabledLimitCanOverlapAndRedHasDefinedPriority() {
        val limits = chartLimits(config(true, 260, false, 300))
        assertEquals(ChartLimits(260.0, 240.0), limits)
        assertEquals(true, limits.overlaps)
        assertEquals(ChartBand.LOW, limits.band(250.0))
        assertEquals(ChartBand.HIGH, limits.band(270.0))
    }

    @Test fun mmolLabelsConvertCanonicalLimitsWithoutChangingClassification() {
        val now = Instant.parse("2026-10-01T12:00:00Z")
        fun label(value: Double) = GlucoseFormat.value(
            Reading("synthetic", now, value, null, GlucoseUnit.MG_DL, null, Trend.STEADY, null, now),
            GlucoseUnit.MMOL_L, Locale.US,
        )
        assertEquals("2.2", label(chartBottomMgDl))
        assertEquals("19.4", label(chartTopMgDl))
        assertEquals("3.9", label(70.0))
        assertEquals("13.3", label(240.0))
        assertEquals(ChartBand.LOW, ChartLimits(70.0, 240.0).band(69.0))
    }

    private fun config(lowEnabled: Boolean, low: Int, highEnabled: Boolean, high: Int) =
        AlertConfig(AlertConfig.defaults().rules + mapOf(
            AlertKind.LOW to AlertRule(lowEnabled, low),
            AlertKind.HIGH to AlertRule(highEnabled, high),
            AlertKind.URGENT_HIGH to AlertRule(false, 400),
        ))
}
