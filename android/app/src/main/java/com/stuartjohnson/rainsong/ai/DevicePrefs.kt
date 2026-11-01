package com.stuartjohnson.rainsong.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * On-device settings (volumes, optional AI URL). The Meta API key is not stored
 * here — it lives on the AI server. Writes [commit] so values survive process death.
 */
class DevicePrefs(context: Context) {
    private val app = context.applicationContext
    private val fallback: SharedPreferences =
        app.getSharedPreferences(FILE_FALLBACK, Context.MODE_PRIVATE)
    private val encrypted: SharedPreferences? = encryptedPrefs(app, FILE)

    init {
        migrateFromLegacy()
        migrateKeyToEncrypted()
    }

    fun apiKey(): String {
        val fromEnc = encrypted?.getString(KEY_API, "").orEmpty()
        if (fromEnc.isNotBlank()) return fromEnc
        return fallback.getString(KEY_API, "").orEmpty()
    }

    fun hasApiKey(): Boolean = apiKey().isNotBlank()

    fun setApiKey(value: String) {
        val trimmed = value.trim()
        write(KEY_API, trimmed)
    }

    fun dropVolume(): Float = readFloat(KEY_DROP_VOL, 0.90f)
    fun musicVolume(): Float = readFloat(KEY_MUSIC_VOL, 0.70f)
    fun aiUrl(): String {
        val fromEnc = encrypted?.getString(KEY_AI_URL, "").orEmpty()
        if (fromEnc.isNotBlank()) return fromEnc
        return fallback.getString(KEY_AI_URL, "").orEmpty()
    }

    fun setDropVolume(value: Float) = writeFloat(KEY_DROP_VOL, value.coerceIn(0f, 1f))
    fun setMusicVolume(value: Float) = writeFloat(KEY_MUSIC_VOL, value.coerceIn(0f, 1f))
    fun setAiUrl(value: String) = write(KEY_AI_URL, value.trim())

    private fun migrateFromLegacy() {
        if (apiKey().isNotBlank()) return
        val legacyFallback = app.getSharedPreferences(LEGACY_FALLBACK, Context.MODE_PRIVATE)
        val legacyEnc = encryptedPrefs(app, LEGACY_FILE)
        val old = sequenceOf(
            legacyEnc?.getString(KEY_API, "").orEmpty(),
            legacyFallback.getString(KEY_API, "").orEmpty(),
        ).firstOrNull { it.isNotBlank() } ?: return
        setApiKey(old)
    }

    private fun migrateKeyToEncrypted() {
        val enc = encrypted ?: return
        if (enc.getString(KEY_API, "").orEmpty().isNotBlank()) return
        val old = fallback.getString(KEY_API, "").orEmpty()
        if (old.isNotBlank()) {
            enc.edit().putString(KEY_API, old).commit()
        }
    }

    private fun write(key: String, value: String) {
        encrypted?.edit()?.putString(key, value)?.commit()
        fallback.edit().putString(key, value).commit()
    }

    private fun writeFloat(key: String, value: Float) {
        encrypted?.edit()?.putFloat(key, value)?.commit()
        fallback.edit().putFloat(key, value).commit()
    }

    private fun readFloat(key: String, default: Float): Float {
        if (encrypted?.contains(key) == true) {
            return encrypted.getFloat(key, default).coerceIn(0f, 1f)
        }
        if (fallback.contains(key)) {
            return fallback.getFloat(key, default).coerceIn(0f, 1f)
        }
        return default
    }

    companion object {
        private const val FILE = "rainsong_prefs"
        private const val FILE_FALLBACK = "rainsong_prefs_fallback"
        private const val LEGACY_FILE = "rainsong_keys"
        private const val LEGACY_FALLBACK = "rainsong_keys_fallback"
        private const val KEY_API = "meta_api_key"
        private const val KEY_DROP_VOL = "drop_volume"
        private const val KEY_MUSIC_VOL = "music_volume"
        private const val KEY_AI_URL = "ai_base_url"

        private fun encryptedPrefs(context: Context, file: String): SharedPreferences? {
            return try {
                val master = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    file,
                    master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
