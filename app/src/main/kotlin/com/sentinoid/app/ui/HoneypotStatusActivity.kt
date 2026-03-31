package com.sentinoid.app.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.sentinoid.app.R
import com.sentinoid.app.SentinoidApp
import com.sentinoid.app.security.ActivityLogger
import com.sentinoid.app.security.HoneypotEngine
import com.sentinoid.app.security.SecurePreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HoneypotStatusActivity : AppCompatActivity() {
    private lateinit var securePreferences: SecurePreferences
    private lateinit var honeypotEngine: HoneypotEngine
    private lateinit var activityLogger: ActivityLogger

    private lateinit var tvHoneypotStatus: MaterialTextView
    private lateinit var tvDecoyFiles: MaterialTextView
    private lateinit var tvAccessAttempts: MaterialTextView
    private lateinit var tvUniqueAttackers: MaterialTextView
    private lateinit var tvLastAccess: MaterialTextView
    private lateinit var tvDecoyLocation: MaterialTextView
    private lateinit var cardHoneypotStatus: MaterialCardView
    private lateinit var recyclerAccessLog: RecyclerView
    private lateinit var btnInitializeHoneypot: MaterialButton
    private lateinit var btnRegenerateDecoys: MaterialButton
    private lateinit var btnPurgeHoneypot: MaterialButton
    private lateinit var btnCheckAccess: MaterialButton

    private val accessLogList = mutableListOf<AccessLogEntry>()
    private lateinit var accessLogAdapter: AccessLogAdapter
    private val handler = Handler(Looper.getMainLooper())

    data class AccessLogEntry(
        val timestamp: Long,
        val fileName: String,
        val operation: String,
        val timeString: String,
        val fullPath: String
    )

    private val honeypotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            val file = intent.getStringExtra("file") ?: return
            val operation = intent.getStringExtra("operation") ?: "EVENT"
            addLogEntry(timestamp, file, operation)
            refreshUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_honeypot_status)

        val app = application as SentinoidApp
        securePreferences = app.securePreferences
        honeypotEngine = HoneypotEngine(this)
        activityLogger = ActivityLogger.getInstance(this)

        initViews()
        setupRecyclerView()
        setupListeners()
        refreshUI()

        // Auto-initialize if not done
        if (!honeypotEngine.isHoneypotInitialized()) {
            honeypotEngine.initializeHoneypot()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val filter = IntentFilter().apply {
                addAction("com.sentinoid.app.HONEYPOT_ACCESS")
                addAction("com.sentinoid.app.HONEYPOT_DECOY_CREATED")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(honeypotReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @SuppressLint("UnspecifiedRegisterReceiverFlag")
                registerReceiver(honeypotReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e("HoneypotStatus", "Register receiver failed: ${e.message}")
        }
        refreshUI()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(honeypotReceiver)
        } catch (e: Exception) {}
        stopAutoRefresh()
    }

    private fun initViews() {
        try {
            tvHoneypotStatus = findViewById(R.id.tv_honeypot_status)
            tvDecoyFiles = findViewById(R.id.tv_decoy_files)
            tvAccessAttempts = findViewById(R.id.tv_access_attempts)
            tvUniqueAttackers = findViewById(R.id.tv_unique_attackers)
            tvLastAccess = findViewById(R.id.tv_last_access)
            tvDecoyLocation = findViewById(R.id.tv_decoy_location)
            cardHoneypotStatus = findViewById(R.id.card_honeypot_status)
            recyclerAccessLog = findViewById(R.id.recycler_access_log)
            btnInitializeHoneypot = findViewById(R.id.btn_initialize_honeypot)
            btnRegenerateDecoys = findViewById(R.id.btn_regenerate_decoys)
            btnPurgeHoneypot = findViewById(R.id.btn_purge_honeypot)
            btnCheckAccess = findViewById(R.id.btn_check_access)
        } catch (e: Exception) {
            Log.e("HoneypotStatus", "View binding error: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        try {
            accessLogAdapter = AccessLogAdapter(accessLogList)
            recyclerAccessLog.layoutManager = LinearLayoutManager(this)
            recyclerAccessLog.adapter = accessLogAdapter
        } catch (e: Exception) {
            Log.e("HoneypotStatus", "Recycler setup error: ${e.message}")
        }
    }

    private fun setupListeners() {
        btnInitializeHoneypot?.setOnClickListener {
            try {
                honeypotEngine.initializeHoneypot()
                Toast.makeText(this, "Honeypot initialized", Toast.LENGTH_SHORT).show()
                refreshUI()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnRegenerateDecoys?.setOnClickListener {
            try {
                Toast.makeText(this, "Regenerating...", Toast.LENGTH_SHORT).show()
                val filePaths = honeypotEngine.regenerateDecoys()
                val stats = honeypotEngine.getHoneypotStats()
                val honeypotDir = honeypotEngine.getHoneypotDirectory()
                refreshUI()

                // Show detailed dialog with decoy information
                val fileList = filePaths.take(5).joinToString("\n") { path ->
                    "• ${File(path).name}"
                }
                val moreFiles = if (filePaths.size > 5) "\n... and ${filePaths.size - 5} more files" else ""

                val message = buildString {
                    appendLine("Decoys regenerated successfully!")
                    appendLine()
                    appendLine("Total files: ${stats.decoyFilesCreated}")
                    appendLine("Location: $honeypotDir")
                    appendLine()
                    appendLine("Files created:")
                    append(fileList)
                    append(moreFiles)
                }

                AlertDialog.Builder(this)
                    .setTitle("Decoys Regenerated")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Copy Path") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Honeypot Path", honeypotDir)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .show()

                Toast.makeText(this, "Done! ${stats.decoyFilesCreated} files created", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HoneypotStatus", "Regenerate error: ${e.message}")
            }
        }

        btnPurgeHoneypot?.setOnClickListener {
            try {
                honeypotEngine.purgeHoneypot()
                Toast.makeText(this, "Honeypot purged", Toast.LENGTH_SHORT).show()
                refreshUI()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnCheckAccess?.setOnClickListener {
            try {
                val events = honeypotEngine.checkForAccess()
                if (events.isNotEmpty()) {
                    Toast.makeText(this, "${events.size} access event(s)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No new access", Toast.LENGTH_SHORT).show()
                }
                refreshUI()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshUI() {
        try {
            val stats = honeypotEngine.getHoneypotStats()

            // Status
            if (honeypotEngine.isHoneypotInitialized()) {
                tvHoneypotStatus?.text = "ACTIVE"
                tvHoneypotStatus?.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                cardHoneypotStatus?.setStrokeColor(ContextCompat.getColor(this, R.color.accent_green))
                btnInitializeHoneypot?.visibility = MaterialButton.GONE
            } else {
                tvHoneypotStatus?.text = "INACTIVE"
                tvHoneypotStatus?.setTextColor(ContextCompat.getColor(this, R.color.warning_yellow))
                cardHoneypotStatus?.setStrokeColor(ContextCompat.getColor(this, R.color.warning_yellow))
                btnInitializeHoneypot?.visibility = MaterialButton.VISIBLE
            }

            // Stats
            tvDecoyFiles?.text = stats.decoyFilesCreated.toString()
            tvAccessAttempts?.text = stats.accessAttempts.toString()
            tvUniqueAttackers?.text = stats.uniqueAttackers.toString()

            // Last access
            tvLastAccess?.text = stats.lastAccessTime?.let {
                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "Never"

            // Decoy location
            val honeypotDir = honeypotEngine.getHoneypotDirectory()
            tvDecoyLocation?.text = "Location: $honeypotDir"

            loadAccessLog()
        } catch (e: Exception) {
            Log.e("HoneypotStatus", "Refresh UI error: ${e.message}")
        }
    }

    private fun loadAccessLog() {
        try {
            accessLogList.clear()
            
            val logs = honeypotEngine.getAccessLog().sortedByDescending { it.timestamp }
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
            
            logs.take(50).forEach { event ->
                val file = File(event.filePath)
                accessLogList.add(AccessLogEntry(
                    timestamp = event.timestamp,
                    fileName = file.name,
                    operation = event.operation,
                    timeString = dateFormat.format(Date(event.timestamp)),
                    fullPath = event.filePath
                ))
            }

            accessLogAdapter?.notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e("HoneypotStatus", "Load log error: ${e.message}")
        }
    }

    private fun addLogEntry(timestamp: Long, filePath: String, operation: String) {
        try {
            val file = File(filePath)
            val entry = AccessLogEntry(
                timestamp = timestamp,
                fileName = file.name,
                operation = operation,
                timeString = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(timestamp)),
                fullPath = filePath
            )
            accessLogList.add(0, entry)
            if (accessLogList.size > 50) accessLogList.removeAt(accessLogList.size - 1)
            accessLogAdapter?.notifyItemInserted(0)
            recyclerAccessLog?.scrollToPosition(0)
        } catch (e: Exception) {
            Log.e("HoneypotStatus", "Add entry error: ${e.message}")
        }
    }

    private var autoRefreshRunnable: Runnable? = null

    private fun startAutoRefresh() {
        autoRefreshRunnable = object : Runnable {
            override fun run() {
                refreshUI()
                handler.postDelayed(this, 2000) // Refresh every 2 seconds
            }
        }
        handler.postDelayed(autoRefreshRunnable!!, 2000)
    }

    private fun stopAutoRefresh() {
        autoRefreshRunnable?.let { handler.removeCallbacks(it) }
    }

    class AccessLogAdapter(private val entries: List<AccessLogEntry>) :
        RecyclerView.Adapter<AccessLogAdapter.ViewHolder>() {

        class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val tvFileName: MaterialTextView = view.findViewById(R.id.tv_log_file)
            val tvOperation: MaterialTextView = view.findViewById(R.id.tv_log_operation)
            val tvTime: MaterialTextView = view.findViewById(R.id.tv_log_time)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_access_log, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            holder.tvFileName.text = entry.fileName
            holder.tvOperation.text = entry.operation
            holder.tvTime.text = entry.timeString

            val color = when (entry.operation) {
                "ACCESSED" -> R.color.error_red
                "DECOY_CREATED" -> R.color.accent_green
                else -> R.color.text_primary
            }
            holder.tvFileName.setTextColor(ContextCompat.getColor(holder.itemView.context, color))
        }

        override fun getItemCount() = entries.size
    }
}
