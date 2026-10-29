package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.PreferencesManager
import com.example.service.DataCheckWorker
import com.example.service.DataTrackerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefsManager = PreferencesManager(context)
            val thresholds = prefsManager.getThresholds()

            // Schedule WorkManager
            DataCheckWorker.schedule(context)

            // Start foreground service if enabled
            if (thresholds.backgroundServiceEnabled) {
                DataTrackerService.start(context)
            }
        }
    }
}
