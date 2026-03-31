package com.sentinoid.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.sentinoid.app.bridge.BridgeModeManager
import com.sentinoid.shield.AoaService

class UsbAttachReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "UsbAttachReceiver"
        const val ACTION_USB_PERMISSION = "com.sentinoid.app.USB_PERMISSION"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action

        when (action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                @Suppress("DEPRECATION")
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    Log.i(TAG, "USB device attached: ${it.deviceName}")
                    
                    // Try BridgeModeManager first for serial devices
                    val bridgeManager = BridgeModeManager(context)
                    if (bridgeManager.requestUsbPermission(it)) {
                        bridgeManager.connectToDevice(it)
                    }
                    
                    // Also start AOA service for log streaming
                    val serviceIntent = Intent(context, AoaService::class.java)
                    serviceIntent.action = AoaService.ACTION_START
                    context.startService(serviceIntent)
                }
            }
            
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Log.i(TAG, "USB device detached")
                // Stop AOA service
                val stopIntent = Intent(context, AoaService::class.java)
                stopIntent.action = AoaService.ACTION_STOP
                context.startService(stopIntent)
            }
            
            ACTION_USB_PERMISSION -> {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.i(TAG, "USB permission granted: $granted")
            }
        }
    }
}
