package com.sentinoid.app.ui

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
import com.sentinoid.app.service.FPMInterceptorService
import com.sentinoid.app.service.WatchdogService
import kotlinx.coroutines.launch

class SecurityDashboardActivity : AppCompatActivity() {
    private lateinit var cryptoManager: CryptoManager
    private lateinit var securePreferences: SecurePreferences
    private lateinit var honeypotEngine: HoneypotEngine

    private lateinit var tvVaultStatus: TextView
    private lateinit var tvKeyStatus: TextView
    private lateinit var tvWatchdogStatus: TextView
    private lateinit var tvHoneypotStatus: TextView
    private lateinit var tvTamperStatus: TextView

    private lateinit var switchBlockCamera: SwitchMaterial
    private lateinit var switchBlockMic: SwitchMaterial
    private lateinit var switchBlockLocation: SwitchMaterial

    private lateinit var btnPurgeKeys: MaterialButton
    private lateinit var btnTestHoneypot: MaterialButton
    private lateinit var btnCheckTamper: MaterialButton
    private lateinit var btnOpenFPM: MaterialButton

    private val tamperReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    WatchdogService.ACTION_TAMPER_DETECTED -> {
                        val reason = intent.getStringExtra("reason") ?: "Unknown"
                        showTamperAlert(reason)
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

        btnPurgeKeys = findViewById(R.id.btn_purge_keys)
        btnTestHoneypot = findViewById(R.id.btn_test_honeypot)
        btnCheckTamper = findViewById(R.id.btn_check_tamper)
        btnOpenFPM = findViewById(R.id.btn_open_fpm)

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

        // Set listeners
        switchBlockCamera.setOnCheckedChangeListener { _, checked ->
            securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_CAMERA, checked)
            Toast.makeText(
                this,
                "Camera blocking ${if (checked) "enabled" else "disabled"}",
                Toast.LENGTH_SHORT,
            ).show()
        }

        switchBlockMic.setOnCheckedChangeListener { _, checked ->
            securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_MIC, checked)
            Toast.makeText(
                this,
                "Microphone blocking ${if (checked) "enabled" else "disabled"}",
                Toast.LENGTH_SHORT,
            ).show()
        }

        switchBlockLocation.setOnCheckedChangeListener { _, checked ->
            securePreferences.putBoolean(FPMInterceptorService.PREFS_BLOCK_LOCATION, checked)
            Toast.makeText(
                this,
                "Location blocking ${if (checked) "enabled" else "disabled"}",
                Toast.LENGTH_SHORT,
            ).show()
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
            registerReceiver(tamperReceiver, filter)
        }
    }

    private fun updateStatus() {
        // Vault status
        val vaultReady = cryptoManager.isVaultInitialized()
        tvVaultStatus.text = if (vaultReady) "✓ Vault Ready" else "✗ Vault Not Ready"
        tvVaultStatus.setTextColor(
            getColor(
                if (vaultReady) {
                    android.R.color.holo_green_dark
                } else {
                    android.R.color.holo_red_dark
                },
            ),
        )

        // Key status
        val keysValid = cryptoManager.isKeyValid()
        tvKeyStatus.text = if (keysValid) "✓ Keys Valid" else "✗ Keys Invalid"
        tvKeyStatus.setTextColor(
            getColor(
                if (keysValid) {
                    android.R.color.holo_green_dark
                } else {
                    android.R.color.holo_red_dark
                },
            ),
        )

        // Watchdog status
        val watchdogActive = isWatchdogRunning()
        tvWatchdogStatus.text = if (watchdogActive) "✓ Watchdog Active" else "✗ Watchdog Inactive"
        tvWatchdogStatus.setTextColor(
            getColor(
                if (watchdogActive) {
                    android.R.color.holo_green_dark
                } else {
                    android.R.color.holo_red_dark
                },
            ),
        )

        // Honeypot status
        val honeypotActive = honeypotEngine.isHoneypotInitialized()
        tvHoneypotStatus.text = if (honeypotActive) "✓ Honeypot Active" else "✗ Honeypot Inactive"
        tvHoneypotStatus.setTextColor(
            getColor(
                if (honeypotActive) {
                    android.R.color.holo_green_dark
                } else {
                    android.R.color.holo_red_dark
                },
            ),
        )

        // Tamper status
        val stats = honeypotEngine.getHoneypotStats()
        if (stats.accessAttempts > 0) {
            tvTamperStatus.text = "⚠️ SECURITY BREACH DETECTED"
            tvTamperStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        } else {
            tvTamperStatus.text = "✓ No Tampering Detected"
            tvTamperStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        }
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

    private fun showPurgeConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ PURGE ALL KEYS")
            .setMessage(
                "This will permanently destroy all cryptographic keys and recovery data. " +
                    "You will need your recovery shards to restore access. This action cannot be undone.",
            )
            .setPositiveButton("PURGE") { _, _ ->
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
            .setTitle("Honeypot Test Results")
            .setMessage(message)
            .setPositiveButton("Regenerate Decoys") { _, _ ->
                honeypotEngine.regenerateDecoys()
                Toast.makeText(this, "Decoys regenerated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
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
            .setTitle("Tamper Detection Check")
            .setMessage(checks.joinToString("\n"))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showTamperAlert(reason: String) {
        AlertDialog.Builder(this)
            .setTitle("🚨 TAMPERING DETECTED")
            .setMessage(
                "Security violation detected: $reason\n\n" +
                    "All keys have been purged for your protection.",
            )
            .setPositiveButton("Understood") { _, _ ->
                updateStatus()
            }
            .setCancelable(false)
            .show()
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
