package com.sentinoid.app.ui

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
import com.sentinoid.app.security.SecurePreferences
import com.sentinoid.app.service.FPMInterceptorService
import com.sentinoid.shield.FeaturePermissionManager
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

    private lateinit var btnEnableFPM: MaterialButton
    private lateinit var btnDisableFPM: MaterialButton
    private lateinit var btnClearEvents: MaterialButton
    private lateinit var btnTestFPM: MaterialButton

    private val eventsList = mutableListOf<FPMEvent>()
    private lateinit var eventsAdapter: FPMEventsAdapter

    private var blockedRequestCount = 0
    private var lastAlertTime: Long = 0

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
    }

    private fun enableFPMService() {
        if (!checkAccessibilityPermission()) {
            showAccessibilityPermissionDialog()
            return
        }

        val intent = Intent(this, FPMInterceptorService::class.java)
        startService(intent)
        Toast.makeText(this, "FPM Service enabled", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun disableFPMService() {
        val intent = Intent(this, FPMInterceptorService::class.java)
        stopService(intent)
        Toast.makeText(this, "FPM Service disabled", Toast.LENGTH_SHORT).show()
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
            .setTitle("Accessibility Permission Required")
            .setMessage("FPM requires accessibility permission to intercept hardware feature requests. Please enable it in settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStatus() {
        val isServiceActive = checkAccessibilityPermission()
        tvServiceStatus.text = if (isServiceActive) "Active" else "Inactive"
        tvServiceStatus.setTextColor(
            ContextCompat.getColor(this, if (isServiceActive) R.color.accent_green else R.color.error_red),
        )

        tvBlockedCount.text = blockedRequestCount.toString()

        if (lastAlertTime > 0) {
            val timeDiff = System.currentTimeMillis() - lastAlertTime
            val minutes = timeDiff / 60000
            tvLastAlert.text =
                when {
                    minutes < 1 -> "Just now"
                    minutes < 60 -> "$minutes min ago"
                    else -> "${minutes / 60} hr ago"
                }
        } else {
            tvLastAlert.text = "None"
        }

        updateFeatureStatus()
        updateThreatLevel()
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
        when (feature) {
            "camera" -> {
                securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_CAMERA, blocked)
                if (blocked) {
                    featurePermissionManager.mockFeature(feature, false, false)
                } else {
                    featurePermissionManager.unmockFeature(feature)
                }
            }
            "microphone" -> {
                securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_MIC, blocked)
                if (blocked) {
                    featurePermissionManager.mockFeature(feature, false, false)
                } else {
                    featurePermissionManager.unmockFeature(feature)
                }
            }
            "location" -> {
                securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_LOCATION, blocked)
                if (blocked) {
                    featurePermissionManager.mockFeature(feature, false, false)
                } else {
                    featurePermissionManager.unmockFeature(feature)
                }
            }
        }
        Toast.makeText(this, "$feature ${if (blocked) "blocked" else "unblocked"}", Toast.LENGTH_SHORT).show()
        updateFeatureStatus()
    }

    private fun updateThreatLevel() {
        val threatCount = eventsList.count { it.severity == "HIGH" }
        val level =
            when {
                threatCount > 5 -> "CRITICAL"
                threatCount > 2 -> "HIGH"
                threatCount > 0 -> "ELEVATED"
                else -> "NORMAL"
            }

        tvThreatLevel.text = level
        tvThreatLevel.setTextColor(
            ContextCompat.getColor(
                this,
                when (level) {
                    "CRITICAL" -> R.color.error_red
                    "HIGH" -> R.color.warning_yellow
                    "ELEVATED" -> R.color.warning_yellow
                    else -> R.color.accent_green
                },
            ),
        )
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
        registerReceiver(securityEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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
        securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_CAMERA, true)
        securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_MIC, true)
        securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_LOCATION, true)

        featurePermissionManager.mockFeature("camera", false, false)
        featurePermissionManager.mockFeature("microphone", false, false)
        featurePermissionManager.mockFeature("location", false, false)

        featurePermissionManager.jamSensor("camera")
        featurePermissionManager.jamSensor("microphone")
        featurePermissionManager.jamSensor("gps")

        Toast.makeText(this, "Auto-protection: All features blocked", Toast.LENGTH_LONG).show()
    }

    private fun autoBlockFeature(feature: String) {
        when (feature.lowercase()) {
            "camera" -> {
                securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_CAMERA, true)
                featurePermissionManager.mockFeature("camera", false, false)
                featurePermissionManager.jamSensor("camera")
            }
            "microphone" -> {
                securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_MIC, true)
                featurePermissionManager.mockFeature("microphone", false, false)
                featurePermissionManager.jamSensor("microphone")
            }
            "location" -> {
                securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_LOCATION, true)
                featurePermissionManager.mockFeature("location", false, false)
                featurePermissionManager.jamSensor("gps")
            }
        }

        Toast.makeText(this, "Auto-protection: $feature blocked", Toast.LENGTH_LONG).show()
    }

    private fun addEvent(event: FPMEvent) {
        eventsList.add(0, event)
        if (eventsList.size > 100) {
            eventsList.removeAt(eventsList.size - 1)
        }
        eventsAdapter.notifyDataSetChanged()
    }

    private fun clearEvents() {
        eventsList.clear()
        eventsAdapter.notifyDataSetChanged()
        blockedRequestCount = 0
        securePreferences.putInt(PREFS_BLOCKED_COUNT, 0)
        lastAlertTime = 0
        securePreferences.putLong(PREFS_LAST_ALERT_TIME, 0)
        updateStatus()
        Toast.makeText(this, "Event history cleared", Toast.LENGTH_SHORT).show()
    }

    private fun testFPMDetection() {
        Toast.makeText(this, "Testing FPM detection...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            delay(1000)
            val testIntent =
                Intent(ACTION_MALWARE_DETECTED).apply {
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

    private fun showThreatNotification(
        title: String,
        content: String,
    ) {
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
                .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID + (System.currentTimeMillis() % 1000).toInt(), notification)
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
            val tvTime: TextView = view.findViewById(R.id.tv_event_time)
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
            holder.tvType.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    if (event.severity == "HIGH") R.color.error_red else R.color.warning_yellow,
                ),
            )
            holder.tvPackage.text = event.packageName

            val timeDiff = System.currentTimeMillis() - event.timestamp
            val minutes = timeDiff / 60000
            holder.tvTime.text =
                when {
                    minutes < 1 -> "Just now"
                    minutes < 60 -> "$minutes min ago"
                    else -> "${minutes / 60} hr ago"
                }
        }

        override fun getItemCount() = events.size
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "fpm_security_alerts"
        const val NOTIFICATION_ID = 3000

        const val PREFS_AUTO_BLOCK_MALWARE = "fpm_auto_block_malware"
        const val PREFS_AUTO_BLOCK_OVERLAY = "fpm_auto_block_overlay"
        const val PREFS_AUTO_BLOCK_EXPLOIT = "fpm_auto_block_exploit"
        const val PREFS_FPM_NOTIFICATIONS = "fpm_notifications"
        const val PREFS_BLOCKED_COUNT = "fpm_blocked_count"
        const val PREFS_LAST_ALERT_TIME = "fpm_last_alert_time"

        const val ACTION_MALWARE_DETECTED = "com.sentinoid.app.MALWARE_DETECTED"
        const val ACTION_OVERLAY_DETECTED = "com.sentinoid.app.OVERLAY_DETECTED"
        const val ACTION_FEATURE_EXPLOITED = "com.sentinoid.app.FEATURE_EXPLOITED"
    }
}
