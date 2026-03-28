package com.sentinoid.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.sentinoid.app.bridge.BridgeModeManager

class UsbAttachReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            device?.let {
                // Auto-connect if it's a known bridge device
                val bridgeManager = BridgeModeManager(context)
                if (bridgeManager.requestUsbPermission(it)) {
                    bridgeManager.connectToDevice(it)
                }
            }
        }
    }
}
