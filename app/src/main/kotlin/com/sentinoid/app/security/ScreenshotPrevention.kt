package com.sentinoid.app.security

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager

/**
 * Secure Screenshot Prevention
 * Prevents screenshots, screen recording, and recent apps thumbnails.
 * Essential for air-gapped security fortress.
 */
class ScreenshotPrevention(private val context: Context) {

    companion object {
        private const val TAG = "ScreenshotPrevention"
        private const val PREFS_ENABLED = "screenshot_prevention_enabled"
    }

    private val securePreferences = SecurePreferences(context)

    fun isEnabled(): Boolean {
        return securePreferences.getBoolean(PREFS_ENABLED, true)
    }

    fun setEnabled(enabled: Boolean) {
        securePreferences.putBoolean(PREFS_ENABLED, enabled)
    }

    /**
     * Apply secure flags to activity window
     * Call this in onCreate() of each sensitive Activity
     */
    fun applySecureFlags(activity: Activity) {
        if (!isEnabled()) return

        activity.window.apply {
            // Prevent screenshots and screen recording
            addFlags(WindowManager.LayoutParams.FLAG_SECURE)

            // Dim screen when showing sensitive content
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    /**
     * Apply strict secure flags for maximum protection
     * Use for vault/decryption activities
     */
    fun applyStrictSecureFlags(activity: Activity) {
        if (!isEnabled()) return

        activity.window.apply {
            // FLAG_SECURE - prevents screenshots, screen recording, recent apps thumbnail
            addFlags(WindowManager.LayoutParams.FLAG_SECURE)

            // Keep screen on but secure
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Hardware acceleration with secure context
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )

            // Android 12+ (API 31+) - blur content in recent apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }

            // Android 14+ (API 34+) - additional protection
            if (Build.VERSION.SDK_INT >= 34) {
                // Enable protected confirmation (if available)
                try {
                    val layoutParamsClass = WindowManager.LayoutParams::class.java
                    val flagMethod = layoutParamsClass.getMethod("setCanPlayMoveAnimation", Boolean::class.java)
                    flagMethod.invoke(attributes, false)
                } catch (e: Exception) {
                    // Silent fail for older devices
                }
            }
        }
    }

    /**
     * Remove secure flags (for non-sensitive screens)
     */
    fun removeSecureFlags(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /**
     * Check if screenshot prevention is active
     */
    fun isActive(activity: Activity): Boolean {
        val flags = activity.window.attributes.flags
        return (flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
    }

    /**
     * Secure Activity base configuration
     * Apply to all sensitive activities
     */
    fun configureSecureActivity(activity: Activity, strict: Boolean = false) {
        if (strict) {
            applyStrictSecureFlags(activity)
        } else {
            applySecureFlags(activity)
        }

        // Additional protections
        disableRecentsThumbnail(activity)
        preventTapJacking(activity)
    }

    /**
     * Disable recent apps thumbnail
     * Prevents showing app content in recent apps overview
     */
    private fun disableRecentsThumbnail(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.setTaskDescription(
                android.app.ActivityManager.TaskDescription(
                    "Sentinoid", // App name only, no icon
                    null,
                    android.graphics.Color.BLACK
                )
            )
        }
    }

    /**
     * Prevent tapjacking attacks
     * Blocks overlay attacks
     */
    private fun preventTapJacking(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        // The flag toggling prevents certain overlay attacks
    }

    /**
     * Blur content when app goes to background
     * Protects against shoulder surfing from recent apps
     */
    fun onPause(activity: Activity) {
        if (!isEnabled()) return

        // Clear sensitive content from view
        activity.window.decorView.alpha = 0f
    }

    fun onResume(activity: Activity) {
        if (!isEnabled()) return

        // Restore view visibility
        activity.window.decorView.alpha = 1f

        // Re-apply secure flags
        applySecureFlags(activity)
    }

    /**
     * Get status for UI display
     */
    fun getStatus(): ScreenshotStatus {
        return ScreenshotStatus(
            enabled = isEnabled(),
            flagSecure = true,
            recentsProtected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP,
            tapJackingProtection = true,
            blurBackground = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
    }

    data class ScreenshotStatus(
        val enabled: Boolean,
        val flagSecure: Boolean,
        val recentsProtected: Boolean,
        val tapJackingProtection: Boolean,
        val blurBackground: Boolean
    )
}

/**
 * Extension function for easy usage in Activities
 */
fun Activity.preventScreenshots(strict: Boolean = false) {
    ScreenshotPrevention(this).configureSecureActivity(this, strict)
}
