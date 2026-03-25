package com.sentinoid.shield

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SecurityScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SecurityScanWorker"
        private const val WORK_NAME = "security_periodic_scan"
        private const val NOTIFICATION_CHANNEL_ID = "security_alerts"
        private const val NOTIFICATION_ID_HIGH_RISK = 2001

        fun schedulePeriodicScans(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<SecurityScanWorker>(
                6, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Scheduled periodic security scans (every 6 hours)")
        }

        fun cancelScheduledScans(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    private val watchdogManager: WatchdogManager by lazy {
        WatchdogManager(applicationContext)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting periodic security scan...")
            createNotificationChannel()
            val highRiskApps = watchdogManager.scanAllApps(0.7f)

            if (highRiskApps.isNotEmpty()) {
                Log.w(TAG, "Periodic scan found ${highRiskApps.size} high-risk apps")
                sendHighRiskNotification(highRiskApps)
            } else {
                Log.d(TAG, "Periodic scan complete: No threats detected")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during security scan", e)
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Security alerts for detected threats"
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendHighRiskNotification(highRiskApps: List<Pair<String, Float>>) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "scanner")
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val contentText = "${highRiskApps.size} threats detected"

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Security Alert")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SECURITY)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_HIGH_RISK, notification)
    }
}
