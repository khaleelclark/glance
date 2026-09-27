package com.khaleel.glance.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated, atomic files in no-backup storage. The key never leaves Android Keystore. */
class SecureFileStore(context: Context) {
    private val directory = context.noBackupFilesDir
    private val keyAlias = "glance_local_v1"

    @Synchronized
    fun write(name: String, payload: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encoded = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        val bytes = ByteBuffer.allocate(2 + cipher.iv.size + encoded.size)
            .put(1).put(cipher.iv.size.toByte()).put(cipher.iv).put(encoded).array()
        val file = atomic(name)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    @Synchronized
    fun read(name: String): JSONObject? {
        val file = atomic(name)
        if (!file.baseFile.exists()) return null
        val bytes = file.readFully()
        val input = ByteBuffer.wrap(bytes)
        require(input.remaining() >= 2 && input.get().toInt() == 1) { "Unsupported local data" }
        val ivLength = input.get().toInt() and 0xff
        require(ivLength == 12 && input.remaining() >= ivLength + 16) { "Invalid local data" }
        val iv = ByteArray(ivLength).also(input::get)
        val encrypted = ByteArray(input.remaining()).also(input::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
    }

    @Synchronized
    fun delete(name: String) = atomic(name).delete()

    private fun atomic(name: String): AtomicFile {
        require(name.matches(Regex("[a-z_]+")))
        return AtomicFile(File(directory, "$name.bin"))
    }

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }
}
