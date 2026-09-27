package com.khaleel.glance.core

import java.util.Locale
import kotlin.math.roundToInt

/** One display conversion for the app, notification, and lock-screen preview. */
object GlucoseFormat {
    private const val MG_DL_PER_MMOL_L = 18.0182

    fun value(reading: Reading, unit: GlucoseUnit, locale: Locale): String {
        reading.specialValue?.let {
            return when (it) {
                SpecialValue.LOW -> "LOW"
                SpecialValue.HIGH -> "HIGH"
                SpecialValue.UNAVAILABLE -> "Unavailable"
            }
        }
        val numeric = when {
            reading.sourceUnit == unit && reading.sourceValue != null -> reading.sourceValue
            unit == GlucoseUnit.MG_DL -> reading.valueMgDl
            else -> reading.valueMgDl?.div(MG_DL_PER_MMOL_L)
        } ?: return "Unavailable"
        return when (unit) {
            GlucoseUnit.MG_DL -> numeric.roundToInt().toString()
            GlucoseUnit.MMOL_L -> String.format(locale, "%.1f", numeric)
        }
    }

    fun withUnit(reading: Reading, unit: GlucoseUnit, locale: Locale): String =
        "${value(reading, unit, locale)} ${unit.label}"

    val GlucoseUnit.label: String
        get() = when (this) {
            GlucoseUnit.MG_DL -> "mg/dL"
            GlucoseUnit.MMOL_L -> "mmol/L"
        }
}
