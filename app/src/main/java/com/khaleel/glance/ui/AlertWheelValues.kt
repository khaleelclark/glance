package com.khaleel.glance.ui

import com.khaleel.glance.core.GlucoseUnit
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class WheelValue(val storedValue: Int, val label: String)

/** Show every mg/dL value or every visible 0.1 mmol/L step within an ordered rule's bounds. */
internal fun thresholdWheelValues(minimumMgDl: Int, maximumMgDl: Int, unit: GlucoseUnit): List<WheelValue> {
    require(minimumMgDl in 40..400 && maximumMgDl in minimumMgDl..400)
    if (unit == GlucoseUnit.MG_DL) {
        return (minimumMgDl..maximumMgDl).map { WheelValue(it, it.toString()) }
    }
    return (minimumMgDl..maximumMgDl)
        .groupBy { (it / MGDL_PER_MMOL * 10).roundToInt() }
        .map { (tenths, mgValues) ->
            val targetMgDl = tenths / 10.0 * MGDL_PER_MMOL
            WheelValue(mgValues.minBy { abs(it - targetMgDl) }, "${tenths / 10}.${tenths % 10}")
        }
}

internal fun nearestWheelIndex(values: List<WheelValue>, storedValue: Int): Int =
    values.indices.minByOrNull { abs(values[it].storedValue - storedValue) } ?: 0

private const val MGDL_PER_MMOL = 18.0182
