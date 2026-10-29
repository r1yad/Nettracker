package com.example.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.data.local.AlertLogRecord
import com.example.data.local.AppDatabase
import com.example.data.local.DailyUsageRecord
import com.example.data.local.PreferencesManager
import com.example.data.model.AlertType
import com.example.data.model.TimeSpan
import com.example.data.network.NetworkStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DataTrackerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var trackingJob: Job? = null
    private lateinit var prefsManager: PreferencesManager
    private lateinit var database: AppDatabase
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, DataTrackerService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, DataTrackerService::class.java)
            try {
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager(applicationContext)
        database = AppDatabase.getDatabase(applicationContext)
        NotificationHelper.createNotificationChannels(this)
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _isServiceRunning.value = true

        // Display initial notification immediately to satisfy foreground service requirements
        val thresholds = prefsManager.getThresholds()
        val initialNotification = NotificationHelper.buildForegroundNotification(
            context = this,
            todayTotalBytes = 0L,
            todayWifiBytes = 0L,
            todayMobileBytes = 0L,
            dailyLimitMb = thresholds.dailyLimitMb
        )
        try {
            startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, initialNotification)
        } catch (e: Exception) {
            // Android 14+ specialUse foreground service start safety
        }

        startTrackingLoop()

        return START_STICKY
    }

    private fun startTrackingLoop() {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            while (isActive) {
                checkAndUpdateUsage()
                delay(25_000) // Check every 25 seconds
            }
        }
    }

    private suspend fun checkAndUpdateUsage() {
        val thresholds = prefsManager.getThresholds()
        val (todayStart, todayEnd) = NetworkStatsHelper.getTimeRange(TimeSpan.TODAY)
        val todayUsage = NetworkStatsHelper.getDeviceUsage(this, todayStart, todayEnd)

        // 1. Update foreground notification
        val notification = NotificationHelper.buildForegroundNotification(
            context = this,
            todayTotalBytes = todayUsage.totalBytes,
            todayWifiBytes = todayUsage.wifiBytes,
            todayMobileBytes = todayUsage.mobileBytes,
            dailyLimitMb = thresholds.dailyLimitMb
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
        } catch (_: Exception) {}

        val todayDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        // 2. Query top app for snapshot record
        val apps = NetworkStatsHelper.getAppsUsage(this, todayStart, todayEnd)
        val topApp = apps.firstOrNull()

        // Persist daily record
        database.appDao().insertOrUpdateDailyUsage(
            DailyUsageRecord(
                date = todayDateKey,
                mobileBytes = todayUsage.mobileBytes,
                wifiBytes = todayUsage.wifiBytes,
                totalBytes = todayUsage.totalBytes,
                topAppName = topApp?.appName,
                topAppBytes = topApp?.totalBytes
            )
        )

        // 3. Threshold Checks (Daily)
        if (thresholds.dailyAlertEnabled && thresholds.dailyLimitMb > 0) {
            val dailyLimitBytes = NetworkStatsHelper.mbToBytes(thresholds.dailyLimitMb)
            val warningBytes = (dailyLimitBytes * (thresholds.dailyWarningPercent / 100.0)).toLong()

            // Daily Exceeded Check
            if (todayUsage.totalBytes >= dailyLimitBytes && !prefsManager.hasDailyExceededFired(todayDateKey)) {
                prefsManager.setDailyExceededFired(todayDateKey)
                NotificationHelper.showThresholdAlert(
                    this,
                    AlertType.DAILY_EXCEEDED,
                    todayUsage.totalBytes,
                    thresholds.dailyLimitMb,
                    isWarning = false
                )
                database.appDao().insertAlertLog(
                    AlertLogRecord(
                        timestamp = System.currentTimeMillis(),
                        type = AlertType.DAILY_EXCEEDED.name,
                        title = "Daily Limit Exceeded",
                        message = "Surpassed ${thresholds.dailyLimitMb} MB daily limit (${NetworkStatsHelper.formatBytes(todayUsage.totalBytes)} used)",
                        usedBytes = todayUsage.totalBytes,
                        thresholdBytes = dailyLimitBytes
                    )
                )
            }
            // Daily Warning Check
            else if (todayUsage.totalBytes >= warningBytes && !prefsManager.hasDailyWarningFired(todayDateKey) && !prefsManager.hasDailyExceededFired(todayDateKey)) {
                prefsManager.setDailyWarningFired(todayDateKey)
                NotificationHelper.showThresholdAlert(
                    this,
                    AlertType.DAILY_WARNING,
                    todayUsage.totalBytes,
                    thresholds.dailyLimitMb,
                    isWarning = true
                )
                database.appDao().insertAlertLog(
                    AlertLogRecord(
                        timestamp = System.currentTimeMillis(),
                        type = AlertType.DAILY_WARNING.name,
                        title = "Daily Warning Reached",
                        message = "Reached ${thresholds.dailyWarningPercent}% of ${thresholds.dailyLimitMb} MB daily quota (${NetworkStatsHelper.formatBytes(todayUsage.totalBytes)} used)",
                        usedBytes = todayUsage.totalBytes,
                        thresholdBytes = warningBytes
                    )
                )
            }
        }

        // 4. Threshold Checks (Monthly)
        if (thresholds.monthlyAlertEnabled && thresholds.monthlyLimitMb > 0) {
            val (monthStart, monthEnd) = NetworkStatsHelper.getBillingCycleRange(thresholds.billingCycleDay)
            val monthUsage = NetworkStatsHelper.getDeviceUsage(this, monthStart, monthEnd)
            val monthlyLimitBytes = NetworkStatsHelper.mbToBytes(thresholds.monthlyLimitMb)
            val monthlyWarningBytes = (monthlyLimitBytes * (thresholds.monthlyWarningPercent / 100.0)).toLong()

            // Monthly Exceeded
            if (monthUsage.totalBytes >= monthlyLimitBytes && !prefsManager.hasMonthlyExceededFired(currentMonthKey)) {
                prefsManager.setMonthlyExceededFired(currentMonthKey)
                NotificationHelper.showThresholdAlert(
                    this,
                    AlertType.MONTHLY_EXCEEDED,
                    monthUsage.totalBytes,
                    thresholds.monthlyLimitMb,
                    isWarning = false
                )
                database.appDao().insertAlertLog(
                    AlertLogRecord(
                        timestamp = System.currentTimeMillis(),
                        type = AlertType.MONTHLY_EXCEEDED.name,
                        title = "Monthly Limit Exceeded",
                        message = "Surpassed ${thresholds.monthlyLimitMb} MB monthly quota (${NetworkStatsHelper.formatBytes(monthUsage.totalBytes)} used)",
                        usedBytes = monthUsage.totalBytes,
                        thresholdBytes = monthlyLimitBytes
                    )
                )
            }
            // Monthly Warning
            else if (monthUsage.totalBytes >= monthlyWarningBytes && !prefsManager.hasMonthlyWarningFired(currentMonthKey) && !prefsManager.hasMonthlyExceededFired(currentMonthKey)) {
                prefsManager.setMonthlyWarningFired(currentMonthKey)
                NotificationHelper.showThresholdAlert(
                    this,
                    AlertType.MONTHLY_WARNING,
                    monthUsage.totalBytes,
                    thresholds.monthlyLimitMb,
                    isWarning = true
                )
                database.appDao().insertAlertLog(
                    AlertLogRecord(
                        timestamp = System.currentTimeMillis(),
                        type = AlertType.MONTHLY_WARNING.name,
                        title = "Monthly Warning Reached",
                        message = "Reached ${thresholds.monthlyWarningPercent}% of ${thresholds.monthlyLimitMb} MB monthly quota",
                        usedBytes = monthUsage.totalBytes,
                        thresholdBytes = monthlyWarningBytes
                    )
                )
            }
        }

        // 5. Daily Summary Report Check
        if (thresholds.dailyReportEnabled) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= 20 && !prefsManager.hasDailyReportSent(todayDateKey)) {
                prefsManager.setDailyReportSent(todayDateKey)
                NotificationHelper.showDailySummary(
                    context = this,
                    todayTotalBytes = todayUsage.totalBytes,
                    wifiBytes = todayUsage.wifiBytes,
                    mobileBytes = todayUsage.mobileBytes,
                    topAppName = topApp?.appName,
                    topAppBytes = topApp?.totalBytes
                )
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder().build()
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    serviceScope.launch { checkAndUpdateUsage() }
                }
                override fun onLost(network: Network) {
                    serviceScope.launch { checkAndUpdateUsage() }
                }
            }
            networkCallback?.let { connectivityManager?.registerNetworkCallback(request, it) }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        trackingJob?.cancel()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
