package org.dumbtv.launcher.jellyfin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-bound session file, excluded from Android backup by its location. */
internal class SessionStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "jellyfin-session")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("jellyfin-session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("jellyfin-session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): String? = runCatching {
        if (!file.isFile || file.length() !in 29..16384) return null
        val data = file.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
    }.getOrNull()
    fun write(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val temporary = File(file.parentFile, "jellyfin-session.tmp")
        temporary.writeBytes(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
        check(temporary.renameTo(file)) { "Could not save Jellyfin sign-in" }
    }
    fun clear() { file.delete() }
}
