package com.khaleel.glance.core

/** Curated accents for the fixed black presentation and matching native droplet. */
data class AccentPreset(val name: String, val hex: String)

object AccentPresets {
    const val DEFAULT = "#B9AEF5"
    val all = listOf(
        AccentPreset("Lilac", DEFAULT),
        AccentPreset("Teal", "#79D3C5"),
        AccentPreset("Ocean", "#8AB9F7"),
        AccentPreset("Berry", "#D4ACF5"),
        AccentPreset("Forest", "#A5D6A7"),
        AccentPreset("Coral", "#FFA8B7"),
        AccentPreset("Sunset", "#FFC28A"),
    )

    /** Old custom colors remain stored but do not silently return with the preset UI. */
    fun selected(hex: String): AccentPreset =
        all.firstOrNull { it.hex.equals(hex.trim(), ignoreCase = true) } ?: all.first()
}
