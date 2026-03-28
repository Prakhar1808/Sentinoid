package com.sentinoid.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    companion object {
        private const val PREFS_FILE = "sentinoid_secure_prefs"
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        throw SecurityException("Failed to initialize secure preferences", e)
    }

    fun putString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        return encryptedPrefs.getString(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        encryptedPrefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return encryptedPrefs.getBoolean(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        encryptedPrefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return encryptedPrefs.getLong(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        encryptedPrefs.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return encryptedPrefs.getInt(key, defaultValue)
    }

    fun putFloat(key: String, value: Float) {
        encryptedPrefs.edit().putFloat(key, value).apply()
    }

    fun getFloat(key: String, defaultValue: Float = 0.0f): Float {
        return encryptedPrefs.getFloat(key, defaultValue)
    }

    fun remove(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    fun clear() {
        encryptedPrefs.edit().clear().apply()
    }

    fun contains(key: String): Boolean {
        return encryptedPrefs.contains(key)
    }
}
