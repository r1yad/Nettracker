package com.example.data.model

data class NetworkUsageSummary(
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val totalBytes: Long = mobileBytes + wifiBytes,
    val startTime: Long = 0L,
    val endTime: Long = 0L
)

data class AppUsageInfo(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val totalBytes: Long = mobileBytes + wifiBytes,
    val percentageOfTotal: Float = 0f,
    val isSystemApp: Boolean = false
)

data class UsageThresholds(
    val dailyLimitMb: Int = 2048,
    val dailyWarningPercent: Int = 80,
    val dailyAlertEnabled: Boolean = true,
    val monthlyLimitMb: Int = 30720,
    val monthlyWarningPercent: Int = 85,
    val monthlyAlertEnabled: Boolean = true,
    val billingCycleDay: Int = 1,
    val backgroundServiceEnabled: Boolean = true,
    val dailyReportEnabled: Boolean = true,
    val monthlyReportEnabled: Boolean = true
)

enum class TimeSpan(val title: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}

enum class NetworkFilter(val label: String) {
    ALL("All Networks"),
    MOBILE("Mobile Data"),
    WIFI("Wi-Fi")
}

enum class AlertType(val displayName: String) {
    DAILY_WARNING("Daily Warning"),
    DAILY_EXCEEDED("Daily Limit Exceeded"),
    MONTHLY_WARNING("Monthly Warning"),
    MONTHLY_EXCEEDED("Monthly Limit Exceeded"),
    DAILY_REPORT("Daily Summary"),
    MONTHLY_REPORT("Monthly Summary"),
    TEST("Test Alert")
}
