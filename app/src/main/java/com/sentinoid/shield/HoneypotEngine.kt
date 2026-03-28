package com.sentinoid.shield

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.FileObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Battery-optimized Honeypot using FileObserver with adaptive monitoring.
 * Uses background thread with low-priority scheduling to minimize CPU wakeups.
 */
class HoneypotEngine : JobService() {
    private var observer: AdaptiveFileObserver? = null
    private val handlerThread = HandlerThread("HoneypotThread", android.os.Process.THREAD_PRIORITY_BACKGROUND)
    private var handler: Handler? = null
    private val isMonitoring = AtomicBoolean(false)
    private var lastTriggerTime = 0L
    private val TRIGGER_COOLDOWN_MS = 30000 // 30 seconds between triggers

    companion object {
        private const val TAG = "HoneypotEngine"
        private const val JOB_ID_HONEYPOT = 1002

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        observer?.stopWatching()

        val honeypotDir = createHoneypotDirectory()
        if (honeypotDir != null) {
            observer = object : FileObserver(honeypotDir.path, OPEN) {
                override fun onEvent(event: Int, path: String?) {
                    if (event == OPEN && path == "vault_keys.txt") {
                        Log.d("HoneypotEngine", "HONEYPOT TRIPPED! File accessed: $path")
                        SilentAlarmManager.triggerLockdown(applicationContext)
                    }
                }
            }
            observer?.startWatching()
            isMonitoring.set(true)
            Log.d(TAG, "Adaptive honeypot monitoring started at ${honeypotDir.path}")
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
            File(
                trapDir,
                "vault_keys.txt"
            ).writeText("This is a honeypot. Accessing this file has triggered a silent alarm.")
            return trapDir
        }
        
        return trapDir
    }

    private fun onHoneypotTripped(path: String?) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) {
            Log.d(TAG, "Honeypot trigger throttled")
            return
        }
        lastTriggerTime = now
        
        Log.e(TAG, "HONEYPOT TRIPPED! File accessed: $path")
        
        // Only trigger lockdown if not in power save mode
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isPowerSaveMode) {
            SilentAlarmManager.triggerLockdown(applicationContext)
        } else {
            Log.w(TAG, "Honeypot tripped but in power save - deferred lockdown")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
    }

    /**
     * Adaptive file observer that reduces monitoring intensity during low power.
     */
    private inner class AdaptiveFileObserver(path: String, private val handler: Handler) : 
        FileObserver(path, OPEN or ACCESS or MODIFY) {
        
        private var eventCount = 0
        private var adaptiveDelay = 0L
        
        override fun onEvent(event: Int, path: String?) {
            eventCount++
            
            // Adaptive throttling - increase delay as events pile up
            if (eventCount > 10) {
                adaptiveDelay = 100
            }
            
            if (adaptiveDelay > 0) {
                handler.postDelayed({ processEvent(event, path) }, adaptiveDelay)
            } else {
                processEvent(event, path)
            }
        }
        
        private fun processEvent(event: Int, path: String?) {
            when (event) {
                OPEN, ACCESS -> {
                    if (path != null) {
                        onHoneypotTripped(path)
                    }
                }
            }
        }
    }
}
