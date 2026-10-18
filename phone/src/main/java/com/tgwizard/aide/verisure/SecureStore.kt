package com.tgwizard.aide.verisure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists the Verisure account (credentials, door code, session cookies) encrypted with an
 * AES-GCM key that never leaves the Android Keystore.
 */
class SecureStore(context: Context) {

    data class Account(
        val email: String,
        val password: String,
        val cookies: Map<String, String> = emptyMap(),
        val giid: String? = null,
        val installationName: String? = null,
        val deviceLabel: String? = null,
        val lockName: String? = null,
        val doorCode: String? = null,
    )

    private val prefs = context.getSharedPreferences("verisure", Context.MODE_PRIVATE)

    fun load(): Account? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return runCatching { Codec.decode(decrypt(blob)) }.getOrNull()
    }

    fun save(account: Account) = prefs.edit { putString(KEY_BLOB, encrypt(Codec.encode(account))) }

    fun clear() = prefs.edit { remove(KEY_BLOB) }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_LENGTH))
        }
        return String(cipher.doFinal(sealed, IV_LENGTH, sealed.size - IV_LENGTH))
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private object Codec {
        fun encode(a: Account): String = org.json.JSONObject()
            .put("email", a.email)
            .put("password", a.password)
            .put("cookies", org.json.JSONObject(a.cookies))
            .put("giid", a.giid)
            .put("installationName", a.installationName)
            .put("deviceLabel", a.deviceLabel)
            .put("lockName", a.lockName)
            .put("doorCode", a.doorCode)
            .toString()

        fun decode(json: String): Account = org.json.JSONObject(json).run {
            val cookieJson = optJSONObject("cookies")
            Account(
                email = getString("email"),
                password = getString("password"),
                cookies = cookieJson?.keys()?.asSequence()?.associateWith { cookieJson.getString(it) }.orEmpty(),
                giid = optString("giid").ifEmpty { null },
                installationName = optString("installationName").ifEmpty { null },
                deviceLabel = optString("deviceLabel").ifEmpty { null },
                lockName = optString("lockName").ifEmpty { null },
                doorCode = optString("doorCode").ifEmpty { null },
            )
        }
    }

    private companion object {
        const val KEY_BLOB = "account"
        const val KEY_ALIAS = "aide_verisure"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
