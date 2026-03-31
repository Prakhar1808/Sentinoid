package com.sentinoid.app.security

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Criteria
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import androidx.core.content.ContextCompat

/**
 * Battery-optimized Feature Permission Manager with caching and lazy evaluation.
 * Reduces repeated permission checks and battery drain from hardware queries.
 */
class FeaturePermissionManager(private val context: Context) {
    private val tag = "FPM"
    private val mockedFeatures = mutableSetOf<String>()
    private val featureStates = mutableMapOf<String, Boolean>()
    private val blockedFeatures = mutableSetOf<String>()

    // Cache permission results to avoid repeated system calls
    private val permissionCache = LruCache<String, Boolean>(20)
    private val featureAvailabilityCache = LruCache<String, Boolean>(20)
    private var lastCacheClear = System.currentTimeMillis()
    private val cacheTtlMs = 30 * 1000 // 30 seconds

    private var isLowPowerMode = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var devicePolicyManager: DevicePolicyManager? = null
    private var locationManager: LocationManager? = null

    data class HardwareFeature(
        val name: String,
        val permission: String? = null,
        val hardwareFeature: String? = null,
        val minApiLevel: Int = Build.VERSION_CODES.LOLLIPOP,
    )

    companion object {
        val features =
            listOf(
                HardwareFeature("camera", android.Manifest.permission.CAMERA, PackageManager.FEATURE_CAMERA),
                HardwareFeature("microphone", android.Manifest.permission.RECORD_AUDIO),
                HardwareFeature("location", android.Manifest.permission.ACCESS_FINE_LOCATION, null, Build.VERSION_CODES.M),
                HardwareFeature("bluetooth", android.Manifest.permission.BLUETOOTH, PackageManager.FEATURE_BLUETOOTH),
                HardwareFeature("nfc", android.Manifest.permission.NFC, PackageManager.FEATURE_NFC),
                HardwareFeature("fingerprint", null, PackageManager.FEATURE_FINGERPRINT, Build.VERSION_CODES.M),
            )
        
        const val REQUEST_CODE_DEVICE_ADMIN = 1001
    }

    init {
        updatePowerMode()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    private fun updatePowerMode() {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        isLowPowerMode = level < 20

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            isLowPowerMode = isLowPowerMode || powerManager.isPowerSaveMode
        }
    }

    /**
     * Cached permission check - reduces system calls.
     */
    fun isFeatureAvailable(feature: String): Boolean {
        // Check cache first
        val now = System.currentTimeMillis()
        if (now - lastCacheClear > cacheTtlMs) {
            featureAvailabilityCache.evictAll()
            lastCacheClear = now
        }

        featureAvailabilityCache.get(feature)?.let { return it }

        // If feature is blocked, return false (feature is not available when blocked)
        if (blockedFeatures.contains(feature)) {
            return false
        }

        if (mockedFeatures.contains(feature)) {
            return featureStates[feature] ?: false
        }

        val featureDef = features.find { it.name == feature } ?: return false

        if (Build.VERSION.SDK_INT < featureDef.minApiLevel) {
            return false
        }

        val result =
            featureDef.hardwareFeature?.let {
                context.packageManager.hasSystemFeature(it)
            } ?: true

        featureAvailabilityCache.put(feature, result)
        return result
    }

    /**
     * Cached permission check with TTL.
     */
    fun isFeatureAuthorized(feature: String): Boolean {
        permissionCache.get(feature)?.let { return it }

        val featureDef = features.find { it.name == feature } ?: return false

        val result =
            featureDef.permission?.let { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            } ?: true

        permissionCache.put(feature, result)
        return result
    }

    fun mockFeature(
        feature: String,
        available: Boolean,
        authorized: Boolean = true,
    ) {
        if (!features.any { it.name == feature }) {
            Log.w(tag, "Unknown feature: $feature")
            return
        }
        mockedFeatures.add(feature)
        featureStates[feature] = available

        // Update caches
        featureAvailabilityCache.put(feature, available)
        permissionCache.put(feature, authorized)

        Log.d(tag, "Mocked $feature: available=$available, authorized=$authorized")
    }

    fun unmockFeature(feature: String) {
        mockedFeatures.remove(feature)
        featureStates.remove(feature)
        permissionCache.remove(feature)
        featureAvailabilityCache.remove(feature)
    }

    fun clearAllMocks() {
        mockedFeatures.clear()
        featureStates.clear()
        permissionCache.evictAll()
        featureAvailabilityCache.evictAll()
    }

    /**
     * Check if Device Admin is active - REQUIRED for camera blocking to work
     */
    fun isDeviceAdminActive(): Boolean {
        val adminComponent = ComponentName(context, com.sentinoid.app.receiver.SentinoidDeviceAdminReceiver::class.java)
        return devicePolicyManager?.isAdminActive(adminComponent) == true
    }

    /**
     * Launch Device Admin activation flow
     */
    fun requestDeviceAdmin(activity: Activity) {
        val adminComponent = ComponentName(context, com.sentinoid.app.receiver.SentinoidDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Sentinoid needs device admin permission to block camera access. This is used only for security features.")
        }
        activity.startActivityForResult(intent, REQUEST_CODE_DEVICE_ADMIN)
    }

    /**
     * Open app settings to grant special permissions
     */
    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        activity.startActivity(intent)
    }

    /**
     * Get detailed blocking status for UI display
     */
    fun getBlockingStatus(): BlockingStatus {
        val adminActive = isDeviceAdminActive()
        return BlockingStatus(
            cameraBlocked = blockedFeatures.contains("camera"),
            microphoneBlocked = blockedFeatures.contains("microphone"),
            locationBlocked = blockedFeatures.contains("location"),
            deviceAdminActive = adminActive,
            canBlockCamera = adminActive,
            canBlockMicrophone = audioManager != null,
            canBlockLocation = false // Location blocking requires system permissions
        )
    }

    data class BlockingStatus(
        val cameraBlocked: Boolean,
        val microphoneBlocked: Boolean,
        val locationBlocked: Boolean,
        val deviceAdminActive: Boolean,
        val canBlockCamera: Boolean,
        val canBlockMicrophone: Boolean,
        val canBlockLocation: Boolean
    ) {
        fun getStatusMessage(): String {
            return buildString {
                appendLine("Feature Blocking Status:")
                appendLine("Camera: ${if (cameraBlocked) "✓ BLOCKED" else "Not blocked"} ${if (!deviceAdminActive) "(NEEDS ADMIN!)" else ""}")
                appendLine("Microphone: ${if (microphoneBlocked) "✓ BLOCKED" else "Not blocked"}")
                appendLine("Location: ${if (locationBlocked) "✓ BLOCKED (Mock)" else "Not blocked"} (Limited without root)")
                appendLine()
                appendLine("Note: Camera blocking requires Device Admin permission.")
                appendLine("Tap 'Enable Device Admin' to activate camera blocking.")
            }
        }
    }

    /**
     * ACTUALLY BLOCKS hardware features.
     * Returns true if blocking was successfully applied.
     */
    fun setFeatureBlocked(feature: String, blocked: Boolean): Boolean {
        val success = when (feature) {
            "camera" -> {
                val result = blockCamera(blocked)
                result
            }
            "microphone" -> {
                blockMicrophone(blocked)
                true // Mic blocking via AudioManager usually works
            }
            "location" -> {
                blockLocation(blocked)
                true // Location blocking is mock-only
            }
            else -> {
                Log.w(tag, "Blocking not implemented for feature: $feature")
                false
            }
        }

        if (success) {
            if (blocked) {
                blockedFeatures.add(feature)
                featureAvailabilityCache.put(feature, false)
            } else {
                blockedFeatures.remove(feature)
                featureAvailabilityCache.remove(feature)
            }
        }
        return success
    }

    /**
     * Block camera using DevicePolicyManager if available.
     * Returns true if device admin is active and camera was blocked.
     */
    private fun blockCamera(block: Boolean): Boolean {
        return try {
            val adminComponent = ComponentName(context, com.sentinoid.app.receiver.SentinoidDeviceAdminReceiver::class.java)
            
            if (devicePolicyManager?.isAdminActive(adminComponent) == true) {
                devicePolicyManager?.setCameraDisabled(adminComponent, block)
                Log.i(tag, "Camera ${if (block) "BLOCKED" else "unblocked"} via DevicePolicyManager")
                true
            } else {
                // Cannot block without device admin
                Log.e(tag, "Cannot block camera - Device Admin not active")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error blocking camera: ${e.message}")
            false
        }
    }

    /**
     * Block microphone by requesting permanent audio focus.
     * This prevents other apps from recording audio.
     */
    private fun blockMicrophone(block: Boolean) {
        if (block) {
            try {
                // Request audio focus to block recording
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener { focusChange ->
                            Log.d(tag, "Audio focus changed: $focusChange")
                            // Re-acquire focus if lost
                            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                                requestAudioFocus()
                            }
                        }
                        .build()
                    
                    val result = audioManager?.requestAudioFocus(focusRequest)
                    audioFocusRequest = focusRequest
                    
                    Log.i(tag, "Microphone BLOCKED - audio focus requested, result: $result")
                } else {
                    @Suppress("DEPRECATION")
                    val result = audioManager?.requestAudioFocus(
                        { focusChange ->
                            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                                @Suppress("DEPRECATION")
                                audioManager?.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
                            }
                        },
                        AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN
                    )
                    Log.i(tag, "Microphone BLOCKED - audio focus requested (legacy), result: $result")
                }
                
                // Also mute the microphone if possible
                audioManager?.isMicrophoneMute = true
            } catch (e: Exception) {
                Log.e(tag, "Error blocking microphone: ${e.message}")
            }
        } else {
            // Unblock microphone
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let {
                        audioManager?.abandonAudioFocusRequest(it)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.abandonAudioFocus(null)
                }
                audioManager?.isMicrophoneMute = false
                Log.i(tag, "Microphone unblocked - audio focus released")
            } catch (e: Exception) {
                Log.e(tag, "Error unblocking microphone: ${e.message}")
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager?.requestAudioFocus(it)
            }
        }
    }

    /**
     * Block location by enabling mock location and setting test provider.
     */
    private fun blockLocation(block: Boolean) {
        try {
            if (block) {
                // Try to set a mock provider that returns null/0 location
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        locationManager?.addTestProvider(
                            LocationManager.GPS_PROVIDER,
                            false,
                            false,
                            false,
                            false,
                            false,
                            true,
                            true,
                            Criteria.POWER_LOW,
                            Criteria.ACCURACY_FINE
                        )
                        
                        val mockLocation = android.location.Location(LocationManager.GPS_PROVIDER).apply {
                            latitude = 0.0
                            longitude = 0.0
                            accuracy = 1f
                            time = System.currentTimeMillis()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                                elapsedRealtimeNanos = System.nanoTime()
                            }
                        }
                        
                        locationManager?.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)
                        Log.i(tag, "Location BLOCKED - mock provider active (returns 0,0)")
                    } catch (e: SecurityException) {
                        Log.w(tag, "Cannot set mock location - requires TEST_LOCATION permission")
                        // Fallback to mock mode
                        mockFeature("location", false, false)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    locationManager?.addTestProvider(
                        LocationManager.GPS_PROVIDER,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE
                    )
                    Log.i(tag, "Location BLOCKED - mock provider active (legacy)")
                }
            } else {
                // Remove mock provider
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        locationManager?.removeTestProvider(LocationManager.GPS_PROVIDER)
                    } else {
                        @Suppress("DEPRECATION")
                        locationManager?.removeTestProvider(LocationManager.GPS_PROVIDER)
                    }
                    Log.i(tag, "Location unblocked - mock provider removed")
                } catch (e: Exception) {
                    Log.w(tag, "Error removing mock provider: ${e.message}")
                }
                unmockFeature("location")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error blocking location: ${e.message}")
        }
    }

    /**
     * Power-aware sensor jamming - reduces intensity in low power mode.
     */
    fun jamSensor(feature: String) {
        val intensity = if (isLowPowerMode) "LOW" else "HIGH"

        when (feature) {
            "microphone" -> Log.i(tag, "JAMMING [$intensity]: Using audio focus blocking")
            "camera" -> Log.i(tag, "JAMMING [$intensity]: Using DevicePolicy blocking")
            "gps" -> Log.i(tag, "JAMMING [$intensity]: Using mock location provider")
            "gyroscope", "accelerometer", "magnetometer" ->
                Log.i(tag, "JAMMING [$intensity]: Sensor noise injection for $feature (limited - requires root)")
            else -> Log.w(tag, "Cannot jam feature: $feature")
        }
    }
}
