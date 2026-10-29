package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AlertType
import com.example.data.network.NetworkStatsHelper

object NotificationHelper {

    const val CHANNEL_MONITOR = "nettrack_channel_monitor"
    const val CHANNEL_ALERTS = "nettrack_channel_alerts"
    const val CHANNEL_REPORTS = "nettrack_channel_reports"

    const val NOTIFICATION_ID_FOREGROUND = 1001
    const val NOTIFICATION_ID_ALERT_DAILY = 2001
    const val NOTIFICATION_ID_ALERT_MONTHLY = 2002
    const val NOTIFICATION_ID_REPORT_DAILY = 3001
    const val NOTIFICATION_ID_REPORT_MONTHLY = 3002
    const val NOTIFICATION_ID_TEST = 4001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Ongoing monitor channel
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR,
                "Live Data Usage Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows continuous internet usage tracking in the background"
                setShowBadge(false)
            }

            // 2. High priority threshold alerts channel
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Data Threshold Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warns when daily or monthly internet data limits are surpassed"
                enableVibration(true)
                setShowBadge(true)
            }

            // 3. Periodic summary reports channel
            val reportChannel = NotificationChannel(
                CHANNEL_REPORTS,
                "Usage Reports & Summaries",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily and monthly usage summaries and top consuming apps"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(monitorChannel, alertChannel, reportChannel)
            )
        }
    }

    private fun getAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildForegroundNotification(
        context: Context,
        todayTotalBytes: Long,
        todayWifiBytes: Long,
        todayMobileBytes: Long,
        dailyLimitMb: Int
    ): Notification {
        val todayStr = NetworkStatsHelper.formatBytes(todayTotalBytes)
        val wifiStr = NetworkStatsHelper.formatBytes(todayWifiBytes)
        val mobileStr = NetworkStatsHelper.formatBytes(todayMobileBytes)
        val limitStr = if (dailyLimitMb > 0) "${dailyLimitMb} MB limit" else "No limit"

        val percent = if (dailyLimitMb > 0) {
            val limitBytes = NetworkStatsHelper.mbToBytes(dailyLimitMb)
            ((todayTotalBytes.toDouble() / limitBytes) * 100).toInt().coerceAtMost(100)
        } else 0

        val contentText = "Today: $todayStr (Mobile: $mobileStr, Wi-Fi: $wifiStr) • $limitStr"

        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("NetTrack • Monitoring Active")
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Today's Usage: $todayStr\n• Mobile: $mobileStr\n• Wi-Fi: $wifiStr\n• Daily Quota: $percent% used"
                )
            )
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(getAppPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun showThresholdAlert(
        context: Context,
        type: AlertType,
        usedBytes: Long,
        limitMb: Int,
        isWarning: Boolean
    ) {
        val usedFormatted = NetworkStatsHelper.formatBytes(usedBytes)
        val limitFormatted = "${limitMb} MB"

        val (title, body, notificationId) = when (type) {
            AlertType.DAILY_WARNING -> Triple(
                "⚠️ Daily Data Warning",
                "You have used $usedFormatted of your $limitFormatted daily quota.",
                NOTIFICATION_ID_ALERT_DAILY
            )
            AlertType.DAILY_EXCEEDED -> Triple(
                "🚨 Daily Data Limit Exceeded!",
                "You have surpassed your $limitFormatted daily limit! Current usage: $usedFormatted.",
                NOTIFICATION_ID_ALERT_DAILY
            )
            AlertType.MONTHLY_WARNING -> Triple(
                "⚠️ Monthly Data Warning",
                "You have reached warning threshold on your $limitFormatted monthly quota ($usedFormatted used).",
                NOTIFICATION_ID_ALERT_MONTHLY
            )
            AlertType.MONTHLY_EXCEEDED -> Triple(
                "🚨 Monthly Data Limit Exceeded!",
                "Monthly data quota of $limitFormatted surpassed! Total used: $usedFormatted.",
                NOTIFICATION_ID_ALERT_MONTHLY
            )
            AlertType.TEST -> Triple(
                "🔔 NetTrack Test Alert",
                "Notifications are configured and working properly! Live tracking is active.",
                NOTIFICATION_ID_TEST
            )
            else -> Triple(
                "NetTrack Alert",
                "Data usage threshold reached: $usedFormatted",
                NOTIFICATION_ID_ALERT_DAILY
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(getAppPendingIntent(context))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }

    fun showDailySummary(
        context: Context,
        todayTotalBytes: Long,
        wifiBytes: Long,
        mobileBytes: Long,
        topAppName: String? = null,
        topAppBytes: Long? = null
    ) {
        val totalFormatted = NetworkStatsHelper.formatBytes(todayTotalBytes)
        val wifiFormatted = NetworkStatsHelper.formatBytes(wifiBytes)
        val mobileFormatted = NetworkStatsHelper.formatBytes(mobileBytes)

        val topAppSummary = if (topAppName != null && topAppBytes != null && topAppBytes > 0) {
            "\nTop Consumer: $topAppName (${NetworkStatsHelper.formatBytes(topAppBytes)})"
        } else ""

        val summaryText = "You used $totalFormatted today (Mobile: $mobileFormatted, Wi-Fi: $wifiFormatted).$topAppSummary"

        val notification = NotificationCompat.Builder(context, CHANNEL_REPORTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📊 Daily Data Summary")
            .setContentText("Total data consumed today: $totalFormatted")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(getAppPendingIntent(context))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_REPORT_DAILY, notification)
        } catch (_: SecurityException) {}
    }

    fun showMonthlySummary(
        context: Context,
        monthTotalBytes: Long,
        monthlyLimitMb: Int
    ) {
        val totalFormatted = NetworkStatsHelper.formatBytes(monthTotalBytes)
        val percent = if (monthlyLimitMb > 0) {
            val limitBytes = NetworkStatsHelper.mbToBytes(monthlyLimitMb)
            ((monthTotalBytes.toDouble() / limitBytes) * 100).toInt()
        } else 0

        val text = "You have consumed $totalFormatted this billing period ($percent% of ${monthlyLimitMb} MB limit)."

        val notification = NotificationCompat.Builder(context, CHANNEL_REPORTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📅 Monthly Data Summary")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(getAppPendingIntent(context))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_REPORT_MONTHLY, notification)
        } catch (_: SecurityException) {}
    }
}
