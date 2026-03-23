package com.sentinoid.shield

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FPMInterceptor : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 🛡️ Detect if the foreground app changes
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Honeypot/Mock stream logic for untrusted apps
            if (isUntrustedApp(packageName)) {
                Log.w("FPMInterceptor", "🚨 Untrusted App Detected: $packageName")
                injectStaticNoise(packageName)
                SilentAlarmManager.addLog("FPM Intercepted $packageName (Video/Audio Mocked)")
            }
        }
    }

    private fun isUntrustedApp(packageName: String): Boolean {
        // In a real scenario, this would check a local DB of known spyware or
        // apps without the user's explicit Sentinoid trust flag.
        val mockSpywareList = listOf(
            "com.example.malware",
            "org.suspicious.scraper"
        )
        return mockSpywareList.contains(packageName)
    }
    
    private fun injectStaticNoise(packageName: String) {
        // Mocking hardware resource access for unauthorized apps
        Log.i("FPMInterceptor", "🛡️ Mocking Hardware Resources for $packageName")
        Log.i("FPMInterceptor", "🔊 [MIC]: Routing 0Hz Null-Stream to prevent background listening")
        Log.i("FPMInterceptor", "📸 [CAMERA]: Overlaying Static Noise frame to neutralize scraper")
        Log.i("FPMInterceptor", "📍 [GPS]: Injecting Mock-Coordinates (0.0, 0.0) for location privacy")
        
        // Notify the user via a tactical log entry
        SilentAlarmManager.addLog("FPM active: Mock-Stream injected to $packageName")
    }

    override fun onInterrupt() {
        Log.w("FPMInterceptor", "Service interrupted.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("FPMInterceptor", "Feature Permission Manager is online.")
    }
}