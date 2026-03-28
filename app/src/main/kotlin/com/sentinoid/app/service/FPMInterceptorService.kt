package com.sentinoid.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.sentinoid.app.security.SecurePreferences

class FPMInterceptorService : AccessibilityService() {

    private lateinit var securePreferences: SecurePreferences
    private var isInterceptionActive = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        securePreferences = SecurePreferences(this)
        
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        
        isInterceptionActive = true
        Log.i(TAG, "FPM Interceptor service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isInterceptionActive) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                checkAppPermissionRequests(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                monitorOverlayAttempts(event)
            }
        }
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    private fun checkAppPermissionRequests(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Skip system UI and self
        if (packageName.startsWith("com.android.") || packageName == packageName) {
            return
        }
        
        // Detect permission request dialogs
        event.text.forEach { text ->
            val textStr = text.toString().lowercase()
            
            when {
                textStr.contains("camera") && securePreferences.getBoolean(PREFS_BLOCK_CAMERA, true) -> {
                    injectMockCameraResponse(packageName)
                }
                textStr.contains("microphone") && securePreferences.getBoolean(PREFS_BLOCK_MIC, true) -> {
                    injectMockAudioResponse(packageName)
                }
                textStr.contains("location") && securePreferences.getBoolean(PREFS_BLOCK_LOCATION, true) -> {
                    injectMockLocationResponse(packageName)
                }
            }
        }
    }

    private fun monitorOverlayAttempts(event: AccessibilityEvent) {
        // Detect suspicious overlay patterns
        val className = event.className?.toString() ?: return
        
        // Check for known overlay attack patterns
        if (className.contains("Toast") || className.contains("Overlay")) {
            val source = event.source
            if (source != null) {
                // Analyze overlay properties
                val bounds = android.graphics.Rect()
                source.getBoundsInScreen(bounds)
                
                // Suspicious if overlay covers significant screen area
                val screenArea = resources.displayMetrics.widthPixels * 
                                resources.displayMetrics.heightPixels
                val overlayArea = bounds.width() * bounds.height()
                
                if (overlayArea > screenArea * 0.5) {
                    // Large overlay detected - potential clickjacking
                    logSecurityEvent("LARGE_OVERLAY_DETECTED", className)
                }
            }
        }
    }

    private fun injectMockCameraResponse(packageName: String) {
        Log.i(TAG, "Intercepting camera request from: $packageName")
        
        // In a full implementation, this would:
        // 1. Return a static black frame
        // 2. Or a decoy image from honeypot
        // 3. Without crashing the requesting app
        
        // Signal that we want to provide mock data
        val intent = Intent(ACTION_MOCK_CAMERA).apply {
            putExtra("package", packageName)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        
        logSecurityEvent("CAMERA_BLOCKED", packageName)
    }

    private fun injectMockAudioResponse(packageName: String) {
        Log.i(TAG, "Intercepting microphone request from: $packageName")
        
        // Create silent audio buffer - simplified mock without actual AudioRecord usage
        val bufferSize = 1024  // Default buffer size
        
        // Return zeros (silence) instead of actual audio
        val _silenceBuffer = ShortArray(bufferSize) { 0 }
        
        val intent = Intent(ACTION_MOCK_AUDIO).apply {
            putExtra("package", packageName)
            putExtra("buffer_size", bufferSize)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        
        logSecurityEvent("MICROPHONE_BLOCKED", packageName)
    }

    private fun injectMockLocationResponse(packageName: String) {
        Log.i(TAG, "Intercepting location request from: $packageName")
        
        val intent = Intent(ACTION_MOCK_LOCATION).apply {
            putExtra("package", packageName)
            putExtra("latitude", MOCK_LOCATION_LAT)
            putExtra("longitude", MOCK_LOCATION_LON)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        
        logSecurityEvent("LOCATION_BLOCKED", packageName)
    }

    private fun logSecurityEvent(type: String, details: String) {
        val timestamp = System.currentTimeMillis()
        val event = SecurityEvent(
            timestamp = timestamp,
            type = type,
            details = details,
            severity = when (type) {
                "LARGE_OVERLAY_DETECTED" -> "HIGH"
                "CAMERA_BLOCKED", "MICROPHONE_BLOCKED", "LOCATION_BLOCKED" -> "MEDIUM"
                else -> "LOW"
            }
        )
        
        // Store in secure preferences
        val key = "security_event_${timestamp}"
        securePreferences.putString(key, "${event.type}|${event.details}|${event.severity}")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isInterceptionActive = false
        Log.i(TAG, "FPM Interceptor service disconnected")
        return super.onUnbind(intent)
    }

    data class SecurityEvent(
        val timestamp: Long,
        val type: String,
        val details: String,
        val severity: String
    )

    companion object {
        private const val TAG = "FPMInterceptor"
        
        // Hardware feature flags
        const val PREFS_BLOCK_CAMERA = "block_camera"
        const val PREFS_BLOCK_MIC = "block_microphone"
        const val PREFS_BLOCK_LOCATION = "block_location"
        
        // Mock data providers
        private val MOCK_LOCATION_LAT = 0.0
        private val MOCK_LOCATION_LON = 0.0

        const val ACTION_MOCK_CAMERA = "com.sentinoid.app.MOCK_CAMERA"
        const val ACTION_MOCK_AUDIO = "com.sentinoid.app.MOCK_AUDIO"
        const val ACTION_MOCK_LOCATION = "com.sentinoid.app.MOCK_LOCATION"
    }
}
