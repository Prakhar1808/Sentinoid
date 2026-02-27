package com.sentinoid.shield

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import java.io.File

class WatchdogService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WatchdogService", "Hardware Watchdog is active.")
        
        if (isDeviceRooted()) {
            Log.e("WatchdogService", "CRITICAL: Device is rooted! Triggering lockdown.")
            // TODO: Trigger a total lockdown of the app
        }

        if (isUsbDebuggingEnabled()) {
            Log.w("WatchdogService", "WARNING: USB Debugging is enabled.")
            // TODO: Notify the user or take other action
        }

        return START_STICKY
    }

    private fun isDeviceRooted(): Boolean {
        // A simple root check. More sophisticated checks are needed for a production app.
        val paths = arrayOf("/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su")
        return paths.any { File(it).exists() }
    }

    private fun isUsbDebuggingEnabled(): Boolean {
        return Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}