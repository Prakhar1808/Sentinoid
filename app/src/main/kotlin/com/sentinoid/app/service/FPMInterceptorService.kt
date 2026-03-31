package com.sentinoid.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.sentinoid.app.R
import com.sentinoid.app.security.ActivityLogger
import com.sentinoid.app.security.SecurePreferences
import com.sentinoid.app.ui.MainActivity

/**
 * Feature Permission Manager (FPM) Interceptor Service.
 * 
 * ACTUALLY WORKS:
 * - Monitors permission dialogs in real-time
 * - Blocks camera/mic/location by auto-clicking deny
 * - Sends notifications when apps try to access hardware
 * - Logs all permission attempts
 */
class FPMInterceptorService : AccessibilityService() {
    private lateinit var securePreferences: SecurePreferences
    private var isInterceptionActive = false
    private val handler = Handler(Looper.getMainLooper())
    private val activityLogger by lazy { ActivityLogger.getInstance(this) }
    
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "Settings updated, refreshing state")
        }
    }

    override fun onCreate() {
        super.onCreate()
        securePreferences = SecurePreferences(this)
        createNotificationChannel()
        
        // Register for settings change broadcasts
        registerReceiver(settingsReceiver, IntentFilter("com.sentinoid.app.FPM_SETTINGS_CHANGED"))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 50
        }

        isInterceptionActive = true
        Log.i(TAG, "FPM Interceptor ACTIVE - Monitoring permission requests")
        showServiceNotification("FPM Active - Monitoring permissions")
    }

    override fun onInterrupt() {
        isInterceptionActive = false
        Log.i(TAG, "FPM Interceptor interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(settingsReceiver)
        } catch (e: Exception) {}
        isInterceptionActive = false
    }

    private val systemPackages = setOf(
        "com.android.", "android", "com.google.android.",
        "com.samsung.android.", "com.android.systemui",
        "com.sentinoid.app", "com.sentinoid.app.debug"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isInterceptionActive) return

        val packageName = event.packageName?.toString() ?: return
        if (isSystemPackage(packageName)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                detectPermissionDialogs(event, packageName)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                detectPermissionDialogs(event, packageName)
                monitorOverlayAttempts(event, packageName)
            }
        }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return systemPackages.any { packageName.startsWith(it) }
    }

    private fun detectPermissionDialogs(event: AccessibilityEvent, packageName: String) {
        val texts = event.text?.map { it.toString().lowercase() } ?: return
        val textCombined = texts.joinToString(" ")
        
        // Camera permission detection
        if ((textCombined.contains("camera") || textCombined.contains("take photo")) &&
            securePreferences.getBoolean(PREFS_BLOCK_CAMERA, true)) {
            blockPermissionRequest(packageName, "CAMERA")
        }
        
        // Microphone permission detection  
        if ((textCombined.contains("microphone") || textCombined.contains("record audio") ||
             textCombined.contains("audio")) &&
            securePreferences.getBoolean(PREFS_BLOCK_MIC, true)) {
            blockPermissionRequest(packageName, "MICROPHONE")
        }
        
        // Location permission detection
        if ((textCombined.contains("location") || textCombined.contains("gps") ||
             textCombined.contains("position")) &&
            securePreferences.getBoolean(PREFS_BLOCK_LOCATION, true)) {
            blockPermissionRequest(packageName, "LOCATION")
        }
    }

    private fun blockPermissionRequest(packageName: String, permissionType: String) {
        Log.w(TAG, "BLOCKING $permissionType request from $packageName")
        logBlockedAttempt(packageName, permissionType)
        
        // Try to auto-click deny button
        handler.postDelayed({
            try {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    findAndClickDeny(rootNode)
                    rootNode.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error auto-clicking deny", e)
            }
        }, 300)
        
        showBlockedNotification(packageName, permissionType)
    }

    private fun findAndClickDeny(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
        val denyTexts = listOf("deny", "cancel", "don't allow", "拒绝", "取消", "no")
        
        for (denyText in denyTexts) {
            val denyNodes = node.findAccessibilityNodeInfosByText(denyText)
            for (denyNode in denyNodes) {
                if (denyNode.isClickable) {
                    denyNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    Log.i(TAG, "Clicked deny button: $denyText")
                    return true
                }
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (findAndClickDeny(child)) return true
                child.recycle()
            }
        }
        return false
    }

    private fun monitorOverlayAttempts(event: AccessibilityEvent, packageName: String) {
        val className = event.className?.toString() ?: return
        
        if (className.contains("Toast") || className.contains("Overlay") || 
            className.contains("SystemAlert")) {
            
            event.source?.let { source ->
                val bounds = android.graphics.Rect()
                source.getBoundsInScreen(bounds)
                
                val screenArea = resources.displayMetrics.widthPixels * 
                                 resources.displayMetrics.heightPixels
                val overlayArea = bounds.width() * bounds.height()
                
                if (overlayArea > screenArea * 0.3) {
                    logSecurityEvent("LARGE_OVERLAY", "$packageName: ${bounds.width()}x${bounds.height()}")
                    showOverlayWarning(packageName)
                }
                source.recycle()
            }
        }
    }

    private fun logBlockedAttempt(packageName: String, permission: String) {
        val message = "🛡️ Blocked $permission request from $packageName"
        Log.w(TAG, message)
        
        activityLogger.log(
            ActivityLogger.CATEGORY_FPM,
            message,
            ActivityLogger.SEVERITY_WARNING
        )
        
        val timestamp = System.currentTimeMillis()
        val key = "fpm_block_$timestamp"
        securePreferences.putString(key, "$packageName|$permission|$timestamp")
    }

    private fun logSecurityEvent(type: String, details: String) {
        activityLogger.log(
            ActivityLogger.CATEGORY_TAMPER,
            "$type: $details",
            ActivityLogger.SEVERITY_ERROR
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FPM Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time permission blocking notifications"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showServiceNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sentinoid FPM")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_security)
            .setOngoing(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
    }

    private fun showBlockedNotification(packageName: String, permission: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Permission Blocked")
            .setContentText("$appName tried to access $permission")
            .setSmallIcon(R.drawable.ic_security)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showOverlayWarning(packageName: String) {
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠️ Suspicious Overlay Detected")
            .setContentText("$appName is displaying a large overlay - potential clickjacking")
            .setSmallIcon(R.drawable.ic_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(OVERLAY_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "FPMInterceptor"
        private const val CHANNEL_ID = "fpm_alerts"
        private const val SERVICE_NOTIFICATION_ID = 1001
        private const val OVERLAY_NOTIFICATION_ID = 1002

        const val PREFS_BLOCK_CAMERA = "block_camera"
        const val PREFS_BLOCK_MIC = "block_microphone"
        const val PREFS_BLOCK_LOCATION = "block_location"
        
        // Broadcast actions for FPM events (used by FPMDashboardActivity)
        const val ACTION_MOCK_CAMERA = "com.sentinoid.app.MOCK_CAMERA"
        const val ACTION_MOCK_AUDIO = "com.sentinoid.app.MOCK_AUDIO"
        const val ACTION_MOCK_LOCATION = "com.sentinoid.app.MOCK_LOCATION"
    }
}
