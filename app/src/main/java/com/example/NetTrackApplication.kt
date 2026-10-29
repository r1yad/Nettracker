package com.example

import android.app.Application
import com.example.data.local.PreferencesManager
import com.example.service.DataCheckWorker
import com.example.service.DataTrackerService
import com.example.service.NotificationHelper

class NetTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Initialize notification channels
            NotificationHelper.createNotificationChannels(this)
        } catch (_: Exception) {}

        try {
            // Schedule periodic background checking
            DataCheckWorker.schedule(this)
        } catch (_: Exception) {}

        try {
            // Start background service if enabled in preferences
            val prefs = PreferencesManager(this)
            if (prefs.getThresholds().backgroundServiceEnabled) {
                DataTrackerService.start(this)
            }
        } catch (_: Exception) {}
    }
}
