package com.sentinoid.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {
    companion object {
        private const val PREFS_FILE = "sentinoid_secure_prefs"
        
        const val PREFS_VAULT_INITIALIZED = "vault_initialized"
        const val PREFS_SHARD_1_ENCRYPTED = "shard_1_encrypted"
        const val PREFS_SHARD_2_ENCRYPTED = "shard_2_encrypted"
        const val PREFS_SHARD_3_ENCRYPTED = "shard_3_encrypted"
        const val PREFS_KEY_ROTATION_TIME = "key_rotation_time"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /**
     * Security-critical operation flag for synchronous commits.
     * When true, uses commit() instead of apply() for immediate persistence.
     */
    private val criticalOperations = setOf(
        "vault_initialized",
        "recovery_setup_complete",
        "silent_alarm_lockdown_active",
        PREFS_VAULT_INITIALIZED,
        PREFS_SHARD_1_ENCRYPTED,
        PREFS_SHARD_2_ENCRYPTED,
        PREFS_SHARD_3_ENCRYPTED,
        PREFS_KEY_ROTATION_TIME
    )

    private fun isCriticalOperation(key: String): Boolean {
        return criticalOperations.any { key.contains(it, ignoreCase = true) }
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            throw SecurityException("Failed to initialize secure preferences", e)
        }
    }

    fun putString(
        key: String,
        value: String?,
        isCritical: Boolean = false
    ) {
        require(key.isNotBlank()) { "Key cannot be blank" }
        val editor = encryptedPrefs.edit().putString(key, value)
        if (isCritical || isCriticalOperation(key)) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    fun getString(
        key: String,
        defaultValue: String? = null,
    ): String? {
        if (key.isBlank()) return defaultValue
        return try {
            encryptedPrefs.getString(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun putBoolean(
        key: String,
        value: Boolean,
        isCritical: Boolean = false
    ) {
        require(key.isNotBlank()) { "Key cannot be blank" }
        val editor = encryptedPrefs.edit().putBoolean(key, value)
        if (isCritical || isCriticalOperation(key)) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    fun getBoolean(
        key: String,
        defaultValue: Boolean = false,
    ): Boolean {
        if (key.isBlank()) return defaultValue
        return try {
            encryptedPrefs.getBoolean(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun putLong(
        key: String,
        value: Long,
        isCritical: Boolean = false
    ) {
        require(key.isNotBlank()) { "Key cannot be blank" }
        val editor = encryptedPrefs.edit().putLong(key, value)
        if (isCritical || isCriticalOperation(key)) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    fun getLong(
        key: String,
        defaultValue: Long = 0L,
    ): Long {
        if (key.isBlank()) return defaultValue
        return try {
            encryptedPrefs.getLong(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun putInt(
        key: String,
        value: Int,
    ) {
        require(key.isNotBlank()) { "Key cannot be blank" }
        encryptedPrefs.edit().putInt(key, value).apply()
    }

    fun getInt(
        key: String,
        defaultValue: Int = 0,
    ): Int {
        if (key.isBlank()) return defaultValue
        return try {
            encryptedPrefs.getInt(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun putFloat(
        key: String,
        value: Float,
    ) {
        require(key.isNotBlank()) { "Key cannot be blank" }
        encryptedPrefs.edit().putFloat(key, value).apply()
    }

    fun getFloat(
        key: String,
        defaultValue: Float = 0.0f,
    ): Float {
        if (key.isBlank()) return defaultValue
        return try {
            encryptedPrefs.getFloat(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun putStringSet(
        key: String,
        value: Set<String>?,
    ) {
        require(key.isNotBlank()) { "Key cannot be blank" }
        encryptedPrefs.edit().putStringSet(key, value).apply()
    }

    fun getStringSet(
        key: String,
        defaultValue: Set<String>? = null,
    ): Set<String>? {
        if (key.isBlank()) return defaultValue
        return try {
            encryptedPrefs.getStringSet(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Batch put operations for atomic updates
     */
    fun putBatch(operations: Map<String, Any?>) {
        val editor = encryptedPrefs.edit()
        operations.forEach { (key, value) ->
            if (key.isBlank()) return@forEach
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> ->
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>?)
                null -> editor.remove(key)
            }
        }
        editor.apply()
    }

    /**
     * Batch get operation
     */
    fun getBatch(keys: List<String>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        keys.forEach { key ->
            if (key.isNotBlank() && encryptedPrefs.contains(key)) {
                result[key] = encryptedPrefs.all[key]
            }
        }
        return result
    }

    fun remove(key: String, isCritical: Boolean = false) {
        if (key.isBlank()) return
        val editor = encryptedPrefs.edit().remove(key)
        if (isCritical) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    fun clear(isCritical: Boolean = false) {
        val editor = encryptedPrefs.edit().clear()
        if (isCritical) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    fun contains(key: String): Boolean {
        if (key.isBlank()) return false
        return try {
            encryptedPrefs.contains(key)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get all keys in the secure preferences
     */
    fun getAllKeys(): Set<String> {
        return try {
            encryptedPrefs.all.keys
        } catch (e: Exception) {
            emptySet()
        }
    }
}
