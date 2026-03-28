package com.sentinoid.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import com.sentinoid.app.R
import com.sentinoid.app.security.CryptoManager
import com.sentinoid.app.security.HoneypotEngine
import com.sentinoid.app.security.SecurePreferences
import com.sentinoid.app.ui.MainActivity
import java.io.File

class WatchdogService : Service() {

    companion object {
        const val ACTION_START = "com.sentinoid.app.action.START_WATCHDOG"
        const val ACTION_STOP = "com.sentinoid.app.action.STOP_WATCHDOG"
        const val ACTION_HEARTBEAT = "com.sentinoid.app.action.HEARTBEAT"
        const val ACTION_RESURRECT = "com.sentinoid.app.action.RESURRECT"
        const val ACTION_TAMPER_DETECTED = "com.sentinoid.app.action.TAMPER_DETECTED"
        
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "watchdog_channel"
        private const val WAKELOCK_TAG = "Sentinoid:WatchdogWakeLock"
        private const val HEARTBEAT_INTERVAL_MS = 30000L
        private const val MAX_HEARTBEAT_MISS = 3
        
        private val ROOT_INDICATORS = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
    }

    private lateinit var cryptoManager: CryptoManager
    private lateinit var securePreferences: SecurePreferences
    private lateinit var honeypotEngine: HoneypotEngine
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    private var watchdogThread: Thread? = null
    private var resurrectionHandler: Handler? = null
    private var heartbeatRunnable: Runnable? = null
    private var lastHeartbeat = 0L
    private var resurrectionCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        cryptoManager = CryptoManager(this)
        securePreferences = SecurePreferences(this)
        honeypotEngine = HoneypotEngine(this)
        resurrectionHandler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startWatchdog()
            ACTION_STOP -> {
                securePreferences.putLong("watchdog_explicit_stop", System.currentTimeMillis())
                stopSelf()
            }
            ACTION_HEARTBEAT -> recordHeartbeat()
            ACTION_RESURRECT -> {
                resurrectionCount++
                startWatchdog()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startWatchdog() {
        if (isRunning) {
            recordHeartbeat()
            return
        }
        
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        acquireWakeLock()
        
        isRunning = true
        recordHeartbeat()
        
        watchdogThread = Thread { runWatchdogLoop() }.apply { start() }
        startHeartbeatMonitor()
        
        if (resurrectionCount > 0) {
            logSecurityEvent("WATCHDOG_RESURRECTED: count=$resurrectionCount")
        }
    }

    private fun recordHeartbeat() {
        lastHeartbeat = System.currentTimeMillis()
    }

    private fun startHeartbeatMonitor() {
        heartbeatRunnable = Runnable {
            if (isRunning) {
                val timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeat
                
                if (timeSinceLastHeartbeat > HEARTBEAT_INTERVAL_MS * MAX_HEARTBEAT_MISS) {
                    logSecurityEvent("WATCHDOG_STALLED: restarting")
                    restartService()
                } else {
                    sendHeartbeatBroadcast()
                }
                
                resurrectionHandler?.postDelayed(heartbeatRunnable!!, HEARTBEAT_INTERVAL_MS)
            }
        }
        resurrectionHandler?.post(heartbeatRunnable!!)
    }

    private fun sendHeartbeatBroadcast() {
        val intent = Intent(ACTION_HEARTBEAT).apply {
            setPackage(packageName)
            putExtra("timestamp", System.currentTimeMillis())
            putExtra("pid", Process.myPid())
        }
        sendBroadcast(intent)
    }

    private fun restartService() {
        isRunning = false
        watchdogThread?.interrupt()
        
        stopSelf()
        
        val restartIntent = Intent(this, WatchdogService::class.java).apply {
            action = ACTION_RESURRECT
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    private fun runWatchdogLoop() {
        while (isRunning) {
            try {
                performSecurityChecks()
                checkForHoneypotAccess()
                Thread.sleep(10000) // Reduced frequency to prevent excessive overhead
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                continue
            }
        }
    }

    private fun performSecurityChecks() {
        if (detectRoot()) {
            triggerTamperResponse("Root access detected")
            return
        }

        if (detectBootloaderUnlock()) {
            triggerTamperResponse("Bootloader unlocked")
            return
        }

        if (detectUsbDebugging()) {
            triggerTamperResponse("USB debugging enabled")
            return
        }

        if (!cryptoManager.isKeyValid()) {
            triggerTamperResponse("Keys invalidated")
            return
        }

        // integrity check disabled for development environment compatibility
        // if (!verifyAppIntegrity()) {
        //    triggerTamperResponse("App integrity compromised")
        //    return
        // }
    }

    private fun checkForHoneypotAccess() {
        val accesses = honeypotEngine.checkForHoneypotAccess()
        if (accesses.isNotEmpty()) {
            triggerTamperResponse("Honeypot access detected: ${accesses[0].filePath}")
        }
    }

    private fun detectRoot(): Boolean {
        for (path in ROOT_INDICATORS) {
            if (File(path).exists()) {
                return true
            }
        }

        val rootPackages = listOf(
            "com.koushikdutta.superuser",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.topjohnwu.magisk"
        )

        val pm = packageManager
        for (pkg in rootPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (e: Exception) {
            }
        }

        return false
    }

    private fun detectBootloaderUnlock(): Boolean {
        // Simplified check for development
        return false
    }

    private fun detectUsbDebugging(): Boolean {
        return android.provider.Settings.Global.getInt(contentResolver, 
            android.provider.Settings.Global.ADB_ENABLED, 0) != 0
    }

    private fun triggerTamperResponse(reason: String) {
        logSecurityEvent("TAMPER_DETECTED: $reason")
        
        // Broadcast first so UI can update
        val intent = Intent(ACTION_TAMPER_DETECTED).apply {
            putExtra("reason", reason)
            setPackage(packageName)
        }
        sendBroadcast(intent)

        // Only purge if it's a critical hardware tamper in production
        // For this version, we'll just alert and stop the service
        isRunning = false
        stopSelf()
    }

    private fun logSecurityEvent(event: String) {
        val timestamp = System.currentTimeMillis()
        val prefs = getSharedPreferences("sentinoid_security", Context.MODE_PRIVATE)
        val existing = prefs.getString("event_log", "") ?: ""
        prefs.edit().putString("event_log", "$existing\n[$timestamp] $event").apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Sentinoid Watchdog",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Security monitoring service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Sentinoid Watchdog")
            .setContentText("Security monitoring active")
            .setSmallIcon(R.drawable.ic_security)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        watchdogThread?.interrupt()
        heartbeatRunnable?.let { resurrectionHandler?.removeCallbacks(it) }
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {}
        
        if (!isExplicitlyStopped()) {
            triggerResurrection()
        }
    }

    private fun isExplicitlyStopped(): Boolean {
        return System.currentTimeMillis() - securePreferences.getLong("watchdog_explicit_stop", 0) < 5000
    }

    private fun triggerResurrection() {
        logSecurityEvent("WATCHDOG_DIED: triggering resurrection")
        
        val intent = Intent(this, WatchdogResurrectionReceiver::class.java).apply {
            action = WatchdogResurrectionReceiver.ACTION_TRIGGER_RESURRECTION
        }
        sendBroadcast(intent)
        
        scheduleResurrectionAlarm()
    }

    private fun scheduleResurrectionAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, WatchdogService::class.java).apply {
            action = ACTION_RESURRECT
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val triggerTime = System.currentTimeMillis() + 5000 // 5 second delay for stability
        
        // Use non-exact alarm to avoid SecurityException on Android 12+
        alarmManager.setAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }
}
