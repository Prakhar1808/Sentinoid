package com.sentinoid.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.sentinoid.app.R
import com.sentinoid.app.security.ActivityLogger
import org.json.JSONObject

class LogsDashboardActivity : AppCompatActivity() {
    
    private lateinit var activityLogger: ActivityLogger
    private lateinit var recyclerLogs: RecyclerView
    private lateinit var logsAdapter: LogsAdapter
    private lateinit var spinnerCategory: Spinner
    private lateinit var tvStats: TextView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnClear: MaterialButton
    private lateinit var btnExport: MaterialButton
    private lateinit var containerStats: LinearLayout
    
    private var currentCategory: String? = null
    private var logsList = mutableListOf<LogItem>()
    
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.sentinoid.app.LOG_EVENT") {
                refreshLogs()
            }
        }
    }
    
    data class LogItem(
        val timestamp: Long,
        val timeFormatted: String,
        val category: String,
        val severity: String,
        val message: String,
        val details: JSONObject?
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs_dashboard)
        
        activityLogger = ActivityLogger.getInstance(this)
        
        setupUI()
        setupSpinner()
        refreshLogs()
        updateStats()
        registerReceiver()
    }
    
    private fun setupUI() {
        recyclerLogs = findViewById(R.id.recycler_logs)
        spinnerCategory = findViewById(R.id.spinner_category)
        tvStats = findViewById(R.id.tv_stats)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnClear = findViewById(R.id.btn_clear)
        btnExport = findViewById(R.id.btn_export)
        containerStats = findViewById(R.id.container_stats)
        
        recyclerLogs.layoutManager = LinearLayoutManager(this)
        logsAdapter = LogsAdapter(logsList) { log ->
            showLogDetails(log)
        }
        recyclerLogs.adapter = logsAdapter
        
        btnRefresh.setOnClickListener {
            refreshLogs()
            updateStats()
        }
        
        btnClear.setOnClickListener {
            showClearDialog()
        }
        
        btnExport.setOnClickListener {
            exportLogs()
        }
    }
    
    private fun setupSpinner() {
        val categories = listOf(
            "All Categories",
            ActivityLogger.CATEGORY_WATCHDOG,
            ActivityLogger.CATEGORY_HONEYPOT,
            ActivityLogger.CATEGORY_FPM,
            ActivityLogger.CATEGORY_BRIDGE,
            ActivityLogger.CATEGORY_ATMOSPHERE,
            ActivityLogger.CATEGORY_RECOVERY,
            ActivityLogger.CATEGORY_CRYPTO,
            ActivityLogger.CATEGORY_TAMPER,
            ActivityLogger.CATEGORY_SYSTEM
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter
        
        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentCategory = if (position == 0) null else categories[position]
                refreshLogs()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun refreshLogs() {
        logsList.clear()
        
        val logs = if (currentCategory != null) {
            activityLogger.getLogsByCategory(currentCategory!!, 100)
        } else {
            activityLogger.getAllLogs(100)
        }
        
        logs.forEach { entry ->
            logsList.add(LogItem(
                timestamp = entry.timestamp,
                timeFormatted = entry.toJson().getString("timeFormatted"),
                category = entry.category,
                severity = entry.severity,
                message = entry.message,
                details = entry.toJson().optJSONObject("details")
            ))
        }
        
        logsAdapter.notifyDataSetChanged()
        
        if (logsList.isEmpty()) {
            tvStats.text = "No logs available"
        } else {
            tvStats.text = "Showing ${logsList.size} logs"
        }
    }
    
    private fun updateStats() {
        containerStats.removeAllViews()
        
        val stats = activityLogger.getLogStats()
        
        stats.forEach { (category, count) ->
            val chip = Chip(this).apply {
                text = "$category: $count"
                setChipBackgroundColorResource(getCategoryColor(category))
                isCheckable = false
                setOnClickListener {
                    // Select this category in spinner
                    val position = (spinnerCategory.adapter as ArrayAdapter<String>).getPosition(category)
                    if (position >= 0) {
                        spinnerCategory.setSelection(position)
                    }
                }
            }
            containerStats.addView(chip)
        }
    }
    
    private fun getCategoryColor(category: String): Int {
        return when (category) {
            ActivityLogger.CATEGORY_TAMPER -> R.color.error_red
            ActivityLogger.CATEGORY_WATCHDOG -> R.color.warning_yellow
            ActivityLogger.CATEGORY_HONEYPOT -> R.color.accent_purple
            ActivityLogger.CATEGORY_FPM -> R.color.accent_blue
            ActivityLogger.CATEGORY_BRIDGE -> R.color.accent_cyan
            ActivityLogger.CATEGORY_ATMOSPHERE -> R.color.accent_green
            else -> R.color.card_background
        }
    }
    
    private fun showLogDetails(log: LogItem) {
        val details = StringBuilder()
        details.appendLine("Time: ${log.timeFormatted}")
        details.appendLine("Category: ${log.category}")
        details.appendLine("Severity: ${log.severity}")
        details.appendLine()
        details.appendLine("Message:")
        details.appendLine(log.message)
        
        log.details?.let {
            if (it.length() > 0) {
                details.appendLine()
                details.appendLine("Details:")
                it.keys().forEach { key ->
                    details.appendLine("  $key: ${it.getString(key)}")
                }
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Log Entry Details")
            .setMessage(details.toString())
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showClearDialog() {
        val options = if (currentCategory != null) {
            arrayOf("Clear ${currentCategory} logs", "Clear ALL logs", "Cancel")
        } else {
            arrayOf("Clear ALL logs", "Cancel")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Clear Logs")
            .setItems(options) { _, which ->
                when {
                    which == 0 && currentCategory != null -> {
                        activityLogger.clearLogsByCategory(currentCategory!!)
                        refreshLogs()
                        updateStats()
                    }
                    which == 0 || (which == 1 && currentCategory != null) -> {
                        activityLogger.clearLogs()
                        refreshLogs()
                        updateStats()
                    }
                }
            }
            .show()
    }
    
    private fun exportLogs() {
        val exported = activityLogger.exportLogs()
        
        AlertDialog.Builder(this)
            .setTitle("Export Logs")
            .setMessage("Logs exported. Length: ${exported.length} characters")
            .setPositiveButton("Copy to Clipboard") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Sentinoid Logs", exported)
                clipboard.setPrimaryClip(clip)
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun registerReceiver() {
        val filter = IntentFilter("com.sentinoid.app.LOG_EVENT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }
    }
    
    override fun onResume() {
        super.onResume()
        refreshLogs()
        updateStats()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(logReceiver)
        } catch (e: Exception) {}
    }
    
    // RecyclerView Adapter
    class LogsAdapter(
        private val logs: List<LogItem>,
        private val onItemClick: (LogItem) -> Unit
    ) : RecyclerView.Adapter<LogsAdapter.ViewHolder>() {
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.card_log)
            val tvTime: TextView = view.findViewById(R.id.tv_log_time)
            val tvCategory: TextView = view.findViewById(R.id.tv_log_category)
            val tvSeverity: TextView = view.findViewById(R.id.tv_log_severity)
            val tvMessage: TextView = view.findViewById(R.id.tv_log_message)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log_entry, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val log = logs[position]
            
            holder.tvTime.text = log.timeFormatted
            holder.tvCategory.text = log.category
            holder.tvSeverity.text = log.severity
            holder.tvMessage.text = log.message
            
            // Color coding based on severity
            val colorRes = when (log.severity) {
                ActivityLogger.SEVERITY_CRITICAL -> R.color.error_red
                ActivityLogger.SEVERITY_WARNING -> R.color.warning_yellow
                ActivityLogger.SEVERITY_INFO -> R.color.accent_green
                else -> R.color.text_secondary
            }
            holder.tvSeverity.setTextColor(ContextCompat.getColor(holder.itemView.context, colorRes))
            
            holder.card.setOnClickListener { onItemClick(log) }
        }
        
        override fun getItemCount() = logs.size
    }
}
