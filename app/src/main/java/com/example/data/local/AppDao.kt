package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDailyUsage(record: DailyUsageRecord)

    @Query("SELECT * FROM daily_usage_records ORDER BY date DESC LIMIT 30")
    fun getRecentUsageRecords(): Flow<List<DailyUsageRecord>>

    @Query("SELECT * FROM daily_usage_records WHERE date = :date LIMIT 1")
    suspend fun getUsageForDate(date: String): DailyUsageRecord?

    @Insert
    suspend fun insertAlertLog(alert: AlertLogRecord): Long

    @Query("SELECT * FROM alert_logs ORDER BY timestamp DESC LIMIT 50")
    fun getAllAlertLogs(): Flow<List<AlertLogRecord>>

    @Query("DELETE FROM alert_logs")
    suspend fun clearAlertLogs()
}
