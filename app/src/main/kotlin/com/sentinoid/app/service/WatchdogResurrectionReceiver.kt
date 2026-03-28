package com.sentinoid.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Watchdog Resurrection Receiver
 * Monitors for watchdog death and triggers service resurrection.
 * Part of the self-healing architecture.
 */
class WatchdogResurrectionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_RESURRECTION = "com.sentinoid.app.TRIGGER_RESURRECTION"
        private const val MAX_RESURRECTION_ATTEMPTS = 5
        private const val RESURRECTION_COOLDOWN_MS = 30000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TRIGGER_RESURRECTION,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                attemptResurrection(context)
            }
        }
    }

    private fun attemptResurrection(context: Context) {
        val prefs = context.getSharedPreferences("watchdog_resurrection", Context.MODE_PRIVATE)
        val lastAttempt = prefs.getLong("last_resurrection_attempt", 0)
        val attempts = prefs.getInt("resurrection_attempts", 0)
        val now = System.currentTimeMillis()

        if (now - lastAttempt < RESURRECTION_COOLDOWN_MS) return
        if (attempts >= MAX_RESURRECTION_ATTEMPTS) return

        prefs.edit().apply {
            putLong("last_resurrection_attempt", now)
            putInt("resurrection_attempts", attempts + 1)
            apply()
        }

        val serviceIntent = Intent(context, WatchdogService::class.java).apply {
            action = WatchdogService.ACTION_RESURRECT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun resetCounter(context: Context) {
        context.getSharedPreferences("watchdog_resurrection", Context.MODE_PRIVATE)
            .edit()
            .putInt("resurrection_attempts", 0)
            .apply()
    }
}
