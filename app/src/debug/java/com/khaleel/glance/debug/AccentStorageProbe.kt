package com.khaleel.glance.debug

import android.content.Context
import android.content.ContextWrapper
import com.khaleel.glance.core.AccentPresets
import com.khaleel.glance.data.UserSettings
import org.json.JSONObject

/** Exercise actual settings persistence in an isolated synthetic preference namespace. */
internal fun runAccentStorageProbe(context: Context): JSONObject {
    val isolated = object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("synthetic-accent-$name", mode)
    }
    val prefs = isolated.getSharedPreferences("settings", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    val receipt = JSONObject()
    try {
        val settings = UserSettings(isolated)
        check(settings.accentHex == AccentPresets.DEFAULT)
        receipt.put("newSettingsUseLilacDefault", true)
        for (preset in AccentPresets.all) {
            settings.accentHex = preset.hex
            check(UserSettings(isolated).accentHex == preset.hex)
        }
        receipt.put("everyExplicitPresetSurvivesSettingsRecreation", true)
        settings.clear()
        check(UserSettings(isolated).accentHex == AccentPresets.DEFAULT)
        settings.accentHex = AccentPresets.all.first { it.name == "Ocean" }.hex
        check(UserSettings(isolated).accentHex == settings.accentHex)
        receipt.put("resetUsesDefaultAndDoesNotReinterpretLaterExplicitPreset", true)
        return receipt
    } finally {
        prefs.edit().clear().commit()
    }
}
