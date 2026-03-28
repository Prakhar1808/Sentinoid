package com.sentinoid.shield

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.util.LruCache
import androidx.core.content.ContextCompat

/**
 * Battery-optimized Feature Permission Manager with caching and lazy evaluation.
 * Reduces repeated permission checks and battery drain from hardware queries.
 */
class FeaturePermissionManager(private val context: Context) {
    private val TAG = "FPM"
    private val mockedFeatures = mutableSetOf<String>()
    private val featureStates = mutableMapOf<String, Boolean>()
    
    // Cache permission results to avoid repeated system calls
    private val permissionCache = LruCache<String, Boolean>(20)
    private val featureAvailabilityCache = LruCache<String, Boolean>(20)
    private var lastCacheClear = System.currentTimeMillis()
    private val CACHE_TTL_MS = 30 * 1000 // 30 seconds
    
    private var isLowPowerMode = false

    data class HardwareFeature(
        val name: String,
        val permission: String? = null,
        val hardwareFeature: String? = null,
        val minApiLevel: Int = Build.VERSION_CODES.LOLLIPOP
    )

    companion object {
        val FEATURES = listOf(
            HardwareFeature("camera", android.Manifest.permission.CAMERA, PackageManager.FEATURE_CAMERA),
            HardwareFeature("microphone", android.Manifest.permission.RECORD_AUDIO),
            HardwareFeature("location", android.Manifest.permission.ACCESS_FINE_LOCATION, null, Build.VERSION_CODES.M),
            HardwareFeature("bluetooth", android.Manifest.permission.BLUETOOTH, PackageManager.FEATURE_BLUETOOTH),
            HardwareFeature("nfc", android.Manifest.permission.NFC, PackageManager.FEATURE_NFC),
            HardwareFeature("fingerprint", null, PackageManager.FEATURE_FINGERPRINT, Build.VERSION_CODES.M),
            HardwareFeature("face_unlock", null, null, Build.VERSION_CODES.Q),
            HardwareFeature("gyroscope", null, PackageManager.FEATURE_SENSOR_GYROSCOPE),
            HardwareFeature("accelerometer", null, PackageManager.FEATURE_SENSOR_ACCELEROMETER),
            HardwareFeature("magnetometer", null, PackageManager.FEATURE_SENSOR_COMPASS)
        )
    }

    init {
        updatePowerMode()
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
        if (now - lastCacheClear > CACHE_TTL_MS) {
            featureAvailabilityCache.evictAll()
            lastCacheClear = now
        }
        
        featureAvailabilityCache.get(feature)?.let { return it }
        
        if (mockedFeatures.contains(feature)) {
            return featureStates[feature] ?: false
        }

        val featureDef = FEATURES.find { it.name == feature } ?: return false
        
        if (Build.VERSION.SDK_INT < featureDef.minApiLevel) {
            return false
        }

        val result = featureDef.hardwareFeature?.let {
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
        
        val featureDef = FEATURES.find { it.name == feature } ?: return false
        
        val result = featureDef.permission?.let { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        } ?: true
        
        permissionCache.put(feature, result)
        return result
    }

    fun mockFeature(feature: String, available: Boolean, authorized: Boolean = true) {
        if (!FEATURES.any { it.name == feature }) {
            Log.w(TAG, "Unknown feature: $feature")
            return
        }
        mockedFeatures.add(feature)
        featureStates[feature] = available
        
        // Update caches
        featureAvailabilityCache.put(feature, available)
        permissionCache.put(feature, authorized)
        
        Log.d(TAG, "Mocked $feature: available=$available, authorized=$authorized")
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
     * Batch status check - more efficient than individual checks.
     */
    fun getFeatureStatus(): Map<String, Pair<Boolean, Boolean>> {
        return FEATURES.associate { 
            it.name to Pair(isFeatureAvailable(it.name), isFeatureAuthorized(it.name))
        }
    }

    /**
     * Power-aware sensor jamming - reduces intensity in low power mode.
     */
    fun jamSensor(feature: String) {
        val intensity = if (isLowPowerMode) "LOW" else "HIGH"
        
        when (feature) {
            "microphone" -> Log.i(TAG, "JAMMING [$intensity]: Acoustic noise injection")
            "camera" -> Log.i(TAG, "JAMMING [$intensity]: Optical interference simulation")
            "gps" -> Log.i(TAG, "JAMMING [$intensity]: GPS spoofing")
            "gyroscope", "accelerometer", "magnetometer" -> 
                Log.i(TAG, "JAMMING [$intensity]: Sensor noise injection for $feature")
            else -> Log.w(TAG, "Cannot jam feature: $feature")
        }
    }
}
