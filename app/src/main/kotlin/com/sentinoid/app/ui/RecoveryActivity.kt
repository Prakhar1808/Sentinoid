package com.sentinoid.app.ui

import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.sentinoid.app.R
import com.sentinoid.app.SentinoidApp
import com.sentinoid.app.security.ActivityLogger
import com.sentinoid.app.security.RecoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyStoreException
import android.util.Log
import java.util.concurrent.Executor

class RecoveryActivity : AppCompatActivity() {
    private lateinit var recoveryManager: RecoveryManager
    private lateinit var executor: Executor
    private var biometricPrompt: BiometricPrompt? = null
    private var promptInfo: BiometricPrompt.PromptInfo? = null
    private lateinit var tvStatus: TextView
    private lateinit var recyclerShards: RecyclerView
    private lateinit var btnSetup: MaterialButton
    private lateinit var btnRecover: MaterialButton
    private lateinit var btnVerify: MaterialButton
    private lateinit var shardAdapter: ShardAdapter

    private var currentShards: List<String> = emptyList()
    private var pendingShardsForRecovery: List<String>? = null
    private var pendingWordsForRecovery: List<String>? = null
    private var isBiometricAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recovery)

        val app = application as SentinoidApp
        recoveryManager = RecoveryManager(app.cryptoManager, app.securePreferences)

        executor = ContextCompat.getMainExecutor(this)
        setupBiometricAuth()
        setupUI()
        updateUI()
    }

    private fun setupBiometricAuth() {
        // Check if biometric is available
        val biometricManager = BiometricManager.from(this)
        isBiometricAvailable = when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d("Recovery", "Biometric authentication available")
                true
            }
            else -> {
                Log.d("Recovery", "Biometric authentication not available")
                false
            }
        }
        
        if (isBiometricAvailable) {
            biometricPrompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        // Retry the pending operation after successful authentication
                        pendingShardsForRecovery?.let { shards ->
                            performShardRecovery(shards)
                            pendingShardsForRecovery = null
                        }
                        pendingWordsForRecovery?.let { words ->
                            performMnemonicRecovery(words)
                            pendingWordsForRecovery = null
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(
                            this@RecoveryActivity,
                            "Authentication failed",
                            Toast.LENGTH_SHORT,
                        ).show()
                        pendingShardsForRecovery = null
                        pendingWordsForRecovery = null
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        super.onAuthenticationError(errorCode, errString)
                        // Don't show error for user cancellation
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            Toast.makeText(
                                this@RecoveryActivity,
                                "Authentication error: $errString",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        pendingShardsForRecovery = null
                        pendingWordsForRecovery = null
                    }
                },
            )

            promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sentinoid Recovery")
                .setSubtitle("Authenticate to access recovery functions")
                .setNegativeButtonText("Use Without Biometric")
                .setConfirmationRequired(true)
                .build()
        }
    }

    private fun authenticateAndExecute(operation: () -> Unit) {
        try {
            operation()
        } catch (e: Exception) {
            when (e) {
                is android.security.keystore.UserNotAuthenticatedException,
                is KeyPermanentlyInvalidatedException,
                is KeyStoreException,
                -> {
                    // If biometric is available, prompt for it
                    // Otherwise just proceed with the operation
                    if (isBiometricAvailable && biometricPrompt != null && promptInfo != null) {
                        biometricPrompt?.authenticate(promptInfo!!)
                    } else {
                        // No biometric available, retry operation directly
                        Log.d("Recovery", "No biometric available, proceeding without authentication")
                        try {
                            operation()
                        } catch (e2: Exception) {
                            // If it fails again, show error but don't block
                            Toast.makeText(
                                this@RecoveryActivity,
                                "Warning: Operating without biometric protection",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.e("Recovery", "Operation failed without biometric: ${e2.message}")
                        }
                    }
                }
                else -> throw e
            }
        }
    }

    private fun setupUI() {
        tvStatus = findViewById(R.id.tv_recovery_status)
        recyclerShards = findViewById(R.id.recycler_shards)
        btnSetup = findViewById(R.id.btn_setup_recovery)
        btnRecover = findViewById(R.id.btn_recover)
        btnVerify = findViewById(R.id.btn_verify)

        shardAdapter = ShardAdapter()
        recyclerShards.layoutManager = LinearLayoutManager(this)
        recyclerShards.adapter = shardAdapter

        btnSetup.setOnClickListener { setupRecovery() }
        btnRecover.setOnClickListener { showRecoverDialog() }
        btnVerify.setOnClickListener { showVerifyDialog() }
    }

    private fun updateUI() {
        if (recoveryManager.isRecoverySetup()) {
            tvStatus.text = getString(R.string.status_configured)
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnSetup.text = getString(R.string.btn_resetup_recovery)
            btnSetup.icon = ContextCompat.getDrawable(this, R.drawable.ic_refresh)
        } else {
            tvStatus.text = getString(R.string.status_not_configured)
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnSetup.text = getString(R.string.btn_setup_recovery)
            btnSetup.icon = ContextCompat.getDrawable(this, R.drawable.ic_shield)
        }

        loadShards()
    }

    private fun loadShards() {
        authenticateAndExecute {
            lifecycleScope.launch(Dispatchers.IO) {
                val shards =
                    try {
                        (1..3).mapNotNull { index ->
                            recoveryManager.getStoredShard(index)
                        }
                    } catch (e: Exception) {
                        emptyList<String>()
                    }

                withContext(Dispatchers.Main) {
                    currentShards = shards
                    shardAdapter.submitList(shards)

                    // Show empty state message if no shards
                    if (shards.isEmpty() && recoveryManager.isRecoverySetup()) {
                        tvStatus.text = "Shards encrypted - authenticate to view"
                    }
                }
            }
        }
    }

    private fun setupRecovery() {
        AlertDialog.Builder(this)
            .setTitle("Setup Recovery")
            .setMessage(
                "This will generate a 24-word mnemonic and split it into 3 shards (2-of-3 required for recovery). " +
                    "Write down your shards and store them in separate secure locations.",
            )
            .setPositiveButton("Continue") { _, _ ->
                performSetup()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performSetup() {
        authenticateAndExecute {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val setup = recoveryManager.setupRecovery()

                    withContext(Dispatchers.Main) {
                        showSetupResults(setup.mnemonicWords, setup.shardStrings)
                        updateUI()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@RecoveryActivity,
                            "Setup failed: ${e.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    private fun showSetupResults(
        words: List<String>,
        shards: List<String>,
    ) {
        val message =
            buildString {
                appendLine("╔════════════════════════════════════╗")
                appendLine("║     WRITE DOWN THESE PHRASES       ║")
                appendLine("║   Store in 3 separate locations    ║")
                appendLine("╚════════════════════════════════════╝")
                appendLine()
                appendLine("【 MNEMONIC WORDS (24 words) 】")
                appendLine("━".repeat(36))
                words.chunked(6).forEachIndexed { index, chunk ->
                    appendLine("${index * 6 + 1}-${index * 6 + chunk.size}: ${chunk.joinToString(" ")}")
                }
                appendLine()
                appendLine("【 SHARDS - 2 of 3 required 】")
                appendLine("━".repeat(36))
                shards.forEachIndexed { index, shard ->
                    appendLine()
                    appendLine("Shard ${index + 1}:")
                    appendLine("─".repeat(36))
                    // Format shard in groups for readability
                    val formatted = shard.chunked(4).joinToString(" ")
                    appendLine(formatted)
                }
                appendLine()
                appendLine("⚠️ IMPORTANT:")
                appendLine("• Anyone with 2 shards can recover your vault")
                appendLine("• Store each shard in a different secure location")
                appendLine("• Do not store all shards on this device")
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.recovery_save_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.recovery_saved_confirm)) { _, _ ->
                // Mark as saved in logs
                ActivityLogger.getInstance(this).logRecovery("Recovery setup completed", ActivityLogger.SEVERITY_INFO)
            }
            .setNeutralButton(getString(R.string.recovery_copy_all)) { _, _ ->
                copyRecoveryDataToClipboard(words, shards)
            }
            .setCancelable(false)
            .show()
    }
    
    private fun copyRecoveryDataToClipboard(
        words: List<String>,
        shards: List<String>,
    ) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        
        val data = buildString {
            appendLine("Sentinoid Recovery Data")
            appendLine("Generated: ${java.util.Date()}")
            appendLine()
            appendLine("MNEMONIC:")
            appendLine(words.joinToString(" "))
            appendLine()
            appendLine("SHARDS:")
            shards.forEachIndexed { index, shard ->
                appendLine("Shard ${index + 1}: $shard")
            }
        }
        
        val clip = android.content.ClipData.newPlainText("Recovery Data", data)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, getString(R.string.recovery_copied), Toast.LENGTH_SHORT).show()
        ActivityLogger.getInstance(this).logRecovery("Recovery data copied to clipboard", ActivityLogger.SEVERITY_WARNING)
    }

    private fun showRecoverDialog() {
        val options = arrayOf("Recover from Shards", "Recover from Mnemonic")

        AlertDialog.Builder(this)
            .setTitle("Select Recovery Method")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showShardRecoveryDialog()
                    1 -> showMnemonicRecoveryDialog()
                }
            }
            .show()
    }

    private fun showShardRecoveryDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_shard_recovery, null)
        val etShard1 = view.findViewById<TextInputEditText>(R.id.et_shard1)
        val etShard2 = view.findViewById<TextInputEditText>(R.id.et_shard2)
        val etShard3 = view.findViewById<TextInputEditText>(R.id.et_shard3)

        AlertDialog.Builder(this)
            .setTitle("Enter 2 or 3 Shards")
            .setView(view)
            .setPositiveButton("Recover") { _, _ ->
                val shards =
                    listOfNotNull(
                        etShard1.text?.toString()?.trim(),
                        etShard2.text?.toString()?.trim(),
                        etShard3.text?.toString()?.trim(),
                    ).filter { it.isNotEmpty() }

                if (shards.size < 2) {
                    Toast.makeText(this, "Please enter at least 2 shards", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                performShardRecovery(shards)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMnemonicRecoveryDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_mnemonic_recovery, null)
        val etMnemonic = view.findViewById<TextInputEditText>(R.id.et_mnemonic)

        AlertDialog.Builder(this)
            .setTitle("Enter 24-Word Mnemonic")
            .setView(view)
            .setPositiveButton("Recover") { _, _ ->
                val mnemonic = etMnemonic.text?.toString() ?: ""
                val words = mnemonic.split(" ", "\n").filter { it.isNotBlank() }
                if (words.size != 24) {
                    Toast.makeText(this, "Please enter all 24 words", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                performMnemonicRecovery(words)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performShardRecovery(shards: List<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = recoveryManager.recoverFromShards(shards)

            withContext(Dispatchers.Main) {
                when (result) {
                    is RecoveryManager.RecoveryResult.Success -> showRecoverySuccess()
                    is RecoveryManager.RecoveryResult.Error ->
                        Toast.makeText(
                            this@RecoveryActivity,
                            "Recovery failed: ${result.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    private fun performMnemonicRecovery(words: List<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = recoveryManager.recoverFromMnemonic(words)

            withContext(Dispatchers.Main) {
                when (result) {
                    is RecoveryManager.RecoveryResult.Success -> showRecoverySuccess()
                    is RecoveryManager.RecoveryResult.Error ->
                        Toast.makeText(
                            this@RecoveryActivity,
                            "Recovery failed: ${result.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    private fun showRecoverySuccess() {
        AlertDialog.Builder(this)
            .setTitle("Recovery Successful")
            .setMessage("Your vault has been successfully recovered.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showVerifyDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_verify_shard, null)
        val etShard = view.findViewById<TextInputEditText>(R.id.et_shard_verify)

        AlertDialog.Builder(this)
            .setTitle("Verify Shard")
            .setView(view)
            .setPositiveButton("Verify") { _, _ ->
                val shard = etShard.text?.toString() ?: ""
                val isValid = recoveryManager.validateShard(shard)

                AlertDialog.Builder(this)
                    .setTitle("Verification Result")
                    .setMessage(if (isValid) "✓ Shard is valid" else "✗ Invalid shard format")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class ShardAdapter : RecyclerView.Adapter<ShardAdapter.ViewHolder>() {
        private var shards: List<String> = emptyList()

        fun submitList(newShards: List<String>) {
            shards = newShards
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val shard = shards[position]
            holder.bind(position + 1, shard)
        }

        override fun getItemCount() = shards.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val titleView: TextView = view.findViewById(android.R.id.text1)
            private val contentView: TextView = view.findViewById(android.R.id.text2)

            fun bind(
                shardNumber: Int,
                shard: String,
            ) {
                titleView.text = "Shard $shardNumber (2-of-3 required)"
                contentView.text = shard
                contentView.setTextIsSelectable(true)
                contentView.setOnLongClickListener {
                    copyToClipboard("Shard $shardNumber", shard)
                    Toast.makeText(itemView.context, "Shard $shardNumber copied!", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            private fun copyToClipboard(
                label: String,
                text: String,
            ) {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText(label, text)
                clipboard.setPrimaryClip(clip)
            }
        }
    }
}
