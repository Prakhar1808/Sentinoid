package com.sentinoid.shield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log

class UsbReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "UsbReceiver"
        const val ACTION_USB_PERMISSION = "com.sentinoid.shield.USB_PERMISSION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(
                    UsbManager.EXTRA_DEVICE
                )
                Log.i(TAG, "USB device attached: ${device?.deviceName}")
                
                val serviceIntent = Intent(context, AoaService::class.java).apply {
                    action = AoaService.ACTION_START
                }
                context.startForegroundService(serviceIntent)
            }
            
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Log.i(TAG, "USB device detached")
                
                val serviceIntent = Intent(context, AoaService::class.java).apply {
                    action = AoaService.ACTION_STOP
                }
                context.startService(serviceIntent)
            }
            
            ACTION_USB_PERMISSION -> {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.i(TAG, "USB permission granted: $granted")
            }
        }
    }
}
