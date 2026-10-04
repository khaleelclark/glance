package com.khaleel.glance.ui

import com.khaleel.glance.alerts.AlertConfig
import com.khaleel.glance.alerts.AlertKind

internal const val chartBottomMgDl = 40.0
internal const val chartTopMgDl = 350.0

internal enum class ChartBand { LOW, IN_RANGE, HIGH }

internal data class ChartLimits(val lowMgDl: Double, val highMgDl: Double) {
    // Independently disabled alerts can make the effective defaults overlap.
    // Keep their actual values visible and consistently give red precedence.
    val overlaps: Boolean get() = lowMgDl >= highMgDl

    fun band(valueMgDl: Double): ChartBand = when {
        valueMgDl < lowMgDl -> ChartBand.LOW
        valueMgDl > highMgDl -> ChartBand.HIGH
        else -> ChartBand.IN_RANGE
    }
}

internal fun chartLimits(alerts: AlertConfig): ChartLimits = ChartLimits(
    alerts[AlertKind.LOW].let { if (it.enabled) it.thresholdMgDl.toDouble() else 70.0 },
    alerts[AlertKind.HIGH].let { if (it.enabled) it.thresholdMgDl.toDouble() else 240.0 },
)

/** Do not clamp source values; drawing clips the trace and marks out-of-scale samples. */
internal fun chartYFraction(valueMgDl: Double): Double =
    (chartTopMgDl - valueMgDl) / (chartTopMgDl - chartBottomMgDl)

/** The plot always keeps its regular 50 mg/dL source-scale grid. */
internal fun chartGridValues(): List<Double> = (350 downTo 50 step 50).map(Int::toDouble)

/** Landscape keeps every grid line but reduces labels and always names both scale endpoints. */
internal fun chartGridLabelValues(compact: Boolean = false): List<Double> =
    chartGridValues().filterIndexed { index, value -> !compact || (index % 2 == 0 && value > 50.0) } + chartBottomMgDl
