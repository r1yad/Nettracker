package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AlertLogRecord
import com.example.data.local.AppDatabase
import com.example.data.local.DailyUsageRecord
import com.example.data.local.PreferencesManager
import com.example.data.model.AlertType
import com.example.data.model.TimeSpan
import com.example.data.network.NetworkStatsHelper
import com.example.widget.NetTrackWidgetProvider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DataCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefsManager = PreferencesManager(applicationContext)
        val thresholds = prefsManager.getThresholds()
        val database = AppDatabase.getDatabase(applicationContext)

        val (todayStart, todayEnd) = NetworkStatsHelper.getTimeRange(TimeSpan.TODAY)
        val todayUsage = NetworkStatsHelper.getDeviceUsage(applicationContext, todayStart, todayEnd)

        val todayDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        // Persist daily record
        database.appDao().insertOrUpdateDailyUsage(
            DailyUsageRecord(
                date = todayDateKey,
                mobileBytes = todayUsage.mobileBytes,
                wifiBytes = todayUsage.wifiBytes,
                totalBytes = todayUsage.totalBytes
            )
        )

        // Update home screen widget
        NetTrackWidgetProvider.updateAllWidgets(applicationContext)

        // Threshold checks (Daily)
        if (thresholds.dailyAlertEnabled && thresholds.dailyLimitMb > 0) {
            val dailyLimitBytes = NetworkStatsHelper.mbToBytes(thresholds.dailyLimitMb)
            val warningBytes = (dailyLimitBytes * (thresholds.dailyWarningPercent / 100.0)).toLong()

            if (todayUsage.totalBytes >= dailyLimitBytes && !prefsManager.hasDailyExceededFired(todayDateKey)) {
                prefsManager.setDailyExceededFired(todayDateKey)
                NotificationHelper.showThresholdAlert(
                    applicationContext,
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
            } else if (todayUsage.totalBytes >= warningBytes && !prefsManager.hasDailyWarningFired(todayDateKey) && !prefsManager.hasDailyExceededFired(todayDateKey)) {
                prefsManager.setDailyWarningFired(todayDateKey)
                NotificationHelper.showThresholdAlert(
                    applicationContext,
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
                        message = "Reached ${thresholds.dailyWarningPercent}% of ${thresholds.dailyLimitMb} MB daily quota",
                        usedBytes = todayUsage.totalBytes,
                        thresholdBytes = warningBytes
                    )
                )
            }
        }

        // Monthly check
        if (thresholds.monthlyAlertEnabled && thresholds.monthlyLimitMb > 0) {
            val (monthStart, monthEnd) = NetworkStatsHelper.getBillingCycleRange(thresholds.billingCycleDay)
            val monthUsage = NetworkStatsHelper.getDeviceUsage(applicationContext, monthStart, monthEnd)
            val monthlyLimitBytes = NetworkStatsHelper.mbToBytes(thresholds.monthlyLimitMb)
            val monthlyWarningBytes = (monthlyLimitBytes * (thresholds.monthlyWarningPercent / 100.0)).toLong()

            if (monthUsage.totalBytes >= monthlyLimitBytes && !prefsManager.hasMonthlyExceededFired(currentMonthKey)) {
                prefsManager.setMonthlyExceededFired(currentMonthKey)
                NotificationHelper.showThresholdAlert(
                    applicationContext,
                    AlertType.MONTHLY_EXCEEDED,
                    monthUsage.totalBytes,
                    thresholds.monthlyLimitMb,
                    isWarning = false
                )
            } else if (monthUsage.totalBytes >= monthlyWarningBytes && !prefsManager.hasMonthlyWarningFired(currentMonthKey) && !prefsManager.hasMonthlyExceededFired(currentMonthKey)) {
                prefsManager.setMonthlyWarningFired(currentMonthKey)
                NotificationHelper.showThresholdAlert(
                    applicationContext,
                    AlertType.MONTHLY_WARNING,
                    monthUsage.totalBytes,
                    thresholds.monthlyLimitMb,
                    isWarning = true
                )
            }
        }

        // Daily evening summary check (only scans apps when needed)
        if (thresholds.dailyReportEnabled) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= 20 && !prefsManager.hasDailyReportSent(todayDateKey)) {
                prefsManager.setDailyReportSent(todayDateKey)
                val apps = NetworkStatsHelper.getAppsUsage(applicationContext, todayStart, todayEnd)
                val topApp = apps.firstOrNull()
                NotificationHelper.showDailySummary(
                    context = applicationContext,
                    todayTotalBytes = todayUsage.totalBytes,
                    wifiBytes = todayUsage.wifiBytes,
                    mobileBytes = todayUsage.mobileBytes,
                    topAppName = topApp?.appName,
                    topAppBytes = topApp?.totalBytes
                )
            }
        }

        // If background service is enabled and not running, ensure it starts
        if (thresholds.backgroundServiceEnabled && !DataTrackerService.isServiceRunning.value) {
            DataTrackerService.start(applicationContext)
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nettrack_periodic_checker"

        fun schedule(context: Context) {
            try {
                val workRequest = PeriodicWorkRequestBuilder<DataCheckWorker>(
                    15, TimeUnit.MINUTES,
                    5, TimeUnit.MINUTES
                ).build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
            } catch (_: Exception) {}
        }
    }
}
