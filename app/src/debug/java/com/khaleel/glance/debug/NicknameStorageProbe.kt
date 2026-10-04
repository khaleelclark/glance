package com.khaleel.glance.debug

import android.content.Context
import android.content.ContextWrapper
import com.khaleel.glance.auth.SecureFileStore
import com.khaleel.glance.data.LocalNicknameStore
import com.khaleel.glance.data.UserSettings
import java.io.File
import org.json.JSONObject

/** Real Android Keystore round-trip in an isolated synthetic no-backup directory. */
internal fun runNicknameStorageProbe(context: Context): JSONObject {
    val isolated = object : ContextWrapper(context) {
        override fun getNoBackupFilesDir(): File = File(super.getNoBackupFilesDir(), "synthetic-nickname-check").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("synthetic-nickname-$name", mode)
    }
    val receipt = JSONObject()
    val store = LocalNicknameStore(SecureFileStore(isolated))
    try {
        store.clear()
        store.save("synthetic-account", mapOf("synthetic-person" to "Sweetheart"))
        check(LocalNicknameStore(SecureFileStore(isolated)).load("synthetic-account") == mapOf("synthetic-person" to "Sweetheart"))
        receipt.put("encryptedNicknameSurvivesStoreRecreation", true)
        val bytes = File(isolated.noBackupFilesDir, "nicknames.bin").readBytes()
        check(!String(bytes, Charsets.ISO_8859_1).contains("Sweetheart"))
        check(!String(bytes, Charsets.ISO_8859_1).contains("synthetic-person"))
        receipt.put("nicknameAndConnectionIdAreAbsentFromCiphertext", true)
        store.save("synthetic-account", mapOf("synthetic-person" to "  Sweetheart\n\t ❤️  "))
        check(store.load("synthetic-account")["synthetic-person"] == "Sweetheart ❤️")
        receipt.put("unicodeAndWhitespaceNormalizeOnDevice", true)
        check(store.load("different-synthetic-account").isEmpty())
        check(!File(isolated.noBackupFilesDir, "nicknames.bin").exists())
        receipt.put("otherAccountClearsStoredAliases", true)
        store.save("synthetic-account", mapOf("synthetic-person" to "Sweetheart"))
        store.save("synthetic-account", emptyMap())
        check(store.load("synthetic-account").isEmpty())
        receipt.put("blankAliasDeletesEncryptedFile", true)
        isolated.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear()
            .putString("accent_hex", "#1677D2").commit()
        val settings = UserSettings(isolated)
        check(settings.accentHex == "#287F7A")
        settings.accentHex = "#1677D2"
        check(settings.accentHex == "#1677D2")
        check(UserSettings(isolated).accentHex == "#1677D2")
        settings.accentHex = "#7046A3"
        check(settings.accentHex == "#7046A3")
        receipt.put("legacyBlueMigratesOnceAndExplicitColorsSurvive", true)
        settings.clear()
        check(settings.accentHex == "#287F7A")
        settings.accentHex = "#1677D2"
        check(UserSettings(isolated).accentHex == "#1677D2")
        receipt.put("explicitOceanSurvivesAccountResetAndRecreation", true)
        return receipt
    } finally {
        store.clear()
        isolated.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
