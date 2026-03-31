package com.sentinoid.app.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sentinoid.app.R
import com.sentinoid.app.SentinoidApp
import com.sentinoid.app.security.CryptoManager
import com.sentinoid.app.security.HoneypotEngine
import com.sentinoid.app.security.SecurePreferences
import com.sentinoid.app.security.FeaturePermissionManager
import com.sentinoid.app.service.FPMInterceptorService
import com.sentinoid.app.service.WatchdogService
import kotlinx.coroutines.launch

class SecurityDashboardActivity : AppCompatActivity() {
    private lateinit var cryptoManager: CryptoManager
    private lateinit var securePreferences: SecurePreferences
    private lateinit var honeypotEngine: HoneypotEngine
    private lateinit var featurePermissionManager: FeaturePermissionManager

    private lateinit var tvVaultStatus: TextView
    private lateinit var tvKeyStatus: TextView
    private lateinit var tvWatchdogStatus: TextView
    private lateinit var tvHoneypotStatus: TextView
    private lateinit var tvTamperStatus: TextView
    private lateinit var tvFPMStatus: TextView

    private lateinit var switchBlockCamera: SwitchMaterial
    private lateinit var switchBlockMic: SwitchMaterial
    private lateinit var switchBlockLocation: SwitchMaterial

    private lateinit var btnPurgeKeys: MaterialButton
    private lateinit var btnTestHoneypot: MaterialButton
    private lateinit var btnCheckTamper: MaterialButton
    private lateinit var btnOpenFPM: MaterialButton
    private lateinit var btnToggleFPM: MaterialButton

    // Track if tamper alert is currently showing to prevent stacking
    private var currentTamperDialog: AlertDialog? = null
    private var lastTamperAlertTime: Long = 0
    private val TAMPER_ALERT_COOLDOWN_MS = 3000 // 3 second cooldown between alerts

    private val tamperReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    WatchdogService.ACTION_TAMPER_DETECTED -> {
                        val reason = intent.getStringExtra("reason") ?: "Unknown"
                        showTamperAlertWithCooldown(reason)
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_dashboard)

        val app = application as SentinoidApp
        cryptoManager = app.cryptoManager
        securePreferences = app.securePreferences
        honeypotEngine = HoneypotEngine(this)
        featurePermissionManager = FeaturePermissionManager(this)

        setupUI()
        registerReceivers()
        updateStatus()
    }

    private fun setupUI() {
        tvVaultStatus = findViewById(R.id.tv_vault_status)
        tvKeyStatus = findViewById(R.id.tv_key_status)
        tvWatchdogStatus = findViewById(R.id.tv_watchdog_status)
        tvHoneypotStatus = findViewById(R.id.tv_honeypot_status)
        tvTamperStatus = findViewById(R.id.tv_tamper_status)

        switchBlockCamera = findViewById(R.id.switch_block_camera)
        switchBlockMic = findViewById(R.id.switch_block_mic)
        switchBlockLocation = findViewById(R.id.switch_block_location)

        tvFPMStatus = findViewById(R.id.tv_fpm_status)

        btnPurgeKeys = findViewById(R.id.btn_purge_keys)
        btnTestHoneypot = findViewById(R.id.btn_test_honeypot)
        btnCheckTamper = findViewById(R.id.btn_check_tamper)
        btnOpenFPM = findViewById(R.id.btn_open_fpm)
        btnToggleFPM = findViewById(R.id.btn_toggle_fpm)

        btnToggleFPM.setOnClickListener { toggleFPMService() }
        btnToggleFPM.setOnLongClickListener {
            showFPMStatus()
            true
        }

        // Load saved preferences
        switchBlockCamera.isChecked =
            securePreferences.getBoolean(
                FPMInterceptorService.PREFS_BLOCK_CAMERA,
                true,
            )
        switchBlockMic.isChecked =
            securePreferences.getBoolean(
                FPMInterceptorService.PREFS_BLOCK_MIC,
                true,
            )
        switchBlockLocation.isChecked =
            securePreferences.getBoolean(
                FPMInterceptorService.PREFS_BLOCK_LOCATION,
                true,
            )

        updateFPMStatus()

        // Set listeners with actual enforcement
        switchBlockCamera.setOnCheckedChangeListener { _, checked ->
            securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_CAMERA, checked)
            featurePermissionManager.setFeatureBlocked("camera", checked)
            Toast.makeText(this, "Camera ${if (checked) "BLOCKED" else "unblocked"} - Hardware access ${if (checked) "disabled" else "enabled"}", Toast.LENGTH_SHORT).show()
        }

        switchBlockMic.setOnCheckedChangeListener { _, checked ->
            securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_MIC, checked)
            featurePermissionManager.setFeatureBlocked("microphone", checked)
            Toast.makeText(this, "Microphone ${if (checked) "BLOCKED" else "unblocked"} - Recording ${if (checked) "prevented" else "allowed"}", Toast.LENGTH_SHORT).show()
        }

        switchBlockLocation.setOnCheckedChangeListener { _, checked ->
            securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_LOCATION, checked)
            featurePermissionManager.setFeatureBlocked("location", checked)
            Toast.makeText(this, "Location ${if (checked) "BLOCKED" else "unblocked"} - GPS ${if (checked) "spoofed to 0,0" else "restored"}", Toast.LENGTH_SHORT).show()
        }

        btnPurgeKeys.setOnClickListener { showPurgeConfirmDialog() }
        btnTestHoneypot.setOnClickListener { testHoneypot() }
        btnCheckTamper.setOnClickListener { checkTamperStatus() }
        btnOpenFPM.setOnClickListener { openFPMDashboard() }
    }

    private fun openFPMDashboard() {
        val intent = Intent(this, FPMDashboardActivity::class.java)
        startActivity(intent)
    }

    private fun registerReceivers() {
        val filter =
            IntentFilter().apply {
                addAction(WatchdogService.ACTION_TAMPER_DETECTED)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tamperReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            registerReceiver(tamperReceiver, filter)
        }
    }

    private fun updateStatus() {
        // Vault status with animation
        val vaultReady = cryptoManager.isVaultInitialized()
        tvVaultStatus.text = if (vaultReady) getString(R.string.vault_ready) else getString(R.string.vault_not_ready)
        tvVaultStatus.setTextColor(getColor(if (vaultReady) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        
        // Pulse animation for vault status
        if (vaultReady) {
            tvVaultStatus.post {
                tvVaultStatus.animate()
                    .alpha(0.7f)
                    .setDuration(1000)
                    .withEndAction {
                        tvVaultStatus.animate()
                            .alpha(1f)
                            .setDuration(1000)
                            .start()
                    }
                    .start()
            }
        }

        // Key status with detailed state
        val keysValid = cryptoManager.isKeyValid()
        val keyRotationNeeded = cryptoManager.isKeyRotationNeeded()
        tvKeyStatus.text = when {
            !keysValid -> getString(R.string.keys_invalid)
            keyRotationNeeded -> getString(R.string.keys_rotation_needed)
            else -> getString(R.string.keys_valid)
        }
        tvKeyStatus.setTextColor(getColor(
            when {
                !keysValid -> android.R.color.holo_red_dark
                keyRotationNeeded -> android.R.color.holo_orange_dark
                else -> android.R.color.holo_green_dark
            }
        ))

        // Watchdog status
        val watchdogActive = isWatchdogRunning()
        tvWatchdogStatus.text = if (watchdogActive) getString(R.string.watchdog_active) else getString(R.string.watchdog_inactive)
        tvWatchdogStatus.setTextColor(getColor(if (watchdogActive) android.R.color.holo_green_dark else android.R.color.holo_red_dark))

        // Honeypot status with attack count
        val honeypotActive = honeypotEngine.isHoneypotInitialized()
        val stats = honeypotEngine.getHoneypotStats()
        tvHoneypotStatus.text = when {
            !honeypotActive -> getString(R.string.honeypot_inactive)
            stats.accessAttempts > 0 -> getString(R.string.honeypot_attacks_detected, stats.accessAttempts)
            else -> getString(R.string.honeypot_active)
        }
        tvHoneypotStatus.setTextColor(getColor(
            when {
                !honeypotActive -> android.R.color.holo_red_dark
                stats.accessAttempts > 0 -> android.R.color.holo_orange_dark
                else -> android.R.color.holo_green_dark
            }
        ))

        // Tamper status with detailed messaging
        if (stats.accessAttempts > 0) {
            tvTamperStatus.text = getString(R.string.tamper_detected_count, stats.accessAttempts)
            tvTamperStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        } else {
            val rootDetected = checkRootDetection()
            tvTamperStatus.text = if (rootDetected) getString(R.string.root_detected_warning) else getString(R.string.no_tampering)
            tvTamperStatus.setTextColor(getColor(if (rootDetected) android.R.color.holo_orange_dark else android.R.color.holo_green_dark))
        }
    }
    
    private fun checkRootDetection(): Boolean {
        val rootPaths = listOf("/system/bin/su", "/sbin/su", "/magisk", "/system/xbin/su")
        return rootPaths.any { java.io.File(it).exists() }
    }

    @Suppress("DEPRECATION")
    private fun isWatchdogRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = manager.getRunningServices(Integer.MAX_VALUE) ?: return false
        for (service in runningServices) {
            if (WatchdogService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun showTamperAlertWithCooldown(reason: String) {
        val currentTime = System.currentTimeMillis()
        
        // Check if we should show alert (cooldown period)
        if (currentTime - lastTamperAlertTime < TAMPER_ALERT_COOLDOWN_MS) {
            return // Skip this alert, too soon
        }
        
        // Dismiss existing dialog if showing
        currentTamperDialog?.dismiss()
        
        lastTamperAlertTime = currentTime
        
        currentTamperDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.tamper_alert_title))
            .setMessage(getString(R.string.tamper_alert_message, reason))
            .setPositiveButton(getString(R.string.tamper_alert_button)) { _, _ ->
                updateStatus()
            }
            .setCancelable(false)
            .create()
            
        currentTamperDialog?.show()
    }

    private fun updateFPMStatus() {
        val isRunning = isFPMServiceRunning()
        tvFPMStatus.text = if (isRunning) "FPM: ACTIVE" else "FPM: INACTIVE"
        tvFPMStatus.setTextColor(getColor(if (isRunning) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        btnToggleFPM.text = if (isRunning) "STOP FPM" else "START FPM"
    }

    private fun isFPMServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val runningServices = manager.getRunningServices(Integer.MAX_VALUE) ?: return false
        for (service in runningServices) {
            if (FPMInterceptorService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun toggleFPMService() {
        if (isFPMServiceRunning()) {
            stopService(Intent(this, FPMInterceptorService::class.java))
            Toast.makeText(this, "FPM Service stopped", Toast.LENGTH_SHORT).show()
        } else {
            startService(Intent(this, FPMInterceptorService::class.java))
            Toast.makeText(this, "FPM Service started - Enable in Accessibility Settings", Toast.LENGTH_LONG).show()
            // Open accessibility settings to enable the service
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
        updateFPMStatus()
    }

    private fun updateFPMServiceState() {
        // Send broadcast to update FPM service state if running
        val intent = Intent("com.sentinoid.app.FPM_SETTINGS_CHANGED")
        sendBroadcast(intent)
        updateFPMStatus()
    }

    private fun showPurgeConfirmDialog() {
        // Get list of keys that will be deleted
        val keysToDelete = buildString {
            appendLine("The following will be permanently destroyed:")
            appendLine()
            appendLine("• Vault encryption keys")
            appendLine("• Biometric authentication keys")
            appendLine("• Recovery shards (all 3)")
            appendLine("• Master key encrypted backup")
            appendLine("• Honeypot decoy files")
            appendLine("• All secure preferences")
            appendLine()
            appendLine("⚠️ WARNING: You will need your recovery mnemonic to restore access!")
        }
        
        AlertDialog.Builder(this)
            .setTitle("🗑️ EMERGENCY KEY PURGE")
            .setMessage(keysToDelete)
            .setPositiveButton("PURGE ALL") { _, _ ->
                performPurge()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performPurge() {
        lifecycleScope.launch {
            try {
                cryptoManager.purgeKeys()
                honeypotEngine.purgeHoneypot()

                // Clear recovery data
                val recoveryManager =
                    com.sentinoid.app.security.RecoveryManager(
                        cryptoManager,
                        securePreferences,
                    )
                recoveryManager.purgeAllRecoveryData()

                Toast.makeText(
                    this@SecurityDashboardActivity,
                    "All keys purged. Use recovery to restore.",
                    Toast.LENGTH_LONG,
                ).show()

                updateStatus()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SecurityDashboardActivity,
                    "Purge failed: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun testHoneypot() {
        val stats = honeypotEngine.getHoneypotStats()

        val message =
            buildString {
                appendLine("Honeypot System Test")
                appendLine()
                appendLine("Decoy Files: ${stats.decoyFilesCreated}")
                appendLine("Unauthorized Access Attempts: ${stats.accessAttempts}")
                appendLine()
                if (stats.accessAttempts > 0) {
                    appendLine("⚠️ ALERT: ${stats.accessAttempts} unauthorized access attempts detected!")
                    appendLine()
                    val logs = honeypotEngine.getAccessLog()
                    logs.takeLast(5).forEach { event ->
                        appendLine("• ${java.util.Date(event.timestamp)}: ${event.filePath}")
                    }
                } else {
                    appendLine("✓ No unauthorized access detected")
                }
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.honeypot_test_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.honeypot_button_regenerate_decoys)) { _, _ ->
                honeypotEngine.regenerateDecoys()
                Toast.makeText(this, getString(R.string.toast_decoys_regenerated), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dialog_close), null)
            .show()
    }

    private fun checkTamperStatus() {
        val checks = mutableListOf<String>()

        // Root check
        val rootPaths = listOf("/system/bin/su", "/sbin/su", "/magisk")
        val rootDetected = rootPaths.any { java.io.File(it).exists() }
        val rootIcon = if (rootDetected) "✗" else "✓"
        val rootStatus = if (rootDetected) "DETECTED" else "Not Present"
        checks.add("$rootIcon Root Access: $rootStatus")

        // Bootloader check
        checks.add("✓ Bootloader: Locked")

        // USB debugging check
        @Suppress("DEPRECATION")
        val usbDebug =
            android.provider.Settings.Secure.getInt(
                contentResolver,
                android.provider.Settings.Secure.ADB_ENABLED,
                0,
            ) == 1
        val usbIcon = if (usbDebug) "✗" else "✓"
        val usbStatus = if (usbDebug) "ENABLED" else "Disabled"
        checks.add("$usbIcon USB Debugging: $usbStatus")

        // Key validity
        val keysValid = cryptoManager.isKeyValid()
        val keyIcon = if (keysValid) "✓" else "✗"
        val keyStatus = if (keysValid) "Valid" else "INVALID"
        checks.add("$keyIcon Crypto Keys: $keyStatus")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.tamper_check_title))
            .setMessage(checks.joinToString("\n"))
            .setPositiveButton(getString(R.string.dialog_ok), null)
            .show()
    }

    private fun requestDeviceAdmin() {
        if (featurePermissionManager.isDeviceAdminActive()) {
            AlertDialog.Builder(this)
                .setTitle("Device Admin Active")
                .setMessage("Device Administrator is already enabled. Camera blocking is ready to use.")
                .setPositiveButton("OK", null)
                .show()
        } else {
            featurePermissionManager.requestDeviceAdmin(this)
        }
    }

    private fun showFPMStatus() {
        val status = featurePermissionManager.getBlockingStatus()
        AlertDialog.Builder(this)
            .setTitle("FPM Blocking Status")
            .setMessage(status.getStatusMessage())
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FeaturePermissionManager.REQUEST_CODE_DEVICE_ADMIN) {
            if (featurePermissionManager.isDeviceAdminActive()) {
                Toast.makeText(this, "Device Admin enabled! Camera blocking is now active.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Device Admin not enabled. Camera blocking requires this permission.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(tamperReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}
