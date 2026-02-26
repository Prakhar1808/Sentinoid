package com.sentinoid.shield

import android.app.Service
import android.content.Intent
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import java.io.File

class HoneypotEngine : Service() {

    private var observer: FileObserver? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val honeypotDir = createHoneypotDirectory()
        if (honeypotDir != null) {
            // The FileObserver is watching for any process trying to OPEN the bait file.
            observer = object : FileObserver(honeypotDir.path, OPEN) {
                override fun onEvent(event: Int, path: String?) {
                    if (event == OPEN && path == "vault_keys.txt") {
                        Log.d("HoneypotEngine", "HONEYPOT TRIPPED! File accessed: $path")
                        SilentAlarmManager.triggerLockdown()
                    }
                }
            }
            observer?.startWatching()
            Log.d("HoneypotEngine", "Honeypot is active at ${honeypotDir.path}")
        }
        return START_STICKY
    }

    private fun createHoneypotDirectory(): File? {
        val externalDir = getExternalFilesDir(null)
        if (externalDir != null) {
            val trapDir = File(externalDir, ".sentinoid_vault")
            if (!trapDir.exists()) {
                trapDir.mkdirs()
            }
            File(trapDir, "vault_keys.txt").writeText("This is a honeypot. Accessing this file has triggered a silent alarm.")
            return trapDir
        }
        return null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        observer?.stopWatching()
    }
}
