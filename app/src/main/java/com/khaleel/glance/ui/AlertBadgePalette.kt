package com.khaleel.glance.ui

import androidx.compose.ui.graphics.Color
import com.khaleel.glance.alerts.AlertKind

internal data class AlertBadgePalette(val border: Color, val background: Color, val foreground: Color)

/** The recorded alert category owns its badge; current readings/settings never reclassify history. */
internal fun alertBadgePalette(kind: AlertKind): AlertBadgePalette = when (kind) {
    AlertKind.LOW, AlertKind.URGENT_LOW -> AlertBadgePalette(Color(0xFFFF5070), Color(0xFF3A0615), Color(0xFFFFF1F3))
    AlertKind.HIGH, AlertKind.URGENT_HIGH -> AlertBadgePalette(Color(0xFFFFD05C), Color(0xFF392C06), Color(0xFFFFF6DC))
}
