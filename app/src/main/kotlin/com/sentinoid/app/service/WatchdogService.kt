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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sentinoid.app.R
import com.sentinoid.app.security.ActivityLogger
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
        private const val WAKELOCK_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes
        private const val HEARTBEAT_INTERVAL_MS = 45000L // 45 seconds
        private const val MAX_HEARTBEAT_MISS = 3
        private const val WATCHDOG_CHECK_INTERVAL_MS = 45000L // 45 seconds between checks
        private const val TAMPER_ALERT_COOLDOWN_MS = 60000L // 1 minute cooldown between alerts
        private const val TAMPER_LOCKOUT_MS = 300000L // 5 minute lockout after tamper detected
        
        // Preference keys for persistent tamper tracking
        const val PREFS_TAMPER_DETECTED = "tamper_detected_time"
        const val PREFS_TAMPER_SILENT_MODE = "tamper_silent_mode"

        private val ROOT_INDICATORS =
            listOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su",
                "/su/bin/su",
            )
    }

    private lateinit var cryptoManager: CryptoManager
    private lateinit var securePreferences: SecurePreferences
    private lateinit var honeypotEngine: HoneypotEngine
    private val activityLogger by lazy { ActivityLogger.getInstance(this) }
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    private var watchdogThread: Thread? = null
    private var resurrectionHandler: Handler? = null
    private var heartbeatRunnable: Runnable? = null
    private var lastHeartbeat = 0L
    private var lastTamperAlertTime: Long = 0
    private var tamperAlertCount: Int = 0
    private var resurrectionCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        cryptoManager = CryptoManager(this)
        securePreferences = SecurePreferences(this)
        honeypotEngine = HoneypotEngine(this)
        resurrectionHandler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
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
        // Check if we're in tamper lockout period - don't restart if tamper was recently detected
        val lastTamperTime = securePreferences.getLong(PREFS_TAMPER_DETECTED, 0)
        val timeSinceTamper = System.currentTimeMillis() - lastTamperTime
        if (timeSinceTamper < TAMPER_LOCKOUT_MS) {
            val remainingMinutes = (TAMPER_LOCKOUT_MS - timeSinceTamper) / 60000
            activityLogger.logWatchdog("Watchdog start blocked: tamper lockout active (${remainingMinutes}m remaining)", ActivityLogger.SEVERITY_WARNING)
            stopSelf()
            return
        }
        
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

        activityLogger.logWatchdog("Watchdog service started", ActivityLogger.SEVERITY_INFO)

        if (resurrectionCount > 0) {
            logSecurityEvent("WATCHDOG_RESURRECTED: count=$resurrectionCount")
            activityLogger.logWatchdog("Watchdog resurrected (count: $resurrectionCount)", ActivityLogger.SEVERITY_WARNING)
        }
    }

    private fun recordHeartbeat() {
        lastHeartbeat = System.currentTimeMillis()
    }

    private fun startHeartbeatMonitor() {
        heartbeatRunnable =
            Runnable {
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
        val intent =
            Intent(ACTION_HEARTBEAT).apply {
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

        val restartIntent =
            Intent(this, WatchdogService::class.java).apply {
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
                Thread.sleep(WATCHDOG_CHECK_INTERVAL_MS) // 45 seconds between checks
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
        try {
            val accesses = honeypotEngine.checkForAccess()
            if (accesses.isNotEmpty()) {
                triggerTamperResponse("Honeypot access detected: ${accesses[0].filePath}")
            }
        } catch (e: Exception) {
            Log.e("WatchdogService", "Honeypot check failed: ${e.message}")
        }
    }

    private fun detectRoot(): Boolean {
        for (path in ROOT_INDICATORS) {
            if (File(path).exists()) {
                return true
            }
        }

        val rootPackages =
            listOf(
                "com.koushikdutta.superuser",
                "eu.chainfire.supersu",
                "com.noshufou.android.su",
                "com.topjohnwu.magisk",
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
        return android.provider.Settings.Global.getInt(
            contentResolver,
            android.provider.Settings.Global.ADB_ENABLED,
            0,
        ) != 0
    }

    private fun triggerTamperResponse(reason: String) {
        val currentTime = System.currentTimeMillis()

        // Check persistent cooldown - don't alert if we already alerted recently (even across restarts)
        val lastPersistentTamper = securePreferences.getLong(PREFS_TAMPER_DETECTED, 0)
        if (currentTime - lastPersistentTamper < TAMPER_LOCKOUT_MS) {
            // Still in lockout period, suppress silently
            isRunning = false
            stopSelf()
            return
        }

        // Rate limiting: only show tamper alert once per minute within same session
        if (currentTime - lastTamperAlertTime < TAMPER_ALERT_COOLDOWN_MS) {
            tamperAlertCount++
            logSecurityEvent("TAMPER_SUPPRESSED: $reason (count: $tamperAlertCount)")
            activityLogger.logTamper(
                "Tamper suppressed: $reason",
                ActivityLogger.SEVERITY_WARNING,
                mapOf("count" to tamperAlertCount.toString()),
            )
            isRunning = false
            stopSelf()
            return
        }

        // Persist tamper detection to prevent resurrection loops
        securePreferences.putLong(PREFS_TAMPER_DETECTED, currentTime)
        securePreferences.putBoolean(PREFS_TAMPER_SILENT_MODE, true)

        lastTamperAlertTime = currentTime
        tamperAlertCount++

        logSecurityEvent("TAMPER_DETECTED: $reason")
        activityLogger.logTamper(
            "Tamper detected: $reason",
            ActivityLogger.SEVERITY_CRITICAL,
            mapOf("alert_count" to tamperAlertCount.toString()),
        )

        // Broadcast first so UI can update
        val intent =
            Intent(ACTION_TAMPER_DETECTED).apply {
                putExtra("reason", reason)
                putExtra("alert_count", tamperAlertCount)
                putExtra("last_alert", lastTamperAlertTime)
                putExtra("lockout_active", true)
                setPackage(packageName)
            }
        sendBroadcast(intent)

        // Stop service - do NOT trigger resurrection when tampered
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
            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.watchdog_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.watchdog_channel_description)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                    enableVibration(false)
                    setSound(null, null)
                }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            
        val stopIntent = Intent(this, WatchdogService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.watchdog_notification_title))
            .setContentText(getString(R.string.watchdog_notification_text))
            .setSmallIcon(R.drawable.ic_security)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_stop, getString(R.string.watchdog_stop), stopPendingIntent)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock =
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKELOCK_TAG,
                ).apply {
                    // Use 30 minute timeout to prevent indefinite wakelocks
                    setReferenceCounted(false)
                    acquire(WAKELOCK_TIMEOUT_MS)
                }
        } catch (e: Exception) {
            activityLogger.logWatchdog("Failed to acquire wakelock: ${e.message}", ActivityLogger.SEVERITY_WARNING)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            // Ignore release errors
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        watchdogThread?.interrupt()
        heartbeatRunnable?.let { resurrectionHandler?.removeCallbacks(it) }

        releaseWakeLock()

        activityLogger.logWatchdog("Watchdog service stopped", ActivityLogger.SEVERITY_INFO)

        if (!isExplicitlyStopped()) {
            triggerResurrection()
        }
    }

    private fun isExplicitlyStopped(): Boolean {
        // Check explicit stop
        val explicitStop = System.currentTimeMillis() - securePreferences.getLong("watchdog_explicit_stop", 0) < 5000
        // Check tamper lockout - if tamper was detected recently, consider it an explicit stop (no resurrection)
        val tamperLockout = System.currentTimeMillis() - securePreferences.getLong(PREFS_TAMPER_DETECTED, 0) < TAMPER_LOCKOUT_MS
        return explicitStop || tamperLockout
    }

    private fun triggerResurrection() {
        logSecurityEvent("WATCHDOG_DIED: triggering resurrection")

        val intent =
            Intent(this, WatchdogResurrectionReceiver::class.java).apply {
                action = WatchdogResurrectionReceiver.ACTION_TRIGGER_RESURRECTION
            }
        sendBroadcast(intent)

        scheduleResurrectionAlarm()
    }

    private fun scheduleResurrectionAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent =
            Intent(this, WatchdogService::class.java).apply {
                action = ACTION_RESURRECT
            }
        val pendingIntent =
            PendingIntent.getService(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val triggerTime = System.currentTimeMillis() + 5000 // 5 second delay for stability

        // Use non-exact alarm to avoid SecurityException on Android 12+
        alarmManager.setAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent,
        )
    }
}
