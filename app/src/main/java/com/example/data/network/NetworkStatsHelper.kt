package com.example.data.network

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.data.model.AppUsageInfo
import com.example.data.model.NetworkUsageSummary
import com.example.data.model.TimeSpan
import java.text.DecimalFormat
import java.util.Calendar

object NetworkStatsHelper {

    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun getTimeRange(timeSpan: TimeSpan, billingCycleDay: Int = 1): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        return when (timeSpan) {
            TimeSpan.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            TimeSpan.YESTERDAY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            TimeSpan.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            TimeSpan.THIS_MONTH -> {
                getBillingCycleRange(billingCycleDay, now)
            }
        }
    }

    fun getBillingCycleRange(billingCycleDay: Int, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        val cycleStartCal = Calendar.getInstance().apply { timeInMillis = now }
        cycleStartCal.set(Calendar.HOUR_OF_DAY, 0)
        cycleStartCal.set(Calendar.MINUTE, 0)
        cycleStartCal.set(Calendar.SECOND, 0)
        cycleStartCal.set(Calendar.MILLISECOND, 0)

        val targetDay = billingCycleDay.coerceIn(1, 28)
        if (currentDay >= targetDay) {
            cycleStartCal.set(Calendar.DAY_OF_MONTH, targetDay)
        } else {
            cycleStartCal.add(Calendar.MONTH, -1)
            cycleStartCal.set(Calendar.DAY_OF_MONTH, targetDay)
        }

        return Pair(cycleStartCal.timeInMillis, now)
    }

    fun getDeviceUsage(
        context: Context,
        startTime: Long,
        endTime: Long
    ): NetworkUsageSummary {
        if (!isUsageAccessGranted(context)) {
            // Fallback approximation via TrafficStats if usage access not yet granted
            val totalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
            val totalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
            val mobileRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0L)
            val mobileTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0L)
            val mobile = mobileRx + mobileTx
            val total = totalRx + totalTx
            val wifi = (total - mobile).coerceAtLeast(0L)
            return NetworkUsageSummary(
                mobileBytes = mobile,
                wifiBytes = wifi,
                totalBytes = total,
                startTime = startTime,
                endTime = endTime
            )
        }

        val networkStatsManager =
            context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
                ?: return NetworkUsageSummary(startTime = startTime, endTime = endTime)

        var wifiBytes = 0L
        var mobileBytes = 0L

        // Query Wi-Fi summary
        try {
            val wifiBucket = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_WIFI,
                null,
                startTime,
                endTime
            )
            wifiBytes = wifiBucket.rxBytes + wifiBucket.txBytes
        } catch (_: Exception) {}

        // Query Mobile summary
        try {
            val mobileBucket = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime
            )
            mobileBytes = mobileBucket.rxBytes + mobileBucket.txBytes
        } catch (_: Exception) {}

        // Some devices don't report via querySummaryForDevice, fallback to querying apps aggregation
        if (wifiBytes == 0L && mobileBytes == 0L) {
            val apps = getAppsUsage(context, startTime, endTime)
            mobileBytes = apps.sumOf { it.mobileBytes }
            wifiBytes = apps.sumOf { it.wifiBytes }
        }

        return NetworkUsageSummary(
            mobileBytes = mobileBytes,
            wifiBytes = wifiBytes,
            totalBytes = mobileBytes + wifiBytes,
            startTime = startTime,
            endTime = endTime
        )
    }

    fun getAppsUsage(
        context: Context,
        startTime: Long,
        endTime: Long
    ): List<AppUsageInfo> {
        val networkStatsManager =
            context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
                ?: return emptyList()

        if (!isUsageAccessGranted(context)) {
            return emptyList()
        }

        val pm = context.packageManager
        val uidMobileMap = mutableMapOf<Int, Long>()
        val uidWifiMap = mutableMapOf<Int, Long>()

        // 1. Mobile data per UID
        try {
            val mobileStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime
            )
            val bucket = NetworkStats.Bucket()
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                val uid = bucket.uid
                val bytes = bucket.rxBytes + bucket.txBytes
                uidMobileMap[uid] = (uidMobileMap[uid] ?: 0L) + bytes
            }
            mobileStats.close()
        } catch (_: Exception) {}

        // 2. Wi-Fi data per UID
        try {
            val wifiStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_WIFI,
                null,
                startTime,
                endTime
            )
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                val uid = bucket.uid
                val bytes = bucket.rxBytes + bucket.txBytes
                uidWifiMap[uid] = (uidWifiMap[uid] ?: 0L) + bytes
            }
            wifiStats.close()
        } catch (_: Exception) {}

        val allUids = (uidMobileMap.keys + uidWifiMap.keys).toSet()
        var grandTotal = 0L

        val appUsageList = mutableListOf<AppUsageInfo>()

        for (uid in allUids) {
            val mobile = uidMobileMap[uid] ?: 0L
            val wifi = uidWifiMap[uid] ?: 0L
            val total = mobile + wifi
            if (total <= 0L) continue

            grandTotal += total

            val (appName, packageName, isSystem) = resolveUidAppInfo(pm, uid)
            appUsageList.add(
                AppUsageInfo(
                    uid = uid,
                    packageName = packageName,
                    appName = appName,
                    mobileBytes = mobile,
                    wifiBytes = wifi,
                    totalBytes = total,
                    isSystemApp = isSystem
                )
            )
        }

        val nonZeroGrandTotal = if (grandTotal > 0L) grandTotal else 1L

        return appUsageList
            .map { it.copy(percentageOfTotal = (it.totalBytes.toFloat() / nonZeroGrandTotal).coerceIn(0f, 1f)) }
            .sortedByDescending { it.totalBytes }
    }

    private fun resolveUidAppInfo(pm: PackageManager, uid: Int): Triple<String, String, Boolean> {
        when (uid) {
            0 -> return Triple("OS Kernel", "android.kernel", true)
            1000 -> return Triple("Android System", "android.system", true)
            1001 -> return Triple("Telephony Service", "com.android.phone", true)
            1013 -> return Triple("Media Server", "android.media", true)
            -4 -> return Triple("Removed Applications", "android.uid.removed", false)
            -5 -> return Triple("Tethering & Hotspot", "android.uid.tethering", false)
        }

        val packages = pm.getPackagesForUid(uid)
        if (!packages.isNullOrEmpty()) {
            val packageName = packages[0]
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                return Triple(label, packageName, isSystem)
            } catch (_: Exception) {
                return Triple(packageName, packageName, false)
            }
        }

        return Triple("UID #$uid", "uid.$uid", false)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val boundedGroup = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, boundedGroup.toDouble())
        val df = if (boundedGroup >= 2) DecimalFormat("#,##0.00") else DecimalFormat("#,##0.0")
        return "${df.format(value)} ${units[boundedGroup]}"
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun mbToBytes(mb: Int): Long = mb.toLong() * 1024L * 1024L
    fun bytesToMb(bytes: Long): Float = bytes / (1024f * 1024f)
}
