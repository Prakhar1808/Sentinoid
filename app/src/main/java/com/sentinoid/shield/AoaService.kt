package com.sentinoid.shield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class AoaService : Service() {
    companion object {
        private const val TAG = "AoaService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sentinoid_aoa_channel"
        
        const val ACTION_START = "com.sentinoid.shield.START_AOA"
        const val ACTION_STOP = "com.sentinoid.shield.STOP_AOA"
        
        private const val AOA_MANUFACTURER = "Sentinoid"
        private const val AOA_MODEL = "SecurityShield"
        private const val AOA_VERSION = "1.0"
        private const val AOA_URL = "https://sentinoid.dev"
        private const val AOA_SERIAL = "SNID001"
    }

    private var usbManager: UsbManager? = null
    private var accessory: UsbAccessory? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAoaService()
            ACTION_STOP -> stopAoaService()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sentinoid USB Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains USB connection to PC for log analysis"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startAoaService() {
        if (isRunning) return
        
        val notification = createNotification("Connecting to PC...")
        startForeground(NOTIFICATION_ID, notification)
        
        isRunning = true
        serviceScope.launch {
            connectToAccessory()
        }
        
        Log.i(TAG, "AOA Service started")
    }

    private fun stopAoaService() {
        isRunning = false
        serviceScope.cancel()
        
        try {
            inputStream?.close()
            outputStream?.close()
            parcelFileDescriptor?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing streams", e)
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "AOA Service stopped")
    }

    private fun createNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Sentinoid Active")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private suspend fun connectToAccessory() = withContext(Dispatchers.IO) {
        while (isRunning) {
            try {
                val accessoryList = usbManager?.accessoryList
                if (accessoryList != null && accessoryList.isNotEmpty()) {
                    accessory = accessoryList[0]
                    
                    if (usbManager?.hasPermission(accessory) == true) {
                        openAccessory(accessory!!)
                        return@withContext
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking accessories", e)
            }
            
            delay(1000)
        }
    }

    private fun openAccessory(acc: UsbAccessory) {
        try {
            parcelFileDescriptor = usbManager?.openAccessory(acc)
            
            if (parcelFileDescriptor != null) {
                val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor
                inputStream = FileInputStream(fileDescriptor)
                outputStream = FileOutputStream(fileDescriptor)
                
                updateNotification("Connected - streaming logs")
                startLogStreaming()
                
                Log.i(TAG, "Accessory opened successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessory", e)
        }
    }

    private fun startLogStreaming() {
        serviceScope.launch {
            val buffer = ByteArray(16384)
            
            while (isRunning) {
                try {
                    val length = inputStream?.read(buffer) ?: -1
                    if (length > 0) {
                        val data = buffer.copyOf(length)
                        onDataReceived(data)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Error reading from accessory", e)
                        delay(1000)
                    }
                }
            }
        }
    }

    private fun onDataReceived(data: ByteArray) {
        val message = String(data, Charsets.UTF_8)
        Log.d(TAG, "Received: $message")
        
        when {
            message.startsWith("PING") -> sendData("PONG")
            message.startsWith("STATUS") -> sendData("OK")
        }
    }

    fun sendData(data: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(data.toByteArray(Charsets.UTF_8))
                Log.d(TAG, "Sent: $data")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending data", e)
            }
        }
    }

    fun sendLogs(logData: String) {
        sendData("LOG:$logData")
    }

    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopAoaService()
        super.onDestroy()
    }
}
