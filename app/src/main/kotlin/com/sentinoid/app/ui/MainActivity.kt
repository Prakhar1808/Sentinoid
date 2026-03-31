package com.sentinoid.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.sentinoid.app.R
import com.sentinoid.app.SentinoidApp
import com.sentinoid.app.atmosphere.AtmosphereManager
import com.sentinoid.app.bridge.BridgeModeManager
import com.sentinoid.app.security.ActivityLogger
import com.sentinoid.app.security.AntiDebugDetection
import com.sentinoid.app.security.HoneypotEngine
import com.sentinoid.app.security.RecoveryManager
import com.sentinoid.app.security.ScreenshotPrevention
import com.sentinoid.app.service.WatchdogService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private lateinit var recoveryManager: RecoveryManager
    private lateinit var honeypotEngine: HoneypotEngine
    private lateinit var bridgeManager: BridgeModeManager
    private lateinit var atmosphereManager: AtmosphereManager
    private lateinit var screenshotPrevention: ScreenshotPrevention
    private lateinit var antiDebugDetection: AntiDebugDetection

    private lateinit var cardStatus: MaterialCardView
    private lateinit var cardRecovery: MaterialCardView
    private lateinit var cardBridge: MaterialCardView
    private lateinit var cardDashboard: MaterialCardView
    private lateinit var cardFPM: MaterialCardView
    private lateinit var cardHoneypot: MaterialCardView
    private lateinit var cardAtmosphere: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as SentinoidApp
        recoveryManager = RecoveryManager(app.cryptoManager, app.securePreferences)
        honeypotEngine = HoneypotEngine(this)
        bridgeManager = BridgeModeManager(this)
        atmosphereManager = AtmosphereManager(this)
        screenshotPrevention = ScreenshotPrevention(this)
        antiDebugDetection = AntiDebugDetection(this)
        
        // Apply screenshot prevention
        screenshotPrevention.configureSecureActivity(this, strict = true)
        
        // Perform anti-debug check
        performSecurityCheck()

        executor = ContextCompat.getMainExecutor(this)

        setupBiometricAuth()
        setupUI()
        initializeServices()
        observeBridgeState()
    }

    private fun setupBiometricAuth() {
        biometricPrompt =
            BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onAuthSuccess?.invoke()
                        onAuthSuccess = null
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        showAuthError(getString(R.string.auth_failed))
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        super.onAuthenticationError(errorCode, errString)
                        when (errorCode) {
                            BiometricPrompt.ERROR_LOCKOUT -> showAuthError(getString(R.string.auth_lockout))
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> showAuthError(getString(R.string.auth_lockout_permanent))
                            BiometricPrompt.ERROR_NO_BIOMETRICS -> showBiometricSetupWarning()
                            else -> showAuthError(errString.toString())
                        }
                    }
                },
            )

        promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setDescription(getString(R.string.biometric_description))
                .setNegativeButtonText(getString(R.string.biometric_negative))
                .setConfirmationRequired(true)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
    }
    
    private var onAuthSuccess: (() -> Unit)? = null
    
    private fun showAuthError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.auth_error_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.dialog_retry)) { _, _ ->
                biometricPrompt.authenticate(promptInfo)
            }
            .setNegativeButton(getString(R.string.dialog_exit)) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupUI() {
        cardStatus = findViewById(R.id.card_status)
        cardRecovery = findViewById(R.id.card_recovery)
        cardBridge = findViewById(R.id.card_bridge)
        cardDashboard = findViewById(R.id.card_dashboard)
        cardFPM = findViewById(R.id.card_fpm)
        cardHoneypot = findViewById(R.id.card_honeypot)
        cardAtmosphere = findViewById(R.id.card_atmosphere)

        cardStatus.setOnClickListener { showStatusDialog() }
        cardRecovery.setOnClickListener { navigateToRecovery() }
        cardBridge.setOnClickListener { toggleBridgeMode() }
        cardDashboard.setOnClickListener { navigateToDashboard() }
        cardFPM.setOnClickListener { navigateToFPM() }
        cardHoneypot.setOnClickListener { showHoneypotStats() }
        cardAtmosphere.setOnClickListener { showAtmosphereDialog() }

        updateStatusCard()
        updateAtmosphereCard()
    }

    private fun initializeServices() {
        // Start watchdog service
        startService(
            Intent(this, WatchdogService::class.java).apply {
                action = WatchdogService.ACTION_START
            },
        )

        // Initialize honeypot
        honeypotEngine.initializeHoneypot()

        // Check if recovery is set up
        if (!recoveryManager.isRecoverySetup()) {
            showSetupRecoveryDialog()
        }
        
        // Schedule periodic anti-debug checks
        scheduleAntiDebugChecks()
    }
    
    private fun performSecurityCheck() {
        val status = antiDebugDetection.checkDebugging()
        if (status.threatLevel >= AntiDebugDetection.ThreatLevel.HIGH) {
            // Handle high threat - could lock down or alert
            ActivityLogger.getInstance(this).log(
                ActivityLogger.CATEGORY_TAMPER,
                "Debug/ tampering detected: ${status.detectedMethods}",
                ActivityLogger.SEVERITY_CRITICAL
            )
        }
    }
    
    private fun scheduleAntiDebugChecks() {
        lifecycleScope.launch {
            while (isActive) {
                delay(30000) // Check every 30 seconds
                performSecurityCheck()
            }
        }
    }

    private fun observeBridgeState() {
        lifecycleScope.launch {
            bridgeManager.connectionState.collectLatest { state ->
                updateBridgeCard(state)
            }
        }
    }

    private fun updateStatusCard() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate =
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            )

        // Update UI based on biometric availability
        when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // All good
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                showBiometricSetupWarning()
            }
        }
    }

    private fun updateBridgeCard(state: BridgeModeManager.BridgeState) {
        when (state) {
            is BridgeModeManager.BridgeState.Connected -> {
                cardBridge.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success_green))
            }
            is BridgeModeManager.BridgeState.Connecting -> {
                cardBridge.setCardBackgroundColor(ContextCompat.getColor(this, R.color.warning_yellow))
            }
            is BridgeModeManager.BridgeState.Error -> {
                cardBridge.setCardBackgroundColor(ContextCompat.getColor(this, R.color.error_red))
            }
            else -> {
                cardBridge.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_background))
            }
        }
    }

    private fun showStatusDialog() {
        val app = application as SentinoidApp
        val status = atmosphereManager.getStatus()

        val message =
            buildString {
                appendLine("Sentinoid Status")
                appendLine()
                appendLine("Atmosphere: ${status.atmosphereName}")
                appendLine("Vault: ${if (app.cryptoManager.isVaultInitialized()) "Initialized" else "Not Ready"}")
                appendLine("Keys: ${if (app.cryptoManager.isKeyValid()) "Valid" else "Invalid"}")
                appendLine("Recovery: ${if (recoveryManager.isRecoverySetup()) "Configured" else "Not Set Up"}")
                appendLine("Honeypot: ${if (honeypotEngine.isHoneypotInitialized()) "Active" else "Inactive"}")

                if (status.isPremium) {
                    appendLine()
                    appendLine("Premium Features:")
                    status.features.take(3).forEach {
                        appendLine("  • $it")
                    }
                }
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_security_status_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.dialog_ok), null)
            .show()
    }

    private fun navigateToRecovery() {
        startActivity(Intent(this, RecoveryActivity::class.java))
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, SecurityDashboardActivity::class.java))
    }

    private fun navigateToFPM() {
        startActivity(Intent(this, FPMDashboardActivity::class.java))
    }

    private fun toggleBridgeMode() {
        val devices = bridgeManager.findCompatibleDevices()
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_no_usb_device_title))
                .setMessage(getString(R.string.bridge_no_devices))
                .setPositiveButton(getString(R.string.dialog_ok), null)
                .show()
            return
        }

        val deviceNames = devices.map { it.productName ?: getString(R.string.bridge_unknown_device) }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_select_bridge_device_title))
            .setItems(deviceNames) { _, which ->
                val device = devices[which]
                if (bridgeManager.requestUsbPermission(device)) {
                    bridgeManager.connectToDevice(device)
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showHoneypotStats() {
        startActivity(Intent(this, HoneypotStatusActivity::class.java))
    }

    private fun showSetupRecoveryDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_setup_recovery_title))
            .setMessage(getString(R.string.recovery_setup_prompt))
            .setPositiveButton(getString(R.string.recovery_button_setup)) { _, _ ->
                navigateToRecovery()
            }
            .setNegativeButton(getString(R.string.recovery_button_later)) { _, _ -> }
            .setCancelable(false)
            .show()
    }

    private fun showBiometricSetupWarning() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.biometric_required))
            .setMessage(getString(R.string.biometric_not_enrolled))
            .setPositiveButton(getString(R.string.biometric_open_settings)) { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
            }
            .setNegativeButton(getString(R.string.dialog_later), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Verify biometric still available
        biometricPrompt.authenticate(promptInfo)
    }

    override fun onDestroy() {
        super.onDestroy()
        bridgeManager.cleanup()
        atmosphereManager.cleanup()
    }

    private fun updateAtmosphereCard() {
        val status = atmosphereManager.getStatus()

        cardAtmosphere?.let { card ->
            when {
                status.isPremium -> {
                    // Premium atmosphere - ULTRA or MOBILE-A
                    card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.accent_blue))
                }
                else -> {
                    // LITE atmosphere
                    card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_background))
                }
            }
        }
    }

    private fun showAtmosphereDialog() {
        val status = atmosphereManager.getStatus()

        val message =
            buildString {
                appendLine("${status.atmosphereName}")
                appendLine()
                appendLine("Hardware Capabilities:")
                appendLine("  NPU: ${if (status.hardwareCapabilities.hasNpu) "Available" else "Not Available"}")
                appendLine("  AMD Accelerator: ${if (status.hardwareCapabilities.hasAmdAccelerator) "Yes" else "No"}")
                appendLine("  SEV-SNP: ${if (status.hardwareCapabilities.hasSevSnp) "Enabled" else "Not Available"}")
                appendLine("  RDNA GPU: ${if (status.hardwareCapabilities.hasRdna) "Yes" else "No"}")
                appendLine()
                appendLine("Available Features:")
                status.features.forEach {
                    appendLine("  • $it")
                }

                if (status.isPremium) {
                    appendLine()
                    appendLine("Module Status:")
                    appendLine("  Accelerator: ${if (status.moduleStatus.acceleratorAvailable) "Ready" else "Offline"}")
                    appendLine("  Side-Channel Jamming: ${if (status.moduleStatus.sideChannelJamming) "Active" else "Inactive"}")
                    if (status.currentAtmosphere.name == "MOBILE_A") {
                        appendLine("  RDNA Obfuscation: ${if (status.moduleStatus.rdnaObfuscation) "Active" else "Inactive"}")
                        appendLine("  Gait Lock: ${if (status.moduleStatus.gaitLock) "Active" else "Inactive"}")
                    }
                }
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_atmosphere_details_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.dialog_ok), null)
            .show()
    }
}
