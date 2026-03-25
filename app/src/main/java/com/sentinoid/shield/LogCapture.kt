package com.sentinoid.shield

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue

class LogCapture : Service() {
    companion object {
        private const val TAG = "LogCapture"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "sentinoid_log_channel"
        
        const val ACTION_START = "com.sentinoid.shield.START_LOGS"
        const val ACTION_STOP = "com.sentinoid.shield.STOP_LOGS"
        
        private val SECURITY_TAGS = listOf(
            "ActivityManager",
            "Kernel",
            "SELinux",
            "usb",
            "Accessory",
            "Security",
            "Audit"
        )
        
        private val SUSPICIOUS_PATTERNS = listOf(
            "root",
            "su:",
            "/system/app/Superuser",
            "avc: denied",
            "IOCTL",
            "permission denied",
            "uid=0",
            "setuid",
            "overlay",
            "accessibility",
            "binder",
            "dev/block"
        )
    }

    private var process: Process? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logBuffer = ConcurrentLinkedQueue<String>()
    private val handler = Handler(Looper.getMainLooper())
    
    private var aoaServiceReference: AoaService? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLogCapture()
            ACTION_STOP -> stopLogCapture()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    fun setAoaService(service: AoaService) {
        aoaServiceReference = service
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sentinoid Log Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Captures security-related Android logs"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startLogCapture() {
        if (isRunning) return
        
        val notification = createNotification("Capturing security logs...")
        startForeground(NOTIFICATION_ID, notification)
        
        isRunning = true
        
        serviceScope.launch {
            captureLogs()
        }
        
        serviceScope.launch {
            flushLogs()
        }
        
        Log.i(TAG, "Log capture started")
    }

    private fun stopLogCapture() {
        isRunning = false
        serviceScope.cancel()
        
        try {
            process?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping log capture", e)
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Log capture stopped")
    }

    private fun createNotification(message: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Sentinoid Log Capture")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private suspend fun captureLogs() = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder(
                "logcat",
                "-v", "threadtime",
                "-s", *SECURITY_TAGS.toTypedArray()
            )
            process = processBuilder.start()
            
            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            
            var line: String?
            while (isRunning) {
                line = reader.readLine()
                if (line != null) {
                    if (isSuspicious(line)) {
                        logBuffer.offer(line)
                        Log.w(TAG, "Suspicious: $line")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing logs", e)
        }
    }

    private suspend fun flushLogs() = withContext(Dispatchers.IO) {
        while (isRunning) {
            val batch = mutableListOf<String>()
            repeat(10) {
                logBuffer.poll()?.let { batch.add(it) }
            }
            
            if (batch.isNotEmpty()) {
                val combined = batch.joinToString("\n")
                
                try {
                    aoaServiceReference?.sendLogs(combined)
                    Log.d(TAG, "Sent ${batch.size} logs to PC")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending logs", e)
                }
            }
            
            delay(500)
        }
    }

    private fun isSuspicious(line: String): Boolean {
        val lowerLine = line.lowercase()
        
        for (pattern in SUSPICIOUS_PATTERNS) {
            if (lowerLine.contains(pattern.lowercase())) {
                return true
            }
        }
        
        return false
    }

    override fun onDestroy() {
        stopLogCapture()
        super.onDestroy()
    }
}
