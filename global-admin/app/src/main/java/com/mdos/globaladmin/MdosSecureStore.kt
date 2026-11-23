package com.mdos.globaladmin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MdosSecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("mdos_admin_vault", Context.MODE_PRIVATE)
    private val alias = "mdos_admin_keystore_aes_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized fun put(deviceId: String, secret: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(deviceId, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    @Synchronized fun get(deviceId: String): String? {
        val value = prefs.getString(deviceId, null) ?: return null
        return try {
            val packed = Base64.decode(value, Base64.NO_WRAP)
            if (packed.size < 29) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0,12)))
            String(cipher.doFinal(packed.copyOfRange(12,packed.size)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    @Synchronized fun remove(deviceId: String) { prefs.edit().remove(deviceId).apply() }
}
