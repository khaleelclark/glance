package com.khaleel.glance.ui

import com.khaleel.glance.core.GlucoseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertWheelValuesTest {
    @Test fun mgDlWheelHasEveryAllowedIntegerAndKeepsCurrentSelection() {
        val values = thresholdWheelValues(200, 300, GlucoseUnit.MG_DL)
        assertEquals(101, values.size)
        assertEquals(WheelValue(200, "200"), values.first())
        assertEquals(WheelValue(300, "300"), values.last())
        assertEquals("240", values[nearestWheelIndex(values, 240)].label)
    }

    @Test fun mmolWheelShowsUniqueTenthsInsideOrderedMgDlBounds() {
        val values = thresholdWheelValues(55, 70, GlucoseUnit.MMOL_L)
        assertTrue(values.isNotEmpty())
        assertEquals(values.map { it.label }.distinct(), values.map { it.label })
        assertTrue(values.all { it.storedValue in 55..70 })
        assertEquals("3.1", values[nearestWheelIndex(values, 55)].label)
        assertEquals("3.9", values[nearestWheelIndex(values, 70)].label)
    }

    @Test fun singleBoundaryStillHasASelectableValue() {
        val values = thresholdWheelValues(240, 240, GlucoseUnit.MMOL_L)
        assertEquals(1, values.size)
        assertEquals(240, values.single().storedValue)
    }
}
