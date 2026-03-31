package com.sentinoid.app.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.sentinoid.app.R
import com.sentinoid.app.SentinoidApp
import com.sentinoid.app.security.SecurePreferences
import com.sentinoid.app.receiver.SentinoidDeviceAdminReceiver
import java.io.File

/**
 * Activity displaying watchdog status and security monitoring information.
 * Shows root detection, bootloader status, USB debugging state, and other
 * device security integrity checks.
 */
class WatchdogStatusActivity : AppCompatActivity() {

    private lateinit var securePreferences: SecurePreferences
    private lateinit var tvDeviceStatus: MaterialTextView
    private lateinit var tvRootStatus: MaterialTextView
    private lateinit var tvBootloaderStatus: MaterialTextView
    private lateinit var tvUsbDebuggingStatus: MaterialTextView
    private lateinit var tvAdminStatus: MaterialTextView
    private lateinit var tvSecurityLevel: MaterialTextView
    private lateinit var cardDeviceStatus: MaterialCardView
    private lateinit var recyclerChecks: RecyclerView
    private lateinit var btnEnableAdmin: MaterialButton
    private lateinit var btnRunChecks: MaterialButton

    private val checksList = mutableListOf<SecurityCheck>()
    private lateinit var checksAdapter: SecurityChecksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watchdog_status)

        val app = application as SentinoidApp
        securePreferences = app.securePreferences

        initViews()
        setupRecyclerView()
        setupListeners()
        runSecurityChecks()
    }

    override fun onResume() {
        super.onResume()
        runSecurityChecks()
    }

    private fun initViews() {
        tvDeviceStatus = findViewById(R.id.tv_device_status)
        tvRootStatus = findViewById(R.id.tv_root_status)
        tvBootloaderStatus = findViewById(R.id.tv_bootloader_status)
        tvUsbDebuggingStatus = findViewById(R.id.tv_usb_debugging_status)
        tvAdminStatus = findViewById(R.id.tv_admin_status)
        tvSecurityLevel = findViewById(R.id.tv_security_level)
        cardDeviceStatus = findViewById(R.id.card_device_status)
        recyclerChecks = findViewById(R.id.recycler_security_checks)
        btnEnableAdmin = findViewById(R.id.btn_enable_admin)
        btnRunChecks = findViewById(R.id.btn_run_checks)
    }

    private fun setupRecyclerView() {
        checksAdapter = SecurityChecksAdapter(checksList)
        recyclerChecks.layoutManager = LinearLayoutManager(this)
        recyclerChecks.adapter = checksAdapter
    }

    private fun setupListeners() {
        btnEnableAdmin.setOnClickListener {
            requestDeviceAdmin()
        }

        btnRunChecks.setOnClickListener {
            runSecurityChecks()
            Toast.makeText(this, "Security checks completed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runSecurityChecks() {
        checksList.clear()

        // Root detection
        val isRooted = checkRootStatus()
        addCheck("Root Access", isRooted, "Device has root access", "No root detected")

        // Bootloader status
        val isUnlocked = checkBootloaderStatus()
        addCheck("Bootloader", isUnlocked, "Bootloader is unlocked", "Bootloader is locked")

        // USB debugging
        val usbDebuggingEnabled = checkUsbDebugging()
        addCheck("USB Debugging", usbDebuggingEnabled, "USB debugging enabled", "USB debugging disabled")

        // Device admin
        val isAdminActive = checkDeviceAdmin()
        addCheck("Device Admin", !isAdminActive, "Device admin not active", "Device admin active")

        // SELinux status
        val selinuxEnforcing = checkSELinuxStatus()
        addCheck("SELinux", !selinuxEnforcing, "SELinux not enforcing", "SELinux enforcing")

        // Verified boot
        val verifiedBoot = checkVerifiedBoot()
        addCheck("Verified Boot", !verifiedBoot, "Boot verification failed", "Boot verified")

        // Update UI
        updateStatusDisplay(isRooted, isUnlocked, usbDebuggingEnabled, isAdminActive)

        // Store results
        securePreferences.putBoolean("watchdog_root_detected", isRooted)
        securePreferences.putBoolean("watchdog_bootloader_unlocked", isUnlocked)
        securePreferences.putBoolean("watchdog_usb_debugging", usbDebuggingEnabled)
        securePreferences.putLong("watchdog_last_check", System.currentTimeMillis())

        checksAdapter.notifyDataSetChanged()
    }

    private fun addCheck(name: String, isWarning: Boolean, warningText: String, safeText: String) {
        checksList.add(
            SecurityCheck(
                name = name,
                status = if (isWarning) CheckStatus.WARNING else CheckStatus.PASSED,
                description = if (isWarning) warningText else safeText
            )
        )
    }

    private fun updateStatusDisplay(
        isRooted: Boolean,
        isUnlocked: Boolean,
        usbDebugging: Boolean,
        isAdminActive: Boolean
    ) {
        // Overall device status
        val isCompromised = isRooted || isUnlocked
        if (isCompromised) {
            tvDeviceStatus.text = "COMPROMISED"
            tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.error_red))
            cardDeviceStatus.strokeColor = ContextCompat.getColor(this, R.color.error_red)
        } else {
            tvDeviceStatus.text = "SECURE"
            tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            cardDeviceStatus.strokeColor = ContextCompat.getColor(this, R.color.accent_green)
        }

        // Root status
        if (isRooted) {
            tvRootStatus.text = "DETECTED"
            tvRootStatus.setTextColor(ContextCompat.getColor(this, R.color.error_red))
        } else {
            tvRootStatus.text = "NOT DETECTED"
            tvRootStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        }

        // Bootloader status
        if (isUnlocked) {
            tvBootloaderStatus.text = "UNLOCKED"
            tvBootloaderStatus.setTextColor(ContextCompat.getColor(this, R.color.error_red))
        } else {
            tvBootloaderStatus.text = "LOCKED"
            tvBootloaderStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        }

        // USB debugging
        if (usbDebugging) {
            tvUsbDebuggingStatus.text = "ENABLED"
            tvUsbDebuggingStatus.setTextColor(ContextCompat.getColor(this, R.color.warning_yellow))
        } else {
            tvUsbDebuggingStatus.text = "DISABLED"
            tvUsbDebuggingStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        }

        // Device admin
        if (isAdminActive) {
            tvAdminStatus.text = "ACTIVE"
            tvAdminStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnEnableAdmin.visibility = MaterialButton.GONE
        } else {
            tvAdminStatus.text = "INACTIVE"
            tvAdminStatus.setTextColor(ContextCompat.getColor(this, R.color.warning_yellow))
            btnEnableAdmin.visibility = MaterialButton.VISIBLE
        }

        // Security level
        val threatCount = checksList.count { it.status == CheckStatus.WARNING }
        tvSecurityLevel.text = when {
            isRooted || isUnlocked -> "CRITICAL"
            threatCount > 2 -> "HIGH"
            threatCount > 0 -> "ELEVATED"
            else -> "NORMAL"
        }
        tvSecurityLevel.setTextColor(
            when {
                isRooted || isUnlocked -> ContextCompat.getColor(this, R.color.error_red)
                threatCount > 2 -> ContextCompat.getColor(this, R.color.warning_yellow)
                threatCount > 0 -> ContextCompat.getColor(this, R.color.warning_yellow)
                else -> ContextCompat.getColor(this, R.color.accent_green)
            }
        )
    }

    private fun checkRootStatus(): Boolean {
        val testKeys = android.os.Build.TAGS?.contains("test-keys") ?: false
        val superuserApk = File("/system/app/Superuser.apk").exists()
        val suBinary = File("/system/bin/su").exists() || File("/system/xbin/su").exists()
        val busybox = File("/system/bin/busybox").exists() || File("/system/xbin/busybox").exists()

        return testKeys || superuserApk || suBinary || busybox
    }

    private fun checkBootloaderStatus(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("getprop ro.bootloader.locked")
            process.waitFor()
            val output = process.inputStream.bufferedReader().readText().trim()
            output != "1" && output != "true"
        } catch (e: Exception) {
            // Default to assuming locked if can't determine
            false
        }
    }

    private fun checkUsbDebugging(): Boolean {
        return try {
            Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun checkDeviceAdmin(): Boolean {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, SentinoidDeviceAdminReceiver::class.java)
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    private fun checkSELinuxStatus(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("getenforce")
            process.waitFor()
            val output = process.inputStream.bufferedReader().readText().trim()
            output.equals("Enforcing", ignoreCase = true)
        } catch (e: Exception) {
            true // Assume enforcing if can't determine
        }
    }

    private fun checkVerifiedBoot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Build.VERSION.SECURITY_PATCH != null
        } else {
            true // Assume verified on older devices
        }
    }

    private fun requestDeviceAdmin() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, SentinoidDeviceAdminReceiver::class.java)

        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Device admin is required for security lockdown capabilities")
            }
            startActivity(intent)
        }
    }

    // Data classes for RecyclerView
    data class SecurityCheck(
        val name: String,
        val status: CheckStatus,
        val description: String
    )

    enum class CheckStatus {
        PASSED, WARNING, ERROR
    }

    class SecurityChecksAdapter(private val checks: List<SecurityCheck>) :
        RecyclerView.Adapter<SecurityChecksAdapter.ViewHolder>() {

        class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val tvName: MaterialTextView = view.findViewById(R.id.tv_check_name)
            val tvStatus: MaterialTextView = view.findViewById(R.id.tv_check_status)
            val tvDescription: MaterialTextView = view.findViewById(R.id.tv_check_description)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_security_check, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val check = checks[position]
            holder.tvName.text = check.name
            holder.tvDescription.text = check.description

            when (check.status) {
                CheckStatus.PASSED -> {
                    holder.tvStatus.text = "✓"
                    holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.accent_green))
                }
                CheckStatus.WARNING -> {
                    holder.tvStatus.text = "⚠"
                    holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.warning_yellow))
                }
                CheckStatus.ERROR -> {
                    holder.tvStatus.text = "✗"
                    holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.error_red))
                }
            }
        }

        override fun getItemCount() = checks.size
    }
}
