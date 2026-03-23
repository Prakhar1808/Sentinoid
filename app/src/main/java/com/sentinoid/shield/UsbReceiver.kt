package com.sentinoid.shield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

class UsbReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            device?.let {
                Log.i("AOA_Bridge", "HANDSHAKE START: Device Attached - ${it.deviceName}")
                
                if (RootDetector.isDebuggerConnected()) {
                    Log.e("UsbReceiver", "🚨 Unauthorized Debugger Connected via USB!")
                    SilentAlarmManager.triggerLockdown("Unauthorized USB Debugger Attached")
                } else {
                    Log.i("AOA_Bridge", "HANDSHAKE COMPLETE: Bridge is ready.")
                    SilentAlarmManager.setBridgeConnected(true)
                }
            }
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
            Log.i("AOA_Bridge", "Bridge Disconnected.")
            SilentAlarmManager.setBridgeConnected(false)
        }
    }
}