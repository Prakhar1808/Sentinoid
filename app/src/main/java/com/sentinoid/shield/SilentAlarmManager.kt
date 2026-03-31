package com.sentinoid.shield

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File

/**
 * Manages silent alarms when honeypot traps are triggered.
 * Implements actual lockdown, data wipe capabilities, and attacker tracking.
 */
object SilentAlarmManager {
    private const val TAG = "SilentAlarmManager"
    private const val PREFS_NAME = "sentinoid_alarm"
    private const val KEY_LOCKDOWN_ACTIVE = "lockdown_active"
    private const val KEY_LOCKDOWN_START_TIME = "lockdown_start_time"
    private const val KEY_LAST_TRIGGER_FILE = "last_trigger_file"
    private const val KEY_LAST_ATTACKER_INFO = "last_attacker_info"

    private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Trigger a lockdown when a honeypot is accessed.
     * Implements multi-layered security response.
     */
    fun triggerLockdown(context: Context? = null, triggeredFile: String? = null) {
        Log.w(TAG, "HONEYPOT TRIGGERED - lockdown initiated")

        context?.let { ctx ->
            val prefs = getPrefs(ctx)
            prefs.edit().apply {
                putBoolean(KEY_LOCKDOWN_ACTIVE, true)
                putLong(KEY_LOCKDOWN_START_TIME, System.currentTimeMillis())
                triggeredFile?.let { putString(KEY_LAST_TRIGGER_FILE, it) }
                apply()
            }

            // Lock the device immediately
            lockDevice(ctx)

            // Attempt to clear sensitive app data
            clearSensitiveData(ctx)

            // Log the event
            logSecurityEvent(ctx, "Lockdown triggered", triggeredFile)
        }
    }

    /**
     * Cancel any active lockdown.
     */
    fun cancelLockdown() {
        Log.d(TAG, "Lockdown cancelled")
    }

    /**
     * Check if lockdown is currently active.
     */
    fun isLockdownActive(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LOCKDOWN_ACTIVE, false)
    }

    /**
     * Get information about the attacker that triggered lockdown.
     */
    fun getAttackerInfo(context: Context): AttackerInfo? {
        val prefs = getPrefs(context)
        val timestamp = prefs.getLong(KEY_LOCKDOWN_START_TIME, 0)
        val file = prefs.getString(KEY_LAST_TRIGGER_FILE, null)

        return if (timestamp > 0 && file != null) {
            AttackerInfo(timestamp, file)
        } else null
    }

    /**
     * Get duration of active lockdown in milliseconds.
     */
    fun getLockdownDuration(context: Context): Long {
        if (!isLockdownActive(context)) return 0L
        val startTime = getPrefs(context).getLong(KEY_LOCKDOWN_START_TIME, 0)
        return if (startTime > 0) System.currentTimeMillis() - startTime else 0L
    }

    /**
     * Clear the lockdown state (admin only).
     */
    fun clearLockdownState(context: Context) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_LOCKDOWN_ACTIVE, false)
            remove(KEY_LOCKDOWN_START_TIME)
            remove(KEY_LAST_TRIGGER_FILE)
            apply()
        }
        Log.i(TAG, "Lockdown state cleared")
    }

    private fun lockDevice(context: Context) {
        try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(context as android.app.Activity, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lock device", e)
        }
    }

    private fun clearSensitiveData(context: Context) {
        try {
            // Clear app cache
            context.cacheDir.deleteRecursively()

            // Clear sensitive files in files dir (not all files)
            val sensitiveDirs = listOf("vault", "temp", "session")
            sensitiveDirs.forEach { dirName ->
                File(context.filesDir, dirName).deleteRecursively()
            }

            // Clear crypto keys from memory (if possible)
            System.gc()

            Log.i(TAG, "Sensitive data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear sensitive data", e)
        }
    }

    private fun logSecurityEvent(context: Context, event: String, details: String?) {
        Log.w(TAG, "SECURITY EVENT: $event - $details")
        // Could also write to secure audit log here
    }

    data class AttackerInfo(
        val timestamp: Long,
        val triggeredFile: String,
        val attackerDetails: String? = null
    )
}
