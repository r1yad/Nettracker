package com.example.viewmodel

import android.app.Application
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AlertLogRecord
import com.example.data.local.AppDatabase
import com.example.data.local.DailyUsageRecord
import com.example.data.local.PreferencesManager
import com.example.data.model.AlertType
import com.example.data.model.AppUsageInfo
import com.example.data.model.NetworkFilter
import com.example.data.model.NetworkUsageSummary
import com.example.data.model.TimeSpan
import com.example.data.model.UsageThresholds
import com.example.data.network.NetworkStatsHelper
import com.example.service.DataTrackerService
import com.example.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val hasUsagePermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val isServiceRunning: Boolean = false,
    val todaySummary: NetworkUsageSummary = NetworkUsageSummary(),
    val monthSummary: NetworkUsageSummary = NetworkUsageSummary(),
    val selectedTimeSpan: TimeSpan = TimeSpan.TODAY,
    val selectedFilter: NetworkFilter = NetworkFilter.ALL,
    val searchQuery: String = "",
    val appsList: List<AppUsageInfo> = emptyList(),
    val thresholds: UsageThresholds = UsageThresholds(),
    val recentRecords: List<DailyUsageRecord> = emptyList(),
    val alertLogs: List<AlertLogRecord> = emptyList(),
    val isLoadingApps: Boolean = false,
    val isRefreshing: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)
    private val database = AppDatabase.getDatabase(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var allQueriedApps: List<AppUsageInfo> = emptyList()

    init {
        checkPermissions()
        observePreferencesAndDb()
        refreshData()
    }

    private fun observePreferencesAndDb() {
        // Observe thresholds
        viewModelScope.launch {
            prefsManager.thresholdsFlow.collectLatest { thresholds ->
                _uiState.value = _uiState.value.copy(thresholds = thresholds)
            }
        }

        // Observe background service status
        viewModelScope.launch {
            DataTrackerService.isServiceRunning.collectLatest { running ->
                _uiState.value = _uiState.value.copy(isServiceRunning = running)
            }
        }

        // Observe recent daily records
        viewModelScope.launch {
            database.appDao().getRecentUsageRecords().collectLatest { records ->
                _uiState.value = _uiState.value.copy(recentRecords = records)
            }
        }

        // Observe alert logs
        viewModelScope.launch {
            database.appDao().getAllAlertLogs().collectLatest { logs ->
                _uiState.value = _uiState.value.copy(alertLogs = logs)
            }
        }
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val hasUsage = NetworkStatsHelper.isUsageAccessGranted(context)
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        _uiState.value = _uiState.value.copy(
            hasUsagePermission = hasUsage,
            hasNotificationPermission = hasNotif
        )
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            checkPermissions()

            val context = getApplication<Application>()
            val thresholds = prefsManager.getThresholds()

            withContext(Dispatchers.IO) {
                // Today summary
                val (todayStart, todayEnd) = NetworkStatsHelper.getTimeRange(TimeSpan.TODAY)
                val today = NetworkStatsHelper.getDeviceUsage(context, todayStart, todayEnd)

                // Monthly summary
                val (monthStart, monthEnd) = NetworkStatsHelper.getBillingCycleRange(thresholds.billingCycleDay)
                val month = NetworkStatsHelper.getDeviceUsage(context, monthStart, monthEnd)

                _uiState.value = _uiState.value.copy(
                    todaySummary = today,
                    monthSummary = month
                )

                // Load apps for currently selected time span
                loadAppsForTimeSpan(_uiState.value.selectedTimeSpan)
            }

            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun setTimeSpan(timeSpan: TimeSpan) {
        _uiState.value = _uiState.value.copy(selectedTimeSpan = timeSpan)
        viewModelScope.launch(Dispatchers.IO) {
            loadAppsForTimeSpan(timeSpan)
        }
    }

    fun setFilter(filter: NetworkFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        applyAppFilters()
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyAppFilters()
    }

    private suspend fun loadAppsForTimeSpan(timeSpan: TimeSpan) {
        _uiState.value = _uiState.value.copy(isLoadingApps = true)
        val context = getApplication<Application>()
        val thresholds = prefsManager.getThresholds()
        val (start, end) = NetworkStatsHelper.getTimeRange(timeSpan, thresholds.billingCycleDay)

        allQueriedApps = NetworkStatsHelper.getAppsUsage(context, start, end)
        applyAppFilters()
        _uiState.value = _uiState.value.copy(isLoadingApps = false)
    }

    private fun applyAppFilters() {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val filter = _uiState.value.selectedFilter

        val filtered = allQueriedApps.filter { app ->
            val matchesSearch = query.isEmpty() ||
                    app.appName.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query)

            val matchesFilter = when (filter) {
                NetworkFilter.ALL -> app.totalBytes > 0
                NetworkFilter.MOBILE -> app.mobileBytes > 0
                NetworkFilter.WIFI -> app.wifiBytes > 0
            }

            matchesSearch && matchesFilter
        }.sortedByDescending { app ->
            when (filter) {
                NetworkFilter.ALL -> app.totalBytes
                NetworkFilter.MOBILE -> app.mobileBytes
                NetworkFilter.WIFI -> app.wifiBytes
            }
        }

        _uiState.value = _uiState.value.copy(appsList = filtered)
    }

    fun updateThresholds(thresholds: UsageThresholds) {
        prefsManager.saveThresholds(thresholds)
        if (thresholds.backgroundServiceEnabled) {
            DataTrackerService.start(getApplication())
        } else {
            DataTrackerService.stop(getApplication())
        }
        refreshData()
    }

    fun toggleBackgroundService(enabled: Boolean) {
        val current = _uiState.value.thresholds
        val updated = current.copy(backgroundServiceEnabled = enabled)
        prefsManager.saveThresholds(updated)
        if (enabled) {
            DataTrackerService.start(getApplication())
        } else {
            DataTrackerService.stop(getApplication())
        }
    }

    fun sendTestAlertNotification() {
        val context = getApplication<Application>()
        NotificationHelper.showThresholdAlert(
            context = context,
            type = AlertType.TEST,
            usedBytes = _uiState.value.todaySummary.totalBytes,
            limitMb = _uiState.value.thresholds.dailyLimitMb,
            isWarning = false
        )
        viewModelScope.launch(Dispatchers.IO) {
            database.appDao().insertAlertLog(
                AlertLogRecord(
                    timestamp = System.currentTimeMillis(),
                    type = AlertType.TEST.name,
                    title = "Test Alert Notification",
                    message = "User triggered diagnostic test notification.",
                    usedBytes = _uiState.value.todaySummary.totalBytes,
                    thresholdBytes = NetworkStatsHelper.mbToBytes(_uiState.value.thresholds.dailyLimitMb)
                )
            )
        }
    }

    fun sendDailySummaryNow() {
        val context = getApplication<Application>()
        val today = _uiState.value.todaySummary
        val top = allQueriedApps.firstOrNull()
        NotificationHelper.showDailySummary(
            context = context,
            todayTotalBytes = today.totalBytes,
            wifiBytes = today.wifiBytes,
            mobileBytes = today.mobileBytes,
            topAppName = top?.appName,
            topAppBytes = top?.totalBytes
        )
        viewModelScope.launch(Dispatchers.IO) {
            database.appDao().insertAlertLog(
                AlertLogRecord(
                    timestamp = System.currentTimeMillis(),
                    type = AlertType.DAILY_REPORT.name,
                    title = "Daily Summary Generated",
                    message = "Usage summary: ${NetworkStatsHelper.formatBytes(today.totalBytes)}",
                    usedBytes = today.totalBytes,
                    thresholdBytes = 0L
                )
            )
        }
    }

    fun sendMonthlySummaryNow() {
        val context = getApplication<Application>()
        val month = _uiState.value.monthSummary
        val limit = _uiState.value.thresholds.monthlyLimitMb
        NotificationHelper.showMonthlySummary(
            context = context,
            monthTotalBytes = month.totalBytes,
            monthlyLimitMb = limit
        )
        viewModelScope.launch(Dispatchers.IO) {
            database.appDao().insertAlertLog(
                AlertLogRecord(
                    timestamp = System.currentTimeMillis(),
                    type = AlertType.MONTHLY_REPORT.name,
                    title = "Monthly Summary Generated",
                    message = "Usage summary: ${NetworkStatsHelper.formatBytes(month.totalBytes)}",
                    usedBytes = month.totalBytes,
                    thresholdBytes = NetworkStatsHelper.mbToBytes(limit)
                )
            )
        }
    }

    fun clearAlertHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            database.appDao().clearAlertLogs()
        }
    }
}
