package com.sentinoid.shield

import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Handles the actual USB AOA (Android Open Accessory) data transfer.
 */
object BridgeMode {
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null

    fun initiateHandshake(manager: UsbManager, accessory: UsbAccessory) {
        try {
            fileDescriptor = manager.openAccessory(accessory)
            fileDescriptor?.let {
                val fd = it.fileDescriptor
                inputStream = FileInputStream(fd)
                outputStream = FileOutputStream(fd)

                Log.i("BridgeMode", "AOA Tunnel Established. Listening for Workstation commands...")
                
                // Start a background thread to listen for data
                thread(start = true) {
                    handleIncomingData()
                }
            }
        } catch (e: Exception) {
            Log.e("BridgeMode", "Failed to open USB accessory tunnel", e)
        }
    }

    private fun handleIncomingData() {
        val buffer = ByteArray(16384)
        try {
            while (true) {
                val bytesRead = inputStream?.read(buffer) ?: -1
                if (bytesRead > 0) {
                    val message = String(buffer, 0, bytesRead)
                    Log.d("BridgeMode", "Received from Workstation: $message")
                    
                    // Logic to handle AI Model updates or Forensic requests
                    if (message.contains("PUSH_AI_MODEL")) {
                        Log.i("BridgeMode", "Workstation is pushing a new Heuristic Model...")
                        // TODO: Receive file stream and save to internal storage
                    }
                } else if (bytesRead == -1) break
            }
        } catch (e: IOException) {
            Log.e("BridgeMode", "Bridge tunnel disconnected", e)
        } finally {
            closeBridge()
        }
    }

    fun sendTacticalLogs(encryptedLogs: ByteArray) {
        try {
            outputStream?.write(encryptedLogs)
            outputStream?.flush()
            Log.i("BridgeMode", "Tactical Logs successfully offloaded to Workstation.")
        } catch (e: Exception) {
            Log.e("BridgeMode", "Failed to send logs via Bridge", e)
        }
    }

    fun closeBridge() {
        try {
            fileDescriptor?.close()
            inputStream?.close()
            outputStream?.close()
        } catch (e: Exception) {}
        fileDescriptor = null
        Log.i("BridgeMode", "Bridge Tunnel Closed.")
    }
}
