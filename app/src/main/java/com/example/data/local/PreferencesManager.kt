package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.UsageThresholds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nettrack_prefs", Context.MODE_PRIVATE)

    private val _thresholdsFlow = MutableStateFlow(getThresholds())
    val thresholdsFlow: StateFlow<UsageThresholds> = _thresholdsFlow.asStateFlow()

    fun getThresholds(): UsageThresholds {
        return UsageThresholds(
            dailyLimitMb = prefs.getInt(KEY_DAILY_LIMIT_MB, 2048), // 2 GB
            dailyWarningPercent = prefs.getInt(KEY_DAILY_WARN_PERCENT, 80),
            dailyAlertEnabled = prefs.getBoolean(KEY_DAILY_ALERT_ENABLED, true),
            monthlyLimitMb = prefs.getInt(KEY_MONTHLY_LIMIT_MB, 30720), // 30 GB
            monthlyWarningPercent = prefs.getInt(KEY_MONTHLY_WARN_PERCENT, 85),
            monthlyAlertEnabled = prefs.getBoolean(KEY_MONTHLY_ALERT_ENABLED, true),
            billingCycleDay = prefs.getInt(KEY_BILLING_CYCLE_DAY, 1),
            backgroundServiceEnabled = prefs.getBoolean(KEY_BG_SERVICE_ENABLED, true),
            dailyReportEnabled = prefs.getBoolean(KEY_DAILY_REPORT_ENABLED, true),
            monthlyReportEnabled = prefs.getBoolean(KEY_MONTHLY_REPORT_ENABLED, true)
        )
    }

    fun saveThresholds(thresholds: UsageThresholds) {
        prefs.edit()
            .putInt(KEY_DAILY_LIMIT_MB, thresholds.dailyLimitMb)
            .putInt(KEY_DAILY_WARN_PERCENT, thresholds.dailyWarningPercent)
            .putBoolean(KEY_DAILY_ALERT_ENABLED, thresholds.dailyAlertEnabled)
            .putInt(KEY_MONTHLY_LIMIT_MB, thresholds.monthlyLimitMb)
            .putInt(KEY_MONTHLY_WARN_PERCENT, thresholds.monthlyWarningPercent)
            .putBoolean(KEY_MONTHLY_ALERT_ENABLED, thresholds.monthlyAlertEnabled)
            .putInt(KEY_BILLING_CYCLE_DAY, thresholds.billingCycleDay)
            .putBoolean(KEY_BG_SERVICE_ENABLED, thresholds.backgroundServiceEnabled)
            .putBoolean(KEY_DAILY_REPORT_ENABLED, thresholds.dailyReportEnabled)
            .putBoolean(KEY_MONTHLY_REPORT_ENABLED, thresholds.monthlyReportEnabled)
            .apply()
        _thresholdsFlow.value = thresholds
    }

    fun hasDailyWarningFired(dateKey: String): Boolean {
        return prefs.getString(KEY_LAST_DAILY_WARN_DATE, "") == dateKey
    }

    fun setDailyWarningFired(dateKey: String) {
        prefs.edit().putString(KEY_LAST_DAILY_WARN_DATE, dateKey).apply()
    }

    fun hasDailyExceededFired(dateKey: String): Boolean {
        return prefs.getString(KEY_LAST_DAILY_EXCEED_DATE, "") == dateKey
    }

    fun setDailyExceededFired(dateKey: String) {
        prefs.edit().putString(KEY_LAST_DAILY_EXCEED_DATE, dateKey).apply()
    }

    fun hasMonthlyWarningFired(monthKey: String): Boolean {
        return prefs.getString(KEY_LAST_MONTHLY_WARN_MONTH, "") == monthKey
    }

    fun setMonthlyWarningFired(monthKey: String) {
        prefs.edit().putString(KEY_LAST_MONTHLY_WARN_MONTH, monthKey).apply()
    }

    fun hasMonthlyExceededFired(monthKey: String): Boolean {
        return prefs.getString(KEY_LAST_MONTHLY_EXCEED_MONTH, "") == monthKey
    }

    fun setMonthlyExceededFired(monthKey: String) {
        prefs.edit().putString(KEY_LAST_MONTHLY_EXCEED_MONTH, monthKey).apply()
    }

    fun hasDailyReportSent(dateKey: String): Boolean {
        return prefs.getString(KEY_LAST_DAILY_REPORT_DATE, "") == dateKey
    }

    fun setDailyReportSent(dateKey: String) {
        prefs.edit().putString(KEY_LAST_DAILY_REPORT_DATE, dateKey).apply()
    }

    companion object {
        private const val KEY_DAILY_LIMIT_MB = "daily_limit_mb"
        private const val KEY_DAILY_WARN_PERCENT = "daily_warn_percent"
        private const val KEY_DAILY_ALERT_ENABLED = "daily_alert_enabled"
        private const val KEY_MONTHLY_LIMIT_MB = "monthly_limit_mb"
        private const val KEY_MONTHLY_WARN_PERCENT = "monthly_warn_percent"
        private const val KEY_MONTHLY_ALERT_ENABLED = "monthly_alert_enabled"
        private const val KEY_BILLING_CYCLE_DAY = "billing_cycle_day"
        private const val KEY_BG_SERVICE_ENABLED = "bg_service_enabled"
        private const val KEY_DAILY_REPORT_ENABLED = "daily_report_enabled"
        private const val KEY_MONTHLY_REPORT_ENABLED = "monthly_report_enabled"

        private const val KEY_LAST_DAILY_WARN_DATE = "last_daily_warn_date"
        private const val KEY_LAST_DAILY_EXCEED_DATE = "last_daily_exceed_date"
        private const val KEY_LAST_MONTHLY_WARN_MONTH = "last_monthly_warn_month"
        private const val KEY_LAST_MONTHLY_EXCEED_MONTH = "last_monthly_exceed_month"
        private const val KEY_LAST_DAILY_REPORT_DATE = "last_daily_report_date"
    }
}
