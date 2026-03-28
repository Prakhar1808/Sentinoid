package com.sentinoid.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sentinoid.app.service.WatchdogService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start watchdog service on boot
            val watchdogIntent = Intent(context, WatchdogService::class.java).apply {
                action = WatchdogService.ACTION_START
            }
            context.startService(watchdogIntent)
        }
    }
}
