package com.sentinoid.app.ui

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sentinoid.app.R
import com.sentinoid.app.SentinoidApp
import com.sentinoid.app.security.ActivityLogger
import com.sentinoid.app.security.FeaturePermissionManager
import com.sentinoid.app.security.SecurePreferences
import com.sentinoid.app.service.FPMInterceptorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FPMDashboardActivity : AppCompatActivity() {
    private lateinit var securePreferences: SecurePreferences
    private lateinit var featurePermissionManager: FeaturePermissionManager

    private lateinit var tvServiceStatus: TextView
    private lateinit var tvThreatLevel: TextView
    private lateinit var tvBlockedCount: TextView
    private lateinit var tvLastAlert: TextView
    private lateinit var containerFeatureStatus: LinearLayout
    private lateinit var recyclerEvents: RecyclerView

    private lateinit var switchAutoBlockMalware: SwitchMaterial
    private lateinit var switchAutoBlockOverlay: SwitchMaterial
    private lateinit var switchAutoBlockExploit: SwitchMaterial
    private lateinit var switchNotifications: SwitchMaterial

    private lateinit var tvDeviceAdminStatus: TextView
    private lateinit var btnEnableDeviceAdmin: MaterialButton
    private lateinit var ivDeviceAdminStatus: android.widget.ImageView
    private lateinit var tvDeviceAdminHelp: TextView
    private lateinit var btnEnableFPM: MaterialButton
    private lateinit var btnDisableFPM: MaterialButton
    private lateinit var btnClearEvents: MaterialButton
    private lateinit var btnTestFPM: MaterialButton

    private val eventsList = mutableListOf<FPMEvent>()
    private lateinit var eventsAdapter: FPMEventsAdapter

    private var blockedRequestCount = 0
    private var lastAlertTime: Long = 0

    // Notification rate limiting
    private var lastNotificationTime: Long = 0
    private var notificationCount: Int = 0
    private val NOTIFICATION_RATE_LIMIT_MS = 5000L // 5 seconds between notifications
    private val MAX_NOTIFICATIONS_BURST = 3 // Max 3 notifications in quick succession
    private val NOTIFICATION_BURST_WINDOW_MS = 60000L // 1 minute window

    private val securityEventReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    FPMInterceptorService.ACTION_MOCK_CAMERA ->
                        handleSecurityEvent(
                            "Camera Blocked",
                            intent.getStringExtra("package") ?: "Unknown",
                        )
                    FPMInterceptorService.ACTION_MOCK_AUDIO ->
                        handleSecurityEvent(
                            "Microphone Blocked",
                            intent.getStringExtra("package") ?: "Unknown",
                        )
                    FPMInterceptorService.ACTION_MOCK_LOCATION ->
                        handleSecurityEvent(
                            "Location Blocked",
                            intent.getStringExtra("package") ?: "Unknown",
                        )
                    ACTION_MALWARE_DETECTED -> handleMalwareDetection(intent)
                    ACTION_OVERLAY_DETECTED -> handleOverlayDetection(intent)
                    ACTION_FEATURE_EXPLOITED -> handleFeatureExploitation(intent)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fpm_dashboard)

        val app = application as SentinoidApp
        securePreferences = app.securePreferences
        featurePermissionManager = FeaturePermissionManager(this)

        initViews()
        setupRecyclerView()
        loadPreferences()
        loadEventsFromStorage()
        setupListeners()
        createNotificationChannel()
        updateStatus()
        startStatusMonitoring()
    }

    override fun onResume() {
        super.onResume()
        registerSecurityReceiver()
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(securityEventReceiver)
    }

    private fun initViews() {
        tvServiceStatus = findViewById(R.id.tv_fpm_service_status)
        tvThreatLevel = findViewById(R.id.tv_fpm_threat_level)
        tvBlockedCount = findViewById(R.id.tv_fpm_blocked_count)
        tvLastAlert = findViewById(R.id.tv_fpm_last_alert)
        containerFeatureStatus = findViewById(R.id.container_feature_status)
        recyclerEvents = findViewById(R.id.recycler_fpm_events)

        switchAutoBlockMalware = findViewById(R.id.switch_auto_block_malware)
        switchAutoBlockOverlay = findViewById(R.id.switch_auto_block_overlay)
        switchAutoBlockExploit = findViewById(R.id.switch_auto_block_exploit)
        switchNotifications = findViewById(R.id.switch_notifications)

        tvDeviceAdminStatus = findViewById(R.id.tv_device_admin_status)
        btnEnableDeviceAdmin = findViewById(R.id.btn_enable_device_admin)
        ivDeviceAdminStatus = findViewById(R.id.iv_device_admin_status)
        tvDeviceAdminHelp = findViewById(R.id.tv_device_admin_help)

        btnEnableFPM = findViewById(R.id.btn_enable_fpm)
        btnDisableFPM = findViewById(R.id.btn_disable_fpm)
        btnClearEvents = findViewById(R.id.btn_clear_events)
        btnTestFPM = findViewById(R.id.btn_test_fpm)
    }

    private fun setupRecyclerView() {
        eventsAdapter = FPMEventsAdapter(eventsList)
        recyclerEvents.layoutManager = LinearLayoutManager(this)
        recyclerEvents.adapter = eventsAdapter
    }

    private fun loadPreferences() {
        switchAutoBlockMalware.isChecked = securePreferences.getBoolean(PREFS_AUTO_BLOCK_MALWARE, true)
        switchAutoBlockOverlay.isChecked = securePreferences.getBoolean(PREFS_AUTO_BLOCK_OVERLAY, true)
        switchAutoBlockExploit.isChecked = securePreferences.getBoolean(PREFS_AUTO_BLOCK_EXPLOIT, true)
        switchNotifications.isChecked = securePreferences.getBoolean(PREFS_FPM_NOTIFICATIONS, true)

        blockedRequestCount = securePreferences.getInt(PREFS_BLOCKED_COUNT, 0)
        lastAlertTime = securePreferences.getLong(PREFS_LAST_ALERT_TIME, 0)
    }

    private fun setupListeners() {
        switchAutoBlockMalware.setOnCheckedChangeListener { _, checked ->
            securePreferences.putBoolean(PREFS_AUTO_BLOCK_MALWARE, checked)
        }

        switchAutoBlockOverlay.setOnCheckedChangeListener { _, checked ->
            securePreferences.putBoolean(PREFS_AUTO_BLOCK_OVERLAY, checked)
        }

        switchAutoBlockExploit.setOnCheckedChangeListener { _, checked ->
            securePreferences.putBoolean(PREFS_AUTO_BLOCK_EXPLOIT, checked)
        }

        switchNotifications.setOnCheckedChangeListener { _, checked ->
            securePreferences.putBoolean(PREFS_FPM_NOTIFICATIONS, checked)
        }

        btnEnableFPM.setOnClickListener { enableFPMService() }
        btnDisableFPM.setOnClickListener { disableFPMService() }
        btnClearEvents.setOnClickListener { clearEvents() }
        btnTestFPM.setOnClickListener { testFPMDetection() }
        btnEnableDeviceAdmin.setOnClickListener { requestDeviceAdmin() }
    }

    private fun enableFPMService() {
        if (!checkAccessibilityPermission()) {
            showAccessibilityPermissionDialog()
            return
        }

        val intent = Intent(this, FPMInterceptorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        // Show success with ripple animation
        btnEnableFPM.isEnabled = false
        btnDisableFPM.isEnabled = true
        
        Toast.makeText(this, getString(R.string.fpm_enabled), Toast.LENGTH_SHORT).show()
        ActivityLogger.getInstance(this).log(
            ActivityLogger.CATEGORY_FPM,
            "FPM Service enabled by user",
            ActivityLogger.SEVERITY_INFO
        )
        
        // Delayed UI update to show transition
        lifecycleScope.launch {
            delay(500)
            updateStatus()
        }
    }

    private fun disableFPMService() {
        val intent = Intent(this, FPMInterceptorService::class.java)
        stopService(intent)
        
        btnEnableFPM.isEnabled = true
        btnDisableFPM.isEnabled = false
        
        Toast.makeText(this, getString(R.string.fpm_disabled), Toast.LENGTH_SHORT).show()
        ActivityLogger.getInstance(this).log(
            ActivityLogger.CATEGORY_FPM,
            "FPM Service disabled by user",
            ActivityLogger.SEVERITY_WARNING
        )
        updateStatus()
    }

    private fun checkAccessibilityPermission(): Boolean {
        val accessibilityEnabled =
            try {
                Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
            } catch (e: Settings.SettingNotFoundException) {
                0
            }

        if (accessibilityEnabled == 1) {
            val service = "$packageName/${FPMInterceptorService::class.java.canonicalName}"
            val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return settingValue?.contains(service) == true
        }
        return false
    }

    private fun showAccessibilityPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_accessibility_title))
            .setMessage(getString(R.string.dialog_accessibility_message))
            .setPositiveButton(getString(R.string.dialog_open_settings)) { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    intent.putExtra("android.provider.extra.APP_PACKAGE", packageName)
                }
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .setCancelable(false)
            .show()
    }

    private fun updateStatus() {
        val isServiceActive = checkAccessibilityPermission()
        tvServiceStatus.text = if (isServiceActive) getString(R.string.status_active) else getString(R.string.status_inactive)
        tvServiceStatus.setTextColor(
            ContextCompat.getColor(this, if (isServiceActive) R.color.accent_green else R.color.error_red),
        )

        tvBlockedCount.text = blockedRequestCount.toString()
        
        // Animate blocked count if increased
        val lastBlocked = securePreferences.getInt("last_displayed_blocked", 0)
        if (blockedRequestCount > lastBlocked) {
            tvBlockedCount.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(200)
                .withEndAction {
                    tvBlockedCount.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
            securePreferences.putInt("last_displayed_blocked", blockedRequestCount)
        }

        if (lastAlertTime > 0) {
            val timeDiff = System.currentTimeMillis() - lastAlertTime
            val minutes = timeDiff / 60000
            tvLastAlert.text =
                when {
                    minutes < 1 -> getString(R.string.alert_just_now)
                    minutes < 60 -> getString(R.string.alert_minutes_ago, minutes.toInt())
                    minutes < 1440 -> getString(R.string.alert_hours_ago, (minutes / 60).toInt())
                    else -> getString(R.string.alert_days_ago, (minutes / 1440).toInt())
                }
        } else {
            tvLastAlert.text = getString(R.string.alert_none)
        }

        updateFeatureStatus()
        updateThreatLevel()
        updateDeviceAdminStatus()
    }

    private fun updateFeatureStatus() {
        containerFeatureStatus.removeAllViews()

        val features =
            listOf(
                Triple("Camera", "camera", true),
                Triple("Microphone", "microphone", true),
                Triple("Location", "location", true),
                Triple("Bluetooth", "bluetooth", false),
                Triple("NFC", "nfc", false),
            )

        features.forEach { (name, key, canBlock) ->
            val isAvailable = featurePermissionManager.isFeatureAvailable(key)
            val isAuthorized = featurePermissionManager.isFeatureAuthorized(key)
            val isBlocked = securePreferences.getBoolean(getBlockPrefKey(key), false)

            val view = LayoutInflater.from(this).inflate(R.layout.item_fpm_feature, containerFeatureStatus, false)
            view.findViewById<TextView>(R.id.tv_feature_name).text = name
            view.findViewById<TextView>(R.id.tv_feature_status).text =
                when {
                    isBlocked -> "Blocked"
                    !isAvailable -> "Unavailable"
                    !isAuthorized -> "No Permission"
                    else -> "Active"
                }
            view.findViewById<TextView>(R.id.tv_feature_status).setTextColor(
                ContextCompat.getColor(
                    this,
                    when {
                        isBlocked -> R.color.error_red
                        !isAvailable || !isAuthorized -> R.color.warning_yellow
                        else -> R.color.accent_green
                    },
                ),
            )

            val switch = view.findViewById<SwitchMaterial>(R.id.switch_feature_block)
            if (canBlock) {
                switch.isChecked = isBlocked
                switch.setOnCheckedChangeListener { _, checked ->
                    blockFeature(key, checked)
                }
            } else {
                switch.visibility = View.GONE
            }

            containerFeatureStatus.addView(view)
        }
    }

    private fun getBlockPrefKey(feature: String): String {
        return when (feature) {
            "camera" -> FPMInterceptorService.PREFS_BLOCK_CAMERA
            "microphone" -> FPMInterceptorService.PREFS_BLOCK_MIC
            "location" -> FPMInterceptorService.PREFS_BLOCK_LOCATION
            else -> "block_$feature"
        }
    }

    private fun blockFeature(
        feature: String,
        blocked: Boolean,
    ) {
        val (prefKey, sensorKey) = getFeatureConfig(feature)
        
        securePreferences.putBoolean(prefKey, blocked, true)
        
        if (blocked) {
            featurePermissionManager.mockFeature(feature, false, false)
            featurePermissionManager.jamSensor(sensorKey)
        } else {
            featurePermissionManager.unmockFeature(feature)
        }

        Toast.makeText(this, "$feature ${if (blocked) "blocked" else "unblocked"}", Toast.LENGTH_SHORT).show()
        ActivityLogger.getInstance(this).log(
            ActivityLogger.CATEGORY_FPM,
            "Feature $feature ${if (blocked) "blocked" else "unblocked"} by user",
            ActivityLogger.SEVERITY_INFO
        )
        updateFeatureStatus()
    }

    /**
     * Get preference key and sensor key for a feature
     */
    private fun getFeatureConfig(feature: String): Pair<String, String> {
        return when (feature.lowercase()) {
            "camera" -> Pair(FPMInterceptorService.PREFS_BLOCK_CAMERA, "camera")
            "microphone" -> Pair(FPMInterceptorService.PREFS_BLOCK_MIC, "microphone")
            "location" -> Pair(FPMInterceptorService.PREFS_BLOCK_LOCATION, "gps")
            else -> Pair("block_$feature", feature)
        }
    }

    private fun updateThreatLevel() {
        val threatCount = eventsList.count { it.severity == "HIGH" || it.severity == "CRITICAL" }
        val mediumCount = eventsList.count { it.severity == "MEDIUM" }
        val recentThreats = eventsList.count { 
            it.severity == "HIGH" && (System.currentTimeMillis() - it.timestamp) < 300000 
        }
        
        val level =
            when {
                recentThreats > 0 || threatCount > 5 -> "CRITICAL"
                threatCount > 2 -> "HIGH"
                threatCount > 0 || mediumCount > 3 -> "ELEVATED"
                eventsList.isEmpty() -> "NORMAL"
                else -> "LOW"
            }

        tvThreatLevel.text = level
        val colorRes =
            when (level) {
                "CRITICAL" -> R.color.error_red
                "HIGH" -> R.color.warning_yellow
                "ELEVATED" -> R.color.warning_yellow
                "LOW" -> R.color.accent_green
                else -> R.color.accent_green
            }
        tvThreatLevel.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun registerSecurityReceiver() {
        val filter =
            IntentFilter().apply {
                addAction(FPMInterceptorService.ACTION_MOCK_CAMERA)
                addAction(FPMInterceptorService.ACTION_MOCK_AUDIO)
                addAction(FPMInterceptorService.ACTION_MOCK_LOCATION)
                addAction(ACTION_MALWARE_DETECTED)
                addAction(ACTION_OVERLAY_DETECTED)
                addAction(ACTION_FEATURE_EXPLOITED)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(securityEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            registerReceiver(securityEventReceiver, filter)
        }
    }

    private fun handleSecurityEvent(
        type: String,
        packageName: String,
    ) {
        blockedRequestCount++
        securePreferences.putInt(PREFS_BLOCKED_COUNT, blockedRequestCount)

        val event =
            FPMEvent(
                timestamp = System.currentTimeMillis(),
                type = type,
                packageName = packageName,
                severity = "MEDIUM",
            )
        addEvent(event)

        if (securePreferences.getBoolean(PREFS_FPM_NOTIFICATIONS, true)) {
            showThreatNotification(type, packageName)
        }

        updateStatus()
    }

    private fun handleMalwareDetection(intent: Intent) {
        val packageName = intent.getStringExtra("package") ?: "Unknown"
        val threatType = intent.getStringExtra("threat_type") ?: "Unknown"

        val event =
            FPMEvent(
                timestamp = System.currentTimeMillis(),
                type = "Malware: $threatType",
                packageName = packageName,
                severity = "HIGH",
            )
        addEvent(event)

        if (securePreferences.getBoolean(PREFS_AUTO_BLOCK_MALWARE, true)) {
            autoBlockAllFeatures()
        }

        if (securePreferences.getBoolean(PREFS_FPM_NOTIFICATIONS, true)) {
            showThreatNotification("Malware Detected: $threatType", packageName)
        }

        lastAlertTime = System.currentTimeMillis()
        securePreferences.putLong(PREFS_LAST_ALERT_TIME, lastAlertTime)
        updateStatus()
    }

    private fun handleOverlayDetection(intent: Intent) {
        val className = intent.getStringExtra("class_name") ?: "Unknown"

        val event =
            FPMEvent(
                timestamp = System.currentTimeMillis(),
                type = "Suspicious Overlay",
                packageName = className,
                severity = "HIGH",
            )
        addEvent(event)

        if (securePreferences.getBoolean(PREFS_AUTO_BLOCK_OVERLAY, true)) {
            autoBlockAllFeatures()
        }

        if (securePreferences.getBoolean(PREFS_FPM_NOTIFICATIONS, true)) {
            showThreatNotification("Suspicious Overlay Detected", className)
        }

        lastAlertTime = System.currentTimeMillis()
        securePreferences.putLong(PREFS_LAST_ALERT_TIME, lastAlertTime)
        updateStatus()
    }

    private fun handleFeatureExploitation(intent: Intent) {
        val feature = intent.getStringExtra("feature") ?: "Unknown"
        val packageName = intent.getStringExtra("package") ?: "Unknown"

        val event =
            FPMEvent(
                timestamp = System.currentTimeMillis(),
                type = "Feature Exploitation: $feature",
                packageName = packageName,
                severity = "HIGH",
            )
        addEvent(event)

        if (securePreferences.getBoolean(PREFS_AUTO_BLOCK_EXPLOIT, true)) {
            autoBlockFeature(feature)
        }

        if (securePreferences.getBoolean(PREFS_FPM_NOTIFICATIONS, true)) {
            showThreatNotification("Feature Exploitation: $feature", packageName)
        }

        lastAlertTime = System.currentTimeMillis()
        securePreferences.putLong(PREFS_LAST_ALERT_TIME, lastAlertTime)
        updateStatus()
    }

    private fun autoBlockAllFeatures() {
        val features = listOf(
            Triple("camera", FPMInterceptorService.PREFS_BLOCK_CAMERA, "camera"),
            Triple("microphone", FPMInterceptorService.PREFS_BLOCK_MIC, "microphone"),
            Triple("location", FPMInterceptorService.PREFS_BLOCK_LOCATION, "gps")
        )

        features.forEach { (featureName, prefKey, sensorKey) ->
            securePreferences.putBoolean(prefKey, true, true)
            featurePermissionManager.mockFeature(featureName, false, false)
            featurePermissionManager.jamSensor(sensorKey)
        }

        Toast.makeText(this, "Auto-protection: All features blocked", Toast.LENGTH_LONG).show()
        ActivityLogger.getInstance(this).log(
            ActivityLogger.CATEGORY_FPM,
            "Auto-protection: All features blocked",
            ActivityLogger.SEVERITY_WARNING
        )
    }

    private fun autoBlockFeature(feature: String) {
        val (prefKey, sensorKey) = getFeatureConfig(feature)
        
        securePreferences.putBoolean(prefKey, true, true)
        featurePermissionManager.mockFeature(feature, false, false)
        featurePermissionManager.jamSensor(sensorKey)

        Toast.makeText(this, "Auto-protection: $feature blocked", Toast.LENGTH_LONG).show()
        ActivityLogger.getInstance(this).log(
            ActivityLogger.CATEGORY_FPM,
            "Auto-protection: $feature blocked",
            ActivityLogger.SEVERITY_WARNING
        )
    }

    /**
     * Saves events to storage with proper error handling and logging.
     * Logs failures to ActivityLogger for debugging.
     */
    private fun saveEventsToStorage() {
        try {
            val eventsJson = org.json.JSONArray()
            eventsList.take(50).forEach { event ->
                val obj =
                    org.json.JSONObject().apply {
                        put("timestamp", event.timestamp)
                        put("type", event.type)
                        put("packageName", event.packageName)
                        put("severity", event.severity)
                    }
                eventsJson.put(obj)
            }
            securePreferences.putString(PREFS_EVENTS_STORAGE, eventsJson.toString(), true)
            ActivityLogger.getInstance(this).log(
                ActivityLogger.CATEGORY_FPM,
                "Events persisted: ${eventsList.size} events",
                ActivityLogger.SEVERITY_DEBUG
            )
        } catch (e: Exception) {
            ActivityLogger.getInstance(this).log(
                ActivityLogger.CATEGORY_FPM,
                "Failed to persist events: ${e.message}",
                ActivityLogger.SEVERITY_ERROR
            )
        }
    }

    /**
     * Loads events from storage with proper error handling.
     * Logs corrupt data for debugging.
     */
    private fun loadEventsFromStorage() {
        try {
            val stored = securePreferences.getString(PREFS_EVENTS_STORAGE) ?: return
            val jsonArray = org.json.JSONArray(stored)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val event =
                    FPMEvent(
                        timestamp = obj.getLong("timestamp"),
                        type = obj.getString("type"),
                        packageName = obj.getString("packageName"),
                        severity = obj.getString("severity"),
                    )
                eventsList.add(event)
            }

            while (eventsList.size > 50) {
                eventsList.removeAt(eventsList.size - 1)
            }

            eventsAdapter.notifyDataSetChanged()
            ActivityLogger.getInstance(this).log(
                ActivityLogger.CATEGORY_FPM,
                "Events loaded: ${eventsList.size} events",
                ActivityLogger.SEVERITY_DEBUG
            )
        } catch (e: org.json.JSONException) {
            ActivityLogger.getInstance(this).log(
                ActivityLogger.CATEGORY_FPM,
                "Corrupt event data cleared: ${e.message}",
                ActivityLogger.SEVERITY_WARNING
            )
            securePreferences.remove(PREFS_EVENTS_STORAGE, true)
        } catch (e: Exception) {
            ActivityLogger.getInstance(this).log(
                ActivityLogger.CATEGORY_FPM,
                "Failed to load events: ${e.message}",
                ActivityLogger.SEVERITY_ERROR
            )
        }
    }

    private fun addEvent(event: FPMEvent) {
        eventsList.add(0, event)
        if (eventsList.size > 100) {
            eventsList.removeAt(eventsList.size - 1)
        }
        eventsAdapter.notifyItemInserted(0)
        recyclerEvents.scrollToPosition(0)

        // Persist periodically (not on every event to reduce I/O)
        if (eventsList.size % 10 == 0) {
            lifecycleScope.launch(Dispatchers.IO) {
                saveEventsToStorage()
            }
        }
    }

    private fun clearEvents() {
        eventsList.clear()
        eventsAdapter.notifyDataSetChanged()
        blockedRequestCount = 0
        lastAlertTime = 0
        securePreferences.putInt(PREFS_BLOCKED_COUNT, 0)
        securePreferences.putLong(PREFS_LAST_ALERT_TIME, 0)
        securePreferences.remove(PREFS_EVENTS_STORAGE)
        updateStatus()
        Toast.makeText(this, "Event history cleared", Toast.LENGTH_SHORT).show()
    }

    private fun testFPMDetection() {
        Toast.makeText(this, "Testing FPM detection...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            delay(1000)
            val testIntent =
                Intent(ACTION_MALWARE_DETECTED).apply {
                    setPackage(packageName) // Keep broadcast within app
                    putExtra("package", "com.test.malware")
                    putExtra("threat_type", "Test Detection")
                }
            sendBroadcast(testIntent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "FPM Security Alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Feature Permission Manager security alerts"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show threat notification with rate limiting to prevent notification spam.
     * Implements burst detection - if too many notifications in short time,
     * consolidates them into a summary notification.
     */
    private fun showThreatNotification(
        title: String,
        content: String,
    ) {
        // Check if notifications are enabled
        if (!securePreferences.getBoolean(PREFS_FPM_NOTIFICATIONS, true)) {
            return
        }

        val currentTime = System.currentTimeMillis()

        // Rate limiting: check time since last notification
        if (currentTime - lastNotificationTime < NOTIFICATION_RATE_LIMIT_MS) {
            notificationCount++
        } else {
            // Reset counter if outside burst window
            if (currentTime - lastNotificationTime > NOTIFICATION_BURST_WINDOW_MS) {
                notificationCount = 0
            }
        }

        // If over burst limit, send consolidated notification instead
        if (notificationCount > MAX_NOTIFICATIONS_BURST) {
            if (notificationCount == MAX_NOTIFICATIONS_BURST + 1) {
                // Only send one consolidated notification
                showConsolidatedNotification()
            }
            lastNotificationTime = currentTime
            return
        }

        val intent =
            Intent(this, FPMDashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID + (System.currentTimeMillis() % 1000).toInt(),
            notification
        )

        lastNotificationTime = currentTime

        // Log notification for debugging
        ActivityLogger.getInstance(this).log(
            ActivityLogger.CATEGORY_FPM,
            "Notification sent: $title",
            ActivityLogger.SEVERITY_DEBUG
        )
    }

    /**
     * Show a consolidated notification when rate limit is exceeded
     */
    private fun showConsolidatedNotification() {
        val intent =
            Intent(this, FPMDashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Multiple Security Threats Detected")
                .setContentText("$notificationCount threats detected. Tap to view details.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setGroupSummary(true)
                .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID_SUMMARY, notification)

        ActivityLogger.getInstance(this).log(
            ActivityLogger.CATEGORY_FPM,
            "Consolidated notification sent for $notificationCount threats",
            ActivityLogger.SEVERITY_WARNING
        )
    }

    private fun startStatusMonitoring() {
        lifecycleScope.launch {
            while (true) {
                delay(5000)
                withContext(Dispatchers.Main) {
                    updateStatus()
                }
            }
        }
    }

    data class FPMEvent(
        val timestamp: Long,
        val type: String,
        val packageName: String,
        val severity: String,
    )

    class FPMEventsAdapter(private val events: List<FPMEvent>) :
        RecyclerView.Adapter<FPMEventsAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvType: TextView = view.findViewById(R.id.tv_event_type)
            val tvPackage: TextView = view.findViewById(R.id.tv_event_package)
            val chipTime: com.google.android.material.chip.Chip = view.findViewById(R.id.chip_event_time)
            val severityContainer: android.widget.FrameLayout = view.findViewById(R.id.fl_severity_container)
            val severityIcon: android.widget.ImageView = view.findViewById(R.id.iv_severity_icon)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_fpm_event, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val event = events[position]
            holder.tvType.text = event.type
            holder.tvPackage.text = event.packageName

            val timeDiff = System.currentTimeMillis() - event.timestamp
            val minutes = timeDiff / 60000
            holder.chipTime.text =
                when {
                    minutes < 1 -> "Just now"
                    minutes < 60 -> "$minutes min ago"
                    else -> "${minutes / 60} hr ago"
                }

            // Set severity colors and icons
            val (color, icon) = when (event.severity) {
                "HIGH" -> Pair(R.color.error_red, R.drawable.ic_alert)
                "MEDIUM" -> Pair(R.color.warning_yellow, R.drawable.ic_info)
                else -> Pair(R.color.accent_green, R.drawable.ic_check_circle)
            }

            holder.tvType.setTextColor(ContextCompat.getColor(holder.itemView.context, color))
            holder.severityContainer.setBackgroundResource(
                when (event.severity) {
                    "HIGH" -> R.drawable.bg_status_critical
                    "MEDIUM" -> R.drawable.bg_status_warning
                    else -> R.drawable.bg_status_secure
                }
            )
            holder.severityIcon.setImageResource(icon)
        }

        override fun getItemCount() = events.size
    }

    /**
     * Updates the Device Admin status UI based on current state.
     * Shows warning if admin is not enabled since camera blocking requires it.
     */
    private fun updateDeviceAdminStatus() {
        val isAdminActive = featurePermissionManager.isDeviceAdminActive()

        if (isAdminActive) {
            tvDeviceAdminStatus.text = "Enabled - Camera blocking active"
            tvDeviceAdminStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            ivDeviceAdminStatus.setImageResource(R.drawable.ic_check_circle)
            btnEnableDeviceAdmin.visibility = View.GONE
            tvDeviceAdminHelp.text = "Device Admin is enabled. Camera blocking is fully operational."
        } else {
            tvDeviceAdminStatus.text = "Not Enabled - Required for Camera Blocking"
            tvDeviceAdminStatus.setTextColor(ContextCompat.getColor(this, R.color.warning_yellow))
            ivDeviceAdminStatus.setImageResource(R.drawable.ic_alert)
            btnEnableDeviceAdmin.visibility = View.VISIBLE
            tvDeviceAdminHelp.text = "Camera blocking requires Device Admin permission to disable the camera hardware. Tap 'Enable' to set it up."
        }
    }

    /**
     * Launch Device Admin activation flow.
     * Shows dialog explaining why it's needed before opening system settings.
     */
    private fun requestDeviceAdmin() {
        if (featurePermissionManager.isDeviceAdminActive()) {
            AlertDialog.Builder(this)
                .setTitle("Device Admin Active")
                .setMessage("Device Administrator is already enabled. Camera blocking is ready to use.")
                .setPositiveButton("OK", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Enable Device Admin")
                .setMessage("Device Admin permission is required to block camera access.\n\nThis permission allows Sentinoid to:\n• Disable camera hardware when threats are detected\n• Prevent unauthorized camera access\n\nThe permission will only be used for security features.")
                .setPositiveButton("Continue") { _, _ ->
                    featurePermissionManager.requestDeviceAdmin(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FeaturePermissionManager.REQUEST_CODE_DEVICE_ADMIN) {
            if (featurePermissionManager.isDeviceAdminActive()) {
                Toast.makeText(this, "Device Admin enabled! Camera blocking is now active.", Toast.LENGTH_LONG).show()
                ActivityLogger.getInstance(this).log(
                    ActivityLogger.CATEGORY_FPM,
                    "Device Admin enabled by user",
                    ActivityLogger.SEVERITY_INFO
                )
            } else {
                Toast.makeText(this, "Device Admin not enabled. Camera blocking requires this permission.", Toast.LENGTH_LONG).show()
                ActivityLogger.getInstance(this).log(
                    ActivityLogger.CATEGORY_FPM,
                    "Device Admin activation declined",
                    ActivityLogger.SEVERITY_WARNING
                )
            }
            updateDeviceAdminStatus()
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "fpm_security_alerts"
        const val NOTIFICATION_ID = 3000
        const val NOTIFICATION_ID_SUMMARY = 3999
        const val NOTIFICATION_GROUP_KEY = "fpm_threat_group"

        const val PREFS_AUTO_BLOCK_MALWARE = "fpm_auto_block_malware"
        const val PREFS_AUTO_BLOCK_OVERLAY = "fpm_auto_block_overlay"
        const val PREFS_AUTO_BLOCK_EXPLOIT = "fpm_auto_block_exploit"
        const val PREFS_FPM_NOTIFICATIONS = "fpm_notifications"
        const val PREFS_BLOCKED_COUNT = "fpm_blocked_count"
        const val PREFS_LAST_ALERT_TIME = "fpm_last_alert_time"
        const val PREFS_EVENTS_STORAGE = "fpm_events_storage"

        const val ACTION_MALWARE_DETECTED = "com.sentinoid.app.MALWARE_DETECTED"
        const val ACTION_OVERLAY_DETECTED = "com.sentinoid.app.OVERLAY_DETECTED"
        const val ACTION_FEATURE_EXPLOITED = "com.sentinoid.app.FEATURE_EXPLOITED"
    }
}
