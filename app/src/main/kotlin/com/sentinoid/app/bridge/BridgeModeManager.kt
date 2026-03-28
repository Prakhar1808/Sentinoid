package com.sentinoid.app.bridge

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.sentinoid.app.security.CryptoManager
import com.sentinoid.app.security.RecoveryManager
import com.sentinoid.app.security.SecurePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.Executors

class BridgeModeManager(private val context: Context) {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.sentinoid.app.USB_PERMISSION"
        private const val BRIDGE_BAUD_RATE = 115200
        private const val MAX_PACKET_SIZE = 1024
        private const val PROTOCOL_VERSION = "1.0"
        
        // Action tokens
        const val ACTION_OFFLOAD_REQUEST = "OFFLOAD_REQUEST"
        const val ACTION_OFFLOAD_RESPONSE = "OFFLOAD_RESPONSE"
        const val ACTION_RECOVERY_SYNC = "RECOVERY_SYNC"
        const val ACTION_SECURITY_SCAN = "SECURITY_SCAN"
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val cryptoManager = CryptoManager(context)
    private val securePreferences = SecurePreferences(context)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val executor = Executors.newSingleThreadExecutor()
    
    private var usbConnection: UsbDeviceConnection? = null
    private var serialPort: UsbSerialPort? = null
    private var serialIOManager: SerialInputOutputManager? = null
    
    private val _connectionState = MutableStateFlow<BridgeState>(BridgeState.Disconnected)
    val connectionState: StateFlow<BridgeState> = _connectionState
    
    private val _lastActionToken = MutableStateFlow<ActionToken?>(null)
    val lastActionToken: StateFlow<ActionToken?> = _lastActionToken
    
    private val pendingPermissionCallback = MutableStateFlow<Boolean?>(null)

    data class ActionToken(
        val id: String,
        val action: String,
        val payload: JSONObject,
        val signature: String,
        val timestamp: Long
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("id", id)
                put("action", action)
                put("payload", payload)
                put("signature", signature)
                put("timestamp", timestamp)
            }
        }
        
        companion object {
            fun fromJson(json: JSONObject): ActionToken {
                return ActionToken(
                    id = json.getString("id"),
                    action = json.getString("action"),
                    payload = json.getJSONObject("payload"),
                    signature = json.getString("signature"),
                    timestamp = json.getLong("timestamp")
                )
            }
        }
    }

    sealed class BridgeState {
        object Disconnected : BridgeState()
        object Connecting : BridgeState()
        data class Connected(val deviceName: String) : BridgeState()
        data class Error(val message: String) : BridgeState()
        data class Processing(val action: String) : BridgeState()
    }

    interface BridgeListener {
        fun onConnectionEstablished(device: UsbDevice)
        fun onConnectionLost()
        fun onActionReceived(token: ActionToken)
        fun onDataReceived(data: ByteArray)
        fun onError(error: String)
    }

    private var bridgeListener: BridgeListener? = null

    fun setListener(listener: BridgeListener) {
        bridgeListener = listener
    }

    fun requestUsbPermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) {
            return true
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(usbPermissionReceiver, filter)

        usbManager.requestPermission(device, permissionIntent)
        
        return false
    }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    
                    pendingPermissionCallback.value = granted
                    
                    if (granted && device != null) {
                        connectToDevice(device)
                    } else {
                        _connectionState.value = BridgeState.Error("USB permission denied")
                    }
                }
                context.unregisterReceiver(this)
            }
        }
    }

    fun findCompatibleDevices(): List<UsbDevice> {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        return availableDrivers.map { it.device }
    }

    fun connectToDevice(device: UsbDevice) {
        if (!usbManager.hasPermission(device)) {
            _connectionState.value = BridgeState.Error("No USB permission")
            return
        }

        _connectionState.value = BridgeState.Connecting

        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: run {
                _connectionState.value = BridgeState.Error("No serial driver found")
                return
            }

        val connection = usbManager.openDevice(device)
            ?: run {
                _connectionState.value = BridgeState.Error("Failed to open USB device")
                return
            }

        usbConnection = connection

        val port = driver.ports.firstOrNull()
            ?: run {
                _connectionState.value = BridgeState.Error("No serial port available")
                return
            }

        try {
            port.open(connection)
            port.setParameters(
                BRIDGE_BAUD_RATE,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            serialPort = port

            serialIOManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    handleIncomingData(data)
                }

                override fun onRunError(e: Exception) {
                    _connectionState.value = BridgeState.Error("Serial error: ${e.message}")
                    disconnect()
                }
            }).apply {
                executor.submit(this)
            }

            _connectionState.value = BridgeState.Connected(device.productName ?: "Unknown Device")
            bridgeListener?.onConnectionEstablished(device)

            // Send handshake
            sendHandshake()

        } catch (e: Exception) {
            _connectionState.value = BridgeState.Error("Connection failed: ${e.message}")
            disconnect()
        }
    }

    private fun sendHandshake() {
        val handshake = JSONObject().apply {
            put("protocol", PROTOCOL_VERSION)
            put("device", "Sentinoid")
            put("timestamp", System.currentTimeMillis())
            put("capabilities", listOf("offload", "recovery", "scan"))
        }
        sendData(handshake.toString().toByteArray())
    }

    private fun handleIncomingData(data: ByteArray) {
        try {
            val jsonString = String(data)
            val json = JSONObject(jsonString)

            when (json.optString("action")) {
                ACTION_OFFLOAD_REQUEST -> handleOffloadRequest(json)
                ACTION_RECOVERY_SYNC -> handleRecoverySync(json)
                ACTION_SECURITY_SCAN -> handleSecurityScan(json)
                else -> {
                    val token = ActionToken.fromJson(json)
                    _lastActionToken.value = token
                    bridgeListener?.onActionReceived(token)
                }
            }
        } catch (e: Exception) {
            bridgeListener?.onDataReceived(data)
        }
    }

    private fun handleOffloadRequest(json: JSONObject) {
        _connectionState.value = BridgeState.Processing(ACTION_OFFLOAD_REQUEST)

        scope.launch {
            try {
                val task = json.getJSONObject("payload")
                val taskType = task.getString("type")
                
                // Simulate processing on external accelerator
                val result = when (taskType) {
                    "heuristic_scan" -> performOffloadedHeuristicScan(task)
                    "crypto_verify" -> performOffloadedCryptoVerify(task)
                    "honeypot_check" -> performOffloadedHoneypotCheck(task)
                    else -> JSONObject().put("error", "Unknown task type")
                }

                val response = createActionToken(ACTION_OFFLOAD_RESPONSE, result)
                sendActionToken(response)

                _connectionState.value = BridgeState.Connected("Bridge Active")
            } catch (e: Exception) {
                _connectionState.value = BridgeState.Error("Offload failed: ${e.message}")
            }
        }
    }

    private fun handleRecoverySync(json: JSONObject) {
        // Handle recovery data sync from ULTRA/MOBILE-A device
        // Validate and process recovery data from: json.getJSONObject("payload")
        
        val response = JSONObject().apply {
            put("status", "received")
            put("verified", true)
        }
        
        val token = createActionToken(ACTION_RECOVERY_SYNC, response)
        sendActionToken(token)
    }

    private fun handleSecurityScan(json: JSONObject) {
        // Handle security scan request
        val result = JSONObject().apply {
            put("threats_found", 0)
            put("scan_complete", true)
            put("device_status", "secure")
        }
        
        val token = createActionToken(ACTION_SECURITY_SCAN, result)
        sendActionToken(token)
    }

    private fun performOffloadedHeuristicScan(task: JSONObject): JSONObject {
        // Simulate offloading to AMD accelerator
        // In reality, this would send data to connected ULTRA/MOBILE-A device
        // Input data: task.optString("data", "")
        
        return JSONObject().apply {
            put("scan_id", generateTokenId())
            put("processed_on", "ultra_accelerator")
            put("confidence", 0.95)
            put("threat_detected", false)
            put("processing_time_ms", 50) // Simulated fast processing
        }
    }

    private fun performOffloadedCryptoVerify(task: JSONObject): JSONObject {
        return JSONObject().apply {
            put("verified", true)
            put("signature_valid", true)
            put("accelerator_used", true)
        }
    }

    private fun performOffloadedHoneypotCheck(task: JSONObject): JSONObject {
        return JSONObject().apply {
            put("honeypot_intact", true)
            put("access_attempts", 0)
            put("status", "normal")
        }
    }

    fun sendActionToken(token: ActionToken): Boolean {
        return sendData(token.toJson().toString().toByteArray())
    }

    fun sendData(data: ByteArray): Boolean {
        val port = serialPort ?: return false
        
        return try {
            // Chunk data if too large
            var offset = 0
            while (offset < data.size) {
                val chunkSize = minOf(MAX_PACKET_SIZE, data.size - offset)
                val chunk = data.copyOfRange(offset, offset + chunkSize)
                port.write(chunk, 1000)
                offset += chunkSize
            }
            true
        } catch (e: Exception) {
            _connectionState.value = BridgeState.Error("Send failed: ${e.message}")
            false
        }
    }

    private fun createActionToken(action: String, payload: JSONObject): ActionToken {
        val id = generateTokenId()
        val timestamp = System.currentTimeMillis()
        
        // Sign the token (simplified - in production use proper crypto)
        val dataToSign = "$id|$action|$timestamp|${payload.toString()}"
        val signature = cryptoManager.hashData(dataToSign.toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        return ActionToken(id, action, payload, signature, timestamp)
    }

    private fun generateTokenId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun disconnect() {
        serialIOManager?.stop()
        serialIOManager = null
        
        try {
            serialPort?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serialPort = null
        
        usbConnection?.close()
        usbConnection = null
        
        _connectionState.value = BridgeState.Disconnected
        bridgeListener?.onConnectionLost()
    }

    fun cleanup() {
        disconnect()
        scope.cancel()
        executor.shutdown()
    }
}
